package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DnsProtocol
import com.example.data.model.DnsProviderPresets
import com.example.data.model.VpnEnforcementMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adZeroDataStore: DataStore<Preferences> by preferencesDataStore(name = "adzero_prefs")

data class AdZeroSettings(
    val protectionDesired: Boolean = false,
    val adBlockingEnabled: Boolean = true,
    val trackerBlockingEnabled: Boolean = true,
    val malwareBlockingEnabled: Boolean = true,
    val strictModeEnabled: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val enforcementMode: VpnEnforcementMode = VpnEnforcementMode.STANDARD_DNS_FILTERING,
    val dnsProviderId: String = DnsProviderPresets.CLOUDFLARE.id,
    val dnsProtocol: DnsProtocol = DnsProtocol.DOH,
    val customDnsIpv4: String = "1.1.1.1",
    val customDotHostname: String = "one.one.one.one",
    val customDohUrl: String = "https://cloudflare-dns.com/dns-query",
    val ghostModeEnabled: Boolean = false,
    val ghostStrictIsolation: Boolean = false,
    val ghostManualOverrideUntil: Long = 0L,
    val ghostPreviewPrivacyMasked: Boolean = false,
    val ghostBiometricLockEnabled: Boolean = false,
    val ghostInboxCaptureEnabled: Boolean = false,
    val youtubeSkipConsentGranted: Boolean = false,
    val youtubeSkipEnabled: Boolean = false,
    val youtubeSkipCount: Long = 0L,
    val youtubeLastSkipAt: Long = 0L,
    val autoBlocklistUpdateEnabled: Boolean = true,
    val lastBlocklistUpdateAt: Long = 0L,
    val analyticsRetentionDays: Int = 7,
    val lastProtectionActivatedAt: Long = 0L,
    val lastDnsProbeLatencyMs: Long = 0L,
    val lastDnsProbeAt: Long = 0L,
    val lastDnsProbeSuccess: Boolean = false
)

class AdZeroPreferences(private val context: Context) {

    private object Keys {
        val PROTECTION_DESIRED = booleanPreferencesKey("protection_desired")
        val AD_BLOCKING = booleanPreferencesKey("ad_blocking")
        val TRACKER_BLOCKING = booleanPreferencesKey("tracker_blocking")
        val MALWARE_BLOCKING = booleanPreferencesKey("malware_blocking")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val ENFORCEMENT_MODE = stringPreferencesKey("enforcement_mode")
        val DNS_PROVIDER_ID = stringPreferencesKey("dns_provider_id")
        val DNS_PROTOCOL = stringPreferencesKey("dns_protocol")
        val CUSTOM_DNS_IPV4 = stringPreferencesKey("custom_dns_ipv4")
        val CUSTOM_DOT_HOST = stringPreferencesKey("custom_dot_host")
        val CUSTOM_DOH_URL = stringPreferencesKey("custom_doh_url")
        val GHOST_MODE_ENABLED = booleanPreferencesKey("ghost_mode_enabled")
        val GHOST_STRICT_ISOLATION = booleanPreferencesKey("ghost_strict_isolation")
        val GHOST_MANUAL_OVERRIDE_UNTIL = longPreferencesKey("ghost_manual_override_until")
        val GHOST_PREVIEW_MASKED = booleanPreferencesKey("ghost_preview_masked")
        val GHOST_BIOMETRIC_LOCK = booleanPreferencesKey("ghost_biometric_lock")
        val GHOST_INBOX_CAPTURE = booleanPreferencesKey("ghost_inbox_capture")
        val YT_SKIP_CONSENT = booleanPreferencesKey("yt_skip_consent")
        val YT_SKIP_ENABLED = booleanPreferencesKey("yt_skip_enabled")
        val YT_SKIP_COUNT = longPreferencesKey("yt_skip_count")
        val YT_LAST_SKIP_AT = longPreferencesKey("yt_last_skip_at")
        val AUTO_BLOCKLIST_UPDATE = booleanPreferencesKey("auto_blocklist_update")
        val LAST_BLOCKLIST_UPDATE_AT = longPreferencesKey("last_blocklist_update_at")
        val ANALYTICS_RETENTION_DAYS = intPreferencesKey("analytics_retention_days")
        val LAST_PROTECTION_ACTIVATED_AT = longPreferencesKey("last_protection_activated_at")
        val LAST_DNS_PROBE_LATENCY = longPreferencesKey("last_dns_probe_latency")
        val LAST_DNS_PROBE_AT = longPreferencesKey("last_dns_probe_at")
        val LAST_DNS_PROBE_SUCCESS = booleanPreferencesKey("last_dns_probe_success")
    }

    val settingsFlow: Flow<AdZeroSettings> = context.adZeroDataStore.data.map { prefs ->
        val protocol = prefs[Keys.DNS_PROTOCOL]?.let {
            runCatching { DnsProtocol.valueOf(it) }.getOrDefault(DnsProtocol.DOH)
        } ?: DnsProtocol.DOH

        val mode = prefs[Keys.ENFORCEMENT_MODE]?.let {
            runCatching { VpnEnforcementMode.valueOf(it) }.getOrDefault(VpnEnforcementMode.STANDARD_DNS_FILTERING)
        } ?: VpnEnforcementMode.STANDARD_DNS_FILTERING

        AdZeroSettings(
            protectionDesired = prefs[Keys.PROTECTION_DESIRED] ?: false,
            adBlockingEnabled = prefs[Keys.AD_BLOCKING] ?: true,
            trackerBlockingEnabled = prefs[Keys.TRACKER_BLOCKING] ?: true,
            malwareBlockingEnabled = prefs[Keys.MALWARE_BLOCKING] ?: true,
            strictModeEnabled = prefs[Keys.STRICT_MODE] ?: false,
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            enforcementMode = mode,
            dnsProviderId = prefs[Keys.DNS_PROVIDER_ID] ?: DnsProviderPresets.CLOUDFLARE.id,
            dnsProtocol = protocol,
            customDnsIpv4 = prefs[Keys.CUSTOM_DNS_IPV4] ?: "1.1.1.1",
            customDotHostname = prefs[Keys.CUSTOM_DOT_HOST] ?: "one.one.one.one",
            customDohUrl = prefs[Keys.CUSTOM_DOH_URL] ?: "https://cloudflare-dns.com/dns-query",
            ghostModeEnabled = prefs[Keys.GHOST_MODE_ENABLED] ?: false,
            ghostStrictIsolation = prefs[Keys.GHOST_STRICT_ISOLATION] ?: false,
            ghostManualOverrideUntil = prefs[Keys.GHOST_MANUAL_OVERRIDE_UNTIL] ?: 0L,
            ghostPreviewPrivacyMasked = prefs[Keys.GHOST_PREVIEW_MASKED] ?: false,
            ghostBiometricLockEnabled = prefs[Keys.GHOST_BIOMETRIC_LOCK] ?: false,
            ghostInboxCaptureEnabled = prefs[Keys.GHOST_INBOX_CAPTURE] ?: false,
            youtubeSkipConsentGranted = prefs[Keys.YT_SKIP_CONSENT] ?: false,
            youtubeSkipEnabled = prefs[Keys.YT_SKIP_ENABLED] ?: false,
            youtubeSkipCount = prefs[Keys.YT_SKIP_COUNT] ?: 0L,
            youtubeLastSkipAt = prefs[Keys.YT_LAST_SKIP_AT] ?: 0L,
            autoBlocklistUpdateEnabled = prefs[Keys.AUTO_BLOCKLIST_UPDATE] ?: true,
            lastBlocklistUpdateAt = prefs[Keys.LAST_BLOCKLIST_UPDATE_AT] ?: 0L,
            analyticsRetentionDays = prefs[Keys.ANALYTICS_RETENTION_DAYS] ?: 7,
            lastProtectionActivatedAt = prefs[Keys.LAST_PROTECTION_ACTIVATED_AT] ?: 0L,
            lastDnsProbeLatencyMs = prefs[Keys.LAST_DNS_PROBE_LATENCY] ?: 0L,
            lastDnsProbeAt = prefs[Keys.LAST_DNS_PROBE_AT] ?: 0L,
            lastDnsProbeSuccess = prefs[Keys.LAST_DNS_PROBE_SUCCESS] ?: false
        )
    }

    suspend fun setProtectionDesired(desired: Boolean) {
        context.adZeroDataStore.edit { it[Keys.PROTECTION_DESIRED] = desired }
    }

    suspend fun recordProtectionActivated(timestamp: Long = System.currentTimeMillis()) {
        context.adZeroDataStore.edit { it[Keys.LAST_PROTECTION_ACTIVATED_AT] = timestamp }
    }

    suspend fun setAdBlocking(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.AD_BLOCKING] = enabled }
    }

    suspend fun setTrackerBlocking(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.TRACKER_BLOCKING] = enabled }
    }

    suspend fun setMalwareBlocking(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.MALWARE_BLOCKING] = enabled }
    }

    suspend fun setStrictMode(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.STRICT_MODE] = enabled }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.AUTO_START_ON_BOOT] = enabled }
    }

    suspend fun setEnforcementMode(mode: VpnEnforcementMode) {
        context.adZeroDataStore.edit { it[Keys.ENFORCEMENT_MODE] = mode.name }
    }

    suspend fun setDnsConfig(
        providerId: String,
        protocol: DnsProtocol,
        customIpv4: String? = null,
        customDot: String? = null,
        customDoh: String? = null
    ) {
        context.adZeroDataStore.edit { prefs ->
            prefs[Keys.DNS_PROVIDER_ID] = providerId
            prefs[Keys.DNS_PROTOCOL] = protocol.name
            if (customIpv4 != null) prefs[Keys.CUSTOM_DNS_IPV4] = customIpv4.trim()
            if (customDot != null) prefs[Keys.CUSTOM_DOT_HOST] = customDot.trim()
            if (customDoh != null) prefs[Keys.CUSTOM_DOH_URL] = customDoh.trim()
        }
    }

    suspend fun resetDnsToDefault() {
        setDnsConfig(
            providerId = DnsProviderPresets.CLOUDFLARE.id,
            protocol = DnsProtocol.DOH,
            customIpv4 = DnsProviderPresets.CLOUDFLARE.ipv4Primary,
            customDot = DnsProviderPresets.CLOUDFLARE.dotHostname,
            customDoh = DnsProviderPresets.CLOUDFLARE.dohUrl
        )
    }

    suspend fun recordDnsProbe(success: Boolean, latencyMs: Long, testedAt: Long = System.currentTimeMillis()) {
        context.adZeroDataStore.edit {
            it[Keys.LAST_DNS_PROBE_SUCCESS] = success
            it[Keys.LAST_DNS_PROBE_LATENCY] = latencyMs
            it[Keys.LAST_DNS_PROBE_AT] = testedAt
        }
    }

    suspend fun setGhostMode(enabled: Boolean, manualOverrideUntil: Long = 0L) {
        context.adZeroDataStore.edit {
            it[Keys.GHOST_MODE_ENABLED] = enabled
            it[Keys.GHOST_MANUAL_OVERRIDE_UNTIL] = manualOverrideUntil
        }
    }

    suspend fun setGhostStrictIsolation(enabled: Boolean) {
        context.adZeroDataStore.edit {
            it[Keys.GHOST_STRICT_ISOLATION] = enabled
            it[Keys.ENFORCEMENT_MODE] = if (enabled) {
                VpnEnforcementMode.STRICT_PACKET_ISOLATION.name
            } else {
                VpnEnforcementMode.STANDARD_DNS_FILTERING.name
            }
        }
    }

    suspend fun setGhostPreviewPrivacyMasked(masked: Boolean) {
        context.adZeroDataStore.edit { it[Keys.GHOST_PREVIEW_MASKED] = masked }
    }

    suspend fun setGhostBiometricLock(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.GHOST_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setGhostInboxCapture(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.GHOST_INBOX_CAPTURE] = enabled }
    }

    suspend fun setYouTubeSkipConsent(granted: Boolean) {
        context.adZeroDataStore.edit {
            it[Keys.YT_SKIP_CONSENT] = granted
            if (!granted) it[Keys.YT_SKIP_ENABLED] = false
        }
    }

    suspend fun setYouTubeSkipEnabled(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.YT_SKIP_ENABLED] = enabled }
    }

    suspend fun incrementYouTubeSkipCount(timestamp: Long = System.currentTimeMillis()) {
        context.adZeroDataStore.edit { prefs ->
            val current = prefs[Keys.YT_SKIP_COUNT] ?: 0L
            prefs[Keys.YT_SKIP_COUNT] = current + 1L
            prefs[Keys.YT_LAST_SKIP_AT] = timestamp
        }
    }

    suspend fun resetYouTubeSkipCount() {
        context.adZeroDataStore.edit { prefs ->
            prefs[Keys.YT_SKIP_COUNT] = 0L
            prefs[Keys.YT_LAST_SKIP_AT] = 0L
        }
    }

    suspend fun setAutoBlocklistUpdate(enabled: Boolean) {
        context.adZeroDataStore.edit { it[Keys.AUTO_BLOCKLIST_UPDATE] = enabled }
    }

    suspend fun recordBlocklistUpdate(timestamp: Long = System.currentTimeMillis()) {
        context.adZeroDataStore.edit { it[Keys.LAST_BLOCKLIST_UPDATE_AT] = timestamp }
    }

    suspend fun setAnalyticsRetentionDays(days: Int) {
        context.adZeroDataStore.edit { it[Keys.ANALYTICS_RETENTION_DAYS] = days.coerceIn(1, 90) }
    }
}
