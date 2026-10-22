import unittest,tempfile,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
class PublicWakeStoreTests(unittest.TestCase):
 def test_real_store_logic_private_preferences_and_real_aes_with_os_keystore_standin(self):
  # Only Android OS KeyStore access is replaced; save/load/validation/defaults
  # and the actual AES-GCM envelope implementation execute unchanged.
  source=(JAVA/'PublicWakeConfigStore.java').read_text();a=source.index('    private static SecretKey getKey(');opening=source.index('{',a);n=1;end=opening+1
  while n:n+=(source[end]=='{')-(source[end]=='}');end+=1
  source=source[:a]+"""
    static SecretKey fixtureKey;static boolean fixtureLocked;
    private static SecretKey getKey(boolean create)throws Exception{
     if(fixtureLocked)throw new Exception("OSKeyStore unavailable fixture");
     if(fixtureKey==null&&create){KeyGenerator g=KeyGenerator.getInstance("AES");g.init(256);fixtureKey=g.generateKey();}
     return fixtureKey;
    }
"""+source[end:]
  source='\n'.join(line for line in source.splitlines()if not line.startswith('import android.security.keystore.'))
  with tempfile.TemporaryDirectory()as tmp:
   t=Path(tmp);ap=t/'android/content';ap.mkdir(parents=True)
   (ap/'SharedPreferences.java').write_text('package android.content;public interface SharedPreferences{String getString(String k,String d);boolean getBoolean(String k,boolean d);Editor edit();interface Editor{Editor putString(String k,String v);Editor putBoolean(String k,boolean v);boolean commit();}}')
   (ap/'Context.java').write_text('package android.content;public abstract class Context{public static final int MODE_PRIVATE=0;public Context getApplicationContext(){return this;}public abstract SharedPreferences getSharedPreferences(String n,int m);}')
   (t/'PublicWakeConfigStore.java').write_text(source)
   (t/'StoreHarness.java').write_text("""package org.opencharge.teslable;
import android.content.*;import java.util.*;
public final class StoreHarness{
 static final String B="bearer-fixture-"+"b".repeat(48),T="tunnel-fixture-"+"t".repeat(140);
 static void check(boolean x){if(!x)throw new AssertionError("secure store behavior");}
 static final class P implements SharedPreferences{
  Map<String,Object> m=new HashMap<>();boolean writable=true;String seenName;int seenMode;
  public String getString(String k,String d){return(String)m.getOrDefault(k,d);}
  public boolean getBoolean(String k,boolean d){return(Boolean)m.getOrDefault(k,d);}
  public Editor edit(){return new Editor(){Map<String,Object> edits=new HashMap<>();public Editor putString(String k,String v){edits.put(k,v);return this;}public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}public boolean commit(){m.putAll(edits);return writable;}};}
 }
 static final class C extends Context{final P p=new P();public SharedPreferences getSharedPreferences(String n,int m){p.seenName=n;p.seenMode=m;return p;}}
 public static void main(String[]args){
  C c=new C();PublicWakeConfigStore s=new PublicWakeConfigStore(c);check(!s.enabled()&&!s.configured());check(!s.setEnabled(true));
  check(s.save(B,T));check(s.configured()&&!s.enabled());check(B.equals(s.bearer())&&T.equals(s.tunnelToken()));
  check(c.p.seenMode==Context.MODE_PRIVATE&&c.p.seenName.equals("tesla_ble_public_wake_v1"));
  String envelope=(String)c.p.m.get("token_envelope");check(!envelope.contains(B)&&!envelope.contains(T));
  check(s.setEnabled(true)&&s.enabled());check(!s.save("invalid",T));check(envelope.equals(c.p.m.get("token_envelope"))&&B.equals(s.bearer()));
  check(s.save("",T+"x")&&B.equals(s.bearer())&&(T+"x").equals(s.tunnelToken()));
  check(!s.save(B,"bad"+(char)10+"token")&&(T+"x").equals(s.tunnelToken()));
  PublicWakeConfigStore.fixtureLocked=true;check(!s.configured()&&s.bearer()==null&&s.tunnelToken()==null&&!s.setEnabled(true));
  check(s.setEnabled(false)&&!s.enabled());PublicWakeConfigStore.fixtureLocked=false;
  check(s.configured()&&!s.enabled());PublicWakeConfigStore.fixtureKey=null;check(!s.configured()&&s.bearer()==null);
  System.out.print("PASS: defaults, private encrypted atomic pair, invalid preservation, merge, locked/missing OS key fail-closed, disable while locked");
 }
}""")
   files=[str(p)for p in t.rglob('*.java')]+[str(JAVA/'PublicWakeSecrets.java'),str(JAVA/'ApiTokenCipher.java')]
   c=subprocess.run(['javac','-d',tmp,*files],capture_output=True,text=True);self.assertEqual(c.returncode,0,c.stderr)
   p=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.StoreHarness'],capture_output=True,text=True,timeout=10);self.assertEqual(p.returncode,0,p.stderr);self.assertIn('PASS:',p.stdout)
