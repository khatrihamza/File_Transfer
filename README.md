# WiFi Direct File Transfer (AIDE-compatible)

This project is a standalone Android app that enables file transfer between two devices using Wi‑Fi Direct (no router, no third-party server). It's built to be compatible with AIDE and uses a WebView-based modern UI stored in `assets/`.

Quick setup (AIDE):

1. Copy the `File_Transfer` folder to your Android device's storage where AIDE can open it.
2. Open AIDE and choose "Open Project" -> select this folder.
3. Build and run. Grant location and storage permissions when prompted.

How to use:

- On both devices open the app. Tap "Discover" on one device to scan for peers.
- When devices connect, the app will receive the group owner IP automatically and fill it into the host field.
- Tap "Pick File" to choose a file, then tap "Send" to send it to the peer IP.
- Incoming files are saved to the device Downloads folder as `received_<timestamp>`.

Notes & Limitations:
- This is a simple reference implementation. Production apps should add robust error handling, secure transfers, and larger-file resume support.
- The UI is offline and uses local assets.
# File_Transfer
File transfer
