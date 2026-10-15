import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "app/src/main/java/org/opencharge/teslable/MqttConfigStore.java"


class MqttConfigStoreContractTests(unittest.TestCase):
    def test_credentials_use_separate_keystore_key_and_encrypted_private_preferences(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn('"tesla_ble_mqtt_credentials_v1"', source)
        self.assertIn("Context.MODE_PRIVATE", source)
        self.assertIn("MqttCredentialCipher.encrypt", source)
        self.assertIn("MqttCredentialCipher.decrypt", source)
        self.assertIn("getBoolean(PREF_ENABLED, false)", source)
        self.assertRegex(source, r"putString\(PREF_CREDENTIALS,\s*encrypted\)")
        self.assertNotIn("putString(PREF_USERNAME", source)
        self.assertNotIn("putString(PREF_PASSWORD", source)
        self.assertIn("MqttBridgeConfig.validateBrokerUri", source)


if __name__ == "__main__":
    unittest.main()
