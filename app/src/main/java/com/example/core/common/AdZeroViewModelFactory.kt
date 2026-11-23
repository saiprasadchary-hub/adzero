package com.example.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.feature.activity.ActivityViewModel
import com.example.feature.apps.AppsViewModel
import com.example.feature.dns.SmartDnsViewModel
import com.example.feature.ghost.GhostViewModel
import com.example.feature.home.HomeViewModel
import com.example.feature.settings.SettingsViewModel

class AdZeroViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
                appContext = container.appContext,
                repository = container.repository,
                preferences = container.preferences,
                installedAppsRepository = container.installedAppsRepository,
                trafficMonitorStateHolder = container.trafficMonitorStateHolder
            ) as T
            modelClass.isAssignableFrom(AppsViewModel::class.java) -> AppsViewModel(
                appContext = container.appContext,
                repository = container.repository,
                preferences = container.preferences,
                installedAppsRepository = container.installedAppsRepository,
                trafficMonitorStateHolder = container.trafficMonitorStateHolder
            ) as T
            modelClass.isAssignableFrom(GhostViewModel::class.java) -> GhostViewModel(
                appContext = container.appContext,
                repository = container.repository,
                preferences = container.preferences,
                scheduleManager = container.ghostScheduleManager
            ) as T
            modelClass.isAssignableFrom(ActivityViewModel::class.java) -> ActivityViewModel(
                repository = container.repository,
                preferences = container.preferences,
                installedAppsRepository = container.installedAppsRepository
            ) as T
            modelClass.isAssignableFrom(SmartDnsViewModel::class.java) -> SmartDnsViewModel(
                appContext = container.appContext,
                repository = container.repository,
                preferences = container.preferences
            ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                appContext = container.appContext,
                repository = container.repository,
                preferences = container.preferences,
                blocklistUpdateManager = container.blocklistUpdateManager
            ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
