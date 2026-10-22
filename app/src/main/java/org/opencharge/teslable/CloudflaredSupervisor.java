package org.opencharge.teslable;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.function.Supplier;

/** One supervised child, no VPN/MQTT/home dependencies. All I/O is off the BLE looper. */
public final class CloudflaredSupervisor implements AutoCloseable {
 private final String expectedSha256;
 private final File binary,directory;private final Supplier<String> token;
 private volatile boolean running;private volatile Process child;private volatile Thread worker;
 private volatile String state="stopped";
 public CloudflaredSupervisor(File binary,File directory,Supplier<String> token,String expectedSha256){if(expectedSha256==null||!expectedSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid packaged connector digest");this.binary=binary;this.directory=directory;this.token=token;this.expectedSha256=expectedSha256;}
 public String state(){return state;}
 public synchronized void start(){if(worker!=null&&worker.isAlive())return;running=true;worker=new Thread(this::loop,"TeslaBLE-Cloudflared");worker.setDaemon(true);worker.start();}
 private void loop(){
  int failures=0;
  while(running){
   File secret=null;Process launched=null;
   try{
    if(!verifiedBinary()){state="connector_binary_unavailable";pause(30000);continue;}
    if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException();
    try(RandomAccessFile lockFile=new RandomAccessFile(new File(directory,"connector.lock"),"rw");FileChannel channel=lockFile.getChannel()){
     FileLock lock;try{lock=channel.tryLock();}catch(OverlappingFileLockException e){lock=null;}
     if(lock==null){state="connector_owner_busy";pause(1000);continue;}
     try{
      String value=token.get();if(value==null){state="tunnel_token_unavailable";pause(30000);continue;}
      secret=File.createTempFile("connector-",".token",directory);
      Files.setPosixFilePermissions(secret.toPath(),PosixFilePermissions.fromString("rw-------"));
      try(FileOutputStream out=new FileOutputStream(secret)){out.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
      if(!running)break;
      ProcessBuilder command=new ProcessBuilder(CloudflaredCommand.args(binary,secret));
      command.environment().put("SSL_CERT_DIR","/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts");
      command.redirectErrorStream(true);command.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
      launched=command.start();child=launched;state="connector_starting";
      while(running&&launched.isAlive()){
       boolean ready=ready();state=ready?"tunnel_connected":"connector_reconnecting";if(ready)failures=0;pause(2000);
      }
      if(running)state="connector_exited_"+launched.exitValue();
     }finally{
      terminate(launched);if(child==launched)child=null;if(secret!=null)secret.delete();lock.release();
     }
    }
   }catch(InterruptedException e){if(!running)break;}
    catch(Exception ignored){state="connector_start_failed";}
   finally{terminate(launched);if(child==launched)child=null;if(secret!=null)secret.delete();}
   if(running){failures=Math.min(failures+1,6);try{pause(Math.min(60000L,1000L<<failures));}catch(InterruptedException ignored){}}
  }
  state="stopped";
 }
 private boolean verifiedBinary()throws Exception{
  if(!binary.isFile()||!binary.canExecute())return false;
  MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];
  try(InputStream in=new FileInputStream(binary)){int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}
  StringBuilder text=new StringBuilder();for(byte v:hash.digest())text.append(String.format(java.util.Locale.US,"%02x",v&255));return expectedSha256.equals(text.toString());
 }
 private boolean ready(){
  HttpURLConnection c=null;
  try{c=(HttpURLConnection)new URL("http://127.0.0.1:27841/ready").openConnection();c.setConnectTimeout(500);c.setReadTimeout(500);c.setUseCaches(false);return c.getResponseCode()==200;}
  catch(IOException e){return false;}finally{if(c!=null)c.disconnect();}
 }
 private void pause(long ms)throws InterruptedException{Thread.sleep(ms);}
 private void terminate(Process p){if(p==null)return;p.destroy();try{if(!p.waitFor(2,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();p.waitFor(2,java.util.concurrent.TimeUnit.SECONDS);}}catch(InterruptedException e){p.destroyForcibly();}}
 @Override public void close(){running=false;Process p=child;if(p!=null)p.destroy();Thread t=worker;if(t!=null)t.interrupt();}
}
