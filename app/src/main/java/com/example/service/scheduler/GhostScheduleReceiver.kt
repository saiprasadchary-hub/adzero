package com.example.service.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.AdZeroApplication
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, and exact Ghost Schedule alarm triggers.
 */
class GhostScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? AdZeroApplication ?: return@launch
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        val settings = app.container.preferences.settingsFlow.first()
                        app.container.ghostScheduleManager.evaluateAndApplyNow("Device boot")
                        if (settings.autoStartOnBoot && VpnService.prepare(context) == null) {
                            AdZeroVpnService.start(context)
                        }
                    }
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        app.container.ghostScheduleManager.evaluateAndApplyNow("Clock or timezone changed")
                    }
                    ACTION_GHOST_SCHEDULE_CHECK -> {
                        app.container.ghostScheduleManager.evaluateAndApplyNow("Exact alarm trigger")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_GHOST_SCHEDULE_CHECK = "com.example.adzero.ACTION_GHOST_SCHEDULE_CHECK"
    }
}
