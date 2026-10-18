import unittest,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class MqttLegacyOptionsTests(unittest.TestCase):
 def test_atomic_legacy_options_on_real_store(self):
  with tempfile.TemporaryDirectory() as out:
   src=ROOT/'app/src/main/java/org/opencharge/teslable'
   files=list((ROOT/'tests/java/mqtt_settings_stubs').rglob('*.java'))+[src/(n+'.java') for n in ['MqttConfigStore','MqttBridgeConfig','MqttCredentialCipher']]+[ROOT/'tests/java/MqttSettingsStoreHarness.java',ROOT/'tests/java/MqttLegacyOptionsHarness.java']
   subprocess.run(['javac','-d',out,*map(str,files)],check=True,capture_output=True,text=True)
   r=subprocess.run(['java','-cp',out,'org.opencharge.teslable.MqttLegacyOptionsHarness'],capture_output=True,text=True)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr)
