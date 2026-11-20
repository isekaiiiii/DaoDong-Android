package com.ayane.dynamicisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.ayane.dynamicisland.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(appContext).settingsFlow.first()
                if (settings.isEnabled && settings.restartOnBoot && Settings.canDrawOverlays(appContext)) {
                    val started = IslandOverlayService.start(appContext)
                    if (!started) {
                        Log.w("AyaneStartup", "Không thể khởi động đảo động sau khi khởi động máy")
                    }
                }
            } catch (error: Exception) {
                Log.e("AyaneStartup", "Lỗi khi khôi phục trạng thái Ayane sau khi khởi động máy", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
