package com.example.feature.ghost

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.SystemPermissionHelper
import com.example.core.database.ServiceEventEntity
import com.example.core.datastore.AdZeroPreferences
import com.example.core.datastore.AdZeroSettings
import com.example.data.model.GhostMessage
import com.example.data.model.GhostSchedule
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnEnforcementMode
import com.example.data.repository.AdZeroRepository
import com.example.domain.usecase.GhostScheduleCalculator
import com.example.domain.usecase.ScheduleEvaluation
import com.example.service.notification.GhostNotificationListenerService
import com.example.service.scheduler.GhostScheduleManager
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class GhostUiState(
    val settings: AdZeroSettings = AdZeroSettings(),
    val vpnState: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val enforcementMode: VpnEnforcementMode = VpnEnforcementMode.STANDARD_DNS_FILTERING,
    val messages: List<GhostMessage> = emptyList(),
    val contacts: List<String> = emptyList(),
    val selectedContact: String? = null,
    val searchQuery: String = "",
    val schedules: List<GhostSchedule> = emptyList(),
    val scheduleEvaluation: ScheduleEvaluation = ScheduleEvaluation(false, null, null, false, null),
    val scheduleHistory: List<ServiceEventEntity> = emptyList(),
    val isNotificationAccessGranted: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val isUnlocked: Boolean = true
)

class GhostViewModel(
    private val appContext: Context,
    private val repository: AdZeroRepository,
    private val preferences: AdZeroPreferences,
    private val scheduleManager: GhostScheduleManager
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedContactFlow = MutableStateFlow<String?>(null)
    private val permissionTickFlow = MutableStateFlow(0)
    private val unlockedFlow = MutableStateFlow(false)

    private val liveClockFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    val uiState: StateFlow<GhostUiState> = combine(
        repository.settingsFlow,
        repository.protectionStateHolder.snapshot,
        repository.ghostMessagesFlow,
        repository.ghostSchedulesFlow,
        repository.recentServiceEventsFlow,
        searchQueryFlow,
        selectedContactFlow,
        permissionTickFlow,
        unlockedFlow,
        GhostNotificationListenerService.isConnected,
        liveClockFlow
    ) { values ->
        val settings = values[0] as AdZeroSettings
        val vpnSnap = values[1] as com.example.data.model.LiveProtectionSnapshot
        @Suppress("UNCHECKED_CAST")
        val allMessages = values[2] as List<GhostMessage>
        @Suppress("UNCHECKED_CAST")
        val schedules = values[3] as List<GhostSchedule>
        @Suppress("UNCHECKED_CAST")
        val events = values[4] as List<ServiceEventEntity>
        val query = values[5] as String
        val selectedContact = values[6] as String?
        val unlockedState = values[8] as Boolean
        val listenerConnected = values[9] as Boolean
        val nowMillis = values[10] as Long

        val contacts = allMessages.map { it.senderName }.distinct().sorted()
        val filteredMessages = allMessages.filter { msg ->
            val matchesContact = selectedContact == null || msg.senderName == selectedContact
            val matchesQuery = query.isBlank() ||
                msg.senderName.contains(query, ignoreCase = true) ||
                msg.messagePreview.contains(query, ignoreCase = true)
            matchesContact && matchesQuery
        }

        val eval = GhostScheduleCalculator.evaluate(schedules, nowMillis)

        GhostUiState(
            settings = settings,
            vpnState = vpnSnap.state,
            enforcementMode = vpnSnap.enforcementMode,
            messages = filteredMessages,
            contacts = contacts,
            selectedContact = selectedContact,
            searchQuery = query,
            schedules = schedules,
            scheduleEvaluation = eval,
            scheduleHistory = events.filter { it.eventType == "SCHEDULE" }.take(15),
            isNotificationAccessGranted = listenerConnected || SystemPermissionHelper.isNotificationListenerEnabled(appContext),
            canScheduleExactAlarms = SystemPermissionHelper.canScheduleExactAlarms(appContext),
            isUnlocked = !settings.ghostBiometricLockEnabled || unlockedState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GhostUiState()
    )

    fun refreshPermissions() {
        permissionTickFlow.value++
    }

    fun setUnlocked(unlocked: Boolean) {
        unlockedFlow.value = unlocked
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun selectContact(contact: String?) {
        selectedContactFlow.value = contact
    }

    fun toggleGhostMode(enabled: Boolean, temporaryOverrideHours: Int = 2) {
        viewModelScope.launch {
            val overrideUntil = if (temporaryOverrideHours > 0) {
                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(temporaryOverrideHours.toLong())
            } else {
                0L
            }
            preferences.setGhostMode(enabled, manualOverrideUntil = overrideUntil)
            repository.reloadFilterEngineSnapshot()
            AdZeroVpnService.rebuildIfRunning(appContext)
            repository.logServiceEvent(
                "SCHEDULE",
                if (enabled) "WhatsApp Ghost Mode enabled manually." else "WhatsApp Ghost Mode disabled manually."
            )
        }
    }

    fun toggleStrictIsolation(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setGhostStrictIsolation(enabled)
            repository.reloadFilterEngineSnapshot()
            AdZeroVpnService.rebuildIfRunning(appContext)
        }
    }

    fun toggleInboxCapture(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setGhostInboxCapture(enabled)
        }
    }

    fun togglePreviewPrivacyMasked(masked: Boolean) {
        viewModelScope.launch {
            preferences.setGhostPreviewPrivacyMasked(masked)
        }
    }

    fun toggleBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setGhostBiometricLock(enabled)
            if (enabled) unlockedFlow.value = true
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch { repository.markGhostMessageRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllGhostMessagesRead() }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { repository.deleteGhostMessage(id) }
    }

    fun clearInbox() {
        viewModelScope.launch { repository.clearGhostInbox() }
    }

    fun saveSchedule(schedule: GhostSchedule) {
        viewModelScope.launch {
            repository.upsertGhostSchedule(schedule)
            scheduleManager.evaluateAndApplyNow("Schedule updated")
        }
    }

    fun toggleScheduleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setGhostScheduleEnabled(id, enabled)
            scheduleManager.evaluateAndApplyNow("Schedule toggled")
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.deleteGhostSchedule(id)
            scheduleManager.rescheduleNextCheck()
        }
    }

    fun clearManualOverride() {
        viewModelScope.launch {
            preferences.setGhostMode(uiState.value.settings.ghostModeEnabled, manualOverrideUntil = 0L)
            scheduleManager.evaluateAndApplyNow("Manual override cleared")
        }
    }
}
