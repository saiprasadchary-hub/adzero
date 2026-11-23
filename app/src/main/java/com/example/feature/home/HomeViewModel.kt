package com.example.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.FormattingUtils
import com.example.core.database.AnalyticsOverviewProjection
import com.example.core.database.AppRuleEntity
import com.example.core.database.AppTrafficProjection
import com.example.core.database.BlocklistSourceEntity
import com.example.core.database.DnsLogEntity
import com.example.core.database.ServiceEventEntity
import com.example.core.database.TimeBucketProjection
import com.example.core.datastore.AdZeroPreferences
import com.example.core.datastore.AdZeroSettings
import com.example.data.model.DnsProtocol
import com.example.data.model.DnsProviderPresets
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LiveProtectionSnapshot
import com.example.data.model.LiveTrafficSnapshot
import com.example.data.model.PrivacyScoreBreakdown
import com.example.data.repository.AdZeroRepository
import com.example.data.repository.DeviceAppRecord
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.NetworkTrafficMonitorStateHolder
import com.example.domain.usecase.PrivacyScoreCalculator
import com.example.domain.usecase.PrivacyScoreInput
import com.example.service.traffic.NetworkTrafficMonitorService
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class HomeUiState(
    val settings: AdZeroSettings = AdZeroSettings(),
    val liveSnapshot: LiveProtectionSnapshot = LiveProtectionSnapshot(),
    val liveTraffic: LiveTrafficSnapshot = LiveTrafficSnapshot(),
    val hourlyBuckets: List<TimeBucketProjection> = emptyList(),
    val topTrafficApps: List<InstalledAppInfo> = emptyList(),
    val adsBlockedToday: Long = 0L,
    val trackersBlockedToday: Long = 0L,
    val dnsFilteredToday: Long = 0L,
    val appsProtectedCount: Int = 0,
    val blockedAppsCount: Int = 0,
    val activeBlocklistDomains: Int = 0,
    val privacyScore: PrivacyScoreBreakdown = PrivacyScoreCalculator.calculate(
        PrivacyScoreInput(
            isVpnActive = false,
            isAdBlockingEnabled = true,
            isTrackerBlockingEnabled = true,
            isMalwareListEnabled = true,
            isStrictModeEnabled = false,
            dnsProtocol = DnsProtocol.DOH,
            hasActiveBlocklistDomains = false,
            isPrivatePreviewOrBiometricEnabled = false,
            isAutoUpdateEnabled = true
        )
    ),
    val recentBlocked: List<DnsLogEntity> = emptyList(),
    val recentEvents: List<ServiceEventEntity> = emptyList(),
    val unreadGhostCount: Int = 0
)

class HomeViewModel(
    private val appContext: Context,
    private val repository: AdZeroRepository,
    private val preferences: AdZeroPreferences,
    val installedAppsRepository: InstalledAppsRepository,
    private val trafficMonitorStateHolder: NetworkTrafficMonitorStateHolder
) : ViewModel() {

    private val installedAppsFlow = MutableStateFlow<List<DeviceAppRecord>>(emptyList())

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            installedAppsFlow.value = installedAppsRepository.loadInstalledApps()
        }
    }

    fun refreshTrafficStats() {
        NetworkTrafficMonitorService.refreshAppUsage(appContext)
    }

    private val startOfToday = FormattingUtils.startOfTodayMillis()
    private val todayOverviewFlow = repository.observeAnalyticsOverview(startOfToday)
    private val todayHourlyFlow = repository.observeBucketedActivity(
        sinceMillis = startOfToday,
        bucketSizeMillis = TimeUnit.HOURS.toMillis(1)
    )
    private val todayAppDnsTrafficFlow = repository.observeAppTraffic(startOfToday)

    private data class CoreHomeBundle(
        val settings: AdZeroSettings,
        val live: LiveProtectionSnapshot,
        val overview: AnalyticsOverviewProjection,
        val sources: List<BlocklistSourceEntity>,
        val appRules: List<AppRuleEntity>,
        val recentBlocked: List<DnsLogEntity>,
        val recentEvents: List<ServiceEventEntity>,
        val unreadGhost: Int
    )

    private val coreBundleFlow = combine(
        repository.settingsFlow,
        repository.protectionStateHolder.snapshot,
        todayOverviewFlow,
        repository.blocklistSourcesFlow,
        repository.appRulesFlow,
        repository.recentBlockedFlow,
        repository.recentServiceEventsFlow,
        repository.unreadGhostCountFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        CoreHomeBundle(
            settings = values[0] as AdZeroSettings,
            live = values[1] as LiveProtectionSnapshot,
            overview = values[2] as AnalyticsOverviewProjection,
            sources = values[3] as List<BlocklistSourceEntity>,
            appRules = values[4] as List<AppRuleEntity>,
            recentBlocked = values[5] as List<DnsLogEntity>,
            recentEvents = values[6] as List<ServiceEventEntity>,
            unreadGhost = values[7] as Int
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreBundleFlow,
        trafficMonitorStateHolder.snapshot,
        todayHourlyFlow,
        todayAppDnsTrafficFlow,
        installedAppsFlow
    ) { core, traffic, hourlyBuckets, appDnsStats, installedApps ->
        val rulesByPkg = core.appRules.associateBy { it.packageName }
        val dnsByPkg = appDnsStats.associateBy { it.packageName }

        val excludedCount = core.appRules.count { it.dnsExcluded && !it.internetBlocked }
        val blockedAppsCount = core.appRules.count { it.internetBlocked }
        val protectedCount = (installedApps.size - excludedCount).coerceAtLeast(0)
        val totalDomains = core.sources.filter { it.enabled }.sumOf { it.domainCount }

        val score = PrivacyScoreCalculator.calculate(
            PrivacyScoreInput(
                isVpnActive = core.live.isRunning,
                isAdBlockingEnabled = core.settings.adBlockingEnabled,
                isTrackerBlockingEnabled = core.settings.trackerBlockingEnabled,
                isMalwareListEnabled = core.settings.malwareBlockingEnabled,
                isStrictModeEnabled = core.settings.strictModeEnabled,
                dnsProtocol = core.settings.dnsProtocol,
                hasActiveBlocklistDomains = totalDomains > 0,
                isPrivatePreviewOrBiometricEnabled = core.settings.ghostPreviewPrivacyMasked || core.settings.ghostBiometricLockEnabled,
                isAutoUpdateEnabled = core.settings.autoBlocklistUpdateEnabled
            )
        )

        val preset = DnsProviderPresets.byId(core.settings.dnsProviderId)
        val liveWithCurrentDns = if (core.live.isRunning) {
            core.live
        } else {
            core.live.copy(activeDnsLabel = "${preset.name} (${core.settings.dnsProtocol.name})")
        }

        val topApps = installedApps
            .filter { !it.isSystemApp || rulesByPkg.containsKey(it.packageName) || (traffic.perAppUsage[it.packageName]?.totalBytesToday ?: 0L) > 0L }
            .map { rec ->
                val rule = rulesByPkg[rec.packageName]
                val dns = dnsByPkg[rec.packageName]
                val usage = traffic.perAppUsage[rec.packageName]
                InstalledAppInfo(
                    packageName = rec.packageName,
                    appName = rec.appName,
                    isSystemApp = rec.isSystemApp,
                    uid = rec.uid,
                    internetBlocked = rule?.internetBlocked == true,
                    dnsExcluded = rule?.dnsExcluded == true && rule.internetBlocked.not(),
                    requestCount = dns?.totalRequests ?: 0L,
                    blockedCount = dns?.blockedRequests ?: 0L,
                    lastActiveAt = dns?.lastActiveAt ?: 0L,
                    rxBytesToday = usage?.rxBytesToday ?: 0L,
                    txBytesToday = usage?.txBytesToday ?: 0L,
                    liveBytesPerSec = usage?.liveBytesPerSec ?: 0L
                )
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.liveBytesPerSec }
                    .thenByDescending { it.totalBytesToday }
                    .thenByDescending { it.requestCount }
                    .thenByDescending { if (it.internetBlocked) 1 else 0 }
                    .thenBy { it.appName.lowercase() }
            )
            .take(4)

        HomeUiState(
            settings = core.settings,
            liveSnapshot = liveWithCurrentDns,
            liveTraffic = traffic,
            hourlyBuckets = hourlyBuckets,
            topTrafficApps = topApps,
            adsBlockedToday = core.overview.blockedAds,
            trackersBlockedToday = core.overview.blockedTrackers,
            dnsFilteredToday = core.overview.totalRequests,
            appsProtectedCount = protectedCount,
            blockedAppsCount = blockedAppsCount,
            activeBlocklistDomains = totalDomains,
            privacyScore = score,
            recentBlocked = core.recentBlocked.take(6),
            recentEvents = core.recentEvents.take(5),
            unreadGhostCount = core.unreadGhost
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun toggleAppInternetAccess(packageName: String, appName: String, blockInternet: Boolean) {
        viewModelScope.launch {
            repository.updateAppRule(
                packageName = packageName,
                internetBlocked = blockInternet,
                dnsExcluded = false
            )
            if (repository.protectionStateHolder.snapshot.value.isRunning) {
                AdZeroVpnService.rebuildIfRunning(appContext)
            }
            repository.logServiceEvent(
                "VPN",
                "Dashboard firewall toggle for $appName ($packageName): internetBlocked=$blockInternet."
            )
        }
    }

    fun toggleAdBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAdBlocking(enabled)
            repository.reloadFilterEngineSnapshot()
        }
    }

    fun toggleTrackerBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setTrackerBlocking(enabled)
            repository.reloadFilterEngineSnapshot()
        }
    }
}
