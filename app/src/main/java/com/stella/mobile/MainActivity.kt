package com.stella.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var prefs: SharedPreferences

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var mode = "single"
    private var laptopHost: String = ""
    private var laptopPort: Int = 8766
    private var laptopToken: String = ""
    private var laptopOnline = false

    private lateinit var nsd: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("stella_v4", Context.MODE_PRIVATE)
        laptopHost = prefs.getString("laptop_host", "") ?: ""
        laptopPort = prefs.getInt("laptop_port", 8766)
        laptopToken = prefs.getString("laptop_token", "") ?: ""
        nsd = getSystemService(NSD_SERVICE) as NsdManager

        buildUi()
        requestMicPermission()
        refreshConnection()
    }

    override fun onDestroy() {
        stopDiscovery()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 20)
            setBackgroundColor(Color.rgb(9, 11, 15))
        }

        val title = TextView(this).apply {
            text = "STELLA V4"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        status = TextView(this).apply {
            text = "● CHECKING"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 14)
            setTextColor(Color.LTGRAY)
        }

        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun modeButton(label: String, value: String): Button = Button(this).apply {
            text = label
            setOnClickListener {
                mode = value
                status.text = "● ${if (laptopOnline) "LAPTOP" else "PHONE"} • ${value.uppercase()}"
            }
        }
        modes.addView(modeButton("Single", "single"))
        modes.addView(modeButton("Multiple", "multiple"))
        modes.addView(modeButton("Sleep", "sleep"))

        input = EditText(this).apply {
            hint = "Talk to Stella…"
            minLines = 2
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val send = Button(this).apply {
            text = "SEND"
            setOnClickListener {
                val text = input.text.toString().trim()
                input.setText("")
                ask(text)
            }
        }

        val mic = Button(this).apply {
            text = "🎙 VOICE"
            setOnClickListener { listen() }
        }

        val laptop = Button(this).apply {
            text = "PAIR / LAPTOP"
            setOnClickListener { pairDialog() }
        }

        val memory = Button(this).apply {
            text = "MEMORY"
            setOnClickListener { showMemory() }
        }

        val key = Button(this).apply {
            text = "PHONE API KEY"
            setOnClickListener { keyDialog() }
        }

        output = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(225, 231, 240))
            setPadding(0, 18, 0, 0)
        }

        val scroll = ScrollView(this).apply { addView(output) }

        root.addView(title)
        root.addView(status)
        root.addView(modes)
        root.addView(input)
        root.addView(send)
        root.addView(mic)
        root.addView(laptop)
        root.addView(memory)
        root.addView(key)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun pairDialog() {
        val code = EditText(this).apply {
            hint = "6-digit code shown by laptop Stella"
            inputType = 2
        }
        val host = EditText(this).apply {
            hint = "Laptop IP (optional — auto discovery first)"
            setText(laptopHost)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 0)
            addView(code)
            addView(host)
        }

        AlertDialog.Builder(this)
            .setTitle("Connect to laptop Stella")
            .setMessage("Start Stella V4 on the laptop. Its Phone Sync panel shows a one-time pairing code. Leave the IP blank if both devices are on the same Wi-Fi.")
            .setView(box)
            .setPositiveButton("PAIR") { _, _ ->
                val pairingCode = code.text.toString().trim()
                val manualHost = host.text.toString().trim()
                if (pairingCode.length != 6) {
                    output.text = "Enter the 6-digit pairing code from laptop Stella."
                } else {
                    pairToLaptop(pairingCode, manualHost)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pairToLaptop(code: String, manualHost: String) {
        output.text = "Finding laptop Stella…"
        if (manualHost.isNotBlank()) {
            postPair(manualHost, laptopPort, code)
            return
        }
        discoverLaptop { host, port -> postPair(host, port, code) }
    }

    private fun postPair(host: String, port: Int, code: String) {
        val payload = JSONObject()
            .put("code", code)
            .put("device_name", "Stella Android")
            .put("created_at", System.currentTimeMillis().toString())
            .toString()

        val request = Request.Builder()
            .url("http://$host:$port/api/pair")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { output.text = "Could not reach laptop: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    runOnUiThread { output.text = "Pairing failed: $body" }
                    return
                }
                try {
                    val obj = JSONObject(body)
                    laptopToken = obj.getString("token")
                    laptopHost = host
                    laptopPort = port
                    prefs.edit()
                        .putString("laptop_token", laptopToken)
                        .putString("laptop_host", laptopHost)
                        .putInt("laptop_port", laptopPort)
                        .apply()
                    runOnUiThread { output.text = "Laptop paired. Syncing Stella memory…" }
                    laptopOnline = true
                    syncMemory()
                    updateStatus()
                } catch (e: Exception) {
                    runOnUiThread { output.text = "Invalid pairing response: ${e.message}" }
                }
            }
        })
    }

    private fun refreshConnection() {
        if (laptopToken.isBlank()) {
            updateStatus()
            return
        }

        if (laptopHost.isNotBlank()) {
            checkLaptop(laptopHost, laptopPort) {
                if (!laptopOnline) discoverLaptop { host, port -> checkLaptop(host, port) { syncMemory() } }
                else syncMemory()
            }
        } else {
            discoverLaptop { host, port -> checkLaptop(host, port) { syncMemory() } }
        }
    }

    private fun checkLaptop(host: String, port: Int, after: () -> Unit) {
        val request = Request.Builder()
            .url("http://$host:$port/api/status")
            .header("X-Stella-Token", laptopToken)
            .get()
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                laptopOnline = false
                runOnUiThread { updateStatus() }
                after()
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                if (ok) {
                    laptopHost = host
                    laptopPort = port
                    laptopOnline = true
                    prefs.edit().putString("laptop_host", host).putInt("laptop_port", port).apply()
                } else {
                    laptopOnline = false
                }
                runOnUiThread { updateStatus() }
                after()
            }
        })
    }

    private fun discoverLaptop(onFound: (String, Int) -> Unit) {
        stopDiscovery()
        var handled = false
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (handled) return
                if (serviceInfo.serviceType.contains("_stella._tcp")) {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            if (handled) return
                            handled = true
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port
                            stopDiscovery()
                            runOnUiThread { output.text = "Found laptop Stella at $host:$port" }
                            onFound(host, port)
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { stopDiscovery() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stopDiscovery() }
        }
        discoveryListener = listener
        try {
            nsd.discoverServices("_stella._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            stopDiscovery()
        }

        android.os.Handler(mainLooper).postDelayed({
            if (!handled) stopDiscovery()
        }, 5000)
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    private fun updateStatus() {
        status.text = if (laptopOnline) {
            "● LAPTOP CONNECTED • ${mode.uppercase()}"
        } else {
            "● STANDALONE • ${mode.uppercase()}"
        }
    }

    private fun ask(text: String) {
        if (text.isBlank()) return
        if (text.lowercase().contains("sleep mode")) {
            mode = "sleep"
            updateStatus()
            return
        }
        if (mode == "sleep") {
            output.text = "Stella is sleeping. Tap Single or Multiple, or say wake up."
            return
        }

        saveHistory("user", text)
        rememberLocalIfNeeded(text)
        output.text = "Stella is thinking…"

        if (laptopOnline) {
            askLaptop(text)
        } else {
            askStandalone(text)
        }
    }

    private fun askLaptop(text: String) {
        val payload = JSONObject().put("text", text).toString()
        val request = Request.Builder()
            .url("http://$laptopHost:$laptopPort/api/chat")
            .header("X-Stella-Token", laptopToken)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                laptopOnline = false
                runOnUiThread { updateStatus(); output.text = "Laptop disconnected. Switching to standalone Stella…" }
                askStandalone(text)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    laptopOnline = false
                    runOnUiThread { updateStatus(); output.text = "Laptop error. Switching to standalone Stella…" }
                    askStandalone(text)
                    return
                }
                try {
                    val answer = JSONObject(body).optString("answer", body)
                    saveHistory("assistant", answer)
                    runOnUiThread { output.text = answer }
                } catch (_: Exception) {
                    runOnUiThread { output.text = body }
                }
            }
        })
    }

    private fun askStandalone(text: String) {
        val key = prefs.getString("groq_key", "") ?: ""
        if (key.isBlank()) {
            runOnUiThread {
                output.text = "Laptop is unavailable. Set a phone Groq API key to use standalone Stella."
                keyDialog()
            }
            return
        }

        val history = loadHistory()
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", standalonePrompt()))
        history.takeLast(12).forEach { item ->
            messages.put(JSONObject().put("role", item.first).put("content", item.second))
        }
        messages.put(JSONObject().put("role", "user").put("content", text))

        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", messages)
            .put("temperature", 0.4)

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { output.text = "Standalone Stella error: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                val answer = try {
                    JSONObject(raw).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                } catch (_: Exception) { raw }
                saveHistory("assistant", answer)
                runOnUiThread { output.text = answer }
                if (laptopOnline) syncMemory()
            }
        })
    }

    private fun standalonePrompt(): String {
        val facts = loadMemories()
        val memoryText = if (facts.isEmpty()) "(none)" else facts.joinToString("\n- ", prefix = "- ")
        return "You are Stella, the user's personal AI assistant. Be natural and concise. " +
            "You are running on the user's Android phone without the laptop. You cannot directly control the PC in standalone mode. " +
            "Use these saved user memories when relevant and never invent memories.\nSAVED MEMORIES:\n$memoryText"
    }

    private fun keyDialog() {
        val e = EditText(this).apply {
            hint = "gsk_…"
            inputType = 129
            setText(prefs.getString("groq_key", "") ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Phone Groq API key")
            .setMessage("Only needed when the laptop is unavailable. It is stored in this app's private storage.")
            .setView(e)
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString("groq_key", e.text.toString().trim()).apply()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun rememberLocalIfNeeded(text: String) {
        val lower = text.lowercase().trim()
        var fact: String? = null
        if (lower.startsWith("remember ")) fact = text.trim().substring(9).trim()
        else if (lower.startsWith("remember that ")) fact = text.trim().substring(14).trim()
        else if (lower.startsWith("my name is ")) fact = "The user's name is " + text.trim().substring(11).trim()
        if (!fact.isNullOrBlank()) addMemory(fact!!)
    }

    private fun addMemory(fact: String) {
        val memories = JSONArray(prefs.getString("memories", "[]") ?: "[]")
        for (i in 0 until memories.length()) if (memories.optString(i).equals(fact, true)) return
        memories.put(fact)
        prefs.edit().putString("memories", memories.toString()).apply()
        if (laptopOnline) syncMemory()
    }

    private fun loadMemories(): List<String> {
        val arr = JSONArray(prefs.getString("memories", "[]") ?: "[]")
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        return list
    }

    private fun showMemory() {
        val list = loadMemories()
        val text = if (list.isEmpty()) "Stella doesn't have any saved phone memories yet." else list.joinToString("\n\n") { "• $it" }
        AlertDialog.Builder(this).setTitle("Stella memory").setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun saveHistory(role: String, text: String) {
        val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
        arr.put(JSONObject().put("role", role).put("content", text))
        while (arr.length() > 60) arr.remove(0)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<Pair<String, String>> {
        val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(o.optString("role") to o.optString("content"))
        }
        return list
    }

    private fun syncMemory() {
        if (!laptopOnline || laptopToken.isBlank()) return
        val request = Request.Builder()
            .url("http://$laptopHost:$laptopPort/api/sync")
            .header("X-Stella-Token", laptopToken)
            .get()
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: return
                if (!response.isSuccessful) return
                try {
                    val knowledge = JSONObject(raw).optJSONArray("knowledge") ?: JSONArray()
                    val local = loadMemories().toMutableList()
                    for (i in 0 until knowledge.length()) {
                        val item = knowledge.optJSONObject(i) ?: continue
                        if (item.optString("kind") == "fact") {
                            val fact = item.optString("content")
                            if (fact.isNotBlank() && !local.any { it.equals(fact, true) }) local.add(fact)
                        }
                    }
                    val arr = JSONArray()
                    local.takeLast(200).forEach { arr.put(it) }
                    prefs.edit().putString("memories", arr.toString()).apply()

                    val synced = JSONArray(prefs.getString("synced_memories", "[]") ?: "[]")
                    val syncedSet = mutableSetOf<String>()
                    for (i in 0 until synced.length()) syncedSet.add(synced.optString(i))
                    for (i in 0 until knowledge.length()) {
                        val item = knowledge.optJSONObject(i) ?: continue
                        if (item.optString("kind") == "fact") {
                            val remoteFact = item.optString("content")
                            if (remoteFact.isNotBlank()) syncedSet.add(sha256(remoteFact))
                        }
                    }
                    for (fact in loadMemories()) {
                        val hash = sha256(fact)
                        if (!syncedSet.contains(hash)) {
                            pushMemoryToLaptop(fact)
                            syncedSet.add(hash)
                        }
                    }
                    val out = JSONArray()
                    syncedSet.take(300).forEach { out.put(it) }
                    prefs.edit().putString("synced_memories", out.toString()).apply()
                } catch (_: Exception) {}
            }
        })
    }

    private fun pushMemoryToLaptop(fact: String) {
        val text = "Remember this user fact for future conversations: $fact"
        val payload = JSONObject().put("text", text).toString()
        val request = Request.Builder()
            .url("http://$laptopHost:$laptopPort/api/chat")
            .header("X-Stella-Token", laptopToken)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            output.text = "Speech recognition isn't available on this phone."
            return
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    input.setText(matches[0])
                    ask(matches[0])
                }
                sr.destroy()
            }
            override fun onError(error: Int) { output.text = "I couldn't hear that."; sr.destroy() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
        output.text = "Listening…"
    }
}
