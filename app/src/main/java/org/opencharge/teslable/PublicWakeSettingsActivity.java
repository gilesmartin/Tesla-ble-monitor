package org.opencharge.teslable;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
/** Credentials are entered locally, never prefilled into the hierarchy or logged. */
public final class PublicWakeSettingsActivity extends Activity {
 private PublicWakeConfigStore config;private EditText bearer,tunnel;private Switch enabled;private TextView status;
 @Override public void onCreate(Bundle bundle){
  super.onCreate(bundle);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  config=new PublicWakeConfigStore(this);ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,24,24,24);scroll.addView(body);setContentView(scroll);
  TextView title=new TextView(this);title.setText("Public wake / Cloudflare Tunnel");title.setTextSize(24);body.addView(title);
  TextView help=new TextView(this);help.setText("Default off. A dedicated ScreenMate tunnel must route the chosen public hostname to http://127.0.0.1:8787. Client: POST /v1/wake, Authorization: Bearer <wake bearer>, JSON {}. No Tailscale, HA or MQTT dependency. Requires power, Internet, Bluetooth permission/proximity and an existing enrolled key. A 202 means queued, NOT proof the car is awake. Keep battery/power restrictions off for reliable background availability.");body.addView(help);
  enabled=new Switch(this);enabled.setText("Enable authenticated public wake gateway");enabled.setChecked(config.enabled());body.addView(enabled);
  bearer=secret("Wake bearer (32+ characters; blank keeps existing)");body.addView(bearer);
  Button generate=new Button(this);generate.setText("Generate a new wake bearer (not saved until Save)");generate.setOnClickListener(v->{byte[] value=new byte[32];new java.security.SecureRandom().nextBytes(value);bearer.setText(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value));});body.addView(generate);
  tunnel=secret("Dedicated Cloudflare Tunnel token (blank keeps existing)");body.addView(tunnel);
  Button save=new Button(this);save.setText("Save and apply");save.setOnClickListener(v->save());body.addView(save);
  status=new TextView(this);body.addView(status);Button refresh=new Button(this);refresh.setText("Refresh status");refresh.setOnClickListener(v->refresh());body.addView(refresh);
  Button power=new Button(this);power.setText("Open battery optimization settings");power.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName())));}catch(android.content.ActivityNotFoundException ignored){}});body.addView(power);
  Button back=new Button(this);back.setText("Back");back.setOnClickListener(v->finish());body.addView(back);refresh();
 }
 private EditText secret(String hint){EditText text=new EditText(this);text.setHint(hint);text.setSingleLine(true);text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);text.setSaveEnabled(false);if(Build.VERSION.SDK_INT>=26)text.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return text;}
 private void save(){
  String b=bearer.getText().toString(),t=tunnel.getText().toString();
  if((!b.isEmpty()||!t.isEmpty())&&!config.save(b,t)){status.setText("Invalid credentials or encrypted save failed; existing configuration retained.");return;}
  if(!config.setEnabled(enabled.isChecked())){status.setText("Configure both wake bearer and dedicated tunnel token first.");return;}
  bearer.setText("");tunnel.setText("");
  Intent apply=new Intent(this,TeslaBleService.class).setAction(TeslaBleService.ACTION_PUBLIC_WAKE_RECONFIGURE);
  try{if(Build.VERSION.SDK_INT>=26)startForegroundService(apply);else startService(apply);status.setText("Saved. Refresh status after connector startup. No wake was sent.");}
  catch(IllegalStateException|SecurityException e){status.setText("Saved, but foreground service could not start. Check Bluetooth/background permissions.");}
 }
 private void refresh(){android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);boolean exempt=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());status.setText("Enabled: "+config.enabled()+"; credentials configured: "+config.configured()+"; "+TeslaBleService.publicWakeSummary()+"\nPower exemption: "+exempt+(exempt?"":" — device may suspend the gateway")+"\nThe vehicle must authorize the existing key for wake; local key presence alone is not vehicle approval.");}
}
