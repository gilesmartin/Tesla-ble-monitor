# Tesla BLE Controller

Android package: `org.opencharge.teslable` (min SDK 26, target SDK 35). The controller is the single owner of Tesla BLE/GATT and the native Tesla protocol client; DiPlay should consume its telemetry rather than implement another BLE stack.

## Current implementation

- VIN-derived BLE advertisement matching and asynchronous GATT transport.
- Android 12+ requires Bluetooth and precise-location permission for this vehicle beacon. The app deliberately does not declare `neverForLocation`: Android can otherwise hide the Tesla advertisement before the VIN matcher sees it. Location permission is used for Bluetooth result delivery; this app does not collect GPS locations or request background-location permission.
- Enrolled Tesla monitor key remains in the controller's existing encrypted storage. Do not invoke `pair` or re-enrollment as part of routine telemetry work.
- Foreground connected-device service keeps operating after the UI closes. A process restart resumes only a previously requested connection.
- A saved connection request is restored after `BOOT_COMPLETED` for the active Android user. Boot restore is gated on a valid saved VIN and `desired_connection=true`, resets the bounded reconnect counter, and performs a passive scan before connecting. It does not use a remembered Bluetooth address. A real cold-power-cycle test remains pending.
- Reconnect attempts continue indefinitely with capped backoff until explicitly disconnected. Temporary Bluetooth, permission, or advertisement failures preserve the saved connection intent.
- A one-shot ChargeState read uses the native `NO_WAKE_SKIP`/`NO_WAKE_FAIL` policy. It sends no explicit wake command. Periodic polling is opt-in, defaults off, and uses the configured 15–3600 second interval.
- Authenticated telemetry is persisted for last-known display; the API marks it stale and nulls the top-level live fields after its freshness window.
- Authenticated, read-only loopback HTTP interface: see [`docs/LOCAL_API.md`](docs/LOCAL_API.md). There is not currently an exported cross-app bound service; DiPlay integration must preserve the API's security boundary or add a permission-controlled IPC path after signer compatibility is checked.

## Safe operation

- Perform vehicle-facing BLE tests only when the Model 3 is parked and the test is safe. A BLE session is not a wake command, but it does establish a vehicle connection.
- Do not pair, enroll, delete, or replace the existing Tesla key for telemetry testing.
- Do not enable continuous polling unless the integration requires it and the polling interval is deliberately selected. Do not enable remote wake or inhibit vehicle sleep.
- The Screenmate loses power during Tesla deep sleep. No app, BLE service, or remote wake operation can run while it is unpowered.
- Preserve VirtualAP, cellular routing, Tailscale, root ADB, TCP/5555, and the existing boot helper; this controller does not manage those components.

## ADB command entry point

The no-UI command activity is protected by `android.permission.DUMP` and is intended for `adb shell`. Use the active driver profile (currently Android user 10) and the hostname supplied by the handoff:

```sh
# Replace with the authorized Android device hostname from the handoff.
ANDROID_ADB_HOST="your-android-device-hostname"
adb connect "${ANDROID_ADB_HOST}:5555"
adb -s "${ANDROID_ADB_HOST}:5555" shell am start --user 10 \
  -n org.opencharge.teslable/.AdbCommandActivity --es cmd status
```

Supported read/connection commands include `status`, `connect` (with the saved VIN), `read-charge`, `disconnect`, `poll-on`, `poll-off`, and `poll-interval`. `pair` is a separate enrollment action and is not part of normal telemetry use. Keep API credentials out of shell history, process arguments, logs, and URLs.

## Build, update, and tests

- The CI workflow is `.github/workflows/android-apk.yml`; it builds the APK and runs the repository's `unittest` checks with the Android/JDK toolchain.
- Local contract tests: `python3 -m unittest discover -s tests -v`. Java harness tests require a usable JDK (`java` and `javac`).
- Before installing an update, confirm package identity and compare its signing certificate with the installed package. Update with `adb install -r`; do not uninstall or clear app data. This preserves the enrolled key and preferences.
- The controller uses `yoziru/tesla-ble`, which is AGPL-3.0; observe its license obligations when distributing builds.

## Important integration boundary

The current HTTP server binds exactly to `127.0.0.1:8765` and requires a bearer token. The Android service itself is `exported=false`; its in-process Binder is not an inter-app API. Do not expose the API on LAN, embed the bearer token in DiPlay source/manifests, or forward it beyond local development. For production DiPlay IPC, verify both APK signing identities first and prefer a narrowly permission-controlled Binder interface if signing permits; otherwise define a secure token provisioning mechanism.
