import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "app/src/main/java/org/opencharge/teslable/ApiTokenStore.java"
TESLA = ROOT / "app/src/main/java/org/opencharge/teslable/NativeTesla.java"


class ApiTokenStoreContractTests(unittest.TestCase):
    def test_api_token_uses_new_keystore_alias_and_encrypted_private_preferences(self):
        source = STORE.read_text(encoding="utf-8") if STORE.exists() else ""
        self.assertIn('"tesla_ble_api_token_v1"', source)
        self.assertIn('"AndroidKeyStore"', source)
        self.assertIn('MODE_PRIVATE', source)
        self.assertIn("ApiTokenCipher.encrypt", source)
        self.assertIn("ApiTokenCipher.decrypt", source)
        self.assertIn("commit()", source)

    def test_existing_vehicle_key_alias_is_not_repurposed(self):
        source = TESLA.read_text(encoding="utf-8")
        self.assertIn('"tesla_ble_monitor_local_key_v1"', source)


if __name__ == "__main__":
    unittest.main()
