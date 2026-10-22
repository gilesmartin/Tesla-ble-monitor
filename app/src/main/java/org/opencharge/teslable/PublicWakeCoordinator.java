package org.opencharge.teslable;

import java.util.UUID;

/** A single bounded explicit wake job. Only Owner's looper calls native/BLE methods. */
public final class PublicWakeCoordinator {
 public interface Owner {
  long now(); boolean hasEnrollment(); boolean connected(); boolean busy();
  void connectionLease(); void release(); boolean enqueue(Runnable task,long delayMs);
  boolean wake(String requestId);
 }
 private final Owner owner;
 private boolean pending,sent;
 private long generation,deadline;
 private String id;
 private String outcome="idle";
 public PublicWakeCoordinator(Owner owner){if(owner==null)throw new IllegalArgumentException();this.owner=owner;}
 public synchronized boolean pending(){return pending;}
 public synchronized String outcome(){return outcome;}
 public synchronized int request(){
  if(pending)return 409;
  pending=true;sent=false;id="publicwake_"+UUID.randomUUID().toString();deadline=owner.now()+45000L;outcome="queued";
  long serial=++generation;
  if(!owner.enqueue(()->pump(serial),0)){pending=false;outcome="owner_unavailable";return 503;}
  return 202;
 }
 private synchronized void pump(long serial){
  if(!pending||serial!=generation)return;
  if(owner.now()>=deadline){finish("timed_out");return;}
  if(!owner.hasEnrollment()){finish("enrollment_required");return;}
  if(!sent){
   if(!owner.connected()){owner.connectionLease();}
   else if(!owner.busy()){
    sent=true;outcome="awaiting_acknowledgement";
    try{if(!owner.wake(id)){finish("dispatch_failed");return;}}
    catch(RuntimeException e){finish("dispatch_failed");return;}
   }
  }
  if(pending&&serial==generation&&!owner.enqueue(()->pump(serial),250L))finish("owner_unavailable");
 }
 public synchronized void result(String requestId,String status,boolean acknowledged){
  if(pending&&id.equals(requestId))finish(acknowledged&&"succeeded".equals(status)?"command_acknowledged":"command_failed");
 }
 /** Called on Owner's looper on explicit disconnect, disable and lifecycle cleanup. */
 public synchronized void cancel(){if(pending)finish("cancelled");}
 private void finish(String value){
  if(!pending)return;
  pending=false;sent=false;generation++;outcome=value;id=null;owner.release();
 }
}
