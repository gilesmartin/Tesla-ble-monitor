package org.opencharge.teslable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restarts only a previously requested BLE session after the active Android user boots. */
public final class BootRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))) return;
        if (!TeslaBleService.hasSavedReconnectIntent(context)) return;

        Intent restore = new Intent(context, TeslaBleService.class);
        restore.setAction(TeslaBleService.ACTION_BOOT_RESTORE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(restore);
            else context.startService(restore);
        } catch (IllegalStateException | SecurityException e) {
            android.util.Log.w("TeslaBLE", "Saved BLE restore deferred; Android denied background start");
        }
    }
}
