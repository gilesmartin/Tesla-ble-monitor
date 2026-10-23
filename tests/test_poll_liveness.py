import os,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SERVICE=ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java'
def method(text,name):
 start=text.index('    private void '+name+'(');brace=text.index('{',start);depth=1;end=brace+1
 while depth:
  depth+=(text[end]=='{')-(text[end]=='}');end+=1
 return text[start:end]
class PollLivenessTests(unittest.TestCase):
 def run_case(self,mode):
  text=SERVICE.read_text();methods=method(text,'runPoll')+'\n'+method(text,'schedulePoll')
  constants='\n'.join(re.findall(r'    private static final long POLL_BUSY_RECHECK_MS\s*=\s*[^;]+;',text))
  source='''import java.util.*;
public final class PollLivenessHarness {
 boolean publicWakeInFlight=false,pollEnabled=true,ready=true,pollInFlight=false,driveReadInFlight=false,vcsecProbeInFlight=false; int pollIntervalSeconds=720,reads;
 long now; final Handler main=new Handler(); final Runnable pollTask=this::runPoll;
 static final class Legacy { boolean pending; boolean hasPendingCommand(){return pending;} }
 Legacy legacyMqttPublisher=new Legacy();
 final class Handler {
  final TreeMap<Long,List<Runnable>> tasks=new TreeMap<>();
  void removeCallbacks(Runnable r){for(var l:tasks.values())l.removeIf(x->x==r);tasks.entrySet().removeIf(e->e.getValue().isEmpty());}
  void postDelayed(Runnable r,long delay){tasks.computeIfAbsent(now+delay,k->new ArrayList<>()).add(r);}
  void advance(long target){int guard=0;while(!tasks.isEmpty()&&tasks.firstKey()<=target){if(++guard>1000)throw new AssertionError("busy spin");var e=tasks.pollFirstEntry();now=e.getKey();for(var r:e.getValue())r.run();}now=target;}
 }
 void requestCharge(boolean periodic){if(pollInFlight||driveReadInFlight||vcsecProbeInFlight||legacyMqttPublisher.pending)throw new AssertionError("overlapping vehicle read");reads++;}
'''+constants+'\n'+methods+'''
 public static void main(String[]args){
  PollLivenessHarness s=new PollLivenessHarness();
  if(args[0].equals("publicwake")){
   s.publicWakeInFlight=true;s.schedulePoll(0);s.main.advance(5000);if(s.reads!=0)throw new AssertionError("poll overlapped explicit wake");
   s.publicWakeInFlight=false;s.main.advance(60000);if(s.reads!=1)throw new AssertionError("due poll not recovered after wake");return;
  }
  if(args[0].equals("vcsec")){
   s.vcsecProbeInFlight=true;s.schedulePoll(0);s.main.advance(5000);if(s.reads!=0)throw new AssertionError("charge overlapped security status owner");s.vcsecProbeInFlight=false;s.main.advance(60000);if(s.reads!=1)throw new AssertionError("overdue charge not resumed after security status");return;
  }
  if(args[0].equals("drive")){
   s.driveReadInFlight=true;s.schedulePoll(0);s.main.advance(0);
   if(s.reads!=0)throw new AssertionError("charge overlapped DriveState owner");
   s.main.advance(5000);s.driveReadInFlight=false;s.main.advance(60000);
   if(s.reads!=1)throw new AssertionError("due charge not recovered after DriveState owner released");
   s.main.advance(80000);if(s.reads!=1)throw new AssertionError("duplicate catch-up charge");return;
  }
  if(args[0].equals("cadence")){
   s.schedulePoll(720000);s.main.advance(719999);if(s.reads!=0)throw new AssertionError("poll cadence shortened");
   s.main.advance(720000);if(s.reads!=1)throw new AssertionError("normal scheduled poll missing");return;
  }
  if(args[0].equals("disabled")){s.pollEnabled=false;s.runPoll();s.main.advance(1500000);if(s.reads!=0)throw new AssertionError("poll while disabled");return;}
  if(args[0].equals("disconnected")){s.ready=false;s.runPoll();s.main.advance(1500000);if(s.reads!=0)throw new AssertionError("poll while disconnected");return;}
  if(args[0].equals("busy")){s.legacyMqttPublisher.pending=true;s.schedulePoll(720000);s.main.advance(900000);if(s.reads!=0)throw new AssertionError("read overlapped long-running command");return;}
  boolean inflight=args[0].equals("inflight");
  if(inflight)s.pollInFlight=true;else s.legacyMqttPublisher.pending=true;
  s.schedulePoll(720000);s.main.advance(720000);
  if(s.reads!=0)throw new AssertionError("read while owner busy");
  s.main.advance(725000);if(inflight)s.pollInFlight=false;else s.legacyMqttPublisher.pending=false;
  s.main.advance(780000);
  if(s.reads!=1)throw new AssertionError("due telemetry poll was lost: brief busy period postponed it by another full polling interval; reads="+s.reads);
  s.main.advance(800000);if(s.reads!=1)throw new AssertionError("duplicate catch-up poll");
  System.out.println("PASS "+args[0]+": one overdue poll recovered without overlap or duplicate");
 }
}
'''
  with tempfile.TemporaryDirectory()as d:
   p=Path(d)/'PollLivenessHarness.java';p.write_text(source)
   c=subprocess.run(['javac','-d',d,str(p)],capture_output=True,text=True);self.assertEqual(c.returncode,0,c.stderr)
   r=subprocess.run(['java','-cp',d,'PollLivenessHarness',mode],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_brief_vehicle_command_does_not_lose_due_telemetry_poll(self):self.run_case('command')
 def test_brief_existing_read_does_not_lose_due_telemetry_poll(self):self.run_case('inflight')
 def test_due_charge_defers_to_drive_read_and_catches_up(self):self.run_case('drive')
 def test_due_poll_waits_for_explicit_public_wake_owner(self):self.run_case('publicwake')
 def test_due_charge_defers_to_security_status_owner(self):self.run_case('vcsec')
 def test_normal_poll_interval_is_preserved(self):self.run_case('cadence')
 def test_disabled_polling_stays_disabled(self):self.run_case('disabled')
 def test_disconnected_owner_performs_no_read(self):self.run_case('disconnected')
 def test_long_running_command_does_not_overlap_reads(self):self.run_case('busy')
if __name__=='__main__':unittest.main()
