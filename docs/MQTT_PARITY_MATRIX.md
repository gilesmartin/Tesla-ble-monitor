# Tesla BLE MQTT / Home Assistant parity matrix

**Status:** source audit and offline implementation only. No production MQTT session, vehicle command, or device install was performed.

- **Android checkout:** `hermes/tesla-mqtt-bridge`, baseline `c7a27e9`.
- **Docker reference:** `tesla_ble_mqtt_docker` commit `69e3b2b20bbe42b167a30c95bd6d4beee2fb7612`; the current `main` ref was the same when checked.
- **BLE core:** Android CMake pins `yoziru/tesla-ble` at `42b899b7816cc3700b7361e65b526fdc04d19a51`; current upstream is `283490ab49c0624c0ac628761f70459a5ce4b9ae` (v6.1.1). The newer upstream commits add rejected-key/unsigned-response reporting; this MQTT change does not update the BLE core pin.
- **Other core reference:** `teslamotors/vehicle-command` pinned/current main is `a4b43c1eff0e09d77deb9f2dce97031141fe8c8a`.

## Identity and wire contract

The Docker source derives `DEVICE_ID=tesla_ble_${vin}`, `DEVICE_NAME=Tesla_BLE_${vin}`, and `TOPIC_ROOT=tesla_ble/${vin}` (`app/mqtt-discovery.sh:11–35`). HA discovery topics follow `homeassistant/<component>/${DEVICE_ID}/<object>/config`; entity `unique_id` values use `${DEVICE_ID}_<suffix>`. The MQTT listener subscribes to `tesla_ble/+/+` and filters messages against `VIN_LIST` (`app/mqtt-listen.sh:27–55`). Actual runtime IDs depend on the retired Pi's VIN list/config; no VIN is reproduced here.

The Android bridge intentionally does **not** reuse that identity formula. It persists a random app-generated device ID and uses `tesla_ble_monitor/<device-id>/...`. Home Assistant will see a new device. Legacy entity continuity is not claimed and no existing discovery records are deleted.

Legacy discovery definitions are piped through `retryMQTTpub ... -l` (`app/mqtt-discovery.sh` and `app/mqtt-discovery-sensors.sh`); the inspected shared publish base and call sites show no explicit retain (`-r`) or publish QoS (`-q`) flag. The `qos` field inside a discovery JSON document configures Home Assistant's subscription; it does not prove that the discovery message itself is retained or sent at that QoS. No broker retained-message readback was made, so this is a source-level finding only. The Docker source also contains empty-payload cleanup attempts for older generic discovery topics (`app/mqtt-discovery.sh:971–1026`); their retained-record effect was not verified. The Android code does not publish cleanup payloads.

## Legacy telemetry coverage

Legacy IDs below are the suffixes only; the full formula is `tesla_ble_<VIN>_<suffix>`. Legacy state topics are under `tesla_ble/<VIN>/...`.

| Legacy HA entity / ID suffix | Legacy source / meaning | Current Android bridge classification |
|---|---|---|
| Battery Level / `charge_state` | `.chargeState.batteryLevel`, `%` (`app/read-state.sh:344`; discovery `app/mqtt-discovery-sensors.sh:42–61`) | **Direct value semantics** with current top-level `battery_percent`; new HA unique ID, not legacy identity. Runtime payload/registry match not verified. |
| Battery Range / `battery_range` | `.chargeState.batteryRange`, HA unit `mi` (`app/read-state.sh:345`; discovery `app/mqtt-discovery-sensors.sh:63–82`) | **Convertible, not implemented as legacy parity**: current API provides `range_km`; the new entity remains km and no mile conversion is applied. |
| Charging State / `charging_state` | `.chargeState.chargingState`, legacy string/enum sensor (`app/read-state.sh:353`; discovery `app/mqtt-discovery-sensors.sh:378–394`) | **Not direct**: current API provides Boolean `charging`; no enum translation is implemented or claimed. |
| Usable battery | No separate legacy usable-battery HA entity found in active discovery definitions | **New field**: current `usable_battery_percent` is published under the new device identity. |
| Charger power, actual current, voltage, energy added, charge range added, charge speed | `charger_power`, `charger_actual_current`, `charger_voltage`, `charge_energy_added`, `charge_range_added`, `charge_speed` (`app/mqtt-discovery-sensors.sh:84–210`) | **Unavailable**: not present in the four-measurement Android API. |
| Tire pressures | `tpms_pressure_fl`, `tpms_pressure_fr`, `tpms_pressure_rl`, `tpms_pressure_rr` (`app/mqtt-discovery-sensors.sh:212–294`) | **Unavailable**. |
| Temperatures | `inside_temp`, `outside_temp`, `passenger_temp` (`app/mqtt-discovery-sensors.sh:296–358`) | **Unavailable**. |
| Charge cable, battery heater, defrost, wiper/mirror heaters | `charge_cable`, `battery_heater_on`, `front_defrost`, `rear_defrost`, `wiper_heater`, `mirror_heater` (`app/mqtt-discovery-sensors.sh:360–376`, `417–734`) | **Unavailable**. |
| Odometer | `odometer`, miles (`app/read-state.sh:476`; discovery `app/mqtt-discovery-sensors.sh:396–415`) | **Unavailable**. |
| Closures and lock | `frunk_open`, `window_open_df/pf/dr/pr`, `door_open_df/pf/dr/pr`, `door_lock` (`app/mqtt-discovery-sensors.sh:437–649`) | **Unavailable**. No lock/closure state is inferred from BLE connectivity or charging. The legacy HA lock *control* definition is commented out in `app/mqtt-discovery.sh:589–612`. |
| Awake/presence | `awake`, `presence`, and `presence_bc` (`app/mqtt-discovery-sensors.sh:14–29`, `735–796`) | **Unavailable**: Android `connected`/`connection_status` does not prove vehicle awake or vehicle presence. |
| `presence_bc` device tracker | A `device_tracker` definition shares the `_presence_bc` unique-ID suffix with a binary sensor (`app/mqtt-discovery-sensors.sh:756–796`) | **Not migrated**; registry behavior was not verified. |
| Charge-port latch | Commented-out `charge_port_latch` definition (`app/mqtt-discovery-sensors.sh:798–816`) | **Inactive in source; unsupported**. |

The battery percentage has matching meaning and unit at source level. Range needs a tested unit conversion for the old miles entity. Charging's old enum/string state and the new Boolean are different contracts. The remaining legacy fields have no backing values in the current API and are not synthesized from `last_known`.

## Legacy controls and command coverage

The Docker discovery source defines writable switches/covers/numbers/selects at `app/mqtt-discovery.sh:183–587`, and action/config buttons at `app/mqtt-discovery.sh:623–933`. The listener dispatches these values in `app/mqtt-listen.sh:57–298`:

| Legacy MQTT topic path | Accepted payload/value names | Side-effect class | Android bridge |
|---|---|---|---|
| `<TOPIC_ROOT>/config` | `body-controller-state`, `generate-keys`, `deploy-key`, `info-bt-adapter`, `read-state-all`, `read-state-envcheck`, `read-state-charge`, `read-state-climate`, `read-state-tyre`, `read-state-closure`, `read-state-drive`, `list-keys` | Read/diagnostic actions (the legacy dispatcher may wake a sleeping vehicle; `app/tesla-commands.sh:23–65`) plus key generation/deployment requiring vehicle-side user confirmation. | **Unsupported** |
| `<TOPIC_ROOT>/command` | `autosecure-modelx`, `charging-schedule-cancel`, `drive`, `flash-lights`, `frunk-open`, `honk`, `list-keys`, `lock`, `media-toggle-playback`, `ping`, `software-update-cancel`, `software-update-start`, `unlock`, `wake` | Vehicle actions, including wake, start/drive, lock/unlock, and software update. | **Unsupported** |
| `<TOPIC_ROOT>/<operation>` | `auto-seat-and-climate`, `charging-schedule`, `charging-set-amps`, `charging-set-amps-override`, `charging-set-limit`, `climate-set-temp`, ``heater-seat-front-left`, `heater-seat-front-right`, `heater-seat-rear-left`, `heater-seat-rear-right``, `media-set-volume`, `sentry-mode`, `steering-wheel-heater`, `climate`, `charging`, `charge-port`, `trunk`, `tonneau`, `windows`, `variables`, `poll_state` | Operation-specific values are routed by the legacy handlers to `tesla-commands.sh`; not duplicated here. | **Unsupported** |

The new publisher subscribes only to `homeassistant/status`, to republish discovery after HA birth. It does not subscribe to `<TOPIC_ROOT>/config`, `<TOPIC_ROOT>/command`, or any vehicle-command topic. The new discovery catalog contains no `command_topic` entries; all legacy command/control entities are unsupported by design in this read-only slice.

## Current Android publisher contract

The unchanged DiPlay/TeslaBLE contract remains `docs/LOCAL_API.md`: schema 1, authenticated read-only loopback API at `127.0.0.1:8765`, routes `GET /v1/status` and `GET /v1/telemetry`. The publisher consumes only the four documented top-level measurements: `battery_percent`, `usable_battery_percent`, `range_km`, and `charging`. It does not read `last_known`.

Freshness is `max(60 seconds, poll_interval_seconds × 3)`. The mapper validates numeric bounds and emits no state for stale/unavailable snapshots. For a fresh partial snapshot, missing individual fields become `unknown`; for stale/unavailable telemetry, the retained availability marker becomes `offline` and old values are not republished. Discovery/state/availability use QoS 1 retained messages; the Last Will is retained `offline`. HA birth is the only subscription.

`TeslaBleService` remains the single native/GATT owner. MQTT does not invoke `readChargeState`, pairing, scan, connect, wake, or polling controls. The periodic MQTT freshness check only recomputes the existing API snapshot. Existing reads retain the native no-wake policy; periodic BLE polling remains opt-in.

## Unresolved migration inputs and verification limits

- The retired Pi's actual runtime `.env`/Compose configuration, `VIN_LIST`, broker endpoint/account/ACL, and generated unique-ID instance values were **not recovered**. The upstream environment-variable schema is not evidence of the retired Pi's actual values.
- Direct SSH to the Pi's Tailscale hostname timed out from this runner. The Tailnet address had no Tailscale route/interface here; no alternate route was attempted. This is a path blocker, not proof the Pi is powered off.
- Home Assistant entity inventory exposed entity IDs/states, not `unique_id`; no retained broker discovery readback was made. Pi-related HA timestamps were stale.
- Therefore the exact runtime unique-ID strings and live legacy HA registry state remain unverified. The source formulas and suffixes above are known; actual continuity is not.
- No MQTT credentials were entered, the in-app bridge remains disabled by default, no app MQTT session was opened, no discovery/state was published to a broker, no vehicle command was sent, and no device install was attempted.
