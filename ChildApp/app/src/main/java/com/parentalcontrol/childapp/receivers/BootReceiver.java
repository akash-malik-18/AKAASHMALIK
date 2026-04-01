package com.parentalcontrol.childapp.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.parentalcontrol.childapp.services.MonitorForegroundService;

/**
 * BootReceiver
 * ────────────
 * Automatically restarts the foreground monitoring service after
 * the device is rebooted, so keyword monitoring is always active.
 *
 * Requires permission: RECEIVE_BOOT_COMPLETED
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed – starting MonitorForegroundService.");

            Intent serviceIntent = new Intent(context, MonitorForegroundService.class);

            // On Android 8+ (Oreo) you must use startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
