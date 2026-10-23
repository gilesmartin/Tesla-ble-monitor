# Tesla BLE Controller local telemetry API

**Contract version:** schema 1<br>
**Package:** `org.opencharge.teslable`<br>
**Listener:** IPv4 loopback `127.0.0.1:8765` only
**Transport:** authenticated loopback HTTP/1.x; vehicle reads only, no control commands

The service starts this listener when `TeslaBleService` is created. The listener is not a LAN service. ADB forwarding is for development/verification only; remove the host forward when finished.

## Security and access

- Requests must include `Authorization: Bearer <token>`. The controller stores its configured token encrypted with Android Keystore-backed AES-GCM in private app storage.
- The server compares bearer values in constant time and never logs request headers or token values.
- The authenticated GET routes are `/v1/status` and `/v1/telemetry`. The only POST routes are `/v1/telemetry/subscribe` and `/v1/telemetry/unsubscribe`; POSTs manage an in-memory, read-only DriveState subscription and accept no body. Other paths return `404`; unauthenticated requests return `401`; unsupported methods return `405`; an unconfigured token returns `503`.
- Responses set `Cache-Control: no-store` and close the connection.
- `TeslaBleService` is `exported=false`. Its local Binder is not available to another package. The loopback HTTP API is currently the only cross-process telemetry surface; DiPlay must not receive a token by embedding it in source, resources, or the manifest.
- There is no TLS on loopback. Do not bind to a non-loopback address, expose a port through VirtualAP/Tailscale, or create a public forward. Android apps on the same device can reach loopback, so possession of the bearer token is the access boundary.

For Mac-side development, an ADB forward maps a local development port to the device listener, e.g. host TCP 18765 to device TCP 8765. Keep the token in a mode-0600 secret store and load it in the HTTP client's memory; do not put it in a URL, command-line argument, shell history, build file, or log. Remove the ADB forward after testing.

## Routes

### `GET /v1/status`

| Field | Type | Meaning |
|---|---|---|
| `schema_version` | integer | Status schema version (currently `1`). |
| `service_running` | boolean | Service lifecycle state. |
| `foreground` | boolean | Whether the connected-device foreground service is active. |
| `ble_connection_status` | string | Current BLE lifecycle state; treat values as extensible. |
| `connected` | boolean | Tesla GATT transport is ready. This alone does not prove an authenticated telemetry response. |
| `authentication_status` | string | Auth state. `authenticated_charge_state_received` means an authenticated ChargeState was received; `enrolled_key_present_not_yet_verified` means key presence only. |
| `enrolled_key_present` | boolean | Local enrolled key exists; this is not proof of a successful session. |
| `last_authenticated_update` | ISO-8601 UTC string or `null` | Timestamp of the most recent authenticated charge-state update. |
| `polling_enabled` | boolean | Whether periodic ChargeState polling is enabled. Defaults to `false`. |
| `poll_interval_seconds` | integer | Configured poll interval (15–3600 seconds; default 15). |
| `telemetry_available` | boolean | A last-known authenticated payload is stored. It can be stale. |
| `api` | object | `enabled`, `running`, `listen_address`, `port`, and `token_configured`. |

### `GET /v1/telemetry`

| Field | Type | Meaning |
|---|---|---|
| `schema_version` | integer | Telemetry schema version (currently `1`). |
| `source` | string | `tesla_ble`. |
| `available` | boolean | A last-known authenticated payload exists. |
| `fresh` | boolean | The last update is within the freshness window below. |
| `connection_status` | string | Current BLE state; may be `disconnected` while a recent cached reading is still fresh. |
| `last_successful_update` | ISO-8601 UTC string or `null` | Timestamp assigned when the authenticated payload was received. |
| `telemetry_age_seconds` | integer or `null` | Age of that timestamp. |
| `battery_percent` | number or `null` | Tesla-reported battery percentage; null when not fresh. |
| `usable_battery_percent` | number or `null` | Tesla-reported usable percentage; null when not fresh. |
| `range_km` | number or `null` | Tesla-reported range estimate converted to kilometres; null when not fresh. |
| `charging` | boolean or `null` | Tesla-reported charging state; null when not fresh. |
| `estimated_range_km` | number or `null` | Native `est_range_km` when present; null when not fresh or unavailable. |
| `drive_state_fresh` | boolean | DriveState is from an active subscription and within its 1.5-second age limit. Independent of charge freshness. |
| `drive_state_updated_at_elapsed_ms` | integer or `null` | Android monotonic elapsed-realtime timestamp of the latest DriveState observation. |
| `drive_state_age_ms` | integer or `null` | Age of the latest DriveState observation. |
| `speed_mph` | number or `null` | Fresh native `speed_float`, falling back to `speed`; never emitted as current when stale. |
| `shift_state` | string or `null` | Fresh native gear/shift state such as D, P, R, or N. |
| `drive_state` | object or `null` | Raw mapped DriveState values, including available power, odometer, and route fields; present only while fresh. It is not persisted. |
| `last_known` | object or `null` | Persisted native charge payload for diagnostics. Treat as opaque and never promote its fields to current data when `fresh` is false. |

The freshness threshold is `max(60 seconds, poll_interval_seconds × 3)`. `available` can remain true after a disconnect or process restart; `fresh` is recalculated from the stored timestamp. If stale, the four top-level measurement fields above are `null`; consumers should not display old values as current.

DriveState uses a separate monotonic freshness window of 1.5 seconds. Its fields are populated only while the local DiPlay subscription lease is active, BLE is already ready, and the most recent no-wake read is fresh. Charge freshness is unaffected by DriveState staleness. Raw DriveState values are held in memory only and omitted when stale.

#### DriveState subscription lifecycle

- `POST /v1/telemetry/subscribe` with bearer authentication and an empty body activates read-only DriveState polling while the Tesla BLE GATT session is already ready. The lease is 15 seconds and expires if the client stops refreshing it or crashes.
- While speed data is requested, send the authenticated `GET /v1/telemetry` header `X-Tesla-Drive-Subscription: active` as the lease heartbeat. A normal telemetry GET without that header does not renew the DriveState lease.
- `POST /v1/telemetry/unsubscribe` with bearer authentication and an empty body immediately revokes the subscription. Calls do not wake the car or issue vehicle-control commands.
- DriveState reads use the native `NO_WAKE_FAIL` path, are serialized with charge reads, and are bounded to one in-flight read with timeout/backoff. Polling stops on unsubscribe, lease expiry, or service teardown.

## Update behavior

- A manual `read-charge` requests one ChargeState read while the GATT transport is ready. The native request uses `NO_WAKE_SKIP`/`NO_WAKE_FAIL`; it does not issue a wake command. A failed/asleep request does not create telemetry.
- Periodic reads are opt-in. `poll-on` begins polling at the saved interval; `poll-off` stops it. Poll intervals are limited to 15–3600 seconds (default 15). Failures back off without a wake request.
- A successful authenticated charge payload is stored with a receive-time UTC timestamp and survives service recreation. BLE disconnection does not erase the last-known charge payload. DriveState is memory-only and is never persisted.
- The service persists an explicit connection desire. On normal process restart it resumes a passive VIN-matched scan only when that desire is set. The boot receiver does the same after `BOOT_COMPLETED` for the active user, with capped backoff until explicitly disconnected. A real Screenmate cold-power-cycle test is still required.

## Consumer requirements for DiPlay

1. Use an asynchronous HTTP client off the UI, AirPlay, and iAP2 protocol threads.
2. Validate `schema_version`; check `available`, `fresh`, timestamp age, and authentication status before publishing vehicle data.
3. Map only the documented top-level measurements. Do not infer battery energy/capacity or other fields from undocumented `last_known` contents.
4. Treat null, stale, disconnected, timeout, `401`, and service-not-running states as unavailable. Do not reuse a stale value as a current iAP2 update.
5. Avoid starting/stopping the controller or changing polling from a telemetry read path. Connection and polling policy should be explicit and lifecycle-aware.
6. Configure the bearer token through DiPlay's encrypted user-provisioned token store; never hard-code or log it. Keep both apps' API access on loopback/ADB forwarding only; do not expose the service to LAN or public networks.

## Maintainer reference

These implementation and release notes previously appeared in the README. Commands assume the repository root. Start with the [project overview](../README.md) if you are deciding whether to use the project.

**Unofficial, experimental community preview. Not affiliated with or endorsed by Tesla, Apple or ScreenMate.**

[Download APK + corresponding source + checksums](https://github.com/gilesmartin/Tesla-ble-monitor/releases/tag/screenmate-preview-2026.10.22) · [ScreenMate setup guide](SCREENMATE_SETUP.md) · [Matching DiPlay fork](https://github.com/gilesmartin/DiPlay/tree/screenmate-preview) · [Third-party notices](THIRD_PARTY_NOTICES.md)

Published preview: **0.3.8-screenmate-preview1 / code13**, package `org.opencharge.teslable`. The `screenmate-preview` branch is the supported source snapshot for this preview; the old `main` branch is retained as history.

This Android foreground service is the sole owner of Tesla BLE/GATT. DiPlay consumes authenticated read-only local telemetry. Optional MQTT and public wake are separate opt-ins, NOT CarPlay requirements. New installs have no enrolled key, vehicle VIN, local token, MQTT account or Cloudflare credentials; users configure their own. No private vehicle/controller/signing credentials are shipped.

### Evidence and limits

- Source/real-loopback tests, native offline fixtures, Android lint/build, signing and APK provenance are checked for release; see release metadata for exact results and warnings.
- This preview adds local-only encrypted telemetry-token setup, so community users need not pass a token in ADB arguments. Clipboard transfer is explicit and sensitive; follow the setup guide.
- A prior compatible build was installed with exact hash/data-preservation readback on the owner's ScreenMate; that is not a universal compatibility or fresh telemetry guarantee. This community APK is not automatically installed on that device.
- DiPlay Maps enrollment was observed on the owner's setup. Accurate arrival-SOC updates/charging stops are NOT established.
- The optional Android-native Cloudflare connector is bundled. A standalone version probe has run; app-UID tunnel behavior, public routing/VPN-off operation and power recovery/physical wake are still unverified end-to-end.
- An unpowered/offline head unit cannot serve telemetry or public wake. No installation silently changes networking/power settings, enrolls a vehicle key or issues a wake.

### Licensing and source

Controller contributions are distributed under GNU AGPL version 3; preserve upstream rights/notices. Native `yoziru/tesla-ble` is AGPL-3.0. Other components keep their own licenses. See [LICENSE](../LICENSE), [third-party notices](THIRD_PARTY_NOTICES.md) and the source/third-party source assets accompanying the prerelease. A disclaimer is not Apple certification or a grant of rights to third-party identities/assets.

---

## Tesla BLE Controller

Android package: `org.opencharge.teslable` (min SDK 26, target SDK 35). The controller is the single owner of Tesla BLE/GATT and the native Tesla protocol client; DiPlay should consume its telemetry rather than implement another BLE stack.

### Current implementation

- VIN-derived BLE advertisement matching and asynchronous GATT transport.
- Android 12+ requires Bluetooth and precise-location permission for this vehicle beacon. The app deliberately does not declare `neverForLocation`: Android can otherwise hide the Tesla advertisement before the VIN matcher sees it. Location permission is used for Bluetooth result delivery; this app does not collect GPS locations or request background-location permission.
- Enrolled Tesla monitor key remains in the controller's existing encrypted storage. Do not invoke `pair` or re-enrollment as part of routine telemetry work.
- Foreground connected-device service keeps operating after the UI closes. A process restart resumes only a previously requested connection.
- A saved connection request is restored after `BOOT_COMPLETED` for the active Android user. Boot restore is gated on a valid saved VIN and `desired_connection=true`, resets reconnect backoff, and performs a passive scan before connecting. It does not use a remembered Bluetooth address. A real cold-power-cycle test remains pending.
- Reconnect attempts continue indefinitely with capped backoff until explicitly disconnected. Temporary Bluetooth, permission, or advertisement failures preserve the saved connection intent.
- A one-shot ChargeState read uses the native `NO_WAKE_SKIP`/`NO_WAKE_FAIL` policy. It sends no explicit wake command. Periodic polling is opt-in, defaults off, and uses the configured 15–3600 second interval.
- Authenticated telemetry is persisted for last-known display; the API marks it stale and nulls the top-level live fields after its freshness window.
- Authenticated, read-only loopback HTTP interface: see [`docs/LOCAL_API.md`](LOCAL_API.md). There is not currently an exported cross-app bound service; DiPlay integration must preserve the API's security boundary or add a permission-controlled IPC path after signer compatibility is checked.

### Safe operation

- Perform vehicle-facing BLE tests only when the Model 3 is parked and the test is safe. A BLE session is not a wake command, but it does establish a vehicle connection.
- Do not pair, enroll, delete, or replace the existing Tesla key for telemetry testing.
- Do not enable continuous polling unless the integration requires it and the polling interval is deliberately selected. Public wake is a separate experimental opt-in; do not enable controls merely to troubleshoot telemetry.
- The Screenmate loses power during Tesla deep sleep. No app, BLE service, or remote wake operation can run while it is unpowered.
- Preserve VirtualAP, cellular routing, Tailscale, root ADB, TCP/5555, and the existing boot helper; this controller does not manage those components.

### ADB command entry point

The no-UI command activity is protected by `android.permission.DUMP` and is intended for `adb shell`. Use the active driver profile (currently Android user 10) and the hostname supplied by the handoff:

```sh
## Replace with the authorized Android device hostname from the handoff.
ANDROID_ADB_HOST="your-android-device-hostname"
adb connect "${ANDROID_ADB_HOST}:5555"
adb -s "${ANDROID_ADB_HOST}:5555" shell am start --user 10 \
  -n org.opencharge.teslable/.AdbCommandActivity --es cmd status
```

Supported read/connection commands include `status`, `connect` (with the saved VIN), `read-charge`, `disconnect`, `poll-on`, `poll-off`, and `poll-interval`. `pair` is a separate enrollment action and is not part of normal telemetry use. Keep API credentials out of shell history, process arguments, logs, and URLs.

### Build, update, and tests

- The CI workflow is `.github/workflows/android-apk.yml`; it builds the APK and runs the repository's `unittest` checks with the Android/JDK toolchain.
- Local contract tests: `python3 -m unittest discover -s tests -v`. Java harness tests require a usable JDK (`java` and `javac`).
- Before installing an update, confirm package identity and compare its signing certificate with the installed package. Update with `adb install -r`; do not uninstall or clear app data. This preserves the enrolled key and preferences.
- The controller uses `yoziru/tesla-ble`, which is AGPL-3.0; observe its license obligations when distributing builds.

### Important integration boundary

The current HTTP server binds exactly to `127.0.0.1:8765` and requires a bearer token. The Android service itself is `exported=false`; its in-process Binder is not an inter-app API. Do not expose the API on LAN, embed the bearer token in DiPlay source/manifests, or forward it beyond local development. For production DiPlay IPC, verify both APK signing identities first and prefer a narrowly permission-controlled Binder interface if signing permits; otherwise define a secure token provisioning mechanism.
