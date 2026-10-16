package org.opencharge.teslable;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** User-entered settings screen; secrets are saved only through MqttConfigStore. */
public final class MqttSettingsActivity extends Activity {
    private MqttConfigStore store;
    private EditText brokerField;
    private EditText usernameField;
    private EditText passwordField;
    private CheckBox allowPlaintext;
    private CheckBox enabled;
    private CheckBox legacyCompatibility;
    private CheckBox legacyControls;
    private TextView status;
    private TextView credentialStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new MqttConfigStore(this);
        buildUi();
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0, 8, 0, 8);
        return text;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.addView(label("MQTT / Home Assistant", 24));
        body.addView(label("Default mode is read-only: fresh battery, usable battery, estimated range, and charging state. No vehicle command topics are subscribed unless legacy compatibility is explicitly enabled below.", 14));
        body.addView(label("Default mode publishes a separate app device. Optional legacy mode preserves the existing Model 3 Home Assistant identities. Review broker ACLs and retire obsolete publishers before enabling it.", 14));
        body.addView(label("Device ID for a narrow broker ACL: " + store.getDeviceId(), 12));

        body.addView(label("Broker URI (use ssl://host:port when available)", 15));
        brokerField = new EditText(this);
        brokerField.setSingleLine(true);
        brokerField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        brokerField.setHint("ssl://broker.example:8883");
        brokerField.setText(store.getBrokerUriForUi());
        body.addView(brokerField);

        body.addView(label("MQTT username", 15));
        usernameField = new EditText(this);
        usernameField.setSingleLine(true);
        usernameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameField.setText(store.getUsernameForUi());
        body.addView(usernameField);

        body.addView(label("MQTT password", 15));
        passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setSaveEnabled(false);
        passwordField.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        passwordField.setHint(store.hasCredentials()
                ? "******** (saved; leave blank to keep)" : "Enter MQTT password");
        body.addView(passwordField);
        credentialStatus = label(credentialMessage(), 13);
        body.addView(credentialStatus);

        allowPlaintext = new CheckBox(this);
        allowPlaintext.setText("Allow plaintext MQTT (credentials are unencrypted in transit; trusted LAN only)");
        allowPlaintext.setChecked(store.isPlaintextAllowed());
        body.addView(allowPlaintext);

        enabled = new CheckBox(this);
        enabled.setText("Enable MQTT/Home Assistant publishing");
        enabled.setChecked(store.isEnabled());
        body.addView(enabled);
        legacyCompatibility = new CheckBox(this);
        legacyCompatibility.setText("Legacy Model 3 compatibility (existing Home Assistant identities; separate opt-in)");
        legacyCompatibility.setChecked(store.isLegacyCompatibilityEnabled());
        body.addView(legacyCompatibility);
        legacyControls = new CheckBox(this);
        legacyControls.setText("Allow validated legacy vehicle controls (opt-in; Deploy Key reuses the current key and requires vehicle approval; ReGenerate Keys changes it only when that HA action is explicitly sent. App installation never changes keys.)");
        legacyControls.setChecked(store.isLegacyControlsEnabled());
        body.addView(legacyControls);
        status = label("TLS certificate and hostname verification are enabled for ssl:// brokers. Publishing is off unless explicitly enabled above.", 13);
        body.addView(status);
        body.addView(button("Save settings", this::saveSettings));
        body.addView(button("Close", this::finish));
        scroll.addView(body);
        setContentView(scroll);
    }

    private String credentialMessage() {
        if (!store.hasCredentials()) return "No MQTT password is saved yet.";
        if (store.getUsernameForUi().isEmpty()) {
            return "Stored credentials could not be read. Re-enter username and password before saving.";
        }
        return "Password saved and encrypted. Leave the password blank to keep it; type a new password to replace it.";
    }

    private void saveSettings() {
        brokerField.setError(null);
        usernameField.setError(null);
        passwordField.setError(null);
        MqttConfigStore.SaveResult result = store.saveWithResult(brokerField.getText().toString(),
                allowPlaintext.isChecked(), enabled.isChecked(), usernameField.getText().toString(),
                passwordField.getText().toString(), legacyCompatibility.isChecked(), legacyControls.isChecked());
        if (!result.isSuccess()) {
            status.setText("Not saved: " + result.getMessage());
            EditText invalid = result.getField() == MqttConfigStore.SaveResult.Field.BROKER ? brokerField
                    : result.getField() == MqttConfigStore.SaveResult.Field.USERNAME ? usernameField
                    : result.getField() == MqttConfigStore.SaveResult.Field.PASSWORD ? passwordField : null;
            if (invalid != null) {
                invalid.setError(result.getMessage());
                invalid.requestFocus();
            }
            return;
        }
        passwordField.setText("");
        passwordField.setHint("******** (saved; leave blank to keep)");
        usernameField.setText(store.getUsernameForUi());
        credentialStatus.setText(credentialMessage());
        status.setText(enabled.isChecked()
                ? (legacyCompatibility.isChecked() ? "Saved. Legacy compatibility is opted in; controls " + (legacyControls.isChecked() ? "enabled" : "disabled") + ". Broker delivery and vehicle permissions still require verification."
                   : "Saved. Read-only MQTT publishing is enabled in configuration; broker connection has not been verified. No command topics are used.")
                : "Saved. MQTT publishing is disabled.");
        Intent service = new Intent(this, TeslaBleService.class);
        service.setAction(TeslaBleService.ACTION_COMMAND);
        service.putExtra(TeslaBleService.EXTRA_COMMAND, "mqtt-reconfigure");
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (RuntimeException ignored) {
            status.setText("Settings saved, but the service did not start. Reopen the app to apply them.");
        }
    }
}
