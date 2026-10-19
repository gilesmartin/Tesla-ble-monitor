import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleService.java"


class MqttServiceIntegrationContractTests(unittest.TestCase):
    def test_mqtt_preserves_ble_owner_and_drive_api_is_lease_gated(self):
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

        api = (ROOT / "app/src/main/java/org/opencharge/teslable/TelemetryHttpServer.java").read_text(encoding="utf-8")
        self.assertIn("/v1/telemetry/subscribe", api)
        self.assertIn("/v1/telemetry/unsubscribe", api)
        self.assertIn("request.driveHeartbeat", api)
        self.assertIn("driveSubscription.accept(true)", api)
        self.assertIn("127.0.0.1", api)
        self.assertIn("this::onDriveSubscriptionChanged", source)
        self.assertIn("DRIVE_SUBSCRIPTION_LEASE_MS", source)
        self.assertIn("tesla.readLegacyState(\"drive\")", source)
        self.assertIn("drive_state_fresh", source)
        self.assertIn("speed_mph", source)
        self.assertIn("NO_WAKE_FAIL", source)
        for field in (
            "estimated_range_km", "drive_state_updated_at_elapsed_ms", "drive_state_age_ms",
            "shift_state", "drive_state",
        ):
            self.assertIn(field, source)
        self.assertIn("driveFresh ? drive : JSONObject.NULL", source)
        self.assertIn("lastDriveStateJson = data.toString()", source)
        self.assertNotIn("PREF_LAST_DRIVE_STATE", source)

        drive_start = source.index("private void runDrivePoll()")
        drive_end = source.index("private void finishDriveReadFailure()", drive_start)
        drive_poll = source[drive_start:drive_end]
        for forbidden in ("requestCharge(", "tesla.wake(", "tesla.command("):
            self.assertNotIn(forbidden, drive_poll)

        contract = (ROOT / "docs/LOCAL_API.md").read_text(encoding="utf-8")
        for field in ("drive_state_fresh", "drive_state_age_ms", "speed_mph", "shift_state"):
            self.assertIn(field, contract)
        self.assertIn("X-Tesla-Drive-Subscription: active", contract)


if __name__ == "__main__":
    unittest.main()
