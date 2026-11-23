package com.example.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.AnalyticsOverviewProjection
import com.example.core.database.AppTrafficProjection
import com.example.core.database.DnsLogEntity
import com.example.core.database.DomainStatsProjection
import com.example.core.database.TimeBucketProjection
import com.example.core.datastore.AdZeroPreferences
import com.example.data.model.RuleType
import com.example.data.repository.AdZeroRepository
import com.example.data.repository.InstalledAppsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class AnalyticsRange(val label: String, val durationMillis: Long, val bucketMillis: Long) {
    HOURLY("24h Hourly", TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(1)),
    DAILY("7d Daily", TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(1)),
    WEEKLY("30d Trend", TimeUnit.DAYS.toMillis(30), TimeUnit.DAYS.toMillis(3))
}

data class ActivityUiState(
    val selectedRange: AnalyticsRange = AnalyticsRange.HOURLY,
    val overview: AnalyticsOverviewProjection = AnalyticsOverviewProjection(0, 0, 0, 0, 0, 0, 0),
    val buckets: List<TimeBucketProjection> = emptyList(),
    val appTraffic: List<AppTrafficProjection> = emptyList(),
    val topBlockedDomains: List<DomainStatsProjection> = emptyList(),
    val topRequestedDomains: List<DomainStatsProjection> = emptyList(),
    val recentLogs: List<DnsLogEntity> = emptyList(),
    val logFilterAction: String = "ALL", // ALL, BLOCKED, ALLOWED
    val logSearchQuery: String = "",
    val retentionDays: Int = 7
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val repository: AdZeroRepository,
    private val preferences: AdZeroPreferences,
    val installedAppsRepository: InstalledAppsRepository
) : ViewModel() {

    private val rangeFlow = MutableStateFlow(AnalyticsRange.HOURLY)
    private val actionFilterFlow = MutableStateFlow("ALL")
    private val searchFlow = MutableStateFlow("")

    private val rangeDataFlow = rangeFlow.flatMapLatest { range ->
        val since = System.currentTimeMillis() - range.durationMillis
        combine(
            repository.observeAnalyticsOverview(since),
            repository.observeBucketedActivity(since, range.bucketMillis),
            repository.observeAppTraffic(since),
            repository.observeTopBlockedDomains(since),
            repository.observeTopRequestedDomains(since)
        ) { overview, buckets, apps, topBlocked, topRequested ->
            RangeBundle(range, overview, buckets, apps, topBlocked, topRequested)
        }
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        rangeDataFlow,
        repository.recentLogsFlow,
        repository.settingsFlow,
        actionFilterFlow,
        searchFlow
    ) { bundle, logs, settings, actionFilter, query ->
        val filteredLogs = logs.filter { entry ->
            val matchesAction = actionFilter == "ALL" || entry.action == actionFilter
            val matchesQuery = query.isBlank() ||
                entry.domain.contains(query, ignoreCase = true) ||
                entry.packageName.contains(query, ignoreCase = true) ||
                entry.matchedRule.contains(query, ignoreCase = true)
            matchesAction && matchesQuery
        }

        ActivityUiState(
            selectedRange = bundle.range,
            overview = bundle.overview,
            buckets = bundle.buckets,
            appTraffic = bundle.apps,
            topBlockedDomains = bundle.topBlocked,
            topRequestedDomains = bundle.topRequested,
            recentLogs = filteredLogs,
            logFilterAction = actionFilter,
            logSearchQuery = query,
            retentionDays = settings.analyticsRetentionDays
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityUiState()
    )

    fun setRange(range: AnalyticsRange) {
        rangeFlow.value = range
    }

    fun setActionFilter(action: String) {
        actionFilterFlow.value = action
    }

    fun setSearchQuery(query: String) {
        searchFlow.value = query
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            preferences.setAnalyticsRetentionDays(days)
            repository.pruneOldLogs()
        }
    }

    fun quickAllowDomain(domain: String) {
        viewModelScope.launch {
            repository.addCustomRule(domain, RuleType.ALLOW)
        }
    }

    fun quickBlockDomain(domain: String) {
        viewModelScope.launch {
            repository.addCustomRule(domain, RuleType.BLOCK)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearDnsLogs()
        }
    }

    suspend fun generateCsvContent(): String = repository.buildAnalyticsCsv()

    private data class RangeBundle(
        val range: AnalyticsRange,
        val overview: AnalyticsOverviewProjection,
        val buckets: List<TimeBucketProjection>,
        val apps: List<AppTrafficProjection>,
        val topBlocked: List<DomainStatsProjection>,
        val topRequested: List<DomainStatsProjection>
    )
}
