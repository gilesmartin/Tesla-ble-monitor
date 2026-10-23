# Reliable service, responsive telemetry, quiet parking

TeslaBLE restores a previously requested connection after Android BOOT_COMPLETED (after the app user's credential storage is available) and after an app update. This does not depend on public wake. Explicit disconnect/stop is respected. Android start denial is logged without clearing saved intent. Force-stop and a powered-off ScreenMate cannot be bypassed; cold boot, user switching and physical sleep still require device acceptance tests.

## Polling policy

| Evidence | Charge-state target | Maximum live sample age |
|---|---:|---:|
| Fresh D/R or validated motion from DriveState, live consumer lease | 10 seconds | 30 seconds |
| Fresh confirmed active charging; no phone needed | 15 seconds | 45 seconds |
| Fresh VCSEC reports awake **and** user present | 30 seconds | 90 seconds |
| Unoccupied/unknown/parked or observed sleeping | Saved normal interval, with a 2-minute quiet minimum | At most 5 minutes; stale values remain only in last-known |

The saved interval is not overwritten. The quiet minimum protects installations whose old normal setting was 15 seconds; status reports the effective interval. Accelerated adaptive reads request ChargeState, not all legacy telemetry sections. Cadence is measured from monotonic sample/attempt time; healthy reads have no old 30-second retry floor. Failure backoff is bounded. Losing a lease or activity evidence cannot relabel an old driving sample fresh.

DriveState remains fast while driving, slows to one second while positively occupied and awake in Park, and goes quiet when parked/unoccupied/unknown without new activity evidence. A lease permits one initial probe, not perpetual parked Infotainment reads. Lease renewal does not reset poll deadlines. While a live consumer exists, a separate 15-second NO_WAKE_SKIP **VCSEC security-status** check can detect occupancy; it never queries Infotainment. Observed sleep suppresses that check. Native sleep and no-wake rules remain authoritative. VCSEC has no motion field in the pinned schema; no motion field or wake command is invented.

A new authenticated telemetry consumer can trigger one bounded no-wake bootstrap read if data is old. Continuous GETs do not repeatedly bootstrap reads or keep a parked aggressive window alive. Anonymous/unauthorized requests and status checks cannot trigger acquisition. Reads are scheduled on the existing native owner, never from the HTTP worker.

NO_WAKE_FAIL/SKIP prevent explicit wake and skip Infotainment requests for a vehicle observed asleep. They do **not**, by themselves, prove zero sleep impact from all BLE traffic. Physical parked/sleep observation is still required before claiming that.

## Recovery and visibility

A GATT write-completion deadline captures the exact connection and operation generation. A late old timeout cannot close a newer write/session. Actual transport write failures rebuild only this app's connection with existing bounded reconnect backoff; vehicle asleep/unavailable responses do not trigger a wake, key reset or a restart loop.

The authenticated IPv4-loopback API is health-checked and rebound after listener death or bind failure, without replacing a healthy listener. Status exposes mode, effective charge interval/freshness, age, permission state, write-in-flight state and read/API failures. Process liveness is not fresh-data availability.

No CarPlay reconnection, enrollment/calibration reset, radio toggle, network change, or vehicle-control command is part of this update. Boot/crash/user-switch and driving/charging/sleep acceptance results must be reported separately from source tests/build/install.

### Cache provenance across restarts

An accepted sample persists its freshness ceiling alongside its timestamp. Process restart cannot turn a 30-second driving sample into a five-minute parked sample. Legacy or invalid provenance defaults to 30 seconds until a new authenticated reading arrives.
