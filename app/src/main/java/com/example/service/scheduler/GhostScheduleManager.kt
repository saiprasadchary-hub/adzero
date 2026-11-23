package com.example.service.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.AdZeroApplication
import com.example.core.common.SystemPermissionHelper
import com.example.data.model.GhostSchedule
import com.example.domain.usecase.GhostScheduleCalculator
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Coordinates WhatsApp Ghost Mode schedules using AlarmManager (when exact alarms are permitted)
 * paired with a WorkManager fallback for Doze / reboot / timezone resilience.
 */
class GhostScheduleManager(private val context: Context) {

    suspend fun evaluateAndApplyNow(reason: String = "Scheduled check") = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? AdZeroApplication ?: return@withContext
        val repo = app.container.repository
        val prefs = app.container.preferences

        val now = System.currentTimeMillis()
        val settings = prefs.settingsFlow.first()
        val schedules = app.container.database.ghostScheduleDao().getAllSnapshot().map { e ->
            GhostSchedule(
                id = e.id,
                label = e.label,
                startMinuteOfDay = e.startMinuteOfDay,
                endMinuteOfDay = e.endMinuteOfDay,
                daysMask = e.daysMask,
                enabled = e.enabled,
                lastTriggeredAt = e.lastTriggeredAt
            )
        }

        val evaluation = GhostScheduleCalculator.evaluate(schedules, now)
        val hasEnabledSchedules = schedules.any { it.enabled }

        if (hasEnabledSchedules) {
            val overrideActive = settings.ghostManualOverrideUntil > now
            if (!overrideActive && settings.ghostModeEnabled != evaluation.shouldGhostModeBeActive) {
                prefs.setGhostMode(evaluation.shouldGhostModeBeActive, manualOverrideUntil = 0L)
                repo.reloadFilterEngineSnapshot()
                AdZeroVpnService.rebuildIfRunning(context)

                evaluation.activeSchedule?.let { active ->
                    app.container.database.ghostScheduleDao().recordTriggered(active.id, now)
                }
                repo.logServiceEvent(
                    "SCHEDULE",
                    if (evaluation.shouldGhostModeBeActive) {
                        "Ghost Mode activated by schedule '${evaluation.activeSchedule?.label ?: "Schedule"}' ($reason)."
                    } else {
                        "Ghost Mode deactivated at end of schedule window ($reason)."
                    }
                )
            }
        }

        scheduleNextBoundary(evaluation.nextTransitionTimeMillis, now)
    }

    suspend fun rescheduleNextCheck() = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? AdZeroApplication ?: return@withContext
        val now = System.currentTimeMillis()
        val schedules = app.container.database.ghostScheduleDao().getAllSnapshot().map { e ->
            GhostSchedule(
                id = e.id,
                label = e.label,
                startMinuteOfDay = e.startMinuteOfDay,
                endMinuteOfDay = e.endMinuteOfDay,
                daysMask = e.daysMask,
                enabled = e.enabled,
                lastTriggeredAt = e.lastTriggeredAt
            )
        }
        val eval = GhostScheduleCalculator.evaluate(schedules, now)
        scheduleNextBoundary(eval.nextTransitionTimeMillis, now)
    }

    private fun scheduleNextBoundary(nextTimeMillis: Long?, nowMillis: Long) {
        val wm = WorkManager.getInstance(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmIntent = Intent(context, GhostScheduleReceiver::class.java).apply {
            action = GhostScheduleReceiver.ACTION_GHOST_SCHEDULE_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (nextTimeMillis == null) {
            alarmManager?.cancel(pendingIntent)
            wm.cancelUniqueWork(UNIQUE_SCHEDULE_WORK)
            return
        }

        val delayMillis = (nextTimeMillis - nowMillis).coerceAtLeast(15_000L)

        // Schedule exact alarm when allowed by Android 12+ policy
        if (alarmManager != null && SystemPermissionHelper.canScheduleExactAlarms(context)) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTimeMillis,
                    pendingIntent
                )
            }
        }

        // Always enqueue persistent WorkManager fallback (survives process death & Doze)
        val request = OneTimeWorkRequestBuilder<GhostScheduleWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(
            UNIQUE_SCHEDULE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 5109
        private const val UNIQUE_SCHEDULE_WORK = "adzero_ghost_schedule_boundary"
    }
}
