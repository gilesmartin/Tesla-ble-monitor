package org.opencharge.teslable;
import java.util.*;
public final class MqttPublishQueueHarness {
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 static final class Fixture {
  final List<MqttPublishQueue.Callback> callbacks=new ArrayList<>();final List<String> sent=new ArrayList<>();final ArrayDeque<Runnable> later=new ArrayDeque<>();MqttPublishQueue queue;int closed;
  Fixture(int cap){queue=new MqttPublishQueue((t,p,q,r,done)->{ok(!Thread.holdsLock(this.queue),"sender under queue lock");sent.add(t);callbacks.add(done);},(work,delay)->later.add(work),cap);}
  void tick(){ok(!later.isEmpty(),"missing scheduled work");later.remove().run();}
 }
 public static void main(String[] args){
  Fixture f=new Fixture(2);f.queue.setConnected(true);f.queue.offer("one","1",1,true);f.queue.offer("two","2",1,true);
  try{f.queue.offer("overflow","3",1,true);throw new AssertionError("capacity ignored");}catch(IllegalStateException expected){}
  ok(f.queue.pendingCount()==2&&f.sent.equals(List.of("one")),"capacity includes in-flight");f.callbacks.get(0).complete(true);f.tick();ok(f.sent.equals(List.of("one","two")),"FIFO");f.callbacks.get(1).complete(true);ok(f.queue.pendingCount()==0,"ACK removal");
  Fixture retry=new Fixture(2);retry.queue.setConnected(true);retry.queue.offer("retry","value",1,true);retry.callbacks.get(0).complete(false);retry.tick();ok(retry.callbacks.size()==2,"retry same head");retry.callbacks.get(0).complete(true);ok(retry.queue.pendingCount()==1,"late callback from failed send acknowledged newer retry");retry.callbacks.get(1).complete(true);ok(retry.queue.pendingCount()==0,"current retry ACK");
  Fixture reconnect=new Fixture(2);reconnect.queue.setConnected(true);reconnect.queue.offer("old","old",1,true);reconnect.queue.setConnected(false);reconnect.queue.setConnected(true);reconnect.queue.offer("new","new",1,true);reconnect.callbacks.get(0).complete(true);ok(reconnect.queue.pendingCount()==1,"old connection ACK consumed new head");reconnect.callbacks.get(1).complete(true);
  Fixture close=new Fixture(2);close.queue.setConnected(true);close.queue.offer("pending","x",1,true);close.queue.closeWhenDrained(()->{ok(!Thread.holdsLock(close.queue),"close callback under lock");close.closed++;},3000);close.tick();ok(close.closed==1&&close.queue.pendingCount()==0,"bounded close timeout");close.callbacks.get(0).complete(true);ok(close.closed==1,"duplicate close");try{close.queue.offer("late","x",1,true);throw new AssertionError("offer after closing");}catch(IllegalStateException expected){}
  System.out.println("PASS FIFO capacity, per-attempt callbacks, reconnect generation, deadline close and lock discipline");
 }
}
