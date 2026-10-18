import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleService.java"


class MqttServiceIntegrationContractTests(unittest.TestCase):
    def test_mqtt_is_added_without_changing_local_api_or_ble_ownership(self):
        source = SERVICE.read_text(encoding="utf-8")
        self.assertIn("MqttConfigStore", source)
        self.assertIn("MqttBridgePublisher", source)
        self.assertIn("MqttTelemetryMapper.toStates", source)
        self.assertIn("mqttFreshnessTask", source)
        self.assertIn("main.removeCallbacks(mqttFreshnessTask)", source)

        start = source.index("private void updateMqttTelemetry()")
        end = source.find("\n    private ", start + 1)
        self.assertGreater(end, start)
        method = source[start:end]
        for forbidden in ("requestCharge(", "connectSelectedDevice(", "beginScan(", "tesla.tick(", "last_known"):
            self.assertNotIn(forbidden, method)

        diff = subprocess.check_output(
            ["git", "diff", "--", "app/src/main/java/org/opencharge/teslable/TelemetryHttpServer.java", "docs/LOCAL_API.md"],
            cwd=ROOT, text=True)
        self.assertEqual(diff, "", "the DiPlay API implementation and contract must remain unchanged")


if __name__ == "__main__":
    unittest.main()
