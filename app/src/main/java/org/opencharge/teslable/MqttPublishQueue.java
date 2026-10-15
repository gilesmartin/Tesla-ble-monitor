package org.opencharge.teslable;

import java.util.ArrayDeque;
import java.util.Objects;

/** Bounded, FIFO, single-flight asynchronous publish queue. */
public final class MqttPublishQueue {
 public interface Sender { void send(String topic,String payload,int qos,boolean retained,Callback cb); }
 public interface Callback { void complete(boolean acknowledged); }
 public interface Scheduler { void later(Runnable work,long delayMillis); }
 private record Message(String topic,String payload,int qos,boolean retained) {}
 private final Sender sender; private final Scheduler scheduler; private final int capacity;
 private final ArrayDeque<Message> queue=new ArrayDeque<>();
 private boolean connected,inFlight,accepting=true,closed,closeChosen;
 private long generation; private Runnable closeAction;
 public MqttPublishQueue(Sender sender,Scheduler scheduler,int capacity){this.sender=Objects.requireNonNull(sender);this.scheduler=Objects.requireNonNull(scheduler);if(capacity<=0)throw new IllegalArgumentException("capacity must be positive");this.capacity=capacity;}
 public void offer(String topic,String payload,int qos,boolean retained){synchronized(this){if(!accepting||closed)throw new IllegalStateException("queue is not accepting offers");if(queue.size()>=capacity)throw new IllegalStateException("queue is full");queue.add(new Message(topic,payload,qos,retained));}kick();}
 public void setConnected(boolean value){Runnable close=null;synchronized(this){if(closed)return;if(!value){connected=false;generation++;inFlight=false;queue.clear();close=chooseCloseIfReady();}else{connected=true;}}runClose(close);if(value)kick();}
 public synchronized int pendingCount(){return queue.size();}
 public void closeWhenDrained(Runnable close,long timeoutMillis){Objects.requireNonNull(close);Runnable now=null;synchronized(this){if(closeChosen)return;accepting=false;closeAction=close;now=chooseCloseIfReady();}runClose(now);if(now==null)scheduler.later(this::expire,Math.max(0,timeoutMillis));}
 private Runnable chooseCloseIfReady(){if(closeChosen||closeAction==null)return null;if(queue.isEmpty()){closeChosen=true;closed=true;inFlight=false;generation++;Runnable r=closeAction;closeAction=null;return r;}return null;}
 private void runClose(Runnable r){if(r!=null)r.run();}
 private void expire(){Runnable r;synchronized(this){if(closeChosen)return;closeChosen=true;closed=true;accepting=false;generation++;inFlight=false;queue.clear();r=closeAction;closeAction=null;}runClose(r);}
 private void kick(){final Message message;final long gen;synchronized(this){if(closed||!connected||inFlight||queue.isEmpty())return;inFlight=true;message=queue.peek();gen=++generation;}try{sender.send(message.topic,message.payload,message.qos,message.retained,ok->completed(message,gen,ok));}catch(Throwable failure){completed(message,gen,false);}}
 private void completed(Message message,long gen,boolean ok){boolean retry=false,advance=false;Runnable close=null;synchronized(this){if(closed||gen!=generation||!inFlight||queue.peek()!=message)return;inFlight=false;if(ok){queue.remove();close=chooseCloseIfReady();advance=!closed&&!queue.isEmpty();}else retry=connected&&!queue.isEmpty();}runClose(close);if(retry)scheduler.later(this::kick,1000);else if(advance)scheduler.later(this::kick,0);}
}
