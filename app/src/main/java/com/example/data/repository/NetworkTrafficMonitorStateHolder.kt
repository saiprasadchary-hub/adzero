package com.example.data.repository

import com.example.data.model.AppDataUsage
import com.example.data.model.LiveTrafficSample
import com.example.data.model.LiveTrafficSnapshot
import com.example.data.model.NetworkTransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reactive runtime state holder fed exclusively by NetworkTrafficMonitorService.
 */
class NetworkTrafficMonitorStateHolder {

    private val _snapshot = MutableStateFlow(LiveTrafficSnapshot())
    val snapshot: StateFlow<LiveTrafficSnapshot> = _snapshot.asStateFlow()

    fun setMonitoring(active: Boolean) {
        _snapshot.update { it.copy(isMonitoring = active) }
    }

    fun updateLiveTick(
        transportType: NetworkTransportType,
        transportLabel: String,
        isMetered: Boolean,
        downloadBytesPerSec: Long,
        uploadBytesPerSec: Long,
        peakDownloadBytesPerSec: Long,
        sessionRxBytes: Long,
        sessionTxBytes: Long,
        hasUsageStatsAccess: Boolean,
        samples: List<LiveTrafficSample>,
        timestampMs: Long
    ) {
        _snapshot.update { cur ->
            cur.copy(
                isMonitoring = true,
                transportType = transportType,
                transportLabel = transportLabel,
                isMetered = isMetered,
                downloadBytesPerSec = downloadBytesPerSec,
                uploadBytesPerSec = uploadBytesPerSec,
                peakDownloadBytesPerSec = peakDownloadBytesPerSec,
                sessionRxBytes = sessionRxBytes,
                sessionTxBytes = sessionTxBytes,
                hasUsageStatsAccess = hasUsageStatsAccess,
                samples = samples,
                lastUpdatedAt = timestampMs
            )
        }
    }

    fun updatePerAppUsage(
        hasUsageStatsAccess: Boolean,
        usageByPackage: Map<String, AppDataUsage>
    ) {
        var totalRx = 0L
        var totalTx = 0L
        usageByPackage.values.forEach {
            totalRx += it.rxBytesToday
            totalTx += it.txBytesToday
        }
        _snapshot.update { cur ->
            cur.copy(
                hasUsageStatsAccess = hasUsageStatsAccess,
                perAppUsage = usageByPackage,
                totalTrackedAppRxBytes = totalRx,
                totalTrackedAppTxBytes = totalTx
            )
        }
    }
}
