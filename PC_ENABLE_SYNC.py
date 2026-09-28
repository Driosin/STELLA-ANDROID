"""One-time patch for the Stella V4 PC project.

Run this script from the folder that contains gui.py and sync_server.py:
    python PC_ENABLE_SYNC.py

It makes the existing desktop GUI start the V4 phone-sync server automatically.
It does not add or expose a Groq API key.
"""
from pathlib import Path

GUI = Path(__file__).with_name("gui.py")
if not GUI.exists():
    raise SystemExit("gui.py was not found. Put this script in your Stella V4 PC folder.")

text = GUI.read_text(encoding="utf-8")
if "from sync_server import StellaSyncServer" not in text:
    text = text.replace(
        "from phone_server import PhoneServer\n",
        "from phone_server import PhoneServer\nfrom sync_server import StellaSyncServer\n",
        1,
    )

if "self.sync_server = StellaSyncServer" not in text:
    text = text.replace(
        "self.phone = PhoneServer(self.agent, self.emit)\n",
        "self.phone = PhoneServer(self.agent, self.emit)\n        self.sync_server = StellaSyncServer(self.agent, self.emit)\n        self.sync_server.start()\n",
        1,
    )

GUI.write_text(text, encoding="utf-8")
print("Done. Stella V4 now starts the phone sync server automatically.")
print("Restart Stella after running this patch.")
