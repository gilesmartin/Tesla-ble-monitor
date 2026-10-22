import unittest,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
class TelemetrySetupUiTests(unittest.TestCase):
 def test_real_activity_explicit_encrypted_save_copy_and_invalid_preservation(self):
  activity=JAVA/'TelemetrySettingsActivity.java'
  self.assertTrue(activity.exists(),'Missing secure on-device telemetry setup screen')
  source=(JAVA/'ApiTokenStore.java').read_text();source=source[:source.index('    private static SecretKey getKey(')]+"    private static SecretKey fixtureKey; private static SecretKey getKey(boolean create)throws Exception { if(fixtureKey==null&&create){KeyGenerator g=KeyGenerator.getInstance(\"AES\");g.init(256);fixtureKey=g.generateKey();}return fixtureKey;}\n}"
  source='\n'.join(l for l in source.splitlines()if not l.startswith('import android.security.keystore.'))
  stubs={
  'android/content/SharedPreferences.java':'package android.content;public interface SharedPreferences{String getString(String k,String d);Editor edit();interface Editor{Editor putString(String k,String v);boolean commit();}}',
  'android/content/Context.java':'package android.content;import java.util.*;public class Context{public static final int MODE_PRIVATE=0;public static final String CLIPBOARD_SERVICE="clipboard";static Map<String,Map<String,String>> db=new HashMap<>();public Context getApplicationContext(){return this;}public Object getSystemService(String s){return new ClipboardManager();}public SharedPreferences getSharedPreferences(String n,int mode){Map<String,String> m=db.computeIfAbsent(n,k->new HashMap<>());return new SharedPreferences(){public String getString(String k,String d){return m.getOrDefault(k,d);}public Editor edit(){return new Editor(){public Editor putString(String k,String v){m.put(k,v);return this;}public boolean commit(){return true;}};}};}}',
  'android/content/ClipData.java':'package android.content;public class ClipData{public String text;public Description d=new Description();public static ClipData newPlainText(String label,String text){ClipData c=new ClipData();c.text=text;return c;}public Description getDescription(){return d;}public static class Description{public android.os.PersistableBundle extras;public void setExtras(android.os.PersistableBundle e){extras=e;}}}',
  'android/content/ClipboardManager.java':'package android.content;public class ClipboardManager{public static ClipData last;public void setPrimaryClip(ClipData c){last=c;}}',
  'android/os/Bundle.java':'package android.os;public class Bundle{}',
  'android/os/PersistableBundle.java':'package android.os;public class PersistableBundle{public boolean sensitive;public void putBoolean(String k,boolean v){sensitive=v;}}',
  'android/view/View.java':'package android.view;import java.util.*;public class View{public static final int IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS=8;public static List<View> all=new ArrayList<>();public boolean save=true;public int autofill;public interface OnClickListener{void onClick(View v);}public OnClickListener listener;public View(){all.add(this);}public void setOnClickListener(OnClickListener l){listener=l;}public void click(){listener.onClick(this);}public void setSaveEnabled(boolean b){save=b;}public void setImportantForAutofill(int i){autofill=i;}}',
  'android/view/WindowManager.java':'package android.view;public interface WindowManager{class LayoutParams{public static final int FLAG_SECURE=8192;}}',
  'android/view/Window.java':'package android.view;public class Window{public int flags;public void addFlags(int f){flags|=f;}}',
  'android/app/Activity.java':'package android.app;public class Activity extends android.content.Context{public android.view.Window window=new android.view.Window();protected void onCreate(android.os.Bundle b){}protected void onStop(){}public android.view.Window getWindow(){return window;}public void setContentView(android.view.View v){}public void finish(){}}',
  'android/text/InputType.java':'package android.text;public class InputType{public static final int TYPE_CLASS_TEXT=1,TYPE_TEXT_VARIATION_PASSWORD=128;}',
  'android/widget/TextView.java':'package android.widget;public class TextView extends android.view.View{public String text="";public TextView(android.content.Context c){}public void setText(String t){text=t;}public CharSequence getText(){return text;}public void setTextSize(int i){}}',
  'android/widget/EditText.java':'package android.widget;public class EditText extends TextView{public int input;public EditText(android.content.Context c){super(c);}public void setSingleLine(boolean b){}public void setInputType(int i){input=i;}public void setHint(String h){}}',
  'android/widget/Button.java':'package android.widget;public class Button extends TextView{public Button(android.content.Context c){super(c);}}',
  'android/widget/LinearLayout.java':'package android.widget;public class LinearLayout extends android.view.View{public static final int VERTICAL=1;public LinearLayout(android.content.Context c){}public void setOrientation(int i){}public void setPadding(int a,int b,int c,int d){}public void addView(android.view.View v){}}',
  'android/widget/ScrollView.java':'package android.widget;public class ScrollView extends android.view.View{public ScrollView(android.content.Context c){}public void addView(android.view.View v){}}'}
  with tempfile.TemporaryDirectory()as tmp:
   t=Path(tmp)
   for path,text in stubs.items():p=t/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
   (t/'ApiTokenStore.java').write_text(source)
   (t/'Harness.java').write_text("""package org.opencharge.teslable;import android.view.*;import android.widget.*;import android.content.*;public class Harness{
 static void check(boolean b){if(!b)throw new AssertionError("telemetry setup behavior");}
 static Button button(String name){return(Button)View.all.stream().filter(v->v instanceof Button&&((Button)v).getText().toString().equals(name)).findFirst().get();}
 public static void main(String[]x){TelemetrySettingsActivity a=new TelemetrySettingsActivity();a.onCreate(null);EditText input=(EditText)View.all.stream().filter(v->v instanceof EditText).findFirst().get();ApiTokenStore store=new ApiTokenStore(a);check(!store.isConfigured());check((a.window.flags&8192)!=0&&!input.save&&input.autofill==8&&(input.input&128)!=0);button("Copy token saved in this screen").click();check(ClipboardManager.last==null);button("Generate a new telemetry token").click();String generated=input.getText().toString();check(generated.length()>=32&&!store.isConfigured());button("Save telemetry token").click();check(store.getToken().equals(generated)&&input.getText().length()==0);button("Copy token saved in this screen").click();check(ClipboardManager.last.text.equals(generated)&&ClipboardManager.last.d.extras.sensitive);input.setText("bad");button("Save telemetry token").click();check(store.getToken().equals(generated));input.setText("");button("Save telemetry token").click();check(store.getToken().equals(generated));a.onStop();ClipboardManager.last=null;button("Copy token saved in this screen").click();check(ClipboardManager.last==null);System.out.println("PASS actual activity/store/AES: explicit generation/save/copy, private UI flags, invalid/blank preserve, secret cleared onStop");}}
""")
   files=[str(p)for p in t.rglob('*.java')]+[str(activity),str(JAVA/'ApiTokenCipher.java')]
   p=subprocess.run(['javac','-d',tmp,*files],capture_output=True,text=True);self.assertEqual(p.returncode,0,p.stderr)
   p=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.Harness'],capture_output=True,text=True,timeout=15);self.assertEqual(p.returncode,0,p.stderr);self.assertIn('PASS actual activity/store/AES',p.stdout)
  self.assertIn('TelemetrySettingsActivity.class',(JAVA/'MainActivity.java').read_text())
  self.assertIn('android:name=".TelemetrySettingsActivity" android:exported="false"',(ROOT/'app/src/main/AndroidManifest.xml').read_text())
