package com.example.feature.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.model.InstalledAppInfo
import com.example.data.repository.InstalledAppsRepository
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroLavenderSurface
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroSuccess
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsControlScreen(
    uiState: AppsUiState,
    installedAppsRepository: InstalledAppsRepository,
    onSearchChange: (String) -> Unit,
    onFilterChange: (AppFilterTab) -> Unit,
    onSortChange: (AppSortOrder) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onUpdateAppRule: (String, String, Boolean, Boolean) -> Unit,
    onToggleStrictIsolation: (Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onRefreshApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apps Control",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AdZeroNavyText
                    )
                    Text(
                        text = "${uiState.apps.size} app(s) shown • Separate firewall & DNS rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                }
                TextButton(
                    onClick = { onSortChange(uiState.sortOrder.next()) },
                    modifier = Modifier.testTag("apps_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort apps",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (uiState.sortOrder) {
                            AppSortOrder.DATA_USAGE -> "Data"
                            AppSortOrder.ACTIVITY -> "DNS"
                            AppSortOrder.NAME -> "Name"
                        }
                    )
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apps_search_input"),
                placeholder = { Text("Search installed apps or package…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppFilterTab.entries) { tab ->
                    FilterChip(
                        selected = uiState.selectedFilter == tab,
                        onClick = { onFilterChange(tab) },
                        label = { Text(tab.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdZeroLavenderSurface,
                            selectedLabelColor = AdZeroPurple
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.showSystemApps,
                        onClick = { onToggleSystemApps(!uiState.showSystemApps) },
                        label = { Text("System Apps") }
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdZeroPurple)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!uiState.statusBannerMessage.isNullOrBlank()) {
                    item {
                        AdZeroCard(containerColor = AdZeroLavenderSurface, contentPadding = 12.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.statusBannerMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdZeroNavyText,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onDismissBanner, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AdZeroMutedText)
                                }
                            }
                        }
                    }
                }

                // Live Per-App Data Usage Monitor Card
                item {
                    AdZeroCard(
                        containerColor = AdZeroLavenderBg,
                        contentPadding = 14.dp,
                        modifier = Modifier.testTag("apps_traffic_monitor_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Per-App Traffic Monitor",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AdZeroNavyText
                                    )
                                    StatusPill(
                                        label = if (uiState.isTrafficMonitoringActive) "Live" else "Idle",
                                        tone = if (uiState.isTrafficMonitoringActive) StatusTone.SUCCESS else StatusTone.NEUTRAL
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                if (uiState.hasUsageStatsAccess) {
                                    Text(
                                        text = "Today across apps: ↓ ${FormattingUtils.formatBytes(uiState.totalTrackedRxBytes)} • ↑ ${FormattingUtils.formatBytes(uiState.totalTrackedTxBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = AdZeroMutedText
                                    )
                                } else {
                                    Text(
                                        text = "Grant Android Usage Access so NetworkStatsManager can track exact Wi-Fi & Cellular data per app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdZeroMutedText
                                    )
                                }
                            }
                            if (!uiState.hasUsageStatsAccess) {
                                Spacer(Modifier.width(10.dp))
                                FilledTonalButton(
                                    onClick = { SystemPermissionHelper.openUsageAccessSettings(context) },
                                    modifier = Modifier.testTag("apps_enable_usage_access_button")
                                ) {
                                    Text("Enable")
                                }
                            }
                        }
                    }
                }

                val hasBlockedApps = uiState.apps.any { it.internetBlocked }
                if (hasBlockedApps || uiState.enforcementMode == com.example.data.model.VpnEnforcementMode.STRICT_PACKET_ISOLATION) {
                    item {
                        AdZeroCard(containerColor = AdZeroLavenderBg, contentPadding = 14.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Kernel Packet Isolation (Blocked Apps)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AdZeroNavyText
                                    )
                                    Text(
                                        text = "Routes 100% of packets from blocked apps into a local null-route tunnel while keeping allowed apps online.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdZeroMutedText
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Switch(
                                    checked = uiState.enforcementMode == com.example.data.model.VpnEnforcementMode.STRICT_PACKET_ISOLATION,
                                    onCheckedChange = onToggleStrictIsolation,
                                    modifier = Modifier.testTag("apps_strict_isolation_switch")
                                )
                            }
                        }
                    }
                }

                if (!uiState.isVpnActive) {
                    item {
                        InfoBanner(
                            icon = Icons.Outlined.Info,
                            title = "Protection Currently Inactive",
                            message = "Per-app firewall and DNS exclusion rules are saved locally and take effect only while AdZero Protection is ON.",
                            tone = StatusTone.WARNING
                        )
                    }
                }

                if (uiState.apps.isEmpty()) {
                    item {
                        AdZeroCard {
                            Text(
                                text = "No matching installed applications",
                                style = MaterialTheme.typography.titleSmall,
                                color = AdZeroNavyText
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Try clearing your search or filter selection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                        }
                    }
                } else {
                    items(uiState.apps, key = { it.packageName }) { app ->
                        AppControlRowCard(
                            app = app,
                            installedAppsRepository = installedAppsRepository,
                            onQuickFirewallToggle = { block ->
                                onUpdateAppRule(app.packageName, app.appName, block, false)
                            },
                            onOpenDetails = { selectedApp = app }
                        )
                    }
                }

                item {
                    InfoBanner(
                        icon = Icons.Outlined.Info,
                        title = "How Per-App Controls Work",
                        message = "Blocking an app's network access prevents it from connecting rather than selectively hiding delivery receipts. Android VpnService per-app routing is applied when the VPN interface is created, so changing rules briefly rebuilds the local VPN interface.",
                        tone = StatusTone.NEUTRAL
                    )
                }
            }
        }
    }

    // Per-App Detailed Control Sheet (Separate Internet Block vs. DNS Exclusion)
    selectedApp?.let { app ->
        val latest = uiState.apps.firstOrNull { it.packageName == app.packageName } ?: app
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconBox(latest.packageName, installedAppsRepository)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = latest.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = latest.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = AdZeroMutedText
                        )
                    }
                }

                // Per-App Live & Today Data Usage Summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AdZeroLavenderBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Downloaded Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdZeroMutedText
                        )
                        Text(
                            text = FormattingUtils.formatBytes(latest.rxBytesToday),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = AdZeroPurple
                        )
                    }
                    Column {
                        Text(
                            text = "Uploaded Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdZeroMutedText
                        )
                        Text(
                            text = FormattingUtils.formatBytes(latest.txBytesToday),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = AdZeroSuccess
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DNS Requests",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdZeroMutedText
                        )
                        Text(
                            text = "${latest.requestCount} (${latest.blockedCount} blk)",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = AdZeroNavyText
                        )
                    }
                }

                HorizontalDivider(color = AdZeroLavenderBorder)

                // Control 1: Block Internet Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Block Internet Access",
                            style = MaterialTheme.typography.titleSmall,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = "Prevents this app from resolving domains or connecting while VPN is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = latest.internetBlocked,
                        onCheckedChange = { blocked ->
                            onUpdateAppRule(latest.packageName, latest.appName, blocked, false)
                        },
                        modifier = Modifier.testTag("sheet_block_internet_switch")
                    )
                }

                // Control 2: Exclude from DNS Filtering
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Exclude from DNS Filtering",
                            style = MaterialTheme.typography.titleSmall,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = "Bypasses AdZero's local VPN completely via Android's disallowed-app list (useful for banking or carrier apps).",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = latest.dnsExcluded,
                        enabled = !latest.internetBlocked,
                        onCheckedChange = { excluded ->
                            onUpdateAppRule(latest.packageName, latest.appName, false, excluded)
                        },
                        modifier = Modifier.testTag("sheet_exclude_dns_switch")
                    )
                }

                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Rule Enforcement",
                    message = if (latest.internetBlocked) {
                        "Internet access is blocked for ${latest.appName}. Enable Strict Packet Isolation in Settings if you also need to sever already-open direct-IP connections."
                    } else if (latest.dnsExcluded) {
                        "${latest.appName} bypasses AdZero DNS filtering and connects directly."
                    } else {
                        "${latest.appName} has normal internet access with Ad & Tracker DNS filtering."
                    },
                    tone = if (latest.internetBlocked) StatusTone.ERROR else StatusTone.PRIMARY
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AppControlRowCard(
    app: InstalledAppInfo,
    installedAppsRepository: InstalledAppsRepository,
    onQuickFirewallToggle: (Boolean) -> Unit,
    onOpenDetails: () -> Unit
) {
    AdZeroCard(
        contentPadding = 14.dp,
        onClick = onOpenDetails,
        modifier = Modifier.testTag("app_card_${app.packageName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconBox(app.packageName, installedAppsRepository)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AdZeroNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = AdZeroMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        app.internetBlocked -> StatusPill("Internet Blocked", StatusTone.ERROR)
                        app.dnsExcluded -> StatusPill("DNS Excluded", StatusTone.WARNING)
                        else -> StatusPill("DNS Filtered", StatusTone.SUCCESS)
                    }
                    if (app.liveBytesPerSec > 0L) {
                        StatusPill(FormattingUtils.formatSpeed(app.liveBytesPerSec), StatusTone.PRIMARY)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val trafficMeta = buildString {
                    append("↓ ${FormattingUtils.formatBytes(app.rxBytesToday)} • ↑ ${FormattingUtils.formatBytes(app.txBytesToday)}")
                    if (app.requestCount > 0L) {
                        append("  |  ${app.requestCount} DNS (${app.blockedCount} blk)")
                    }
                }
                Text(
                    text = trafficMeta,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = AdZeroMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = !app.internetBlocked,
                    onCheckedChange = { allowed -> onQuickFirewallToggle(!allowed) },
                    modifier = Modifier.testTag("app_internet_switch_${app.packageName}")
                )
                Text(
                    text = if (app.internetBlocked) "Blocked" else "Online",
                    style = MaterialTheme.typography.labelSmall,
                    color = AdZeroMutedText
                )
            }
        }
    }
}

@Composable
private fun AppIconBox(
    packageName: String,
    installedAppsRepository: InstalledAppsRepository
) {
    val bitmap = remember(packageName) {
        runCatching {
            installedAppsRepository.getAppIcon(packageName)?.toBitmap(96, 96)?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AdZeroLavenderBg),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$packageName icon",
                modifier = Modifier.size(38.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                tint = AdZeroPurple,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
