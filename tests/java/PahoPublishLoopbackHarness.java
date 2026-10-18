package org.opencharge.teslable;
import java.util.concurrent.*;
public final class PahoPublishLoopbackHarness {
 public static void main(String[] args)throws Exception{
  PahoMqttTransport t=new PahoMqttTransport();CountDownLatch connected=new CountDownLatch(1),complete=new CountDownLatch(1),metadata=new CountDownLatch(1);
  try{
   t.connect("tcp://127.0.0.1:"+args[0],"loopback-fixture","fixture-user","fixture-password".toCharArray(),"fixture/availability","offline",new MqttBridgePublisher.Transport.Listener(){
    public void onConnected(){connected.countDown();}
    public void onConnectionLost(){}
    public void onMessage(String topic,String payload){}
    public void onMessage(String topic,String payload,boolean retained,boolean duplicate){if(topic.equals("fixture/result")&&payload.equals("200"))complete.countDown();if(topic.equals("fixture/retained")&&retained&&!duplicate)metadata.countDown();}
   });
   if(!connected.await(5,TimeUnit.SECONDS))throw new AssertionError("loopback connect failed");
   t.subscribe("fixture/#",1);
   for(int i=0;i<200;i++)t.publish("fixture/state/"+i,Integer.toString(i),1,true);
   if(!complete.await(8,TimeUnit.SECONDS))throw new AssertionError("200-message QoS1 burst lost publications");
   if(!metadata.await(2,TimeUnit.SECONDS))throw new AssertionError("retained metadata was lost");
   System.out.println("PASS real Paho loopback200 QoS1 publications and retained flag");
  }finally{t.disconnect();}
 }
}
