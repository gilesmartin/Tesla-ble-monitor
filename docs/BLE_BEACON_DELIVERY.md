# Android vehicle-beacon delivery

## Verified failure boundary

The ScreenMate Bluetooth controller received correctly encoded, VIN-matching vehicle advertisements while the app reported zero matches. Android Bluetooth logs explicitly recorded the same vehicle in `Skipping data matching denylist` entries. No raw identifiers or advertisement bytes belong in this document.

The app declared `BLUETOOTH_SCAN` with `neverForLocation` and restricted its location permissions to API 30. Android 14 applies a location-advertisement denylist to scans from a client that disavows location; filtered reports never reach the app's VIN matcher. A higher-duty app scan did not remove this gate.

Android implementation reference: `GattService.java`, `mLocationDenylistPredicate`, and the `client.hasDisavowedLocation` gate in `onScanResult`:
https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/android/app/src/com/android/bluetooth/gatt/GattService.java

## Fix and permission scope

- Do not declare `neverForLocation` for this beacon protocol.
- Declare/request precise and approximate location on modern Android, in addition to Bluetooth scan/connect. Require precise permission before starting a scan; preserve the existing reconnect desire if permission is unavailable.
- Use location permission only to allow Android to deliver Bluetooth results. Do not collect GPS, request background-location permission, change system Bluetooth denylist policy, or reset Bluetooth.
- Keep VIN-bound matching, the enrolled key, encrypted preferences, indefinite bounded-backoff retries, and the configured no-wake polling interval unchanged.

## Verification

`python3 -m unittest discover -s tests -p test_ble_location_permission.py -v` executes manifest and real UI/service-method permission regressions. The three regressions were observed failing before the change, then passing afterward.

A signed build/test result is not device proof. After a same-signer in-place update and the driver's permission grant, independently verify: advertisement match, GATT readiness, authenticated telemetry freshness, and downstream iAP2 traffic. Keep Apple Maps EV Connect enrollment distinct from those lower-level successes.
