package com.example.feature.dns

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.SystemPermissionHelper
import com.example.core.datastore.AdZeroPreferences
import com.example.core.datastore.AdZeroSettings
import com.example.data.model.DnsProbeResult
import com.example.data.model.DnsProtocol
import com.example.data.repository.AdZeroRepository
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmartDnsUiState(
    val settings: AdZeroSettings = AdZeroSettings(),
    val isProbing: Boolean = false,
    val lastProbeResult: DnsProbeResult? = null,
    val privateDnsModeLabel: String = "System Default",
    val cacheSize: Int = 0,
    val cacheHits: Long = 0L,
    val feedbackMessage: String? = null
)

class SmartDnsViewModel(
    private val appContext: Context,
    private val repository: AdZeroRepository,
    private val preferences: AdZeroPreferences
) : ViewModel() {

    private val isProbingFlow = MutableStateFlow(false)
    private val probeResultFlow = MutableStateFlow<DnsProbeResult?>(null)
    private val feedbackFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SmartDnsUiState> = combine(
        repository.settingsFlow,
        repository.protectionStateHolder.snapshot,
        isProbingFlow,
        probeResultFlow,
        feedbackFlow
    ) { settings, snap, probing, probe, feedback ->
        SmartDnsUiState(
            settings = settings,
            isProbing = probing,
            lastProbeResult = probe,
            privateDnsModeLabel = SystemPermissionHelper.getPrivateDnsModeLabel(appContext),
            cacheSize = snap.cacheSize,
            cacheHits = snap.cacheHits,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SmartDnsUiState()
    )

    fun selectProviderAndProtocol(
        providerId: String,
        protocol: DnsProtocol,
        customIpv4: String? = null,
        customDot: String? = null,
        customDoh: String? = null
    ) {
        viewModelScope.launch {
            preferences.setDnsConfig(providerId, protocol, customIpv4, customDot, customDoh)
            repository.filterEngine.clearCache()
            AdZeroVpnService.rebuildIfRunning(appContext)
            feedbackFlow.value = "DNS configuration saved."
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            preferences.resetDnsToDefault()
            repository.filterEngine.clearCache()
            AdZeroVpnService.rebuildIfRunning(appContext)
            feedbackFlow.value = "Reset to Cloudflare DNS (DoH)."
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            isProbingFlow.value = true
            val result = repository.runDnsProbe()
            probeResultFlow.value = result
            isProbingFlow.value = false
        }
    }

    fun clearDnsCache() {
        repository.filterEngine.clearCache()
        repository.protectionStateHolder.updateCacheStats(0, 0L)
        feedbackFlow.value = "Local DNS cache cleared."
    }

    fun clearFeedback() {
        feedbackFlow.value = null
    }
}
