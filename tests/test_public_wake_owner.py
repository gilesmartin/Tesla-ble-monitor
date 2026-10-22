import unittest,subprocess,tempfile,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
HARNESS='package org.opencharge.teslable;\nimport java.util.*;\npublic class WakeOwnerHarness {\n static class Fake implements PublicWakeCoordinator.Owner {\n  long now;boolean enrolled=true,connected=true,busy=false,accept=true;int wakes,leases,releases;String id;TreeMap<Long,List<Runnable>> q=new TreeMap<>();\n  public long now(){return now;}public boolean hasEnrollment(){return enrolled;}\n  public boolean connected(){return connected;}public boolean busy(){return busy;}\n  public void connectionLease(){leases++;}public void release(){releases++;}\n  public boolean enqueue(Runnable r,long delay){q.computeIfAbsent(now+delay,x->new ArrayList<>()).add(r);return true;}\n  public boolean wake(String i){wakes++;id=i;return accept;}\n  void advance(long ms){long end=now+ms;while(!q.isEmpty()&&q.firstKey()<=end){now=q.firstKey();for(Runnable r:q.pollFirstEntry().getValue())r.run();}now=end;}\n }\n static void ok(boolean x){if(!x)throw new AssertionError();}\n public static void main(String[] args){Fake f=new Fake();PublicWakeCoordinator c=new PublicWakeCoordinator(f);\n  switch(args[0]){\n   case "queued":ok(c.request()==202);ok(f.wakes==0);f.advance(0);ok(f.wakes==1);ok(c.pending());c.result(f.id,"succeeded",true);ok(!c.pending());ok(f.releases==1);break;\n   case "disconnected":f.connected=false;ok(c.request()==202);f.advance(100);ok(f.wakes==0&&f.leases>0);f.connected=true;f.advance(250);ok(f.wakes==1);break;\n   case "busy":f.busy=true;c.request();f.advance(1000);ok(f.wakes==0);f.busy=false;f.advance(250);ok(f.wakes==1);break;\n   case "timeout":f.connected=false;c.request();f.advance(46000);ok(f.wakes==0&&!c.pending()&&f.releases==1);break;\n   case "overlap":ok(c.request()==202&&c.request()==409);f.advance(1000);ok(f.wakes==1);break;\n   case "missing":f.enrolled=false;c.request();f.advance(0);ok(f.wakes==0&&!c.pending());break;\n   case "cancel":c.request();c.cancel();f.advance(50000);ok(f.wakes==0&&!c.pending());break;\n   case "stale_result":c.request();f.advance(0);c.result("wrong-request","succeeded",true);ok(c.pending());c.result(f.id,"failed",false);ok(!c.pending());break;\n   case "callback_wait":c.request();f.advance(30000);ok(f.wakes==1&&c.pending());f.advance(16000);ok(f.wakes==1&&!c.pending());break;\n   default:throw new AssertionError();\n  }System.out.println("PASS "+args[0]);\n }\n}'
class PublicWakeOwnerTests(unittest.TestCase):
 def test_real_coordinator_on_fake_owner(self):
  self.assertTrue((JAVA/'PublicWakeCoordinator.java').is_file(),'missing bounded owner coordinator')
  with tempfile.TemporaryDirectory()as tmp:
   p=Path(tmp);(p/'WakeOwnerHarness.java').write_text(HARNESS)
   c=subprocess.run(['javac','-d',tmp,str(JAVA/'PublicWakeCoordinator.java'),str(p/'WakeOwnerHarness.java')],capture_output=True,text=True)
   self.assertEqual(c.returncode,0,c.stderr)
   for case in CASES:
    with self.subTest(case=case):
     c=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.WakeOwnerHarness',case],capture_output=True,text=True,timeout=10)
     self.assertEqual(c.returncode,0,c.stdout+c.stderr);self.assertIn('PASS '+case,c.stdout)

CASES=["queued","disconnected","busy","timeout","overlap","missing","cancel","stale_result","callback_wait"]
