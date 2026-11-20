package com.ayane.dynamicisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ayane.dynamicisland.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = SettingsRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val settings = repository.settingsFlow.first()
                if (settings.isEnabled && settings.restartOnBoot && Settings.canDrawOverlays(context)) {
                    runCatching { IslandOverlayService.start(context.applicationContext) }
                }
            }
        }
    }
}
