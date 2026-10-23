package org.opencharge.teslable;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** UI client for TeslaBleService; this activity never owns a GATT connection or native client. */
public final class MainActivity extends Activity implements TeslaBleService.Listener {
    private static final String TAG = "TeslaBLE";
    private EditText vinField;
    private LinearLayout deviceList;
    private TextView statusView;
    private TextView telemetryView;
    private TextView logView;
    private TeslaBleService service;
    private boolean bound;
    private Runnable pendingAfterPermissions;
    private final Set<String> shownAddresses = new HashSet<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            TeslaBleService.LocalBinder local = (TeslaBleService.LocalBinder) binder;
            service = local.getService();
            service.setListener(MainActivity.this);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (service != null) service.clearListener(MainActivity.this);
            service = null;
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        vinField.setText(getSharedPreferences("MainActivity", MODE_PRIVATE).getString("vin", ""));
    }

    @Override protected void onStart() {
        super.onStart();
        if (!bound) {
            bound = bindService(new Intent(this, TeslaBleService.class), serviceConnection, Context.BIND_AUTO_CREATE);
            if (!bound) showLog("Could not bind to the BLE service");
        }
    }

    @Override protected void onStop() {
        if (service != null) service.clearListener(this);
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
        service = null;
        super.onStop();
    }

    private TextView makeText(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(32, 46, 57));
        view.setTextSize(size);
        view.setPadding(0, 9, 0, 9);
        return view;
    }

    private Button makeButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
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
        body.setBackgroundColor(Color.rgb(246, 248, 252));
        TextView title = makeText("Tesla BLE Monitor", 26);
        title.setTextColor(Color.rgb(14, 48, 82));
        body.addView(title);
        body.addView(makeText("Bluetooth is owned by the foreground service. Adaptive polling uses no-wake reads: 10s driving, 15s charging, 30s occupied; unoccupied fallback is at least 2 minutes. Polling is opt-in.", 13));
        body.addView(makeButton("Public wake / Cloudflare Tunnel", () -> startActivity(new Intent(MainActivity.this, PublicWakeSettingsActivity.class))));
        body.addView(makeButton("MQTT / Home Assistant settings", () ->
            startActivity(new Intent(MainActivity.this, MqttSettingsActivity.class))));
        body.addView(makeButton("Background Bluetooth permission", () ->
                withPermissions(this::showBackgroundBlePermissionHelp)));
        body.addView(makeText("Automatic reconnection needs Location: Allow all the time so Android delivers Tesla Bluetooth beacons while this screen is closed. The app does not collect GPS location.", 13));
        body.addView(makeText("Vehicle VIN", 15));
        vinField = new EditText(this);
        vinField.setSingleLine(true);
        vinField.setHint("17-character Tesla VIN");
        vinField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        body.addView(vinField);
        body.addView(makeButton("1. Scan nearby Tesla BLE devices", () -> {
            shownAddresses.clear();
            if (deviceList != null) deviceList.removeAllViews();
            withPermissions(() -> {
                String vin = readVin();
                if (vin != null) dispatch(TeslaBleService.ACTION_UI_SCAN, vin, null);
            });
        }));
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        body.addView(deviceList);
        body.addView(makeButton("2. Connect selected Tesla", () -> withPermissions(() -> {
            String vin = readVin();
            if (vin != null) dispatch(TeslaBleService.ACTION_UI_CONNECT, vin, null);
        })));
        body.addView(makeButton("3. Enroll monitor key (manual; tap Tesla key card)", () ->
            withPermissions(() -> dispatch(TeslaBleService.ACTION_UI_PAIR, null, null))));
        body.addView(makeButton("Authorize existing key for controls (manual; parked only)", this::confirmControllerEnrollment));
        body.addView(makeButton("4. Read ChargeState (no intentional wake)", () ->
            withPermissions(() -> dispatch(TeslaBleService.ACTION_UI_READ, null, null))));
        body.addView(makeButton("Enable adaptive polling", () -> withPermissions(() ->
            dispatch(TeslaBleService.ACTION_COMMAND, null, "poll-on"))));
        body.addView(makeButton("Disable polling", () -> dispatch(TeslaBleService.ACTION_COMMAND, null, "poll-off")));
        body.addView(makeButton("Disconnect", () -> withPermissions(() ->
            dispatch(TeslaBleService.ACTION_UI_DISCONNECT, null, null))));
        statusView = makeText("Service idle", 15);
        body.addView(statusView);
        telemetryView = makeText("No authenticated ChargeState received yet", 16);
        body.addView(telemetryView);
        body.addView(makeText("Operational log", 15));
        logView = makeText("", 12);
        logView.setTextIsSelectable(true);
        body.addView(logView);
        scroll.addView(body);
        setContentView(scroll);
    }

    private void showBackgroundBlePermissionHelp() {
        if (Build.VERSION.SDK_INT < 29 ||
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            showLog("Background Bluetooth permission is already allowed; existing retry policy remains active");
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Allow Bluetooth discovery in the background")
                .setMessage("Android treats Tesla's Bluetooth beacon as location-sensitive. To reconnect after this screen closes or after boot, open Permissions > Location and select Allow all the time. This app does not collect GPS location. This is separate from the ordinary Bluetooth/precise-location permission request.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open app settings", (dialog, which) -> {
                    try {
                        startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (android.content.ActivityNotFoundException e) {
                        showLog("Open Android app settings for Tesla BLE Monitor and select Location: Allow all the time");
                    }
                }).show();
    }

    private void confirmControllerEnrollment() {
        final String confirmedVin = readVin();
        if (confirmedVin == null || service == null) { showLog("Connect the saved vehicle first"); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Authorize the existing key for controls?")
                .setMessage("Only continue while the vehicle is parked. This reuses the existing private key for VIN "
                        + confirmedVin + "; it never generates or deletes a key. Tesla must approve the requested Driver role with the physical key card. MQTT control remains separately opt-in. Cancel leaves the key unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Vehicle parked — request approval", (dialog, which) -> withPermissions(() -> {
                    if (service == null || !service.requestControllerEnrollment(confirmedVin)) showLog("Enrollment request rejected; connect the matching vehicle with its existing key first");
                })).show();
    }

    private String readVin() {
        String value = vinField.getText().toString().trim().toUpperCase(Locale.US);
        if (!value.matches("[A-HJ-NPR-Z0-9]{17}")) {
            showLog("Enter a valid 17-character VIN first");
            return null;
        }
        getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putString("vin", value).apply();
        return value;
    }

    private void withPermissions(Runnable action) {
        ArrayList<String> required = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                required.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                required.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        // Without this permission Android filters Tesla's beacon when location has
        // not been disavowed. Android 12+ requires fine/coarse to be requested together.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 31)
                required.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            required.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (required.isEmpty()) {
            action.run();
            return;
        }
        pendingAfterPermissions = action;
        requestPermissions(required.toArray(new String[0]), 191);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 191) return;
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        Runnable next = pendingAfterPermissions;
        pendingAfterPermissions = null;
        if (granted && next != null) next.run();
        else showLog("Required Bluetooth/notification permission was not granted");
    }

    private void dispatch(String action, String vin, String command) {
        Intent intent = new Intent(this, TeslaBleService.class);
        intent.setAction(action);
        if (vin != null) intent.putExtra(TeslaBleService.EXTRA_VIN, vin);
        if (command != null) intent.putExtra(TeslaBleService.EXTRA_COMMAND, command);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException e) {
            showLog("Could not start Tesla BLE service; check Bluetooth permissions and notification settings");
        }
    }

    @Override public void onStatus(String json) {
        if (statusView != null) statusView.setText(json);
    }

    @Override public void onDeviceFound(String name, String address, int rssi) {
        if (deviceList == null || !shownAddresses.add(address)) return;
        Button choose = makeButton("Select Tesla BLE device  •  " + address + "  •  " + rssi + " dBm", () -> {
            if (service != null) service.selectDevice(address);
        });
        deviceList.addView(choose);
    }

    @Override public void onTelemetry(String json) {
        if (telemetryView != null) telemetryView.setText(json);
    }

    @Override public void onLog(String message) { showLog(message); }

    private void showLog(String message) {
        if (message == null) return;
        Log.i(TAG, message);
        if (logView != null) {
            String updated = message + "\n" + logView.getText();
            if (updated.length() > 8000) updated = updated.substring(0, 8000);
            logView.setText(updated);
        }
    }
}
