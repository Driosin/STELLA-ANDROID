package com.stella.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var output: TextView

    private var mode = "single"
    private var apiKey = ""

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiKey = getPreferences(0)
            .getString("groq_key", "") ?: ""

        buildUi()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                10
            )
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 24)
        }

        val title = TextView(this).apply {
            text = "STELLA"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        status = TextView(this).apply {
            text = "● SLEEP"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }

        val modes = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        fun modeButton(label: String, value: String): Button {
            return Button(this).apply {
                text = label
                setOnClickListener {
                    mode = value
                    status.text = "● ${value.uppercase()}"
                }
            }
        }

        modes.addView(modeButton("Single", "single"))
        modes.addView(modeButton("Multiple", "multiple"))
        modes.addView(modeButton("Sleep", "sleep"))

        input = EditText(this).apply {
            hint = "Talk to Stella…"
            minLines = 2
        }

        val send = Button(this).apply {
            text = "SEND"
            setOnClickListener {
                ask(input.text.toString())
                input.setText("")
            }
        }

        val mic = Button(this).apply {
            text = "🎙 VOICE"
            setOnClickListener {
                listen()
            }
        }

        val key = Button(this).apply {
            text = "Set Groq API key"
            setOnClickListener {
                keyDialog()
            }
        }

        output = TextView(this).apply {
            textSize = 16f
            setPadding(0, 20, 0, 0)
        }

        root.addView(title)
        root.addView(status)
        root.addView(modes)
        root.addView(input)
        root.addView(send)
        root.addView(mic)
        root.addView(key)
        root.addView(output)

        setContentView(root)
    }

    private fun keyDialog() {
        val e = EditText(this).apply {
            hint = "gsk_…"
            inputType = 129
            setText(apiKey)
        }

        AlertDialog.Builder(this)
            .setTitle("Groq API key")
            .setMessage("Stored only in this app's private preferences.")
            .setView(e)
            .setPositiveButton("Save") { _, _ ->
                apiKey = e.text.toString()

                getPreferences(0)
                    .edit()
                    .putString("groq_key", apiKey)
                    .apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ask(text: String) {
        if (text.isBlank()) return

        if (text.lowercase().contains("sleep mode")) {
            mode = "sleep"
            status.text = "● SLEEP"
            return
        }

        if (mode == "sleep") {
            output.text =
                "Stella is sleeping. Tap Single or Multiple, or say wake up."
            return
        }

        if (apiKey.isBlank()) {
            keyDialog()
            return
        }

        output.text = "Stella is thinking…"

        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "You are Stella, a concise personal AI assistant. " +
                            "Current mode: $mode. " +
                            "Explain what you can do and answer naturally. " +
                            "This mobile prototype does not directly control the PC yet."
                    )
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", text)
            )

        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", messages)
            .put("temperature", 0.4)

        val requestBody = body
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    runOnUiThread {
                        output.text = "Groq error: ${e.message}"
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    val responseText =
                        response.body?.string() ?: ""

                    runOnUiThread {
                        try {
                            val answer = JSONObject(responseText)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")

                            output.text = answer
                        } catch (_: Exception) {
                            output.text = responseText
                        }
                    }
                }
            }
        )
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            output.text =
                "Speech recognition isn't available on this phone."
            return
        }

        val speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    if (!matches.isNullOrEmpty()) {
                        input.setText(matches[0])
                        ask(matches[0])
                    }

                    speechRecognizer.destroy()
                }

                override fun onError(error: Int) {
                    output.text = "I couldn't hear that."
                    speechRecognizer.destroy()
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }

        speechRecognizer.startListening(intent)

        output.text = "Listening…"
    }
}
