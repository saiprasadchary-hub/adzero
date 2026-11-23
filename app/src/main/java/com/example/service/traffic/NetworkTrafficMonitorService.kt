package com.example.service.traffic

import android.app.Service
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.IBinder
import com.example.AdZeroApplication
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.network.TrafficTelemetryEngine
import com.example.data.model.AppDataUsage
import com.example.data.model.NetworkTransportType
import com.example.data.repository.DeviceAppRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time Android network traffic monitoring service.
 * - Samples device download/upload throughput every 1s via Android TrafficStats.
 * - Detects active transport (Wi-Fi / Cellular / Ethernet / Offline) and metered status.
 * - Captures per-second blocked ad/tracker/allowed DNS deltas from ProtectionStateHolder.
 * - Tracks per-app Wi-Fi & Cellular data usage via NetworkStatsManager (when Usage Access is granted),
 *   with graceful per-UID TrafficStats fallback when Usage Access is not yet enabled.
 */
class NetworkTrafficMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val telemetryEngine = TrafficTelemetryEngine(TrafficTelemetryEngine.DEFAULT_WINDOW_SECONDS)
    private var monitorJob: Job? = null

    @Volatile
    private var cachedInstalledApps: List<DeviceAppRecord> = emptyList()

    @Volatile
    private var lastAppListRefreshMs: Long = 0L

    private val appContainer by lazy { (application as AdZeroApplication).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoringAndSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_APP_USAGE -> {
                ensureMonitorLoopRunning()
                serviceScope.launch {
                    refreshPerAppUsageNow(forceReloadApps = true)
                }
                return START_NOT_STICKY
            }
            else -> {
                ensureMonitorLoopRunning()
                return START_NOT_STICKY
            }
        }
    }

    private fun ensureMonitorLoopRunning() {
        if (monitorJob?.isActive == true) return
        appContainer.trafficMonitorStateHolder.setMonitoring(true)
        monitorJob = serviceScope.launch {
            var tickIndex = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val transport = readActiveTransport()
                val totalRx = runCatching { TrafficStats.getTotalRxBytes() }.getOrDefault(TrafficStats.UNSUPPORTED.toLong())
                val totalTx = runCatching { TrafficStats.getTotalTxBytes() }.getOrDefault(TrafficStats.UNSUPPORTED.toLong())

                val protectionSnap = appContainer.protectionStateHolder.snapshot.value
                val tick = telemetryEngine.recordTick(
                    nowMs = now,
                    totalRxBytes = totalRx,
                    totalTxBytes = totalTx,
                    sessionBlockedAds = protectionSnap.sessionBlockedAds,
                    sessionBlockedTrackersAndOther = protectionSnap.sessionBlockedTrackers + protectionSnap.sessionBlockedOther,
                    sessionTotalDnsRequests = protectionSnap.sessionTotalRequests
                )

                val hasUsageAccess = SystemPermissionHelper.hasUsageStatsPermission(this@NetworkTrafficMonitorService)
                appContainer.trafficMonitorStateHolder.updateLiveTick(
                    transportType = transport.type,
                    transportLabel = transport.label,
                    isMetered = transport.isMetered,
                    downloadBytesPerSec = tick.downloadBytesPerSec,
                    uploadBytesPerSec = tick.uploadBytesPerSec,
                    peakDownloadBytesPerSec = tick.peakDownloadBytesPerSec,
                    sessionRxBytes = tick.sessionRxBytes,
                    sessionTxBytes = tick.sessionTxBytes,
                    hasUsageStatsAccess = hasUsageAccess,
                    samples = tick.samples,
                    timestampMs = now
                )

                // Query per-app usage on first tick and every 3 seconds thereafter
                if (tickIndex % 3 == 0) {
                    refreshPerAppUsageNow(forceReloadApps = (now - lastAppListRefreshMs) > APP_LIST_CACHE_MS)
                }
                tickIndex++
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshPerAppUsageNow(forceReloadApps: Boolean) {
        val now = System.currentTimeMillis()
        if (forceReloadApps || cachedInstalledApps.isEmpty()) {
            cachedInstalledApps = appContainer.installedAppsRepository.loadInstalledApps()
            lastAppListRefreshMs = now
        }
        val apps = cachedInstalledApps
        if (apps.isEmpty()) return

        val hasUsageAccess = SystemPermissionHelper.hasUsageStatsPermission(this)
        val rawMap = if (hasUsageAccess) {
            queryNetworkStatsPerApp(apps, FormattingUtils.startOfTodayMillis(), now)
        } else {
            queryTrafficStatsFallbackPerApp(apps)
        }

        val withRates = telemetryEngine.computePerAppLiveRates(now, rawMap)
        appContainer.trafficMonitorStateHolder.updatePerAppUsage(
            hasUsageStatsAccess = hasUsageAccess,
            usageByPackage = withRates
        )
    }

    @Suppress("DEPRECATION")
    private fun queryNetworkStatsPerApp(
        apps: List<DeviceAppRecord>,
        startMillis: Long,
        endMillis: Long
    ): Map<String, AppDataUsage> {
        val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return queryTrafficStatsFallbackPerApp(apps)

        val rxByUid = HashMap<Int, Long>()
        val txByUid = HashMap<Int, Long>()

        fun accumulateNetworkType(networkType: Int) {
            runCatching {
                val stats = nsm.querySummary(networkType, null, startMillis, endMillis)
                val bucket = NetworkStats.Bucket()
                try {
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val uid = bucket.uid
                        if (uid <= 0) continue
                        rxByUid[uid] = (rxByUid[uid] ?: 0L) + bucket.rxBytes.coerceAtLeast(0L)
                        txByUid[uid] = (txByUid[uid] ?: 0L) + bucket.txBytes.coerceAtLeast(0L)
                    }
                } finally {
                    stats.close()
                }
            }
        }

        accumulateNetworkType(ConnectivityManager.TYPE_WIFI)
        accumulateNetworkType(ConnectivityManager.TYPE_MOBILE)

        val result = LinkedHashMap<String, AppDataUsage>(apps.size)
        for (app in apps) {
            val rx = rxByUid[app.uid] ?: 0L
            val tx = txByUid[app.uid] ?: 0L
            result[app.packageName] = AppDataUsage(
                packageName = app.packageName,
                uid = app.uid,
                rxBytesToday = rx,
                txBytesToday = tx
            )
        }
        return result
    }

    private fun queryTrafficStatsFallbackPerApp(apps: List<DeviceAppRecord>): Map<String, AppDataUsage> {
        val result = LinkedHashMap<String, AppDataUsage>(apps.size)
        for (app in apps) {
            val rawRx = runCatching { TrafficStats.getUidRxBytes(app.uid) }.getOrDefault(-1L)
            val rawTx = runCatching { TrafficStats.getUidTxBytes(app.uid) }.getOrDefault(-1L)
            val rx = if (rawRx > 0L) rawRx else 0L
            val tx = if (rawTx > 0L) rawTx else 0L
            result[app.packageName] = AppDataUsage(
                packageName = app.packageName,
                uid = app.uid,
                rxBytesToday = rx,
                txBytesToday = tx
            )
        }
        return result
    }

    private data class TransportSnapshot(
        val type: NetworkTransportType,
        val label: String,
        val isMetered: Boolean
    )

    private fun readActiveTransport(): TransportSnapshot {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return TransportSnapshot(NetworkTransportType.OFFLINE, "Offline", false)
        val active = cm.activeNetwork
            ?: return TransportSnapshot(NetworkTransportType.OFFLINE, "Offline", false)
        val caps = cm.getNetworkCapabilities(active)
            ?: return TransportSnapshot(NetworkTransportType.OFFLINE, "Offline", false)

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return TransportSnapshot(NetworkTransportType.OFFLINE, "Offline", false)
        }

        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                TransportSnapshot(NetworkTransportType.WIFI, "Wi-Fi", metered)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                TransportSnapshot(NetworkTransportType.CELLULAR, "Cellular", metered)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                TransportSnapshot(NetworkTransportType.ETHERNET, "Ethernet", metered)
            else ->
                TransportSnapshot(NetworkTransportType.OTHER, "Connected", metered)
        }
    }

    private fun stopMonitoringAndSelf() {
        monitorJob?.cancel()
        monitorJob = null
        appContainer.trafficMonitorStateHolder.setMonitoring(false)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        monitorJob = null
        appContainer.trafficMonitorStateHolder.setMonitoring(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.adzero.TRAFFIC_MONITOR_START"
        const val ACTION_STOP = "com.example.adzero.TRAFFIC_MONITOR_STOP"
        const val ACTION_REFRESH_APP_USAGE = "com.example.adzero.TRAFFIC_MONITOR_REFRESH_APPS"

        private const val SAMPLE_INTERVAL_MS = 1_000L
        private const val APP_LIST_CACHE_MS = 60_000L

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, NetworkTrafficMonitorService::class.java).apply {
                    action = ACTION_START
                }
                context.startService(intent)
            }
        }

        fun refreshAppUsage(context: Context) {
            runCatching {
                val intent = Intent(context, NetworkTrafficMonitorService::class.java).apply {
                    action = ACTION_REFRESH_APP_USAGE
                }
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, NetworkTrafficMonitorService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }
}
