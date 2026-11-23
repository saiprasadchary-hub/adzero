package com.example.data.repository

import com.example.data.model.DomainCategory
import com.example.data.model.LiveProtectionSnapshot
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnEnforcementMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for live VPN runtime state.
 * Only AdZeroVpnService transitions state to ACTIVE after VpnService.Builder.establish() succeeds.
 */
class ProtectionStateHolder {

    private val _snapshot = MutableStateFlow(LiveProtectionSnapshot())
    val snapshot: StateFlow<LiveProtectionSnapshot> = _snapshot.asStateFlow()

    fun setState(
        state: VpnConnectionState,
        enforcementMode: VpnEnforcementMode? = null,
        activeDnsLabel: String? = null,
        errorMessage: String? = null
    ) {
        val now = System.currentTimeMillis()
        _snapshot.update { cur ->
            cur.copy(
                state = state,
                enforcementMode = enforcementMode ?: cur.enforcementMode,
                sessionStartedAt = when (state) {
                    VpnConnectionState.ACTIVE -> if (cur.sessionStartedAt == 0L) now else cur.sessionStartedAt
                    VpnConnectionState.DISCONNECTED, VpnConnectionState.ERROR -> 0L
                    else -> cur.sessionStartedAt
                },
                lastRestartAt = if (state == VpnConnectionState.ACTIVE) now else cur.lastRestartAt,
                sessionTotalRequests = if (state == VpnConnectionState.DISCONNECTED) 0L else cur.sessionTotalRequests,
                sessionBlockedAds = if (state == VpnConnectionState.DISCONNECTED) 0L else cur.sessionBlockedAds,
                sessionBlockedTrackers = if (state == VpnConnectionState.DISCONNECTED) 0L else cur.sessionBlockedTrackers,
                sessionBlockedOther = if (state == VpnConnectionState.DISCONNECTED) 0L else cur.sessionBlockedOther,
                activeDnsLabel = activeDnsLabel ?: cur.activeDnsLabel,
                errorMessage = errorMessage
            )
        }
    }

    fun recordDnsEvent(
        blocked: Boolean,
        category: DomainCategory,
        cacheSize: Int,
        cacheHits: Long
    ) {
        _snapshot.update { cur ->
            cur.copy(
                sessionTotalRequests = cur.sessionTotalRequests + 1L,
                sessionBlockedAds = if (blocked && category == DomainCategory.AD) cur.sessionBlockedAds + 1L else cur.sessionBlockedAds,
                sessionBlockedTrackers = if (blocked && category == DomainCategory.TRACKER) cur.sessionBlockedTrackers + 1L else cur.sessionBlockedTrackers,
                sessionBlockedOther = if (blocked && category != DomainCategory.AD && category != DomainCategory.TRACKER) {
                    cur.sessionBlockedOther + 1L
                } else {
                    cur.sessionBlockedOther
                },
                cacheSize = cacheSize,
                cacheHits = cacheHits
            )
        }
    }

    fun updateNetworkAvailability(available: Boolean) {
        _snapshot.update { it.copy(isNetworkAvailable = available) }
    }

    fun updateCacheStats(cacheSize: Int, cacheHits: Long) {
        _snapshot.update { it.copy(cacheSize = cacheSize, cacheHits = cacheHits) }
    }
}
