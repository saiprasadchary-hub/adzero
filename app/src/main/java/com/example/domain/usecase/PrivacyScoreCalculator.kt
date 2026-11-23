package com.example.domain.usecase

import com.example.data.model.DnsProtocol
import com.example.data.model.PrivacyScoreBreakdown
import com.example.data.model.PrivacyScoreCriterion

data class PrivacyScoreInput(
    val isVpnActive: Boolean,
    val isAdBlockingEnabled: Boolean,
    val isTrackerBlockingEnabled: Boolean,
    val isMalwareListEnabled: Boolean,
    val isStrictModeEnabled: Boolean,
    val dnsProtocol: DnsProtocol,
    val hasActiveBlocklistDomains: Boolean,
    val isPrivatePreviewOrBiometricEnabled: Boolean,
    val isAutoUpdateEnabled: Boolean
)

/**
 * Computes a deterministic, 100% transparent Privacy Score from 0 to 100
 * based solely on verified protection settings and live VPN state.
 */
object PrivacyScoreCalculator {
    fun calculate(input: PrivacyScoreInput): PrivacyScoreBreakdown {
        val criteria = listOf(
            PrivacyScoreCriterion(
                id = "vpn_active",
                title = "Local VPN Protection Active",
                description = "Local Android VpnService interface is established and actively inspecting DNS queries.",
                maxPoints = 30,
                earnedPoints = if (input.isVpnActive) 30 else 0,
                isSatisfied = input.isVpnActive
            ),
            PrivacyScoreCriterion(
                id = "ad_blocking",
                title = "Ad Domain Filtering",
                description = "Advertising domain blocklists are enabled.",
                maxPoints = 15,
                earnedPoints = if (input.isAdBlockingEnabled && input.hasActiveBlocklistDomains) 15 else 0,
                isSatisfied = input.isAdBlockingEnabled && input.hasActiveBlocklistDomains
            ),
            PrivacyScoreCriterion(
                id = "tracker_blocking",
                title = "Tracker & Telemetry Filtering",
                description = "Analytics and cross-app tracker domains are filtered.",
                maxPoints = 15,
                earnedPoints = if (input.isTrackerBlockingEnabled && input.hasActiveBlocklistDomains) 15 else 0,
                isSatisfied = input.isTrackerBlockingEnabled && input.hasActiveBlocklistDomains
            ),
            PrivacyScoreCriterion(
                id = "malware_blocking",
                title = "Malware & Phishing Guard",
                description = "Known malicious and phishing domains are blocked.",
                maxPoints = 10,
                earnedPoints = if (input.isMalwareListEnabled) 10 else 0,
                isSatisfied = input.isMalwareListEnabled
            ),
            PrivacyScoreCriterion(
                id = "encrypted_dns",
                title = "Encrypted Upstream DNS",
                description = "Upstream DNS queries use DNS-over-HTTPS or DNS-over-TLS instead of plaintext UDP.",
                maxPoints = 15,
                earnedPoints = if (input.dnsProtocol == DnsProtocol.DOH || input.dnsProtocol == DnsProtocol.DOT) 15 else 0,
                isSatisfied = input.dnsProtocol == DnsProtocol.DOH || input.dnsProtocol == DnsProtocol.DOT
            ),
            PrivacyScoreCriterion(
                id = "strict_mode",
                title = "Strict Filtering Rules",
                description = "Extended aggressive tracker and CNAME/subdomain blocking rules enabled.",
                maxPoints = 5,
                earnedPoints = if (input.isStrictModeEnabled) 5 else 0,
                isSatisfied = input.isStrictModeEnabled
            ),
            PrivacyScoreCriterion(
                id = "auto_updates",
                title = "Automatic Blocklist Updates",
                description = "WorkManager keeps local blocklists fresh automatically.",
                maxPoints = 5,
                earnedPoints = if (input.isAutoUpdateEnabled) 5 else 0,
                isSatisfied = input.isAutoUpdateEnabled
            ),
            PrivacyScoreCriterion(
                id = "inbox_privacy",
                title = "Ghost Inbox Privacy Hardening",
                description = "Preview masking or biometric lock is enabled for locally stored notifications.",
                maxPoints = 5,
                earnedPoints = if (input.isPrivatePreviewOrBiometricEnabled) 5 else 0,
                isSatisfied = input.isPrivatePreviewOrBiometricEnabled
            )
        )

        val total = criteria.sumOf { it.earnedPoints }.coerceIn(0, 100)
        val rating = when {
            total >= 85 -> "Strong Protection"
            total >= 60 -> "Good Protection"
            total >= 30 -> "Basic Protection"
            else -> "Protection Inactive"
        }
        return PrivacyScoreBreakdown(
            totalScore = total,
            ratingLabel = rating,
            criteria = criteria
        )
    }
}
