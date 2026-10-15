import os,pathlib,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class MqttSettingsStoreBehaviorTests(unittest.TestCase):
 def test_execute_real_store_and_cipher_with_private_preference_standin(self):
  with tempfile.TemporaryDirectory() as tmp:
   src=ROOT/'app/src/main/java/org/opencharge/teslable'
   files=list((ROOT/'tests/java/mqtt_settings_stubs').rglob('*.java'))+[src/'MqttConfigStore.java',src/'MqttBridgeConfig.java',src/'MqttCredentialCipher.java',ROOT/'tests/java/MqttSettingsStoreHarness.java']
   subprocess.run(['javac','-d',tmp,*map(str,files)],check=True,capture_output=True,text=True)
   r=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.MqttSettingsStoreHarness'],check=True,capture_output=True,text=True)
   self.assertIn('PASS:',r.stdout)
