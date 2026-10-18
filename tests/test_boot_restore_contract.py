import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "http://schemas.android.com/apk/res/android"


class BootRestoreContractTests(unittest.TestCase):
    def test_manifest_declares_boot_receiver_and_permission(self):
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {node.get(f"{{{ANDROID}}}name") for node in root.findall("uses-permission")}
        self.assertIn("android.permission.RECEIVE_BOOT_COMPLETED", permissions)
        app = root.find("application")
        receivers = [node for node in app.findall("receiver")
                     if node.get(f"{{{ANDROID}}}name", "").endswith("BootRestoreReceiver")]
        self.assertEqual(len(receivers), 1)
        receiver = receivers[0]
        self.assertEqual(receiver.get(f"{{{ANDROID}}}exported"), "false")
        actions = {node.get(f"{{{ANDROID}}}name")
                   for node in receiver.findall("intent-filter/action")}
        self.assertIn("android.intent.action.BOOT_COMPLETED", actions)

    def test_receiver_is_gated_by_persisted_user_intent(self):
        source = (ROOT / "app/src/main/java/org/opencharge/teslable/BootRestoreReceiver.java").read_text()
        self.assertIn("Intent.ACTION_BOOT_COMPLETED", source)
        self.assertIn("TeslaBleService.hasSavedReconnectIntent(context)", source)
        self.assertIn("TeslaBleService.ACTION_BOOT_RESTORE", source)
        self.assertIn("startForegroundService", source)

    def test_service_restores_only_a_saved_connection_intent(self):
        source = (ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleService.java").read_text()
        self.assertIn("ACTION_BOOT_RESTORE", source)
        self.assertIn("desiredConnection && isValidVin(currentVin)", source)
        self.assertIn("beginPassiveReconnectScan()", source)
        self.assertIn("START_STICKY", source)


if __name__ == "__main__":
    unittest.main()
