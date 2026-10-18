# MQTT bridge evidence report

## Implemented in source

- Added a read-only mapping from the existing authenticated/fresh ChargeState API to four MQTT fields. Bounds reject invalid battery percentages/range; stale/unavailable snapshots do not produce measurement states.
- Added four Home Assistant discovery definitions under a random app-generated device ID, retained availability, LWT `offline`, retained state, and republishing on `homeassistant/status=online`.
- Added a Paho 1.2.5 asynchronous transport with clean sessions, a retained LWT, bounded retry backoff, explicit TLS hostname verification, and no credential/error logging.
- Added private settings UI with a default-disabled publisher, password masking/screenshot protection, opt-in plaintext warning, encrypted credential storage, and displayed non-VIN device ID for narrow broker ACLs.
- Integrated publishing into the existing `TeslaBleService` telemetry lifecycle. No second GATT/native client was added. The loopback API implementation and `docs/LOCAL_API.md` were left unchanged.
- No command topic is subscribed; no writable discovery entity is generated.

## Verification performed

- App checkout confirmed on `hermes/tesla-mqtt-bridge` at baseline `c7a27e9`; work was done on CT157.
- Baseline tests before changes: **22 passed**.
- Final host suite after implementation: **38 passed** (includes existing BLE/API tests and new mapper, discovery, config, cipher, publisher, service/UI contract tests).
- Paho 1.2.5 artifact downloaded from Maven Central in CT157; published SHA-1 matched. Pure Java publisher/Paho adapter sources compiled with `javac` against that JAR: **PASS**.
- Current reference check: Docker repo `main` remains at inspected `69e3b2b`; `teslamotors/vehicle-command` main remains at pinned `a4b43c1`. `yoziru/tesla-ble` current main is `283490a` (v6.1.1), ahead of the app's pinned `42b899b`; the newer commits add authentication rejection reporting and were not adopted.
- Source-derived legacy matrix and setup/rollback instructions are in `MQTT_PARITY_MATRIX.md` and `MQTT_SETUP.md`.

## Not verified / blocked

- **No Android APK was built.** CT157 has OpenJDK 17 but no Gradle executable, Android SDK, or Android NDK. The terminal safety gate blocked the Gradle installation/extraction and explicitly prohibited retrying the same installation or an equivalent route. No alternate build host or installer was used.
- No artifact exists to inspect for package/version/signature. The persistent signing-store/password inputs were absent in the CT157 execution environment; signer match and Screenmate installation state remain unverified. No install or app-data operation was attempted.
- No MQTT credentials were entered and the app publisher remains disabled. No authenticated broker session, retained discovery readback, HA entity creation, or live telemetry publication was tested.
- No vehicle command, pairing/key deployment, wake, or BLE test was performed.
- The retired Pi configuration and exact runtime HA unique-ID instances remain unresolved. Its direct Tailnet SSH route timed out from this runner; the Tailnet address had no route through a local Tailscale interface. No alternate route or credential-bearing files were accessed. Source formulas/suffixes are documented, but runtime configuration and exact instance IDs are not claimed recovered.

## Change boundaries

No changes were made to broker configuration/ACLs, Home Assistant storage, DiPlay, vehicle keys, BLE pairing state, signing assets, the Mac checkout, or Screenmate. The new bridge is disabled by default and creates a separate HA device identity if later enabled.
