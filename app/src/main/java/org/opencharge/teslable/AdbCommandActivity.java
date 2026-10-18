package org.opencharge.teslable;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import java.util.Locale;

/** Shell-only dispatcher protected by android.permission.DUMP in the manifest. */
public final class AdbCommandActivity extends Activity {
    private static final String TAG = "TeslaBLE";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        process(getIntent());
        finish();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        process(intent);
        finish();
    }

    private void process(Intent source) {
        if (source == null) { Log.e(TAG, "ADB command intent missing"); return; }
        String raw = source.getStringExtra("cmd");
        if (raw == null) { Log.e(TAG, "ADB command name missing"); return; }
        String command = raw.trim().toLowerCase(Locale.US);
        if (!supported(command)) { Log.e(TAG, "Unsupported ADB command"); return; }

        Intent service = new Intent(this, TeslaBleService.class);
        service.setAction(TeslaBleService.ACTION_COMMAND);
        service.putExtra(TeslaBleService.EXTRA_COMMAND, command);
        String vin = source.getStringExtra(TeslaBleService.EXTRA_VIN);
        if (vin != null) {
            vin = vin.trim().toUpperCase(Locale.US);
            if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
                Log.e(TAG, "Invalid VIN argument; command rejected");
                return;
            }
            service.putExtra(TeslaBleService.EXTRA_VIN, vin);
        }
        if ("set-api-token".equals(command)) {
            String token = source.getStringExtra(TeslaBleService.EXTRA_TOKEN);
            if (token == null) token = source.getStringExtra("api_token");
            if (token == null) {
                Log.e(TAG, "API token argument missing; token value suppressed");
                return;
            }
            service.putExtra(TeslaBleService.EXTRA_TOKEN, token);
        }
        if ("poll-interval".equals(command)) {
            int seconds = source.getIntExtra(TeslaBleService.EXTRA_INTERVAL_SECONDS, Integer.MIN_VALUE);
            if (seconds == Integer.MIN_VALUE) {
                String value = source.getStringExtra(TeslaBleService.EXTRA_INTERVAL_SECONDS);
                try { seconds = Integer.parseInt(value); } catch (Exception ignored) { seconds = -1; }
            }
            if (seconds < 15 || seconds > 3600) {
                Log.e(TAG, "Polling interval rejected; valid range is 15..3600 seconds");
                return;
            }
            service.putExtra(TeslaBleService.EXTRA_INTERVAL_SECONDS, seconds);
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            Log.i(TAG, "ADB command accepted: " + command);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not dispatch ADB command: " + command);
        }
    }

    private static boolean supported(String command) {
        switch (command) {
            case "scan": case "connect": case "disconnect": case "status":
            case "read-charge": case "poll-on": case "poll-off": case "poll-interval":
            case "storage-test": case "stop": case "pair": case "set-api-token":
                return true;
            default: return false;
        }
    }
}
