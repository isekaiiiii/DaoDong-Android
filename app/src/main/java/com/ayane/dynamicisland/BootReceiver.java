package com.ayane.dynamicisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Restores the user-enabled island after device restart or app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        android.content.SharedPreferences prefs = context.getSharedPreferences("island_settings", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_start", true)) return;
        if (!Settings.canDrawOverlays(context)) return;

        Intent service = new Intent(context, IslandService.class);
        service.setAction(IslandService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (RuntimeException ignored) {
            // Android/OEM background-start policies may prevent automatic restoration.
        }
    }
}
