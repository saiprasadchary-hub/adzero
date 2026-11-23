package com.example.data.repository

import com.example.core.database.AdZeroDatabase
import com.example.core.database.AnalyticsOverviewProjection
import com.example.core.database.AppRuleEntity
import com.example.core.database.AppTrafficProjection
import com.example.core.database.BlocklistSourceEntity
import com.example.core.database.CustomRuleEntity
import com.example.core.database.DnsLogEntity
import com.example.core.database.DomainStatsProjection
import com.example.core.database.GhostMessageEntity
import com.example.core.database.GhostScheduleEntity
import com.example.core.database.ServiceEventEntity
import com.example.core.database.TimeBucketProjection
import com.example.core.datastore.AdZeroPreferences
import com.example.core.datastore.AdZeroSettings
import com.example.core.network.BundledBlocklists
import com.example.core.network.DnsFilterEngine
import com.example.core.network.FilterConfigSnapshot
import com.example.core.network.UpstreamDnsConfig
import com.example.core.network.UpstreamDnsResolver
import com.example.core.security.CryptoManager
import com.example.core.security.EncryptedPayload
import com.example.data.model.DnsProbeResult
import com.example.data.model.DomainCategory
import com.example.data.model.GhostMessage
import com.example.data.model.GhostSchedule
import com.example.data.model.RuleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AdZeroRepository(
    private val database: AdZeroDatabase,
    private val preferences: AdZeroPreferences,
    private val cryptoManager: CryptoManager,
    val filterEngine: DnsFilterEngine,
    val upstreamResolver: UpstreamDnsResolver,
    val logBatcher: DnsLogBatcher,
    val protectionStateHolder: ProtectionStateHolder
) {
    val settingsFlow: Flow<AdZeroSettings> = preferences.settingsFlow

    val customRulesFlow: Flow<List<CustomRuleEntity>> = database.customRuleDao().observeAll()
    val blocklistSourcesFlow: Flow<List<BlocklistSourceEntity>> = database.blocklistDao().observeSources()
    val appRulesFlow: Flow<List<AppRuleEntity>> = database.appRuleDao().observeAll()
    val recentLogsFlow: Flow<List<DnsLogEntity>> = database.dnsLogDao().observeRecentLogs(250)
    val recentBlockedFlow: Flow<List<DnsLogEntity>> = database.dnsLogDao().observeRecentBlocked(40)
    val recentServiceEventsFlow: Flow<List<ServiceEventEntity>> = database.serviceEventDao().observeRecent(60)
    val unreadGhostCountFlow: Flow<Int> = database.ghostMessageDao().observeUnreadCount()

    val ghostMessagesFlow: Flow<List<GhostMessage>> = database.ghostMessageDao().observeAll().map { entities ->
        entities.map { entity ->
            val sender = cryptoManager.decrypt(EncryptedPayload(entity.senderCiphertext, entity.senderIv))
            val preview = cryptoManager.decrypt(EncryptedPayload(entity.previewCiphertext, entity.previewIv))
            GhostMessage(
                id = entity.id,
                senderName = sender,
                messagePreview = preview,
                sourcePackage = entity.sourcePackage,
                sourceLabel = if (entity.sourcePackage == DnsFilterEngine.WHATSAPP_BUSINESS_PKG) {
                    "WhatsApp Business"
                } else {
                    "WhatsApp"
                },
                timestamp = entity.timestamp,
                isRead = entity.isRead
            )
        }
    }

    val ghostSchedulesFlow: Flow<List<GhostSchedule>> = database.ghostScheduleDao().observeAll().map { list ->
        list.map { e ->
            GhostSchedule(
                id = e.id,
                label = e.label,
                startMinuteOfDay = e.startMinuteOfDay,
                endMinuteOfDay = e.endMinuteOfDay,
                daysMask = e.daysMask,
                enabled = e.enabled,
                lastTriggeredAt = e.lastTriggeredAt
            )
        }
    }

    suspend fun initializeDefaultsIfNeeded() = withContext(Dispatchers.IO) {
        val existing = database.blocklistDao().getSourcesSnapshot()
        if (existing.isEmpty()) {
            database.blocklistDao().upsertSources(BundledBlocklists.defaultSources)
            BundledBlocklists.defaultSources.forEach { source ->
                val entities = BundledBlocklists.bundledEntitiesForSource(source.id)
                database.blocklistDao().replaceDomainsForSource(source.id, entities)
            }
        }
        val existingSchedules = database.ghostScheduleDao().getAllSnapshot()
        if (existingSchedules.isEmpty()) {
            // Seed helpful default templates in DISABLED state so AdZero never silently alters connectivity
            database.ghostScheduleDao().upsert(
                GhostScheduleEntity(
                    label = "Night Focus (10 PM – 8 AM)",
                    startMinuteOfDay = 22 * 60,
                    endMinuteOfDay = 8 * 60,
                    daysMask = GhostSchedule.ALL_DAYS_MASK,
                    enabled = false
                )
            )
            database.ghostScheduleDao().upsert(
                GhostScheduleEntity(
                    label = "Work Hours (Weekdays 9 AM – 5 PM)",
                    startMinuteOfDay = 9 * 60,
                    endMinuteOfDay = 17 * 60,
                    daysMask = GhostSchedule.WEEKDAYS_MASK,
                    enabled = false
                )
            )
        }
        reloadFilterEngineSnapshot()
        pruneOldLogs()
    }

    suspend fun reloadFilterEngineSnapshot() = withContext(Dispatchers.IO) {
        val settings = preferences.settingsFlow.first()
        val enabledDomains = database.blocklistDao().getEnabledDomains()
        val domainMap = HashMap<String, DomainCategory>(enabledDomains.size + 128)
        enabledDomains.forEach { row ->
            val category = runCatching { DomainCategory.valueOf(row.category) }.getOrDefault(DomainCategory.AD)
            domainMap[row.domain] = category
        }

        val customRules = database.customRuleDao().getAllSnapshot()
        val allowExact = HashSet<String>()
        val allowWildcardRegex = mutableListOf<Regex>()
        val allowWildcardPatterns = mutableListOf<String>()
        val blockExact = HashSet<String>()
        val blockWildcardRegex = mutableListOf<Regex>()
        val blockWildcardPatterns = mutableListOf<String>()

        customRules.forEach { rule ->
            val pattern = DnsFilterEngine.normalizeDomain(rule.domainPattern)
            if (rule.ruleType == RuleType.ALLOW.name) {
                if (rule.isWildcard || pattern.contains('*')) {
                    allowWildcardRegex.add(DnsFilterEngine.compileWildcard(pattern))
                    allowWildcardPatterns.add(pattern)
                } else {
                    allowExact.add(pattern)
                }
            } else {
                if (rule.isWildcard || pattern.contains('*')) {
                    blockWildcardRegex.add(DnsFilterEngine.compileWildcard(pattern))
                    blockWildcardPatterns.add(pattern)
                } else {
                    blockExact.add(pattern)
                }
            }
        }

        val appRules = database.appRuleDao().getAllSnapshot()
        val blockedApps = appRules.filter { it.internetBlocked }.map { it.packageName }.toSet()
        val excludedApps = appRules.filter { it.dnsExcluded && !it.internetBlocked }.map { it.packageName }.toSet()

        val snapshot = FilterConfigSnapshot(
            adBlockingEnabled = settings.adBlockingEnabled,
            trackerBlockingEnabled = settings.trackerBlockingEnabled,
            malwareBlockingEnabled = settings.malwareBlockingEnabled,
            strictModeEnabled = settings.strictModeEnabled,
            ghostModeEnabled = settings.ghostModeEnabled,
            blockedDomains = domainMap,
            strictDomains = BundledBlocklists.bundledStrictDomains.toSet(),
            customBlockExact = blockExact,
            customBlockWildcards = blockWildcardRegex,
            customBlockWildcardPatterns = blockWildcardPatterns,
            allowExact = allowExact,
            allowWildcards = allowWildcardRegex,
            allowWildcardPatterns = allowWildcardPatterns,
            blockedAppPackages = blockedApps,
            dnsExcludedAppPackages = excludedApps
        )
        filterEngine.updateConfig(snapshot)
    }

    suspend fun addCustomRule(pattern: String, type: RuleType): Result<Unit> = withContext(Dispatchers.IO) {
        val cleaned = DnsFilterEngine.normalizeDomain(pattern)
        if (cleaned.length < 3 || !cleaned.contains('.') || cleaned.contains(' ')) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid domain or wildcard (e.g., ads.example.com or *.tracker.net)."))
        }
        val isWildcard = cleaned.contains('*')
        database.customRuleDao().upsert(
            CustomRuleEntity(
                domainPattern = cleaned,
                ruleType = type.name,
                category = if (type == RuleType.ALLOW) DomainCategory.ALLOWED.name else DomainCategory.CUSTOM_BLOCK.name,
                isWildcard = isWildcard
            )
        )
        reloadFilterEngineSnapshot()
        Result.success(Unit)
    }

    suspend fun deleteCustomRule(id: Long) = withContext(Dispatchers.IO) {
        database.customRuleDao().deleteById(id)
        reloadFilterEngineSnapshot()
    }

    suspend fun setBlocklistSourceEnabled(sourceId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        database.blocklistDao().setSourceEnabled(sourceId, enabled)
        reloadFilterEngineSnapshot()
    }

    suspend fun updateAppRule(packageName: String, internetBlocked: Boolean, dnsExcluded: Boolean) = withContext(Dispatchers.IO) {
        // Mutual clarity: if internet is blocked, DNS exclusion is off
        val normalizedExcluded = if (internetBlocked) false else dnsExcluded
        database.appRuleDao().upsert(
            AppRuleEntity(
                packageName = packageName,
                internetBlocked = internetBlocked,
                dnsExcluded = normalizedExcluded,
                updatedAt = System.currentTimeMillis()
            )
        )
        reloadFilterEngineSnapshot()
    }

    suspend fun saveEncryptedGhostMessage(
        senderName: String,
        messagePreview: String,
        sourcePackage: String,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val encSender = cryptoManager.encrypt(senderName.ifBlank { "Unknown Sender" })
        val encPreview = cryptoManager.encrypt(messagePreview.ifBlank { "(No text preview available)" })
        val entity = GhostMessageEntity(
            senderCiphertext = encSender.ciphertextBase64,
            senderIv = encSender.ivBase64,
            previewCiphertext = encPreview.ciphertextBase64,
            previewIv = encPreview.ivBase64,
            contactHash = cryptoManager.hashIdentifier(senderName),
            sourcePackage = sourcePackage,
            timestamp = timestamp,
            isRead = false
        )
        database.ghostMessageDao().insert(entity)
    }

    suspend fun markGhostMessageRead(id: Long) = withContext(Dispatchers.IO) {
        database.ghostMessageDao().markRead(id)
    }

    suspend fun markAllGhostMessagesRead() = withContext(Dispatchers.IO) {
        database.ghostMessageDao().markAllRead()
    }

    suspend fun deleteGhostMessage(id: Long) = withContext(Dispatchers.IO) {
        database.ghostMessageDao().deleteById(id)
    }

    suspend fun clearGhostInbox() = withContext(Dispatchers.IO) {
        database.ghostMessageDao().clearAll()
    }

    suspend fun upsertGhostSchedule(schedule: GhostSchedule): Long = withContext(Dispatchers.IO) {
        database.ghostScheduleDao().upsert(
            GhostScheduleEntity(
                id = schedule.id,
                label = schedule.label.ifBlank { "Ghost Schedule" },
                startMinuteOfDay = schedule.startMinuteOfDay,
                endMinuteOfDay = schedule.endMinuteOfDay,
                daysMask = schedule.daysMask,
                enabled = schedule.enabled,
                lastTriggeredAt = schedule.lastTriggeredAt
            )
        )
    }

    suspend fun setGhostScheduleEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        database.ghostScheduleDao().setEnabled(id, enabled)
    }

    suspend fun deleteGhostSchedule(id: Long) = withContext(Dispatchers.IO) {
        database.ghostScheduleDao().deleteById(id)
    }

    suspend fun logServiceEvent(eventType: String, detail: String) = withContext(Dispatchers.IO) {
        database.serviceEventDao().insert(
            ServiceEventEntity(
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                detail = detail
            )
        )
    }

    fun observeAnalyticsOverview(sinceMillis: Long): Flow<AnalyticsOverviewProjection> =
        database.dnsLogDao().observeOverviewSince(sinceMillis)

    fun observeAppTraffic(sinceMillis: Long): Flow<List<AppTrafficProjection>> =
        database.dnsLogDao().observeAppTrafficSince(sinceMillis)

    fun observeTopBlockedDomains(sinceMillis: Long): Flow<List<DomainStatsProjection>> =
        database.dnsLogDao().observeTopBlockedDomainsSince(sinceMillis)

    fun observeTopRequestedDomains(sinceMillis: Long): Flow<List<DomainStatsProjection>> =
        database.dnsLogDao().observeTopRequestedDomainsSince(sinceMillis)

    fun observeBucketedActivity(sinceMillis: Long, bucketSizeMillis: Long): Flow<List<TimeBucketProjection>> =
        database.dnsLogDao().observeBucketedActivity(sinceMillis, bucketSizeMillis)

    suspend fun runDnsProbe(): DnsProbeResult {
        val settings = preferences.settingsFlow.first()
        val cfg = UpstreamDnsConfig.fromPreferences(
            providerId = settings.dnsProviderId,
            protocol = settings.dnsProtocol,
            customIpv4 = settings.customDnsIpv4,
            customDot = settings.customDotHostname,
            customDoh = settings.customDohUrl
        )
        val result = upstreamResolver.probeDns(cfg)
        preferences.recordDnsProbe(result.success, result.latencyMs, result.testedAt)
        logServiceEvent(
            "DNS",
            if (result.success) {
                "DNS probe succeeded: ${result.providerName} (${result.protocol.name}) in ${result.latencyMs}ms"
            } else {
                "DNS probe failed: ${result.errorMessage}"
            }
        )
        return result
    }

    suspend fun buildAnalyticsCsv(): String = withContext(Dispatchers.IO) {
        logBatcher.flushNow()
        val logs = database.dnsLogDao().getAllLogsForExport()
        buildString {
            appendLine("timestamp,domain,package_name,action,category,matched_rule,latency_ms")
            logs.forEach { row ->
                append(row.timestamp).append(',')
                append(csvEscape(row.domain)).append(',')
                append(csvEscape(row.packageName)).append(',')
                append(csvEscape(row.action)).append(',')
                append(csvEscape(row.category)).append(',')
                append(csvEscape(row.matchedRule)).append(',')
                appendLine(row.latencyMs)
            }
        }
    }

    private fun csvEscape(value: String): String {
        val safe = value.replace("\"", "\"\"")
        return "\"$safe\""
    }

    suspend fun pruneOldLogs() = withContext(Dispatchers.IO) {
        val days = preferences.settingsFlow.first().analyticsRetentionDays
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        database.dnsLogDao().pruneOlderThan(cutoff)
        database.serviceEventDao().pruneOlderThan(cutoff)
    }

    suspend fun clearDnsLogs() = withContext(Dispatchers.IO) {
        logBatcher.flushNow()
        database.dnsLogDao().clearAll()
        logServiceEvent("PRIVACY", "Cleared local DNS activity history.")
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        logBatcher.flushNow()
        database.dnsLogDao().clearAll()
        database.ghostMessageDao().clearAll()
        database.serviceEventDao().clearAll()
        filterEngine.clearCache()
        protectionStateHolder.updateCacheStats(0, 0L)
        preferences.resetYouTubeSkipCount()
        logServiceEvent("PRIVACY", "Cleared local DNS logs, Ghost Inbox previews, and DNS cache.")
    }
}
