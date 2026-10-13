"""Run with `python3 -m unittest discover -s tests -v`."""
import hashlib
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class ProtocolSanity(unittest.TestCase):
    def test_official_vin_advertisement_vector(self):
        vin = "5YJS0000000000000"
        name = "S" + hashlib.sha1(vin.encode("ascii")).digest()[:8].hex() + "C"
        self.assertEqual(name, "S1a87a5a75f3df858C")

    def test_manifest_is_well_formed(self):
        ET.parse(ROOT / "app/src/main/AndroidManifest.xml")

    def test_no_fake_telemetry(self):
        src = (ROOT / "app/src/main/cpp/native_bridge.cpp").read_text()
        self.assertIn("set_charge_state_callback", src)
        self.assertIn("CarServer_GetVehicleData_getChargeState_tag", src)

if __name__ == '__main__':
    unittest.main()
