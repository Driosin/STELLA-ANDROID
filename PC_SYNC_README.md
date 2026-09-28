# Laptop sync setup

Your Stella V4 PC project already contains `sync_server.py`, which provides:

- one-time pairing code;
- authenticated phone chat;
- memory export/sync;
- automatic LAN discovery through `_stella._tcp`.

To make the desktop GUI start it automatically, copy `PC_ENABLE_SYNC.py` into the same folder as the PC Stella `gui.py` and run:

```text
python PC_ENABLE_SYNC.py
```

Then restart Stella.

The Android app discovers `_stella._tcp` automatically after pairing. It stores the resulting token and laptop address, so you do not have to enter the address every time.
