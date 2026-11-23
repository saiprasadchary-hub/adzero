package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dns_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["domain"]),
        Index(value = ["packageName"]),
        Index(value = ["action"])
    ]
)
data class DnsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val domain: String,
    val packageName: String, // "unknown" when OS cannot attribute UID
    val action: String, // "ALLOWED" or "BLOCKED"
    val category: String, // DomainCategory.name
    val matchedRule: String,
    val latencyMs: Long = 0L
)

@Entity(
    tableName = "custom_rules",
    indices = [Index(value = ["domainPattern", "ruleType"], unique = true)]
)
data class CustomRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainPattern: String,
    val ruleType: String, // "BLOCK" or "ALLOW"
    val category: String, // DomainCategory.name
    val isWildcard: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocklist_sources")
data class BlocklistSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String, // AD, TRACKER, MALWARE
    val url: String,
    val enabled: Boolean,
    val domainCount: Int,
    val lastUpdatedAt: Long,
    val status: String, // "BUNDLED", "UPDATED", "UPDATING", "ERROR"
    val versionTag: String
)

@Entity(tableName = "blocklist_domains", indices = [Index(value = ["domain"]), Index(value = ["sourceId"])])
data class BlocklistDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val domain: String,
    val category: String
)

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val internetBlocked: Boolean = false,
    val dnsExcluded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ghost_messages",
    indices = [Index(value = ["timestamp"]), Index(value = ["contactHash"])]
)
data class GhostMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderCiphertext: String,
    val senderIv: String,
    val previewCiphertext: String,
    val previewIv: String,
    val contactHash: String,
    val sourcePackage: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

@Entity(tableName = "ghost_schedules")
data class GhostScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val lastTriggeredAt: Long = 0L
)

@Entity(tableName = "service_events", indices = [Index(value = ["timestamp"])])
data class ServiceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String, // VPN, SCHEDULE, BLOCKLIST, DNS, YOUTUBE
    val detail: String
)
