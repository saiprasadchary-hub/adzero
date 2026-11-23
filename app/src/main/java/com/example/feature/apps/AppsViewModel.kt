package com.example.feature.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.AppRuleEntity
import com.example.core.database.AppTrafficProjection
import com.example.core.datastore.AdZeroPreferences
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LiveProtectionSnapshot
import com.example.data.model.LiveTrafficSnapshot
import com.example.data.model.VpnEnforcementMode
import com.example.data.repository.AdZeroRepository
import com.example.data.repository.DeviceAppRecord
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.NetworkTrafficMonitorStateHolder
import com.example.service.traffic.NetworkTrafficMonitorService
import com.example.service.vpn.AdZeroVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class AppFilterTab(val label: String) {
    ALL("All Apps"),
    BLOCKED("Blocked"),
    EXCLUDED("DNS Excluded"),
    ALLOWED("Allowed"),
    RECENT("Active Data")
}

enum class AppSortOrder(val label: String) {
    DATA_USAGE("Data Usage"),
    ACTIVITY("DNS Activity"),
    NAME("Name (A–Z)");

    fun next(): AppSortOrder = when (this) {
        DATA_USAGE -> ACTIVITY
        ACTIVITY -> NAME
        NAME -> DATA_USAGE
    }
}

data class AppsUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: AppFilterTab = AppFilterTab.ALL,
    val sortOrder: AppSortOrder = AppSortOrder.DATA_USAGE,
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true,
    val isVpnActive: Boolean = false,
    val enforcementMode: VpnEnforcementMode = VpnEnforcementMode.STANDARD_DNS_FILTERING,
    val hasUsageStatsAccess: Boolean = false,
    val isTrafficMonitoringActive: Boolean = false,
    val totalTrackedRxBytes: Long = 0L,
    val totalTrackedTxBytes: Long = 0L,
    val statusBannerMessage: String? = null
)

class AppsViewModel(
    private val appContext: Context,
    private val repository: AdZeroRepository,
    private val preferences: AdZeroPreferences,
    val installedAppsRepository: InstalledAppsRepository,
    private val trafficMonitorStateHolder: NetworkTrafficMonitorStateHolder
) : ViewModel() {

    private val rawAppsFlow = MutableStateFlow<List<DeviceAppRecord>>(emptyList())
    private val isLoadingFlow = MutableStateFlow(true)
    private val searchQueryFlow = MutableStateFlow("")
    private val filterTabFlow = MutableStateFlow(AppFilterTab.ALL)
    private val sortOrderFlow = MutableStateFlow(AppSortOrder.DATA_USAGE)
    private val showSystemAppsFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    private val recentTrafficFlow = repository.observeAppTraffic(
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    )

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isLoadingFlow.value = true
            rawAppsFlow.value = installedAppsRepository.loadInstalledApps()
            isLoadingFlow.value = false
            NetworkTrafficMonitorService.refreshAppUsage(appContext)
        }
    }

    private data class FilterCriteria(
        val query: String,
        val filter: AppFilterTab,
        val sort: AppSortOrder,
        val showSystem: Boolean,
        val loading: Boolean,
        val banner: String?
    )

    private val criteriaFlow = combine(
        combine(searchQueryFlow, filterTabFlow, sortOrderFlow) { q, f, s -> Triple(q, f, s) },
        combine(showSystemAppsFlow, isLoadingFlow, statusMessageFlow) { sys, load, banner -> Triple(sys, load, banner) }
    ) { left, right ->
        FilterCriteria(
            query = left.first,
            filter = left.second,
            sort = left.third,
            showSystem = right.first,
            loading = right.second,
            banner = right.third
        )
    }

    val uiState: StateFlow<AppsUiState> = combine(
        rawAppsFlow,
        repository.appRulesFlow,
        recentTrafficFlow,
        trafficMonitorStateHolder.snapshot,
        repository.protectionStateHolder.snapshot,
        criteriaFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val installed = values[0] as List<DeviceAppRecord>
        @Suppress("UNCHECKED_CAST")
        val rules = (values[1] as List<AppRuleEntity>).associateBy { it.packageName }
        @Suppress("UNCHECKED_CAST")
        val dnsTraffic = (values[2] as List<AppTrafficProjection>).associateBy { it.packageName }
        val liveTraffic = values[3] as LiveTrafficSnapshot
        val vpnSnap = values[4] as LiveProtectionSnapshot
        val criteria = values[5] as FilterCriteria

        val merged = installed
            .filter { criteria.showSystem || !it.isSystemApp || rules.containsKey(it.packageName) }
            .map { rec ->
                val rule = rules[rec.packageName]
                val dns = dnsTraffic[rec.packageName]
                val usage = liveTraffic.perAppUsage[rec.packageName]
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
            .filter { app ->
                if (criteria.query.isBlank()) true
                else app.appName.contains(criteria.query, ignoreCase = true) ||
                    app.packageName.contains(criteria.query, ignoreCase = true)
            }
            .filter { app ->
                when (criteria.filter) {
                    AppFilterTab.ALL -> true
                    AppFilterTab.BLOCKED -> app.internetBlocked
                    AppFilterTab.EXCLUDED -> app.dnsExcluded
                    AppFilterTab.ALLOWED -> !app.internetBlocked
                    AppFilterTab.RECENT -> app.totalBytesToday > 0L || app.lastActiveAt > 0L
                }
            }
            .let { list ->
                when (criteria.sort) {
                    AppSortOrder.NAME -> list.sortedBy { it.appName.lowercase() }
                    AppSortOrder.ACTIVITY -> list.sortedWith(
                        compareByDescending<InstalledAppInfo> { it.requestCount }
                            .thenByDescending { it.totalBytesToday }
                            .thenBy { it.appName.lowercase() }
                    )
                    AppSortOrder.DATA_USAGE -> list.sortedWith(
                        compareByDescending<InstalledAppInfo> { it.liveBytesPerSec }
                            .thenByDescending { it.totalBytesToday }
                            .thenByDescending { it.requestCount }
                            .thenBy { it.appName.lowercase() }
                    )
                }
            }

        AppsUiState(
            apps = merged,
            searchQuery = criteria.query,
            selectedFilter = criteria.filter,
            sortOrder = criteria.sort,
            showSystemApps = criteria.showSystem,
            isLoading = criteria.loading,
            isVpnActive = vpnSnap.isRunning,
            enforcementMode = vpnSnap.enforcementMode,
            hasUsageStatsAccess = liveTraffic.hasUsageStatsAccess,
            isTrafficMonitoringActive = liveTraffic.isMonitoring,
            totalTrackedRxBytes = liveTraffic.totalTrackedAppRxBytes,
            totalTrackedTxBytes = liveTraffic.totalTrackedAppTxBytes,
            statusBannerMessage = criteria.banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppsUiState()
    )

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun setFilter(tab: AppFilterTab) {
        filterTabFlow.value = tab
    }

    fun setSortOrder(order: AppSortOrder) {
        sortOrderFlow.value = order
    }

    fun setShowSystemApps(show: Boolean) {
        showSystemAppsFlow.value = show
    }

    fun updateAppFirewall(packageName: String, appName: String, internetBlocked: Boolean, dnsExcluded: Boolean) {
        viewModelScope.launch {
            repository.updateAppRule(packageName, internetBlocked, dnsExcluded)
            val isRunning = repository.protectionStateHolder.snapshot.value.isRunning
            if (isRunning) {
                AdZeroVpnService.rebuildIfRunning(appContext)
                statusMessageFlow.value = "Updated rule for $appName and rebuilt local VPN routing."
            } else {
                statusMessageFlow.value = "Saved rule for $appName. Turn ON Protection to enforce firewall rules."
            }
            repository.logServiceEvent(
                "VPN",
                "App rule changed for $packageName (internetBlocked=$internetBlocked, dnsExcluded=$dnsExcluded)."
            )
        }
    }

    fun toggleStrictPacketIsolation(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setGhostStrictIsolation(enabled)
            repository.reloadFilterEngineSnapshot()
            AdZeroVpnService.rebuildIfRunning(appContext)
            statusMessageFlow.value = if (enabled) {
                "Strict Packet Isolation enabled. Blocked apps are null-routed at the kernel VPN layer."
            } else {
                "Switched to Standard DNS Filtering mode."
            }
        }
    }

    fun clearBanner() {
        statusMessageFlow.value = null
    }
}
