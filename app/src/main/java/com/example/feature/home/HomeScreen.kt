package com.example.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.AdZeroShieldLogo
import com.example.core.design.InfoBanner
import com.example.core.design.SectionHeader
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnEnforcementMode
import com.example.data.repository.InstalledAppsRepository
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroLavenderSurface
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroPurpleSoft
import com.example.ui.theme.AdZeroSuccess
import com.example.ui.theme.AdZeroWarning
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    installedAppsRepository: InstalledAppsRepository,
    onToggleProtection: () -> Unit,
    onPauseOrResume: () -> Unit,
    onToggleAppInternet: (packageName: String, appName: String, blockInternet: Boolean) -> Unit,
    onRefreshTraffic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenGhost: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenSmartDns: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenBlocklists: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scoreExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshTraffic()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: AdZero Logo, Name, Tagline, Settings Shortcut
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdZeroShieldLogo(size = 42.dp)
                    Column {
                        Text(
                            text = "AdZero",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = "Block Ads. Protect Privacy. Take Control.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Open Settings",
                        tint = AdZeroNavyText
                    )
                }
            }
        }

        // Hero Protection Control Card
        item {
            ProtectionHeroCard(
                uiState = uiState,
                onToggleProtection = onToggleProtection,
                onPauseOrResume = onPauseOrResume
            )
        }

        // Live Statistics 2x2 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Protection Statistics",
                    subtitle = "Observed locally today (${FormattingUtils.formatCompactCount(uiState.activeBlocklistDomains.toLong())} domains loaded)"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        label = "Ads Blocked Today",
                        value = FormattingUtils.formatCompactCount(uiState.adsBlockedToday),
                        subtext = "Session: ${uiState.liveSnapshot.sessionBlockedAds}",
                        accent = AdZeroPurple,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Trackers Blocked",
                        value = FormattingUtils.formatCompactCount(uiState.trackersBlockedToday),
                        subtext = "Session: ${uiState.liveSnapshot.sessionBlockedTrackers}",
                        accent = AdZeroSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        label = "DNS Filtered",
                        value = FormattingUtils.formatCompactCount(uiState.dnsFilteredToday),
                        subtext = "Session: ${uiState.liveSnapshot.sessionTotalRequests}",
                        accent = AdZeroNavyText,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Apps Protected",
                        value = uiState.appsProtectedCount.toString(),
                        subtext = "${uiState.blockedAppsCount} blocked",
                        accent = AdZeroPurple,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenApps
                    )
                }
            }
        }

        // Real-Time Network Traffic Status & Dual-Series Throughput Chart
        item {
            LiveNetworkTrafficCard(
                traffic = uiState.liveTraffic,
                isVpnActive = uiState.liveSnapshot.isRunning
            )
        }

        // Real-Time Blocked Ads & DNS Traffic Chart (Live 30s & Hourly Today)
        item {
            LiveAdBlockChartCard(
                traffic = uiState.liveTraffic,
                hourlyBuckets = uiState.hourlyBuckets,
                adsBlockedToday = uiState.adsBlockedToday,
                trackersBlockedToday = uiState.trackersBlockedToday,
                dnsFilteredToday = uiState.dnsFilteredToday,
                isVpnActive = uiState.liveSnapshot.isRunning,
                onOpenAnalytics = onOpenActivity
            )
        }

        // Top Data-Using Apps Quick Firewall Control
        item {
            TopAppTrafficQuickControlCard(
                apps = uiState.topTrafficApps,
                hasUsageStatsAccess = uiState.liveTraffic.hasUsageStatsAccess,
                isVpnActive = uiState.liveSnapshot.isRunning,
                totalTrackedRxBytes = uiState.liveTraffic.totalTrackedAppRxBytes,
                totalTrackedTxBytes = uiState.liveTraffic.totalTrackedAppTxBytes,
                installedAppsRepository = installedAppsRepository,
                onToggleAppInternet = onToggleAppInternet,
                onGrantUsageAccess = { SystemPermissionHelper.openUsageAccessSettings(context) },
                onOpenAllApps = onOpenApps
            )
        }

        // Transparent Privacy Score Card
        item {
            AdZeroCard(
                containerColor = AdZeroLavenderBg,
                onClick = { scoreExpanded = !scoreExpanded },
                modifier = Modifier.testTag("home_privacy_score_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = uiState.privacyScore.ratingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${uiState.privacyScore.totalScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = when {
                                uiState.privacyScore.totalScore >= 75 -> AdZeroSuccess
                                uiState.privacyScore.totalScore >= 40 -> AdZeroWarning
                                else -> AdZeroPurple
                            }
                        )
                        Text(
                            text = "/ 100",
                            style = MaterialTheme.typography.labelMedium,
                            color = AdZeroMutedText
                        )
                        Icon(
                            imageVector = if (scoreExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (scoreExpanded) "Collapse score details" else "Expand score details",
                            tint = AdZeroMutedText
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { uiState.privacyScore.totalScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = AdZeroPurple,
                    trackColor = AdZeroLavenderBorder
                )
                AnimatedVisibility(visible = scoreExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Calculated transparently from active local protections:",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdZeroMutedText
                        )
                        uiState.privacyScore.criteria.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isSatisfied) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (item.isSatisfied) AdZeroSuccess else AdZeroMutedText,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AdZeroNavyText
                                    )
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdZeroMutedText
                                    )
                                }
                                Text(
                                    text = "+${item.earnedPoints}/${item.maxPoints}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (item.isSatisfied) AdZeroSuccess else AdZeroMutedText
                                )
                            }
                        }
                    }
                }
            }
        }

        // Feature Shortcuts (2x3)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Protection Modules")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureShortcutCard(
                        icon = Icons.Filled.Shield,
                        title = "Ad Blocking",
                        subtitle = if (uiState.settings.adBlockingEnabled) "Enabled" else "Off",
                        active = uiState.settings.adBlockingEnabled,
                        onClick = onOpenBlocklists,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_ad_blocking")
                    )
                    FeatureShortcutCard(
                        icon = Icons.Filled.PrivacyTip,
                        title = "Trackers",
                        subtitle = if (uiState.settings.trackerBlockingEnabled) "Protected" else "Off",
                        active = uiState.settings.trackerBlockingEnabled,
                        onClick = onOpenBlocklists,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_tracker_protection")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureShortcutCard(
                        icon = Icons.Filled.VisibilityOff,
                        title = "Ghost Mode",
                        subtitle = if (uiState.settings.ghostModeEnabled) {
                            "WA Blocked"
                        } else if (uiState.unreadGhostCount > 0) {
                            "${uiState.unreadGhostCount} unread"
                        } else {
                            "Inbox ready"
                        },
                        active = uiState.settings.ghostModeEnabled,
                        onClick = onOpenGhost,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_ghost_mode")
                    )
                    FeatureShortcutCard(
                        icon = Icons.Filled.FastForward,
                        title = "YT Skipper",
                        subtitle = if (uiState.settings.youtubeSkipEnabled) {
                            "${uiState.settings.youtubeSkipCount} skipped"
                        } else {
                            "Optional"
                        },
                        active = uiState.settings.youtubeSkipEnabled,
                        onClick = onOpenYouTube,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_youtube_skip")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureShortcutCard(
                        icon = Icons.Filled.Dns,
                        title = "Smart DNS",
                        subtitle = uiState.settings.dnsProtocol.name,
                        active = true,
                        onClick = onOpenSmartDns,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_smart_dns")
                    )
                    FeatureShortcutCard(
                        icon = Icons.Filled.BarChart,
                        title = "Analytics",
                        subtitle = "Live activity",
                        active = uiState.dnsFilteredToday > 0,
                        onClick = onOpenActivity,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_analytics")
                    )
                }
            }
        }

        // Recent Blocked Activity & VPN Transitions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Recent Activity",
                    action = {
                        TextButton(onClick = onOpenActivity) {
                            Text("View All")
                        }
                    }
                )
                AdZeroCard {
                    if (uiState.recentBlocked.isEmpty() && uiState.recentEvents.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No DNS filtering activity yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = AdZeroNavyText
                            )
                            Text(
                                text = "Enable Protection above to inspect DNS requests locally. AdZero never fabricates activity counts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                        }
                    } else {
                        uiState.recentBlocked.forEachIndexed { idx, entry ->
                            if (idx > 0) HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.domain,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = AdZeroNavyText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.matchedRule} • ${FormattingUtils.formatRelativeTime(entry.timestamp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdZeroMutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                StatusPill(label = entry.category, tone = StatusTone.ERROR)
                            }
                        }

                        if (uiState.recentEvents.isNotEmpty()) {
                            if (uiState.recentBlocked.isNotEmpty()) {
                                HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 10.dp))
                            }
                            Text(
                                text = "Recent Service Transitions",
                                style = MaterialTheme.typography.labelMedium,
                                color = AdZeroMutedText
                            )
                            Spacer(Modifier.height(6.dp))
                            uiState.recentEvents.take(3).forEach { ev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = ev.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdZeroNavyText,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = FormattingUtils.formatTime(ev.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AdZeroMutedText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Honest Technical Scope Disclosure
        item {
            InfoBanner(
                icon = Icons.Outlined.Info,
                title = "Local On-Device Filtering Scope",
                message = "AdZero uses a local Android VpnService to filter DNS requests without routing traffic to external VPN servers. DNS filtering cannot reliably remove first-party or YouTube in-stream video ads served from content domains.",
                tone = StatusTone.PRIMARY
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProtectionHeroCard(
    uiState: HomeUiState,
    onToggleProtection: () -> Unit,
    onPauseOrResume: () -> Unit
) {
    val state = uiState.liveSnapshot.state
    val isActive = state == VpnConnectionState.ACTIVE
    val isPaused = state == VpnConnectionState.PAUSED
    val isConnecting = state == VpnConnectionState.CONNECTING || state == VpnConnectionState.REQUESTING_PERMISSION

    val buttonColor by animateColorAsState(
        targetValue = when (state) {
            VpnConnectionState.ACTIVE -> AdZeroPurple
            VpnConnectionState.PAUSED -> AdZeroWarning
            VpnConnectionState.ERROR -> AdZeroError
            VpnConnectionState.CONNECTING, VpnConnectionState.REQUESTING_PERMISSION -> AdZeroPurpleSoft
            VpnConnectionState.DISCONNECTED -> Color(0xFF8C8A9E)
        },
        label = "protection_btn_color"
    )

    val ringColor = when (state) {
        VpnConnectionState.ACTIVE -> AdZeroPurple.copy(alpha = 0.16f)
        VpnConnectionState.PAUSED -> AdZeroWarning.copy(alpha = 0.16f)
        VpnConnectionState.ERROR -> AdZeroError.copy(alpha = 0.16f)
        else -> AdZeroLavenderBorder.copy(alpha = 0.7f)
    }

    AdZeroCard(
        containerColor = AdZeroLavenderBg,
        contentPadding = 20.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = when (state) {
                        VpnConnectionState.ACTIVE -> "VPN Connected"
                        VpnConnectionState.PAUSED -> "Paused"
                        VpnConnectionState.CONNECTING -> "Establishing VPN…"
                        VpnConnectionState.REQUESTING_PERMISSION -> "Awaiting Consent"
                        VpnConnectionState.ERROR -> "Connection Error"
                        VpnConnectionState.DISCONNECTED -> "VPN Disconnected"
                    },
                    tone = when (state) {
                        VpnConnectionState.ACTIVE -> StatusTone.SUCCESS
                        VpnConnectionState.PAUSED, VpnConnectionState.CONNECTING, VpnConnectionState.REQUESTING_PERMISSION -> StatusTone.WARNING
                        VpnConnectionState.ERROR -> StatusTone.ERROR
                        VpnConnectionState.DISCONNECTED -> StatusTone.NEUTRAL
                    }
                )
                Text(
                    text = uiState.liveSnapshot.activeDnsLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AdZeroMutedText
                )
            }

            Spacer(Modifier.height(18.dp))

            // Large Power Button
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .clip(CircleShape)
                    .background(ringColor)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(buttonColor)
                        .clickable(onClick = onToggleProtection)
                        .semantics {
                            contentDescription = if (isActive) {
                                "Turn off AdZero Protection"
                            } else {
                                "Turn on AdZero Protection"
                            }
                        }
                        .testTag("home_protection_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = AdZeroWhite,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = when (state) {
                    VpnConnectionState.ACTIVE -> "Protection is ON"
                    VpnConnectionState.PAUSED -> "Protection is PAUSED"
                    VpnConnectionState.CONNECTING -> "Starting Local VPN…"
                    VpnConnectionState.REQUESTING_PERMISSION -> "Approve VPN Permission"
                    VpnConnectionState.ERROR -> "Protection Error"
                    VpnConnectionState.DISCONNECTED -> "Protection is OFF"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = AdZeroNavyText
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = when {
                    !uiState.liveSnapshot.errorMessage.isNullOrBlank() -> uiState.liveSnapshot.errorMessage
                    isActive && uiState.liveSnapshot.enforcementMode == VpnEnforcementMode.STRICT_PACKET_ISOLATION ->
                        "Strict Packet Isolation active for blocked apps. System DNS filtering resumes in Standard mode."
                    isActive ->
                        "Local DNS filtering active • Started ${FormattingUtils.formatTime(uiState.liveSnapshot.sessionStartedAt)}"
                    isPaused -> "DNS requests are passing through unfiltered while paused."
                    isConnecting -> "Waiting for Android VpnService interface establishment."
                    uiState.settings.lastProtectionActivatedAt > 0L ->
                        "Last active: ${FormattingUtils.formatDateTime(uiState.settings.lastProtectionActivatedAt)}"
                    else -> "Tap power to establish local on-device DNS protection."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state == VpnConnectionState.ERROR) AdZeroError else AdZeroMutedText
            )

            if (isActive || isPaused) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onPauseOrResume,
                    modifier = Modifier.testTag("home_pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPaused) "Resume Filtering" else "Pause Filtering")
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    subtext: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    AdZeroCard(
        modifier = modifier,
        contentPadding = 14.dp,
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AdZeroMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = JetBrainsMonoFamily,
            color = accent
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelSmall,
            color = AdZeroMutedText
        )
    }
}

@Composable
private fun FeatureShortcutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = AdZeroWhite,
        border = BorderStroke(1.dp, if (active) AdZeroPurple.copy(alpha = 0.25f) else AdZeroLavenderBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) AdZeroLavenderSurface else AdZeroLavenderBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) AdZeroPurple else AdZeroMutedText,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AdZeroNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) AdZeroPurple else AdZeroMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
