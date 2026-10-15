import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/org/opencharge/teslable/MqttSettingsActivity.java"
MAIN = ROOT / "app/src/main/java/org/opencharge/teslable/MainActivity.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


class MqttSettingsUiContractTests(unittest.TestCase):
    def test_settings_are_user_entered_private_and_commands_remain_absent(self):
        activity = ACTIVITY.read_text(encoding="utf-8")
        main = MAIN.read_text(encoding="utf-8")
        manifest = MANIFEST.read_text(encoding="utf-8")
        self.assertIn("TYPE_TEXT_VARIATION_PASSWORD", activity)
        self.assertIn("FLAG_SECURE", activity)
        self.assertIn("MqttConfigStore", activity)
        self.assertIn("store.getDeviceId()", activity)
        self.assertIn("Device ID", activity)
        self.assertIn("connection has not been verified", activity)
        self.assertIn("MqttSettingsActivity.class", main)
        self.assertIn('android:name=".MqttSettingsActivity"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertIn("legacy HA unique IDs are not migrated", activity)
        self.assertNotIn("Log.", activity)
        self.assertNotIn("System.out", activity)
        self.assertNotIn("EXTRA_TOKEN", activity)


if __name__ == "__main__":
    unittest.main()
