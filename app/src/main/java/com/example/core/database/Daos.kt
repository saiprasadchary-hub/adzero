package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class AnalyticsOverviewProjection(
    val totalRequests: Long,
    val blockedRequests: Long,
    val allowedRequests: Long,
    val blockedAds: Long,
    val blockedTrackers: Long,
    val uniqueDomains: Long,
    val activeApps: Long
)

data class AppTrafficProjection(
    val packageName: String,
    val totalRequests: Long,
    val blockedRequests: Long,
    val lastActiveAt: Long
)

data class DomainStatsProjection(
    val domain: String,
    val category: String,
    val matchedRule: String,
    val action: String,
    val hitCount: Long
)

data class TimeBucketProjection(
    val bucketIndex: Long,
    val blockedCount: Long,
    val allowedCount: Long
)

@Dao
interface DnsLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DnsLogEntity>)

    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 200): Flow<List<DnsLogEntity>>

    @Query("SELECT * FROM dns_logs WHERE action = 'BLOCKED' ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentBlocked(limit: Int = 50): Flow<List<DnsLogEntity>>

    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsForExport(): List<DnsLogEntity>

    @Query(
        """
        SELECT 
            COUNT(*) AS totalRequests,
            SUM(CASE WHEN action = 'BLOCKED' THEN 1 ELSE 0 END) AS blockedRequests,
            SUM(CASE WHEN action = 'ALLOWED' THEN 1 ELSE 0 END) AS allowedRequests,
            SUM(CASE WHEN action = 'BLOCKED' AND category = 'AD' THEN 1 ELSE 0 END) AS blockedAds,
            SUM(CASE WHEN action = 'BLOCKED' AND category = 'TRACKER' THEN 1 ELSE 0 END) AS blockedTrackers,
            COUNT(DISTINCT domain) AS uniqueDomains,
            COUNT(DISTINCT CASE WHEN packageName != 'unknown' THEN packageName END) AS activeApps
        FROM dns_logs
        WHERE timestamp >= :sinceMillis
        """
    )
    fun observeOverviewSince(sinceMillis: Long): Flow<AnalyticsOverviewProjection>

    @Query(
        """
        SELECT 
            packageName,
            COUNT(*) AS totalRequests,
            SUM(CASE WHEN action = 'BLOCKED' THEN 1 ELSE 0 END) AS blockedRequests,
            MAX(timestamp) AS lastActiveAt
        FROM dns_logs
        WHERE timestamp >= :sinceMillis
        GROUP BY packageName
        ORDER BY totalRequests DESC
        LIMIT 50
        """
    )
    fun observeAppTrafficSince(sinceMillis: Long): Flow<List<AppTrafficProjection>>

    @Query(
        """
        SELECT 
            domain,
            category,
            matchedRule,
            action,
            COUNT(*) AS hitCount
        FROM dns_logs
        WHERE timestamp >= :sinceMillis AND action = 'BLOCKED'
        GROUP BY domain
        ORDER BY hitCount DESC
        LIMIT :limit
        """
    )
    fun observeTopBlockedDomainsSince(sinceMillis: Long, limit: Int = 25): Flow<List<DomainStatsProjection>>

    @Query(
        """
        SELECT 
            domain,
            category,
            matchedRule,
            action,
            COUNT(*) AS hitCount
        FROM dns_logs
        WHERE timestamp >= :sinceMillis
        GROUP BY domain
        ORDER BY hitCount DESC
        LIMIT :limit
        """
    )
    fun observeTopRequestedDomainsSince(sinceMillis: Long, limit: Int = 25): Flow<List<DomainStatsProjection>>

    @Query(
        """
        SELECT
            (timestamp / :bucketSizeMillis) AS bucketIndex,
            SUM(CASE WHEN action = 'BLOCKED' THEN 1 ELSE 0 END) AS blockedCount,
            SUM(CASE WHEN action = 'ALLOWED' THEN 1 ELSE 0 END) AS allowedCount
        FROM dns_logs
        WHERE timestamp >= :sinceMillis
        GROUP BY bucketIndex
        ORDER BY bucketIndex ASC
        """
    )
    fun observeBucketedActivity(sinceMillis: Long, bucketSizeMillis: Long): Flow<List<TimeBucketProjection>>

    @Query("DELETE FROM dns_logs WHERE timestamp < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long): Int

    @Query("DELETE FROM dns_logs")
    suspend fun clearAll()
}

@Dao
interface CustomRuleDao {
    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules")
    suspend fun getAllSnapshot(): List<CustomRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CustomRuleEntity)

    @Query("DELETE FROM custom_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_rules")
    suspend fun clearAll()
}

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist_sources ORDER BY name ASC")
    fun observeSources(): Flow<List<BlocklistSourceEntity>>

    @Query("SELECT * FROM blocklist_sources")
    suspend fun getSourcesSnapshot(): List<BlocklistSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSources(sources: List<BlocklistSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: BlocklistSourceEntity)

    @Query("UPDATE blocklist_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setSourceEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM blocklist_domains WHERE sourceId = :sourceId")
    suspend fun deleteDomainsForSource(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomains(domains: List<BlocklistDomainEntity>)

    @Transaction
    suspend fun replaceDomainsForSource(sourceId: String, domains: List<BlocklistDomainEntity>) {
        deleteDomainsForSource(sourceId)
        domains.chunked(500).forEach { chunk ->
            insertDomains(chunk)
        }
    }

    @Query(
        """
        SELECT d.domain, d.category
        FROM blocklist_domains d
        INNER JOIN blocklist_sources s ON d.sourceId = s.id
        WHERE s.enabled = 1
        """
    )
    suspend fun getEnabledDomains(): List<DomainCategoryRow>
}

data class DomainCategoryRow(
    val domain: String,
    val category: String
)

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAllSnapshot(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules")
    suspend fun clearAll()
}

@Dao
interface GhostMessageDao {
    @Query("SELECT * FROM ghost_messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<GhostMessageEntity>>

    @Query("SELECT COUNT(*) FROM ghost_messages WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: GhostMessageEntity): Long

    @Query("UPDATE ghost_messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE ghost_messages SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM ghost_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ghost_messages")
    suspend fun clearAll()
}

@Dao
interface GhostScheduleDao {
    @Query("SELECT * FROM ghost_schedules ORDER BY id ASC")
    fun observeAll(): Flow<List<GhostScheduleEntity>>

    @Query("SELECT * FROM ghost_schedules")
    suspend fun getAllSnapshot(): List<GhostScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: GhostScheduleEntity): Long

    @Query("UPDATE ghost_schedules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE ghost_schedules SET lastTriggeredAt = :timestamp WHERE id = :id")
    suspend fun recordTriggered(id: Long, timestamp: Long)

    @Query("DELETE FROM ghost_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ServiceEventDao {
    @Query("SELECT * FROM service_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ServiceEventEntity>>

    @Insert
    suspend fun insert(event: ServiceEventEntity)

    @Query("DELETE FROM service_events WHERE timestamp < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long)

    @Query("DELETE FROM service_events")
    suspend fun clearAll()
}
