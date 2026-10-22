# ScreenMate community preview: TeslaBLE + DiPlay

This is an owner-maintained community preview, not Tesla, Apple, or ScreenMate certified software. Use only on a vehicle you are authorized to access. Start setup while parked. Wider hardware/iOS compatibility is not established.

## Downloads and compatibility

- [TeslaBLE preview APK, source and checksums](https://github.com/gilesmartin/Tesla-ble-monitor/releases/tag/screenmate-preview-2026.10.22)
- [Matching DiPlay ScreenMate preview](https://github.com/gilesmartin/DiPlay/releases/tag/screenmate-preview-2026.10.22)
- TeslaBLE package: `org.opencharge.teslable`, Android 8.0/API 26+, Bluetooth LE.
- The bundled optional Cloudflare executable is ARM64 Android only; its presence does not establish compatibility with other CPUs/head units.
- DiPlay preview is the separate `com.shihab.diplay.hudtest` package. Do not run it and another CarPlay receiver simultaneously.
- Updating an existing package requires the same signing certificate. Compare SHA256SUMS and release metadata first. Never uninstall or clear app data merely to force an update; that can erase vehicle enrollment/settings. A source build's local debug signer may differ from these published builds.

## Required versus optional

For Tesla telemetry you need both apps on the SAME Android device, Bluetooth access, your own vehicle's approved BLE key, and a shared local telemetry token. Core CarPlay does not depend on Cloudflare, MQTT, Home Assistant or Tailscale. MQTT and public wake are separate optional opt-ins. The apps do not create/manage ScreenMate VirtualAP or cellular routing.

## 1. Configure TeslaBLE

1. Install the verified APK on ScreenMate, open Tesla BLE Monitor, and grant the requested Bluetooth/Nearby devices and precise-location permissions. Precise location is needed for Tesla BLE beacon delivery on affected Android versions; this app does not request GPS tracks/background location. Also allow the foreground-service notifications where Android asks.
2. Enter YOUR Tesla VIN, scan, select its device and connect. If no key is enrolled yet, use the explicit **Enroll monitor key** flow with your physical Tesla key card and follow vehicle approval. Do not re-enroll or regenerate an already working key. Granting a control/Driver role is separate and is NOT required for basic read-only telemetry.
3. Use **Read ChargeState** to obtain authenticated data. Reads use the native no-intentional-wake policy; a connection/read is still vehicle-facing activity, not a guarantee of zero battery impact. A stale cache or connected indicator is not fresh telemetry.
4. Polling is off by default. Enable only deliberately. The default interval is 15 seconds, configurable from 15 to 3600 seconds. For less frequent parked polling, the documented shell-only `poll-interval` command can set e.g. 720 seconds; no credentials are involved. The button uses the saved interval, rather than always overriding it to 15. Driving refresh while a read-only lease is active is separate from that saved interval.
5. Permit the appropriate Android battery/background behavior, noting that firmware/head-unit power removal cannot be overridden by an app. Boot/cold-power recovery still needs broader real-device validation.

## 2. Share a LOCAL telemetry token with DiPlay

1. In TeslaBLE open **Local telemetry / DiPlay setup**.
2. Generate a new telemetry token (or enter a distinct 32–512-character printable token), then **Save telemetry token**. Nothing is connected, paired or woken by this screen. Blank leaves the current token unchanged. Generating without Save does not change configuration.
3. Explicitly press **Copy token saved in this screen**. Clipboard transfer is sensitive: paste directly into DiPlay on the SAME device, then clear clipboard/history, especially on older Android. This screen prevents screenshots/autofill; do not send the token to chat, screenshots or logs. Previously stored tokens are not displayed/copied; rotating an existing token requires updating all its local consumers.
4. Open DiPlay's Advanced / Advanced vehicle data Tesla telemetry controls, paste the token, save and select/enable Tesla telemetry. Use the matching DiPlay guide for exact UI labels. Do not put a token in source, a URL, an intent/ADB command line or diagnostics.
5. The destination is fixed to `http://127.0.0.1:8765/v1/telemetry`; never expose this port to the LAN/Internet, and do not point Cloudflare at it. Read-only DriveState subscription POSTs manage short-lived local leases only; they do not control/wake the car.
6. Check that the source is authenticated, available and fresh. Missing, stale or rejected readings must remain unavailable. Vehicle battery readings are not an Apple Maps arrival estimate.

## 3. Connect CarPlay

Follow the [DiPlay ScreenMate guide](https://github.com/gilesmartin/DiPlay/tree/screenmate-preview). Choose a supported connection backend. External VirtualAP mode only ATTACHES to an already running interface; it does not create an AP, change Wi-Fi/cellular/VPN routing, or replace ScreenMate provisioning. If that interface is absent, choose an appropriate existing connection mode instead of expecting this fork to create one.

## 4. Experimental EV routing

Default off. Configure your own usable battery capacity and adapter/connector capabilities; do not copy another owner's capacity, health percentage or assumed charge power. Maps enrollment has been owner-observed on one tested setup. Reliable arrival-SOC updates and charging-stop planning are NOT established. Unknown receiver policy/schema/model semantics remain experimental. Do not rely on this preview to plan a journey or charging stops; keep normal navigation/vehicle energy information available.

## 5. Optional integrations

- MQTT/Home Assistant: disabled by default; [separate setup](MQTT_SETUP.md). Use your own broker/credentials and narrow ACLs. Default publishing is read-only. Legacy controls are a separate, potentially consequential opt-in. Retire duplicate writers before using legacy identities.
- Cloudflare public wake: disabled by default; [separate guide](../PUBLIC-WAKE.md). This needs a tunnel CONNECTOR token, not a Global API Key, and a separate public wake bearer. Configure locally; remote credential import is not implemented. App-UID connector readiness, real named-tunnel routing, recovery, VPN-off paths and physical wake are not yet accepted end-to-end. It is not a prerequisite for DiPlay.

## Troubleshooting and reporting

Report app versions, Android/head-unit firmware, iPhone/iOS, connection mode and sanitized failure time. Never publish your VIN, BLE/vehicle private key, telemetry/wake bearer, Cloudflare token, MQTT credentials, signing keys, raw location payloads or personal diagnostic archives. Do not infer a BLE key/session failure from a network error or vice versa. See [local API contract](LOCAL_API.md) for freshness and status semantics. A 202 on optional wake means queued, not physically awake. Disable experimental integrations to roll back configuration; never clear enrollment as a troubleshooting shortcut.
