import unittest,subprocess,tempfile,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
VIN='5YJ3E1EBXJF096057'
class LegacyDiscoveryCatalogTests(unittest.TestCase):
 def render(self,vin=VIN):
  with tempfile.TemporaryDirectory() as out:
   sources=[ROOT/'app/src/main/java/org/opencharge/teslable/LegacyMqttDiscoveryCatalog.java',ROOT/'tests/java/LegacyMqttDiscoveryCatalogHarness.java']
   subprocess.run(['javac','-d',out,*map(str,sources)],check=True,capture_output=True,text=True)
   r=subprocess.run(['java','-cp',out,'org.opencharge.teslable.LegacyMqttDiscoveryCatalogHarness',vin],check=True,capture_output=True,text=True)
   return [json.loads(line) for line in r.stdout.splitlines()]
 def test_component_scoped_registry_identity_and_availability(self):
  actual=self.render();catalog={(d.get('device',{}).get('identifiers',[None])[0],d['unique_id']) for d in actual}
  registry=json.loads((ROOT/'docs/LEGACY_RUNTIME_HA_MANIFEST.json').read_text())['entities']
  expected=[e for e in registry if e['unique_id'].startswith('tesla_ble_'+VIN+'_')]
  self.assertEqual(len(expected),79)
  # Count per-domain identity using the source fixture; shared presence IDs are intentional.
  self.assertGreaterEqual(len(actual),79)
  for entry in expected:self.assertIn(('tesla_ble_'+VIN,entry['unique_id']),catalog)
  for d in actual:
   self.assertNotIn('availability_topic',d)
   self.assertEqual(d['availability_mode'],'all')
   for a in d['availability']:self.assertTrue(a['topic'].startswith('tesla_ble/'+VIN+'/availability'))
  trackers=[d for d in actual if d.get('state_topic','').endswith('/device_tracker/presence_bc')]
  self.assertEqual(len(trackers),1)
 def test_vin_replacement_and_original_state_units(self):
  other='5YJ3E1EA7JF000001';actual=self.render(other)
  self.assertNotIn(VIN,json.dumps(actual))
  loaded=json.loads((ROOT/'docs/LEGACY_RUNTIME_MQTT_DISCOVERY.json').read_text())['entities'];by_uid={d['unique_id']:d for d in self.render()}
  for e in loaded:
   c=e['config'];d=by_uid[c['unique_id']]
   for field in ['state_topic','command_topic','unit_of_measurement','min','max','options']:
    if field in c:self.assertEqual(d[field],c[field])
