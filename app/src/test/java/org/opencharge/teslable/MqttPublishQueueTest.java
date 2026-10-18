package org.opencharge.teslable;
import java.util.*;
public final class MqttPublishQueueTest {
 static final class FakeScheduler implements MqttPublishQueue.Scheduler {
  record Task(Runnable work,long delay){} final ArrayDeque<Task> tasks=new ArrayDeque<>();
  public void later(Runnable r,long d){tasks.add(new Task(r,d));}
  void run(){if(tasks.isEmpty())throw new AssertionError("no scheduled work"); tasks.remove().work.run();}
  void runAll(){int n=0;while(!tasks.isEmpty()){if(++n>10000)throw new AssertionError("scheduler runaway");run();}}
 }
 static final class FakeSender implements MqttPublishQueue.Sender {
  record Sent(String topic,String payload,int qos,boolean retained,MqttPublishQueue.Callback cb){}
  final List<Sent> sent=new ArrayList<>(); public void send(String t,String p,int q,boolean r,MqttPublishQueue.Callback c){sent.add(new Sent(t,p,q,r,c));}
 }
 static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError("expected "+b+" got "+a);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void ack(FakeSender s,int i,boolean ok){s.sent.get(i).cb().complete(ok);}
 static void testOrdered200AndAsyncSingleFlight(){FakeScheduler clock=new FakeScheduler();FakeSender sender=new FakeSender();MqttPublishQueue q=new MqttPublishQueue(sender,clock,512);q.setConnected(true);for(int i=0;i<200;i++)q.offer("t", ""+i,1,false);eq(sender.sent.size(),1);for(int i=0;i<200;i++){ack(sender,i,true);if(i<199){eq(sender.sent.size(),i+1);clock.run();eq(sender.sent.size(),i+2);} }eq(q.pendingCount(),0);}
 static void testCapacityAndNoCoalescing(){FakeScheduler c=new FakeScheduler();FakeSender s=new FakeSender();MqttPublishQueue q=new MqttPublishQueue(s,c,2);q.offer("global","offline",1,false);q.offer("global","online",1,false);try{q.offer("x","3",0,false);throw new AssertionError("capacity");}catch(IllegalStateException expected){}eq(q.pendingCount(),2);q.setConnected(true);eq(s.sent.get(0).payload(),"offline");ack(s,0,true);c.run();eq(s.sent.get(1).payload(),"online");}
 static void testFailureRetryAndStaleGeneration(){FakeScheduler c=new FakeScheduler();FakeSender s=new FakeSender();MqttPublishQueue q=new MqttPublishQueue(s,c,8);q.setConnected(true);q.offer("t","old",0,false);MqttPublishQueue.Callback old=s.sent.get(0).cb();ack(s,0,false);eq(c.tasks.peek().delay(),1000L);eq(s.sent.size(),1);c.run();eq(s.sent.size(),2);q.setConnected(false);old.complete(true);eq(q.pendingCount(),0);q.setConnected(true);q.offer("t","fresh",0,false);eq(s.sent.size(),3);eq(s.sent.get(2).payload(),"fresh");}
 static void testClearReconnectSnapshot(){FakeScheduler c=new FakeScheduler();FakeSender s=new FakeSender();MqttPublishQueue q=new MqttPublishQueue(s,c,8);q.setConnected(true);q.offer("t","old1",0,false);q.offer("t","old2",0,false);q.setConnected(false);eq(q.pendingCount(),0);q.setConnected(true);q.offer("t","snapshot",0,false);eq(s.sent.get(1).payload(),"snapshot");ack(s,0,true);eq(q.pendingCount(),1);ack(s,1,true);eq(q.pendingCount(),0);}
 static void testDrainCloseExactlyOnceAndDeadline(){FakeScheduler c=new FakeScheduler();FakeSender s=new FakeSender();MqttPublishQueue q=new MqttPublishQueue(s,c,8);q.setConnected(true);q.offer("t","a",0,false);int[] closed={0};q.closeWhenDrained(()->closed[0]++,5000);try{q.offer("t","b",0,false);throw new AssertionError("accepted after closing");}catch(IllegalStateException expected){}ack(s,0,true);eq(closed[0],1);c.runAll();eq(closed[0],1);try{q.offer("t","c",0,false);throw new AssertionError("accepted closed");}catch(IllegalStateException expected){}
 FakeScheduler c2=new FakeScheduler();FakeSender s2=new FakeSender();MqttPublishQueue q2=new MqttPublishQueue(s2,c2,8);q2.setConnected(true);q2.offer("t","held",0,false);int[] deadline={0};q2.closeWhenDrained(()->deadline[0]++,5);c2.run();eq(deadline[0],1);eq(q2.pendingCount(),0);ack(s2,0,true);eq(deadline[0],1);}
 public static void main(String[]x){testOrdered200AndAsyncSingleFlight();testCapacityAndNoCoalescing();testFailureRetryAndStaleGeneration();testClearReconnectSnapshot();testDrainCloseExactlyOnceAndDeadline();System.out.println("PASS: 5 MQTT publish queue tests");}
}
