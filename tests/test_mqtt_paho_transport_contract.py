import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRANSPORT = ROOT / "app/src/main/java/org/opencharge/teslable/PahoMqttTransport.java"


class PahoMqttTransportContractTests(unittest.TestCase):
    def test_paho_transport_is_async_reconnecting_and_does_not_log_secrets(self):
        source = TRANSPORT.read_text(encoding="utf-8")
        self.assertIn("MqttAsyncClient", source)
        self.assertIn("MemoryPersistence", source)
        self.assertIn("ScheduledExecutorService", source)
        self.assertIn("scheduleRetry", source)
        self.assertIn("setAutomaticReconnect(false)", source)
        self.assertIn("setHttpsHostnameVerificationEnabled(true)", source)
        self.assertIn("setWill(", source)
        self.assertIn("setUserName(username)", source)
        self.assertIn("setPassword(password)", source)
        self.assertNotIn("System.out", source)
        self.assertNotIn("Log.", source)
        self.assertNotIn("getMessage()", source)


if __name__ == "__main__":
    unittest.main()
