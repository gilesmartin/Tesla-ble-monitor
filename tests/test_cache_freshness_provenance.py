import os,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
class CacheFreshnessProvenanceTests(unittest.TestCase):
 def test_real_policy_restores_mode_ceilings_and_rejects_unproven_metadata(self):
  with tempfile.TemporaryDirectory() as d:
   h=Path(d)/'Harness.java'
   h.write_text('''package org.opencharge.teslable;
public class Harness { public static void main(String[] a) {
for(long v:new long[]{30000,45000,90000,300000})if(TelemetryPollingPolicy.restoredFreshness(v)!=v)throw new AssertionError("sample provenance changed");
for(long v:new long[]{0,-1,Long.MAX_VALUE,99999})if(TelemetryPollingPolicy.restoredFreshness(v)!=30000)throw new AssertionError("unproven cache promoted");
if(TelemetryPollingPolicy.restoredFreshness(30000)>=60000)throw new AssertionError("old driving sample promoted after restart");}}
''')
   env=os.environ.copy()
   p=subprocess.run(['javac','-d',d,str(JAVA/'TelemetryPollingPolicy.java'),str(h)],env=env,capture_output=True,text=True)
   self.assertEqual(p.returncode,0,p.stdout+p.stderr)
   p=subprocess.run(['java','-cp',d,'org.opencharge.teslable.Harness'],env=env,capture_output=True,text=True)
   self.assertEqual(p.returncode,0,p.stdout+p.stderr)
 def test_real_startup_restores_provenance_before_exposing_the_api(self):
  s=(JAVA/'TeslaBleService.java').read_text()
  m=re.search(r'public void onCreate\s*\(',s)
  self.assertIsNotNone(m)
  start=s.index('{',m.end());n=1;end=start+1
  while n:
   n+=(s[end]=='{')-(s[end]=='}');end+=1
  create=s[start:end]
  self.assertRegex(create,r'lastChargeFreshnessMs\s*=\s*TelemetryPollingPolicy.restoredFreshness\(')
  self.assertRegex(create,r'preferences.getLong\(PREF_LAST_CHARGE_FRESHNESS,\s*30_000L\)')
  self.assertLess(create.index('TelemetryPollingPolicy.restoredFreshness('),create.index('startApiServer()'))
if __name__=='__main__':unittest.main()
