package com.example.data.model

enum class VpnConnectionState {
    DISCONNECTED,
    REQUESTING_PERMISSION,
    CONNECTING,
    ACTIVE,
    PAUSED,
    ERROR
}

enum class VpnEnforcementMode {
    STANDARD_DNS_FILTERING,
    STRICT_PACKET_ISOLATION
}

enum class DnsProtocol(val displayName: String) {
    UDP("Standard UDP (Port 53)"),
    DOT("DNS-over-TLS (Port 853)"),
    DOH("DNS-over-HTTPS (RFC 8484)")
}

enum class DomainCategory(val label: String) {
    AD("Advertising"),
    TRACKER("Tracker"),
    MALWARE("Malware / Phishing"),
    FIREWALL_BLOCK("App Firewall"),
    CUSTOM_BLOCK("Custom Block"),
    ALLOWED("Allowed")
}

enum class DnsAction(val label: String) {
    ALLOWED("Allowed"),
    BLOCKED("Blocked")
}

enum class RuleType {
    BLOCK,
    ALLOW
}

data class DnsProviderPreset(
    val id: String,
    val name: String,
    val description: String,
    val ipv4Primary: String,
    val ipv4Secondary: String,
    val ipv6Primary: String,
    val dotHostname: String,
    val dohUrl: String
)

object DnsProviderPresets {
    val CLOUDFLARE = DnsProviderPreset(
        id = "cloudflare",
        name = "Cloudflare DNS",
        description = "Fast privacy-focused resolver (1.1.1.1)",
        ipv4Primary = "1.1.1.1",
        ipv4Secondary = "1.0.0.1",
        ipv6Primary = "2606:4700:4700::1111",
        dotHostname = "one.one.one.one",
        dohUrl = "https://cloudflare-dns.com/dns-query"
    )
    val QUAD9 = DnsProviderPreset(
        id = "quad9",
        name = "Quad9 Security",
        description = "Threat-blocking DNS with DNSSEC (9.9.9.9)",
        ipv4Primary = "9.9.9.9",
        ipv4Secondary = "149.112.112.112",
        ipv6Primary = "2620:fe::fe",
        dotHostname = "dns.quad9.net",
        dohUrl = "https://dns.quad9.net/dns-query"
    )
    val ADGUARD_DNS = DnsProviderPreset(
        id = "adguard",
        name = "AdGuard DNS",
        description = "Upstream ad & tracker filtering DNS",
        ipv4Primary = "94.140.14.14",
        ipv4Secondary = "94.140.15.15",
        ipv6Primary = "2a10:50c0::ad1:ff",
        dotHostname = "dns.adguard-dns.com",
        dohUrl = "https://dns.adguard-dns.com/dns-query"
    )
    val GOOGLE_DNS = DnsProviderPreset(
        id = "google",
        name = "Google Public DNS",
        description = "High-availability global resolver (8.8.8.8)",
        ipv4Primary = "8.8.8.8",
        ipv4Secondary = "8.8.4.4",
        ipv6Primary = "2001:4860:4860::8888",
        dotHostname = "dns.google",
        dohUrl = "https://dns.google/dns-query"
    )
    val CUSTOM = DnsProviderPreset(
        id = "custom",
        name = "Custom Provider",
        description = "User-configured IPv4, DoT hostname, or DoH URL",
        ipv4Primary = "1.1.1.1",
        ipv4Secondary = "1.0.0.1",
        ipv6Primary = "2606:4700:4700::1111",
        dotHostname = "",
        dohUrl = ""
    )

    val all: List<DnsProviderPreset> = listOf(CLOUDFLARE, QUAD9, ADGUARD_DNS, GOOGLE_DNS, CUSTOM)

    fun byId(id: String): DnsProviderPreset = all.firstOrNull { it.id == id } ?: CLOUDFLARE
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val uid: Int = -1,
    val internetBlocked: Boolean = false,
    val dnsExcluded: Boolean = false,
    val requestCount: Long = 0L,
    val blockedCount: Long = 0L,
    val lastActiveAt: Long = 0L,
    val rxBytesToday: Long = 0L,
    val txBytesToday: Long = 0L,
    val liveBytesPerSec: Long = 0L
) {
    val totalBytesToday: Long get() = rxBytesToday + txBytesToday
}

data class GhostMessage(
    val id: Long,
    val senderName: String,
    val messagePreview: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val timestamp: Long,
    val isRead: Boolean
)

data class GhostSchedule(
    val id: Long = 0,
    val label: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int, // Bit 0 = Monday .. Bit 6 = Sunday
    val enabled: Boolean = true,
    val lastTriggeredAt: Long = 0L
) {
    companion object {
        const val ALL_DAYS_MASK = 0b1111111
        const val WEEKDAYS_MASK = 0b0011111
        const val WEEKENDS_MASK = 0b1100000
    }
}

data class DnsProbeResult(
    val providerName: String,
    val protocol: DnsProtocol,
    val endpoint: String,
    val success: Boolean,
    val latencyMs: Long,
    val resolvedIp: String?,
    val errorMessage: String?,
    val testedAt: Long
)

data class PrivacyScoreCriterion(
    val id: String,
    val title: String,
    val description: String,
    val maxPoints: Int,
    val earnedPoints: Int,
    val isSatisfied: Boolean
)

data class PrivacyScoreBreakdown(
    val totalScore: Int,
    val ratingLabel: String,
    val criteria: List<PrivacyScoreCriterion>
)

data class LiveProtectionSnapshot(
    val state: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val enforcementMode: VpnEnforcementMode = VpnEnforcementMode.STANDARD_DNS_FILTERING,
    val sessionStartedAt: Long = 0L,
    val lastRestartAt: Long = 0L,
    val sessionTotalRequests: Long = 0L,
    val sessionBlockedAds: Long = 0L,
    val sessionBlockedTrackers: Long = 0L,
    val sessionBlockedOther: Long = 0L,
    val activeDnsLabel: String = "Cloudflare DNS",
    val errorMessage: String? = null,
    val isNetworkAvailable: Boolean = true,
    val cacheSize: Int = 0,
    val cacheHits: Long = 0L
) {
    val isRunning: Boolean get() = state == VpnConnectionState.ACTIVE
}

enum class NetworkTransportType(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Cellular"),
    ETHERNET("Ethernet"),
    OTHER("Connected"),
    OFFLINE("Offline")
}

data class LiveTrafficSample(
    val timestampMs: Long,
    val downloadBytesPerSec: Long,
    val uploadBytesPerSec: Long,
    val blockedAdsDelta: Int,
    val blockedTrackersDelta: Int,
    val allowedDnsDelta: Int
)

data class AppDataUsage(
    val packageName: String,
    val uid: Int,
    val rxBytesToday: Long,
    val txBytesToday: Long,
    val liveBytesPerSec: Long = 0L
) {
    val totalBytesToday: Long get() = rxBytesToday + txBytesToday
}

data class LiveTrafficSnapshot(
    val isMonitoring: Boolean = false,
    val transportType: NetworkTransportType = NetworkTransportType.OFFLINE,
    val transportLabel: String = "Offline",
    val isMetered: Boolean = false,
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val peakDownloadBytesPerSec: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val hasUsageStatsAccess: Boolean = false,
    val samples: List<LiveTrafficSample> = emptyList(),
    val perAppUsage: Map<String, AppDataUsage> = emptyMap(),
    val totalTrackedAppRxBytes: Long = 0L,
    val totalTrackedAppTxBytes: Long = 0L,
    val lastUpdatedAt: Long = 0L
)
