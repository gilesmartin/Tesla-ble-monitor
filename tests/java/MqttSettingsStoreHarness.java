package org.opencharge.teslable;
import android.content.SharedPreferences;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
public final class MqttSettingsStoreHarness {
 static final String SECRET="test-only-fixture-password",USER="saved-user",BROKER="tcp://broker.example:1883";
 static final javax.crypto.SecretKey KEY=new SecretKeySpec(new byte[32],"AES");
 static final class Preferences implements SharedPreferences {
  Map<String,Object> data=new HashMap<>();boolean fail;int edits;
  public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
  public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}
  public Editor edit(){edits++;return new Editor(){Map<String,Object> changes=new HashMap<>();
   public Editor putString(String k,String v){changes.put(k,v);return this;}
   public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}
   public boolean commit(){if(fail)return false;data.putAll(changes);return true;}
  };}
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Preferences p=new Preferences();MqttConfigStore s=new MqttConfigStore(p,create->KEY);
  check(s.save(BROKER,true,true,USER,SECRET),"seed save");
  check(s.getUsernameForUi().equals(USER),"saved username visible");
  String envelope=p.getString("credentials_envelope",null);
  check(!envelope.contains(SECRET),"encrypted credential");
  check(s.saveWithResult(BROKER,true,false,USER,"").isSuccess(),"unchanged visible username retains password");
  check(p.getString("credentials_envelope",null).equals(envelope),"envelope preserved");
  check(s.getUsernameForUi().equals(USER),"username visible while disabled");check(s.loadForService()==null,"disabled stays disabled");
  Map<String,Object> saved=new HashMap<>(p.data);int edits=p.edits;
  String[] invalid={"", "broker.example", "https://broker.example:8883", "ssl://broker.example", "ssl://broker.example:0", "ssl://broker.example:70000", "ssl://broker.example:8883/path", "ssl://credential-user:credential-secret@broker.example:8883"};
  for(String uri:invalid){MqttConfigStore.SaveResult r=s.saveWithResult(uri,true,true,USER,"");check(!r.isSuccess(),"bad broker rejected");check(r.getField()==MqttConfigStore.SaveResult.Field.BROKER,"broker field error");check(!r.getMessage().contains("credential-secret"),"error sanitized");check(p.data.equals(saved)&&p.edits==edits,"invalid broker must not edit prefs");}
  MqttConfigStore.SaveResult r=s.saveWithResult(BROKER,false,true,USER,"");check(!r.isSuccess()&&r.getMessage().contains("plaintext"),"transport policy feedback");
  r=s.saveWithResult(BROKER,true,true,"different-user","");check(!r.isSuccess()&&r.getField()==MqttConfigStore.SaveResult.Field.PASSWORD,"changed username requires new password");check(p.data.equals(saved),"old config preserved");
  r=s.saveWithResult(BROKER,true,true,"bad\nuser",SECRET);check(!r.isSuccess()&&r.getField()==MqttConfigStore.SaveResult.Field.USERNAME,"username control char rejected");
  r=s.saveWithResult(BROKER,true,true,USER,"x".repeat(513));check(!r.isSuccess()&&r.getField()==MqttConfigStore.SaveResult.Field.PASSWORD,"password size feedback");
  check(p.data.equals(saved)&&p.edits==edits,"invalid credentials must not edit prefs");
  check(s.saveWithResult(BROKER,true,true,USER,"********").isSuccess(),"literal stars are a real replacement, not a sentinel");check(s.loadForService().getPassword().equals("********"),"literal password round trip");
  check(s.saveWithResult(BROKER,true,true,"","").isSuccess(),"legacy blank-both save compatible");
  MqttConfigStore brokenKey=new MqttConfigStore(p,create->{throw new java.security.GeneralSecurityException();});
  saved=new HashMap<>(p.data);edits=p.edits;
  r=brokenKey.saveWithResult(BROKER,true,true,USER,"replacement");check(!r.isSuccess()&&r.getField()==MqttConfigStore.SaveResult.Field.STORAGE,"encryption failure surfaced");check(p.data.equals(saved)&&p.edits==edits,"encryption error no edit");
  check(brokenKey.getUsernameForUi().isEmpty(),"unreadable username not fabricated");
  r=brokenKey.saveWithResult(BROKER,true,true,USER,"");check(!r.isSuccess()&&r.getMessage().contains("decrypted"),"unreadable old password rejected");
  p.fail=true;r=s.saveWithResult(BROKER,true,true,USER,"");check(!r.isSuccess()&&r.getField()==MqttConfigStore.SaveResult.Field.STORAGE,"disk failure surfaced");p.fail=false;
  Preferences blank=new Preferences();MqttConfigStore first=new MqttConfigStore(blank,create->KEY);r=first.saveWithResult(BROKER,true,true,USER,"");check(!r.isSuccess()&&blank.edits==0&&blank.data.isEmpty(),"initial password required; no partial save");
  System.out.println("PASS: saved username, disabled reload, credential retention/replacement, invalid configuration atomicity, encrypted storage and specific safe errors");
 }
}
