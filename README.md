# Tesla BLE Monitor (Android, test prototype)

**Status:** Source prototype, **not yet successfully compiled or tested on a device**. It uses real Tesla BLE rather than mock telemetry and does not require root. The external C++ protocol library is built by CMake when Gradle builds the application. A GitHub Actions workflow is included to produce an APK.

## Capabilities in this first build

- Android 8.0+ with BLE; BLE scan/connection permissions requested at runtime for Android 12+
- VIN-based matching of Tesla BLE advertisement (`S` + SHA-1(VIN)[0:8] hex + `C`)
- GATT connection, CCCD notification subscription, write-with-response fragmentation, asynchronous queue
- Native Tesla protocol engine: `yoziru/tesla-ble` via JNI (session handshakes, Tesla key pairing, protobuf decoding)
- **Vehicle Monitor** key enrollment (read-only role), with physical Tesla key-card confirmation
- Explicit, manual VCSEC status + charge-state requests while vehicle is awake
- Battery percentage, usable battery percentage, battery range / estimated range (converted from miles to kilometres), and charging state
- Vehicle-specific application key material and session state encrypted using Android Keystore AES-256-GCM
- No remote API, no wake button, no CAN, no background service, no simulated data

## Build an installable APK (GitHub Actions)

1. Upload all the files in this directory into a GitHub repository (preserve `.github/workflows/android-apk.yml`).
2. Push the repository's `main` branch or open **Actions → Build Android test APK → Run workflow**.
3. Download the **tesla-ble-monitor-debug-apk** artifact from the completed workflow run. Unzip it to find `app-debug.apk`.
4. Install with `adb install -r app-debug.apk` or transfer the APK directly to the Android device.

**The build fetches the protocol-engine source and native dependencies from GitHub, so network access is required.** Native compilation can take several minutes. The Android SDK 35 and NDK 28 will be installed in the GitHub Actions runner.

Local Android Studio alternative: Open this folder in Android Studio (JDK 17, Gradle 8.9, Android SDK 35, NDK 28.2.13676358, CMake 3.22.1). Build → Build APK(s). A Gradle wrapper binary isn't bundled; use an installed Gradle 8.9 or generate a wrapper (`gradle wrapper --gradle-version 8.9`).

## First test (in a parked, awake Model 3)

1. Enable Bluetooth and install the app. Grant the requested permissions.
2. Enter the full 17-character VIN. Tap **Scan** while within BLE range. Verify the expected BLE name is found.
3. Select your Tesla, then tap **Connect**. The status should become **Tesla BLE transport ready**.
4. Tap **Enroll monitor key** once. Have your Tesla NFC key card available; approve the request on the Tesla screen. Check Controls → Locks for the new key. This is **Tesla vehicle-key enrollment**, not Android Bluetooth bonding.
5. Tap **Read charge state**. Watch for a fresh `ChargeState` result. Never read from a sleeping car during this stage.
6. Capture the on-screen diagnostics or `adb logcat -s TeslaBLE:* AndroidRuntime:*` for failures. Do **not** include key material when sharing logs.

The app does not poll automatically. Keep it in the foreground for testing. Pairing a key may consume one of the vehicle's allowed key slots. Remove the entry from Controls → Locks if testing is abandoned. Tesla's VCSEC has a limited number of simultaneous Bluetooth connections (keyfobs/phone keys can occupy them).

## Limitations and development notes

- The C++ Tesla protocol dependency (`yoziru/tesla-ble`) uses the **AGPL-3.0** license. Distributing builds requires following that license's obligations. The Android/JNI glue is only an initial test implementation.
- This CMake version pins upstream commit `42b899b7816cc3700b7361e65b526fdc04d19a51` (October 12, 2026) for repeatability; the snapshot is not device-validated.
- This prototype hasn't been independently verified against a real Tesla. Pairing acceptance, infotainment reachability, permissions, CCCD, and read-only Vehicle Monitor role need real hardware testing.
- No automated reconnect, lifecycle-resilient background service, driver-screen-safe experience, charging commands, or wake support are included. Those come after the initial app works reliably.
- BLE radio performance varies by Android vendor. Some models restrict scanning during airplane mode or power-saving mode.
- `range_km` and `est_range_km` are Tesla-reported range estimates, not an independent prediction of actual achievable range.
- Android's Keystore secures app keys at rest subject to device security; rooted devices can still be compromised while the app runs. No backup is allowed.

## File map

- `app/src/main/java/org/opencharge/teslable/MainActivity.java` — UI, Android BLE GATT, queue/framing
- `app/src/main/java/org/opencharge/teslable/NativeTesla.java` — Tesla protocol JNI and Android Keystore storage
- `app/src/main/cpp/native_bridge.cpp` — JNI bridge to the TeslaBLE C++ library
- `app/src/main/cpp/CMakeLists.txt` — CMake dependency fetch/build
- `.github/workflows/android-apk.yml` — GitHub Actions APK builder

## Why a source ZIP instead of APK here?

The development runtime used to prepare this bundle does not have an Android SDK, NDK, Gradle, `aapt2`, or an Android device attached, and has no network access to install them. Therefore no finished `.apk` can be built or tested in this runtime. **Do not rename this ZIP as an APK.** Use GitHub Actions or Android Studio as above to build it.
