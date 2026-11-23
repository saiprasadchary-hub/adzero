package com.example

import android.app.Application
import com.example.core.common.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdZeroApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.repository.initializeDefaultsIfNeeded()
            val settings = container.preferences.settingsFlow.first()
            container.blocklistUpdateManager.schedulePeriodicUpdates(settings.autoBlocklistUpdateEnabled)
            container.ghostScheduleManager.rescheduleNextCheck()
        }
    }
}
