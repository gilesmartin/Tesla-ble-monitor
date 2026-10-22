package org.opencharge.teslable;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
/** Local-only explicit telemetry credential setup; never starts BLE or sends a command. */
public final class TelemetrySettingsActivity extends Activity {
 private ApiTokenStore store;private EditText token;private TextView status;private String newlySaved;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);store=new ApiTokenStore(this);
  ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,24,24,24);scroll.addView(body);
  TextView title=new TextView(this);title.setText("Local telemetry / DiPlay setup");title.setTextSize(24);body.addView(title);
  TextView help=new TextView(this);help.setText("Only the authenticated loopback telemetry API uses this token. It is NOT a vehicle key, MQTT password or Cloudflare credential. Generate or enter 32–512 printable characters, Save, then explicitly Copy and paste into DiPlay on this device. Clipboard copying is sensitive: clear the clipboard after pasting, especially on older Android versions. Never send this token in chat or diagnostics. Blank preserves the existing token. Changing it requires updating DiPlay too; no wake or connection is initiated here.");body.addView(help);
  token=new EditText(this);token.setSingleLine(true);token.setHint("New telemetry token; existing value is never shown");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSaveEnabled(false);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);body.addView(token);
  Button generate=new Button(this);generate.setText("Generate a new telemetry token");generate.setOnClickListener(v->{byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);token.setText(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));});body.addView(generate);
  Button save=new Button(this);save.setText("Save telemetry token");save.setOnClickListener(v->{String value=token.getText().toString();if(value.isEmpty()){status.setText("Unchanged. Existing telemetry token retained.");return;}if(!store.setToken(value)){status.setText("Not saved: invalid token or encrypted storage unavailable. Existing value retained.");return;}newlySaved=value;token.setText("");status.setText("Token saved encrypted. Copy it into DiPlay. No vehicle command was sent.");});body.addView(save);
  Button copy=new Button(this);copy.setText("Copy token saved in this screen");copy.setOnClickListener(v->{if(newlySaved==null){status.setText("Save a new token here before copying. Existing stored secrets are not displayed or copied.");return;}ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard==null)return;ClipData clip=ClipData.newPlainText("TeslaBLE telemetry token",newlySaved);PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);clipboard.setPrimaryClip(clip);status.setText("Copied sensitive token. Paste into DiPlay, then clear the clipboard. No wake was sent.");});body.addView(copy);
  status=new TextView(this);status.setText(store.isConfigured()?"Telemetry token already configured; its value is hidden.":"No telemetry token configured.");body.addView(status);Button back=new Button(this);back.setText("Back");back.setOnClickListener(v->finish());body.addView(back);setContentView(scroll);
 }
 @Override protected void onStop(){newlySaved=null;if(token!=null)token.setText("");super.onStop();}
}
