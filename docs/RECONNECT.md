# BLE reconnection

An explicit Connect persists the desired connection until an explicit Disconnect or Stop. Advertisement timeouts, GATT failures, scanner unavailability, and temporary missing Bluetooth permissions do not exhaust a retry budget or clear that intent. Retries continue indefinitely, with delays of 5, 10, 20, 40, then at most 60 seconds between attempts. A scan attempt lasts up to 10 seconds. The diagnostic attempt counter saturates rather than overflowing. Restarts/boot restore retain the saved connection intent.

This does not enable Bluetooth, bypass revoked permissions, enroll a key, or wake the vehicle. Android must permit the foreground service to run. Once permissions/Bluetooth are restored, subsequent retries can proceed. Scan-only remains distinct from Connect; explicit Disconnect/Stop cancels retries.
