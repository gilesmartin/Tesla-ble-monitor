# MQTT / Home Assistant setup

This is an **additive, read-only** publisher from the Android Tesla BLE service to MQTT discovery/state. It does not move BLE ownership: `TeslaBleService` remains the only GATT/native Tesla client, and DiPlay continues to use the unchanged authenticated loopback HTTP API documented in `LOCAL_API.md`.

## What is implemented

- Home Assistant discovery for exactly four current API values: battery %, usable battery %, estimated range in km, and charging Boolean.
- State is sourced only from fresh top-level telemetry. `last_known` is never published.
- A fresh partial snapshot publishes `unknown` for missing fields; stale/unavailable telemetry marks the device `offline` and does not replay old measurement values.
- Discovery/state/availability are retained QoS 1. The Last Will is retained `offline`; `homeassistant/status` is the only subscription, used to republish discovery after HA birth.
- No vehicle command topic, writable HA control, wake, connect, BLE scan, key enrollment, or polling change is implemented.
- MQTT is disabled by default. The settings screen displays a random app-generated device ID for a narrow broker ACL. This is intentionally a **new** HA device ID; legacy ID migration is not implemented.

## Configure in the app

Open **Tesla BLE Monitor → MQTT / Home Assistant settings**.

1. Enter the broker URI and a dedicated least-privilege MQTT username/password. Use `ssl://host:port` when TLS is available. TLS certificate and hostname verification are enabled; the app uses Android's default trust store.
2. The password field is blank on reopen. Leave both username and password blank to retain the existing encrypted credential; enter both to replace them.
3. Leave **Allow plaintext MQTT** unchecked unless the broker is on a trusted, isolated LAN and has no TLS listener. Checking it permits `tcp://host:port` and sends credentials without transport encryption.
4. Leave publishing disabled until broker ACLs and HA behavior are ready. Saving the enabled checkbox is a user action that starts read-only MQTT publishing; the screen explicitly does not claim that broker connectivity succeeded.

Credentials are AES-GCM encrypted with a dedicated Android Keystore key and stored in private app preferences. They are not placed in intent arguments, shell history, logs, or the URI. The credential key is separate from the existing vehicle-key and local-API-token keys.

### Suggested least-privilege ACL scope

Use the **Device ID** displayed in the settings screen. The publisher requires only:

- **Write** `homeassistant/sensor/<DEVICE_ID>/#`
- **Write** `homeassistant/binary_sensor/<DEVICE_ID>/#`
- **Write** `tesla_ble_monitor/<DEVICE_ID>/#`
- **Read** `homeassistant/status`

Do not grant access to `tesla_ble/#` or vehicle command topics for this Android publisher. Broker ACL changes are outside the app and were not made in this task.

## Limits and legacy compatibility

The current source contract is schema 1 and contains only `battery_percent`, `usable_battery_percent`, `range_km`, and `charging`. Legacy BLE discovery includes many more sensors and vehicle controls. The legacy ID formula embeds the VIN (`DEVICE_ID=tesla_ble_<VIN>`); this app instead uses a random UUID and does not reuse that topic tree. This avoids accidental control-topic overlap and duplicate-ID collisions, but creates a separate HA device rather than preserving old entity identity.

The old Pi's actual `VIN_LIST`, broker endpoint/account/ACL, and runtime-generated IDs were not recovered. Do not assume the setup values documented by the Docker project are the retired Pi's actual configuration. See `MQTT_PARITY_MATRIX.md` for source-derived differences and unresolved evidence.

## Build and validation status

The implementation has host-side mapper, discovery, configuration, credential-envelope, publisher, and source-contract tests. The Paho Java adapter is pinned to Maven Central `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`; the artifact SHA-1 was checked and the adapter compiled with `javac` against that JAR.

A full Android APK build was **not** performed: CT157 currently has Java 17 but no Gradle installation and no Android SDK/NDK. The previous tool gate explicitly blocked retrying the Gradle installation, so no alternative installer/build route was attempted. Signing identity, APK artifact, Screenmate package state, live broker connection, HA discovery, and device installation remain unverified. Do not treat the host-side tests as an Android build or live MQTT acceptance test.

## Disable and rollback

- To stop publishing while keeping configuration, uncheck **Enable read-only MQTT/Home Assistant publishing** and save. The service sends retained `offline` when connected, then disconnects; it does not delete discovery records.
- To revert the code, review `git diff` on the `hermes/tesla-mqtt-bridge` branch and revert only the MQTT-specific source/tests/docs and dependency addition. Do not use a broad `git clean`, clear app data, replace signing keys, or remove the existing vehicle key/API-token stores.
- Because production publishing was not enabled during this task, no broker/HA cleanup is required for this work.
