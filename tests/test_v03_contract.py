"""Static safety/packaging contract checks for the v0.3 Android migration."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "http://schemas.android.com/apk/res/android"


def source(path):
    candidate = ROOT / path
    return candidate.read_text(encoding="utf-8") if candidate.exists() else ""


class V03Contract(unittest.TestCase):
    def test_identity_and_version_are_update_compatible(self):
        gradle = source("app/build.gradle")
        self.assertRegex(gradle, r"applicationId\s+['\"]org\.opencharge\.teslable['\"]")
        self.assertRegex(gradle, r"versionName\s+['\"]0\.3\.3-beacon-fix['\"]")
        code = re.search(r"versionCode\s+(\d+)", gradle)
        self.assertIsNotNone(code)
        self.assertGreater(int(code.group(1)), 3)

    def test_foreground_service_is_private_and_typed(self):
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = root.find("application")
        services = [x for x in app.findall("service") if x.get(f"{{{ANDROID}}}name", "").endswith("TeslaBleService")]
        self.assertEqual(len(services), 1)
        svc = services[0]
        self.assertEqual(svc.get(f"{{{ANDROID}}}exported"), "false")
        self.assertIn("connectedDevice", svc.get(f"{{{ANDROID}}}foregroundServiceType", ""))
        perms = {x.get(f"{{{ANDROID}}}name") for x in root.findall("uses-permission")}
        self.assertIn("android.permission.FOREGROUND_SERVICE", perms)
        self.assertIn("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE", perms)

    def test_adb_activity_keeps_dump_boundary_and_repeated_command_handler(self):
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = root.find("application")
        act = next(x for x in app.findall("activity") if x.get(f"{{{ANDROID}}}name", "").endswith("AdbCommandActivity"))
        self.assertEqual(act.get(f"{{{ANDROID}}}permission"), "android.permission.DUMP")
        text = source("app/src/main/java/org/opencharge/teslable/AdbCommandActivity.java")
        self.assertIn("onNewIntent", text)
        for cmd in ("connect", "disconnect", "status", "read-charge", "poll-on", "poll-off", "poll-interval", "storage-test", "stop", "set-api-token", "pair"):
            self.assertIn('"' + cmd + '"', text)

    def test_gui_is_not_a_second_ble_or_native_owner(self):
        text = source("app/src/main/java/org/opencharge/teslable/MainActivity.java")
        for forbidden in ("connectGatt(", "BluetoothGattCallback", "new NativeTesla("):
            self.assertNotIn(forbidden, text)
        self.assertIn("TeslaBleService", text)

    def test_native_no_wake_policy_and_existing_keystore_alias_are_preserved(self):
        native = source("app/src/main/cpp/native_bridge.cpp")
        self.assertIn("WakePolicy::NO_WAKE_SKIP", native)
        self.assertIn("WakePolicy::NO_WAKE_FAIL", native)
        tesla = source("app/src/main/java/org/opencharge/teslable/NativeTesla.java")
        self.assertIn('"tesla_ble_monitor_local_key_v1"', tesla)
        self.assertIn("storageSelfTest", tesla)

    def test_api_binds_ipv4_loopback_only_and_requires_bearer_auth(self):
        text = source("app/src/main/java/org/opencharge/teslable/TelemetryHttpServer.java")
        self.assertIn('"127.0.0.1"', text)
        self.assertNotIn('"0.0.0.0"', text)
        self.assertIn("8765", text)
        self.assertIn("authorization", text.lower())
        self.assertIn("Bearer ", text)
        self.assertIn("MessageDigest.isEqual", text)
        self.assertIn("/v1/telemetry", text)
        self.assertIn("/v1/status", text)

    def test_api_read_timeout_allows_local_adb_forward_latency(self):
        text = source("app/src/main/java/org/opencharge/teslable/TelemetryHttpServer.java")
        self.assertIn("client.setSoTimeout(5000)", text)

    def test_api_distinguishes_missing_stale_and_fresh_telemetry(self):
        text = source("app/src/main/java/org/opencharge/teslable/TeslaBleService.java")
        for marker in ("last_successful_update", "age_seconds", "fresh", "last_known", "battery_percent", "usable_battery_percent", "range_km", "charging"):
            self.assertIn(marker, text)
        self.assertIn("isNull", text)


if __name__ == "__main__":
    unittest.main()
