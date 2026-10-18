package org.opencharge.teslable;
import java.lang.reflect.*;
import java.util.*;
public final class MqttLegacyOptionsHarness {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static boolean flag(Object obj,String name)throws Exception {try{return (Boolean)obj.getClass().getMethod(name).invoke(obj);}catch(NoSuchMethodException e){throw new AssertionError("Missing opt-in settings contract: "+name);}}
 static final class LiveFailurePrefs implements android.content.SharedPreferences {
  final Map<String,Object> data=new HashMap<>(); boolean fail;
  public String getString(String k,String d){Object v=data.get(k);return v instanceof String?(String)v:d;}
  public boolean getBoolean(String k,boolean d){Object v=data.get(k);return v instanceof Boolean?(Boolean)v:d;}
  public boolean contains(String k){return data.containsKey(k);}
  public android.content.SharedPreferences.Editor edit(){return new android.content.SharedPreferences.Editor(){final Map<String,Object> changes=new HashMap<>();
   public android.content.SharedPreferences.Editor putString(String k,String v){changes.put(k,v);return this;}
   public android.content.SharedPreferences.Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}
   public boolean commit(){data.putAll(changes);return !fail;}
  };}
 }
 public static void main(String[] ignored)throws Exception{
  var p=new MqttSettingsStoreHarness.Preferences();var store=new MqttConfigStore(p,c->MqttSettingsStoreHarness.KEY);
  check(!flag(store,"isLegacyCompatibilityEnabled")&&!flag(store,"isLegacyControlsEnabled"),"defaults off");
  Method save;
  try{save=MqttConfigStore.class.getMethod("saveWithResult",String.class,boolean.class,boolean.class,String.class,String.class,boolean.class,boolean.class);}catch(NoSuchMethodException e){throw new AssertionError("Missing atomic option save");}
  check(store.save("tcp://broker.example:1883",true,true,"fixture-user","fixture-password"),"seed");String envelope=p.getString("credentials_envelope",null);
  var result=(MqttConfigStore.SaveResult)save.invoke(store,"tcp://broker.example:1883",true,true,"fixture-user","",true,false);check(result.isSuccess()&&flag(store,"isLegacyCompatibilityEnabled")&&!flag(store,"isLegacyControlsEnabled"),"compat separately opted in");
  check(p.getString("credentials_envelope",null).equals(envelope),"opt-in preserves credential envelope");
  Map<String,Object> old=new HashMap<>(p.data);int edits=p.edits;
  result=(MqttConfigStore.SaveResult)save.invoke(store,"invalid",true,true,"fixture-user","",false,false);check(!result.isSuccess()&&p.data.equals(old)&&edits==p.edits,"invalid save does not partly change options");
  result=(MqttConfigStore.SaveResult)save.invoke(store,"tcp://broker.example:1883",true,true,"fixture-user","",false,true);check(!result.isSuccess()&&p.data.equals(old),"controls cannot exist without compatibility");
  result=(MqttConfigStore.SaveResult)save.invoke(store,"tcp://broker.example:1883",true,true,"fixture-user","",true,true);check(result.isSuccess()&&flag(store.loadForService(),"isLegacyControlsEnabled"),"explicit controls persisted and consumed");
  result=(MqttConfigStore.SaveResult)save.invoke(store,"tcp://broker.example:1883",true,true,"fixture-user","",false,false);check(result.isSuccess()&&!flag(store.loadForService(),"isLegacyCompatibilityEnabled"),"opt out restores read-only mode");
  LiveFailurePrefs disk=new LiveFailurePrefs();MqttConfigStore diskStore=new MqttConfigStore(disk,c->MqttSettingsStoreHarness.KEY);
  check(diskStore.save("tcp://fixture.invalid:1883",true,true,"fixture-user","fixture-password"),"disk seed");Map<String,Object> prior=new HashMap<>(disk.data);disk.fail=true;
  var failedSave=(MqttConfigStore.SaveResult)save.invoke(diskStore,"tcp://changed.invalid:1883",true,true,"fixture-user","",true,true);
  check(!failedSave.isSuccess(),"disk failure signaled");
  check(prior.equals(disk.data),"failed Android-style commit changed live options or credential envelope");
  System.out.println("PASS atomic opt-in defaults, credentials preservation, invalid-save isolation, control prerequisite and opt-out");
 }
}
