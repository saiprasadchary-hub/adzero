package com.example.core.network

import com.example.data.model.DomainCategory
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class FilterDecision(
    val blocked: Boolean,
    val category: DomainCategory,
    val matchedRule: String
)

data class FilterConfigSnapshot(
    val adBlockingEnabled: Boolean = true,
    val trackerBlockingEnabled: Boolean = true,
    val malwareBlockingEnabled: Boolean = true,
    val strictModeEnabled: Boolean = false,
    val ghostModeEnabled: Boolean = false,
    val blockedDomains: Map<String, DomainCategory> = emptyMap(),
    val strictDomains: Set<String> = emptySet(),
    val customBlockExact: Set<String> = emptySet(),
    val customBlockWildcards: List<Regex> = emptyList(),
    val customBlockWildcardPatterns: List<String> = emptyList(),
    val allowExact: Set<String> = emptySet(),
    val allowWildcards: List<Regex> = emptyList(),
    val allowWildcardPatterns: List<String> = emptyList(),
    val blockedAppPackages: Set<String> = emptySet(),
    val dnsExcludedAppPackages: Set<String> = emptySet()
)

private data class CachedDnsEntry(
    val responsePayload: ByteArray,
    val expiresAtMillis: Long
)

/**
 * Thread-safe, lock-free snapshot DNS filtering engine with:
 * - Per-app firewall blocking (takes highest precedence over allowlists when app is blocked)
 * - Per-app DNS exclusion support
 * - Allowlist precedence over all blocklists and custom block rules
 * - Exact domain, parent-subdomain, and wildcard matching (`*.example.com`, `ads.*`)
 * - Bounded LRU DNS response cache to reduce upstream queries and battery drain
 */
class DnsFilterEngine(
    private val maxCacheEntries: Int = 512,
    private val cacheTtlMillis: Long = 120_000L
) {
    private val configRef = AtomicReference(FilterConfigSnapshot())

    private val lruCache: MutableMap<String, CachedDnsEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedDnsEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDnsEntry>?): Boolean {
                return size > maxCacheEntries
            }
        }
    )

    private val cacheHitCounter = AtomicLong(0L)

    fun updateConfig(snapshot: FilterConfigSnapshot) {
        configRef.set(snapshot)
    }

    fun getConfig(): FilterConfigSnapshot = configRef.get()

    fun evaluate(domain: String, packageName: String = "unknown"): FilterDecision {
        val normalized = normalizeDomain(domain)
        if (normalized.isEmpty()) {
            return FilterDecision(false, DomainCategory.ALLOWED, "empty")
        }

        val cfg = configRef.get()

        // 1. Per-app firewall block (including WhatsApp Ghost Mode when enabled)
        if (cfg.ghostModeEnabled && matchDomainOrParent(normalized, WHATSAPP_DOMAINS) != null) {
            return FilterDecision(
                blocked = true,
                category = DomainCategory.FIREWALL_BLOCK,
                matchedRule = "WhatsApp Ghost Mode"
            )
        }
        if (packageName != "unknown") {
            val isWhatsApp = packageName == WHATSAPP_PKG || packageName == WHATSAPP_BUSINESS_PKG
            if ((cfg.ghostModeEnabled && isWhatsApp) || packageName in cfg.blockedAppPackages) {
                return FilterDecision(
                    blocked = true,
                    category = DomainCategory.FIREWALL_BLOCK,
                    matchedRule = if (cfg.ghostModeEnabled && isWhatsApp) {
                        "WhatsApp Ghost Mode"
                    } else {
                        "App Firewall: $packageName"
                    }
                )
            }
            // 2. Per-app DNS exclusion skips ad/tracker filtering
            if (packageName in cfg.dnsExcludedAppPackages) {
                return FilterDecision(
                    blocked = false,
                    category = DomainCategory.ALLOWED,
                    matchedRule = "App DNS Excluded"
                )
            }
        }

        // 3. Explicit Allowlist ALWAYS wins over blocklists and custom block rules
        matchDomainOrParent(normalized, cfg.allowExact)?.let { matched ->
            return FilterDecision(false, DomainCategory.ALLOWED, "Allowlist: $matched")
        }
        cfg.allowWildcards.forEachIndexed { idx, regex ->
            if (regex.matches(normalized)) {
                val pattern = cfg.allowWildcardPatterns.getOrElse(idx) { "*" }
                return FilterDecision(false, DomainCategory.ALLOWED, "Allowlist wildcard: $pattern")
            }
        }

        // 4. Custom Blocklist exact & wildcards
        matchDomainOrParent(normalized, cfg.customBlockExact)?.let { matched ->
            return FilterDecision(true, DomainCategory.CUSTOM_BLOCK, "Custom rule: $matched")
        }
        cfg.customBlockWildcards.forEachIndexed { idx, regex ->
            if (regex.matches(normalized)) {
                val pattern = cfg.customBlockWildcardPatterns.getOrElse(idx) { "*" }
                return FilterDecision(true, DomainCategory.CUSTOM_BLOCK, "Custom wildcard: $pattern")
            }
        }

        // 5. Strict mode domains
        if (cfg.strictModeEnabled) {
            matchDomainOrParent(normalized, cfg.strictDomains)?.let { matched ->
                return FilterDecision(true, DomainCategory.TRACKER, "Strict rule: $matched")
            }
        }

        // 6. Active blocklists (exact or parent domain match)
        val blockMatch = matchDomainOrParentWithCategory(normalized, cfg.blockedDomains)
        if (blockMatch != null) {
            val (matchedDomain, category) = blockMatch
            val categoryEnabled = when (category) {
                DomainCategory.AD -> cfg.adBlockingEnabled
                DomainCategory.TRACKER -> cfg.trackerBlockingEnabled
                DomainCategory.MALWARE -> cfg.malwareBlockingEnabled
                else -> true
            }
            if (categoryEnabled) {
                return FilterDecision(
                    blocked = true,
                    category = category,
                    matchedRule = "Blocklist: $matchedDomain"
                )
            }
        }

        return FilterDecision(false, DomainCategory.ALLOWED, "Passed")
    }

    fun getCachedResponse(domain: String, qType: Int, nowMillis: Long = System.currentTimeMillis()): ByteArray? {
        val key = "${normalizeDomain(domain)}#$qType"
        val entry = lruCache[key] ?: return null
        if (entry.expiresAtMillis <= nowMillis) {
            lruCache.remove(key)
            return null
        }
        cacheHitCounter.incrementAndGet()
        return entry.responsePayload.copyOf()
    }

    fun putCachedResponse(
        domain: String,
        qType: Int,
        responsePayload: ByteArray,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val key = "${normalizeDomain(domain)}#$qType"
        lruCache[key] = CachedDnsEntry(
            responsePayload = responsePayload.copyOf(),
            expiresAtMillis = nowMillis + cacheTtlMillis
        )
    }

    fun clearCache() {
        lruCache.clear()
        cacheHitCounter.set(0L)
    }

    fun cacheSize(): Int = lruCache.size

    fun cacheHits(): Long = cacheHitCounter.get()

    private fun matchDomainOrParent(domain: String, set: Set<String>): String? {
        if (set.isEmpty()) return null
        if (domain in set) return domain
        var dotIdx = domain.indexOf('.')
        while (dotIdx != -1 && dotIdx + 1 < domain.length) {
            val parent = domain.substring(dotIdx + 1)
            if (parent in set) return parent
            dotIdx = domain.indexOf('.', dotIdx + 1)
        }
        return null
    }

    private fun matchDomainOrParentWithCategory(
        domain: String,
        map: Map<String, DomainCategory>
    ): Pair<String, DomainCategory>? {
        if (map.isEmpty()) return null
        map[domain]?.let { return domain to it }
        var dotIdx = domain.indexOf('.')
        while (dotIdx != -1 && dotIdx + 1 < domain.length) {
            val parent = domain.substring(dotIdx + 1)
            map[parent]?.let { return parent to it }
            dotIdx = domain.indexOf('.', dotIdx + 1)
        }
        return null
    }

    companion object {
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
        val WHATSAPP_DOMAINS = setOf("whatsapp.net", "whatsapp.com", "wa.me")

        fun normalizeDomain(raw: String): String {
            return raw.trim().lowercase().trimEnd('.')
        }

        fun compileWildcard(pattern: String): Regex {
            val normalized = normalizeDomain(pattern)
            val escaped = buildString {
                append('^')
                normalized.forEach { ch ->
                    when (ch) {
                        '*' -> append(".*")
                        '.' -> append("\\.")
                        '?' -> append('.')
                        else -> if (ch.isLetterOrDigit() || ch == '-' || ch == '_') append(ch)
                    }
                }
                append('$')
            }
            return Regex(escaped)
        }
    }
}
