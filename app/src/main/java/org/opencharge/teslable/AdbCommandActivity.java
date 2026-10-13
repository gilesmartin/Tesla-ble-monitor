package org.opencharge.teslable;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import java.util.Locale;

/** Entry point for ADB shell only; no intent extras reach the exported normal GUI. */
public final class AdbCommandActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String cmd = getIntent().getStringExtra("cmd");
        if (cmd == null) { Log.e("TeslaBLE", "Missing cmd extra"); finish(); return; }
        cmd = cmd.trim().toLowerCase(Locale.US);
        switch (cmd) {
            case "scan": case "connect": case "pair": case "read-charge":
            case "status": case "disconnect": case "storage-test": break;
            default: Log.e("TeslaBLE", "Unsupported ADB command: " + cmd); finish(); return;
        }
        String vin = getIntent().getStringExtra("vin");
        if (vin != null) {
            vin = vin.trim().toUpperCase(Locale.US);
            if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
                Log.e("TeslaBLE", "Invalid VIN argument"); finish(); return;
            }
        }
        SharedPreferences.Editor e = getSharedPreferences("tesla_adb_commands", MODE_PRIVATE)
            .edit().putString("cmd", cmd);
        if (vin == null) e.remove("vin"); else e.putString("vin", vin);
        if (!e.commit()) { Log.e("TeslaBLE", "Could not persist ADB command"); finish(); return; }
        Log.i("TeslaBLE", "ADB command accepted: " + cmd);
        Intent gui = new Intent(this, MainActivity.class);
        gui.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(gui);
        finish();
    }
}
