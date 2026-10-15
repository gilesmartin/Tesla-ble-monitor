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
    private TextView status;

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
        body.addView(label("Read-only telemetry only: fresh battery, usable battery, estimated range, and charging state. No vehicle command topics are subscribed.", 14));
        body.addView(label("This publishes a new app-generated HA device identity; legacy HA unique IDs are not migrated until the old runtime identifiers are recovered.", 14));
        body.addView(label("Device ID for a narrow broker ACL: " + store.getDeviceId(), 12));

        body.addView(label("Broker URI (use ssl://host:port when available)", 15));
        brokerField = new EditText(this);
        brokerField.setSingleLine(true);
        brokerField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        brokerField.setHint("ssl://broker.example:8883");
        brokerField.setText(store.getBrokerUriForUi());
        body.addView(brokerField);

        body.addView(label("MQTT username (leave both credential fields blank to keep saved encrypted credentials)", 15));
        usernameField = new EditText(this);
        usernameField.setSingleLine(true);
        usernameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        body.addView(usernameField);

        body.addView(label("MQTT password", 15));
        passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setHint("Not displayed; leave blank to keep saved credential");
        body.addView(passwordField);
        body.addView(label(store.hasCredentials() ? "Encrypted credentials are stored on this device." : "No MQTT credentials are stored yet.", 13));

        allowPlaintext = new CheckBox(this);
        allowPlaintext.setText("Allow plaintext MQTT (credentials are unencrypted in transit; trusted LAN only)");
        allowPlaintext.setChecked(store.isPlaintextAllowed());
        body.addView(allowPlaintext);

        enabled = new CheckBox(this);
        enabled.setText("Enable read-only MQTT/Home Assistant publishing");
        enabled.setChecked(store.isEnabled());
        body.addView(enabled);
        status = label("TLS certificate and hostname verification are enabled for ssl:// brokers. Publishing is off unless explicitly enabled above.", 13);
        body.addView(status);
        body.addView(button("Save settings", this::saveSettings));
        body.addView(button("Close", this::finish));
        scroll.addView(body);
        setContentView(scroll);
    }

    private void saveSettings() {
        boolean saved = store.save(brokerField.getText().toString(), allowPlaintext.isChecked(),
                enabled.isChecked(), usernameField.getText().toString(), passwordField.getText().toString());
        if (!saved) {
            status.setText("Settings were not saved. Check the broker URI, transport choice, and credential fields.");
            return;
        }
        passwordField.setText("");
        usernameField.setText("");
        status.setText(enabled.isChecked()
                ? "Saved. Read-only MQTT publishing is enabled in configuration; broker connection has not been verified. No command topics are used."
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
