import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/java/org/opencharge/teslable/MqttDiscoveryCatalog.java"
HARNESS = ROOT / "tests/java/MqttDiscoveryCatalogHarness.java"


class MqttDiscoveryCatalogTests(unittest.TestCase):
    def test_catalog_is_read_only_and_discovery_payloads_are_stable(self):
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK javac is required for discovery tests")
        self.assertIsNotNone(java, "JDK java is required for discovery tests")
        with tempfile.TemporaryDirectory(prefix="tesla-mqtt-discovery-") as output:
            compiled = subprocess.run(
                [javac, "-encoding", "UTF-8", "-d", output, str(CATALOG), str(HARNESS)],
                cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(
                [java, "-cp", output, "org.opencharge.teslable.MqttDiscoveryCatalogHarness"],
                cwd=ROOT, text=True, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

        entries = []
        for line in result.stdout.splitlines():
            topic, payload = line.split("|", 1)
            entries.append((topic, json.loads(payload)))
        self.assertEqual(len(entries), 4)
        by_name = {payload["name"]: (topic, payload) for topic, payload in entries}
        self.assertEqual(set(by_name), {"Battery", "Usable battery", "Estimated range", "Charging"})
        for topic, payload in entries:
            self.assertIn("test_vehicle_123_", payload["unique_id"])
            self.assertTrue(topic.startswith("homeassistant/"))
            self.assertNotIn("command_topic", payload)
            self.assertEqual(payload["availability_topic"], "tesla_ble_monitor/test_vehicle_123/availability")
            self.assertEqual(payload["device"]["identifiers"], ["test_vehicle_123"])
        battery = by_name["Battery"][1]
        self.assertEqual(battery["state_topic"], "tesla_ble_monitor/test_vehicle_123/battery_percent")
        self.assertEqual(battery["unit_of_measurement"], "%")
        self.assertEqual(battery["device_class"], "battery")
        range_sensor = by_name["Estimated range"][1]
        self.assertEqual(range_sensor["unit_of_measurement"], "km")
        self.assertEqual(range_sensor["device_class"], "distance")
        charging = by_name["Charging"][1]
        self.assertEqual(charging["payload_on"], "true")
        self.assertEqual(charging["payload_off"], "false")
        self.assertEqual(charging["device_class"], "battery_charging")


if __name__ == "__main__":
    unittest.main()
