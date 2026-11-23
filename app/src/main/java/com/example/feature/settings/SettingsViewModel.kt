package com.example.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.SystemPermissionHelper
import com.example.core.database.BlocklistSourceEntity
import com.example.core.database.CustomRuleEntity
import com.example.core.database.ServiceEventEntity
import com.example.core.datastore.AdZeroPreferences
import com.example.core.datastore.AdZeroSettings
import com.example.core.network.BlocklistUpdateManager
import com.example.data.model.LiveProtectionSnapshot
import com.example.data.model.RuleType
import com.example.data.repository.AdZeroRepository
import com.example.service.accessibility.YouTubeSkipAccessibilityService
import com.example.service.notification.GhostNotificationListenerService
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AdZeroSettings = AdZeroSettings(),
    val liveSnapshot: LiveProtectionSnapshot = LiveProtectionSnapshot(),
    val blocklistSources: List<BlocklistSourceEntity> = emptyList(),
    val customRules: List<CustomRuleEntity> = emptyList(),
    val recentEvents: List<ServiceEventEntity> = emptyList(),
    val isUpdatingBlocklists: Boolean = false,
    val updateProgress: Float = 0f,
    val updateStatusText: String? = null,
    val vpnPermissionGranted: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val accessibilityServiceEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val privateDnsMode: String = "System Default",
    val isNetworkConnected: Boolean = true,
    val feedbackMessage: String? = null
)

class SettingsViewModel(
    private val appContext: Context,
    private val repository: AdZeroRepository,
    private val preferences: AdZeroPreferences,
    private val blocklistUpdateManager: BlocklistUpdateManager
) : ViewModel() {

    private val isUpdatingFlow = MutableStateFlow(false)
    private val progressFlow = MutableStateFlow(0f)
    private val updateTextFlow = MutableStateFlow<String?>(null)
    private val feedbackFlow = MutableStateFlow<String?>(null)
    private val refreshTickFlow = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settingsFlow,
        repository.protectionStateHolder.snapshot,
        repository.blocklistSourcesFlow,
        repository.customRulesFlow,
        repository.recentServiceEventsFlow,
        isUpdatingFlow,
        progressFlow,
        updateTextFlow,
        feedbackFlow,
        refreshTickFlow,
        GhostNotificationListenerService.isConnected,
        YouTubeSkipAccessibilityService.isConnected
    ) { values ->
        val settings = values[0] as AdZeroSettings
        val live = values[1] as LiveProtectionSnapshot
        @Suppress("UNCHECKED_CAST")
        val sources = values[2] as List<BlocklistSourceEntity>
        @Suppress("UNCHECKED_CAST")
        val rules = values[3] as List<CustomRuleEntity>
        @Suppress("UNCHECKED_CAST")
        val events = values[4] as List<ServiceEventEntity>
        val updating = values[5] as Boolean
        val progress = values[6] as Float
        val updateText = values[7] as String?
        val feedback = values[8] as String?
        val liveNotificationConnected = values[10] as Boolean
        val liveAccessibilityConnected = values[11] as Boolean

        SettingsUiState(
            settings = settings,
            liveSnapshot = live,
            blocklistSources = sources,
            customRules = rules,
            recentEvents = events,
            isUpdatingBlocklists = updating,
            updateProgress = progress,
            updateStatusText = updateText,
            vpnPermissionGranted = SystemPermissionHelper.isVpnPermissionGranted(appContext),
            notificationAccessEnabled = liveNotificationConnected || SystemPermissionHelper.isNotificationListenerEnabled(appContext),
            accessibilityServiceEnabled = liveAccessibilityConnected || SystemPermissionHelper.isAccessibilityServiceEnabled(appContext),
            batteryOptimizationIgnored = SystemPermissionHelper.isIgnoringBatteryOptimizations(appContext),
            exactAlarmsAllowed = SystemPermissionHelper.canScheduleExactAlarms(appContext),
            privateDnsMode = SystemPermissionHelper.getPrivateDnsModeLabel(appContext),
            isNetworkConnected = live.isNetworkAvailable && SystemPermissionHelper.isNetworkConnected(appContext),
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun refreshDiagnostics() {
        refreshTickFlow.value++
    }

    fun setAdBlocking(enabled: Boolean) = viewModelScope.launch {
        preferences.setAdBlocking(enabled)
        repository.reloadFilterEngineSnapshot()
    }

    fun setTrackerBlocking(enabled: Boolean) = viewModelScope.launch {
        preferences.setTrackerBlocking(enabled)
        repository.reloadFilterEngineSnapshot()
    }

    fun setMalwareBlocking(enabled: Boolean) = viewModelScope.launch {
        preferences.setMalwareBlocking(enabled)
        repository.reloadFilterEngineSnapshot()
    }

    fun setStrictMode(enabled: Boolean) = viewModelScope.launch {
        preferences.setStrictMode(enabled)
        repository.reloadFilterEngineSnapshot()
    }

    fun setAutoStartOnBoot(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoStartOnBoot(enabled)
    }

    fun setAutoBlocklistUpdate(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoBlocklistUpdate(enabled)
        blocklistUpdateManager.schedulePeriodicUpdates(enabled)
    }

    fun toggleBlocklistSource(id: String, enabled: Boolean) = viewModelScope.launch {
        repository.setBlocklistSourceEnabled(id, enabled)
    }

    fun runManualBlocklistUpdate() {
        if (isUpdatingFlow.value) return
        viewModelScope.launch {
            isUpdatingFlow.value = true
            progressFlow.value = 0.05f
            updateTextFlow.value = "Preparing blocklist update…"
            val outcome = blocklistUpdateManager.updateAllEnabledSources { p, msg ->
                progressFlow.value = p
                updateTextFlow.value = msg
            }
            isUpdatingFlow.value = false
            updateTextFlow.value = outcome.message
            feedbackFlow.value = outcome.message
        }
    }

    fun addCustomRule(pattern: String, type: RuleType) {
        viewModelScope.launch {
            val res = repository.addCustomRule(pattern, type)
            feedbackFlow.value = res.fold(
                onSuccess = {
                    AdZeroVpnService.rebuildIfRunning(appContext)
                    "Added ${type.name.lowercase()} rule: ${pattern.trim().lowercase()}"
                },
                onFailure = { it.message ?: "Invalid domain pattern." }
            )
        }
    }

    fun deleteCustomRule(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomRule(id)
            feedbackFlow.value = "Removed custom rule."
        }
    }

    fun setYouTubeConsent(granted: Boolean) = viewModelScope.launch {
        preferences.setYouTubeSkipConsent(granted)
    }

    fun setYouTubeSkipEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setYouTubeSkipEnabled(enabled)
    }

    fun resetYouTubeStats() = viewModelScope.launch {
        preferences.resetYouTubeSkipCount()
        feedbackFlow.value = "Reset YouTube Skip Assistant statistics."
    }

    fun clearDnsCache() {
        repository.filterEngine.clearCache()
        repository.protectionStateHolder.updateCacheStats(0, 0L)
        feedbackFlow.value = "Cleared local DNS cache."
    }

    fun clearAllLocalData() = viewModelScope.launch {
        repository.clearAllLocalData()
        feedbackFlow.value = "Cleared DNS logs, Ghost Inbox history, and local DNS cache."
    }

    suspend fun generateCsvContent(): String = repository.buildAnalyticsCsv()

    fun setFeedback(msg: String?) {
        feedbackFlow.value = msg
    }
}
