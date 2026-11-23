package com.example.feature.activity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.FormattingUtils
import com.example.core.database.TimeBucketProjection
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.SectionHeader
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.repository.InstalledAppsRepository
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroPurpleSoft
import com.example.ui.theme.AdZeroSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ActivityAnalyticsScreen(
    uiState: ActivityUiState,
    installedAppsRepository: InstalledAppsRepository,
    onSelectRange: (AnalyticsRange) -> Unit,
    onSelectActionFilter: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    onQuickAllowDomain: (String) -> Unit,
    onQuickBlockDomain: (String) -> Unit,
    onClearHistory: () -> Unit,
    onExportCsv: suspend () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val csv = onExportCsv()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                exportFeedback = if (result.isSuccess) {
                    "Exported DNS activity log to CSV successfully."
                } else {
                    "Export failed: ${result.exceptionOrNull()?.message ?: "Unable to write file."}"
                }
            }
        }
    }

    val total = uiState.overview.totalRequests
    val blockPct = if (total > 0) (uiState.overview.blockedRequests * 100.0 / total) else 0.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Network Activity",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AdZeroNavyText
                    )
                    Text(
                        text = "Observed local DNS engine analytics",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { csvExportLauncher.launch("adzero_dns_activity.csv") },
                        modifier = Modifier.testTag("activity_export_csv")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV", tint = AdZeroPurple)
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("activity_clear_history")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear history", tint = AdZeroError)
                    }
                }
            }
        }

        if (exportFeedback != null) {
            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Export Status",
                    message = exportFeedback!!,
                    tone = StatusTone.PRIMARY
                )
            }
        }

        // Range Selector
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AnalyticsRange.entries) { range ->
                    FilterChip(
                        selected = uiState.selectedRange == range,
                        onClick = { onSelectRange(range) },
                        label = { Text(range.label) }
                    )
                }
            }
        }

        // Overview Metrics Grid
        item {
            AdZeroCard(containerColor = AdZeroLavenderBg) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricCol("Total DNS", FormattingUtils.formatCompactCount(total), AdZeroNavyText, Modifier.weight(1f))
                    MetricCol("Blocked", FormattingUtils.formatCompactCount(uiState.overview.blockedRequests), AdZeroPurple, Modifier.weight(1f))
                    MetricCol("Allowed", FormattingUtils.formatCompactCount(uiState.overview.allowedRequests), AdZeroSuccess, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = AdZeroLavenderBorder)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricCol("Block Rate", String.format("%.1f%%", blockPct), AdZeroPurple, Modifier.weight(1f))
                    MetricCol("Domains", FormattingUtils.formatCompactCount(uiState.overview.uniqueDomains), AdZeroNavyText, Modifier.weight(1f))
                    MetricCol("Active Apps", uiState.overview.activeApps.toString(), AdZeroNavyText, Modifier.weight(1f))
                }
            }
        }

        // Activity Chart (Blocked vs Allowed)
        item {
            AdZeroCard {
                SectionHeader(
                    title = "${uiState.selectedRange.label} Activity",
                    subtitle = "Purple = Blocked • Soft Lavender = Allowed"
                )
                Spacer(Modifier.height(12.dp))
                if (uiState.buckets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No observed DNS buckets in this timeframe yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                } else {
                    ActivityBarChart(
                        buckets = uiState.buckets.takeLast(14),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }
        }

        // App-Wise Analytics
        item {
            SectionHeader(
                title = "App-Wise DNS Activity",
                subtitle = "OS-unattributable queries are labeled System / Unknown"
            )
        }
        item {
            AdZeroCard {
                if (uiState.appTraffic.isEmpty()) {
                    Text(
                        text = "No per-app DNS queries recorded in this window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                } else {
                    uiState.appTraffic.take(8).forEachIndexed { idx, app ->
                        if (idx > 0) HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                        val label = remember(app.packageName) {
                            installedAppsRepository.resolveAppName(app.packageName)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = AdZeroMutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${app.totalRequests} req",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = AdZeroNavyText
                                )
                                Text(
                                    text = "${app.blockedRequests} blocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AdZeroPurple
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Blocked Domains
        item {
            SectionHeader(title = "Top Blocked Domains")
        }
        item {
            AdZeroCard {
                if (uiState.topBlockedDomains.isEmpty()) {
                    Text(
                        text = "No blocked domains recorded in this window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                } else {
                    uiState.topBlockedDomains.take(8).forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = AdZeroNavyText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.category} • ${item.matchedRule} • ${item.hitCount}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AdZeroMutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { onQuickAllowDomain(item.domain) }) {
                                Text("Allow")
                            }
                        }
                    }
                }
            }
        }

        // Live DNS History & Retention
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "DNS Request History",
                    subtitle = "Retention: ${uiState.retentionDays} day(s)"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 7, 30).forEach { days ->
                        FilterChip(
                            selected = uiState.retentionDays == days,
                            onClick = { onSetRetentionDays(days) },
                            label = { Text("Keep ${days}d") }
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.logSearchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activity_search_input"),
                    placeholder = { Text("Filter history by domain, app, or rule…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.logSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All", "BLOCKED" to "Blocked", "ALLOWED" to "Allowed").forEach { (key, label) ->
                        FilterChip(
                            selected = uiState.logFilterAction == key,
                            onClick = { onSelectActionFilter(key) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (uiState.recentLogs.isEmpty()) {
            item {
                AdZeroCard {
                    Text(
                        text = "No DNS requests match this filter yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                }
            }
        } else {
            items(uiState.recentLogs.take(60), key = { it.id }) { log ->
                AdZeroCard(contentPadding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.domain,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = AdZeroNavyText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (log.packageName == "unknown") "System / Unknown" else log.packageName} • ${log.matchedRule} • ${FormattingUtils.formatTime(log.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AdZeroMutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(
                                label = log.action,
                                tone = if (log.action == "BLOCKED") StatusTone.ERROR else StatusTone.SUCCESS
                            )
                            TextButton(
                                onClick = {
                                    if (log.action == "BLOCKED") onQuickAllowDomain(log.domain)
                                    else onQuickBlockDomain(log.domain)
                                }
                            ) {
                                Text(if (log.action == "BLOCKED") "Allow" else "Block")
                            }
                        }
                    }
                }
            }
        }

        item {
            InfoBanner(
                icon = Icons.Outlined.Info,
                title = "Analytics Transparency",
                message = "Only DNS queries actually processed by AdZero's local VPN engine are shown here. Direct-IP traffic, apps excluded from DNS filtering, or traffic using hardcoded DoH cannot be observed by local DNS filtering.",
                tone = StatusTone.NEUTRAL
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Analytics History?") },
            text = { Text("Deletes all locally stored DNS request logs and resets charts to zero.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    }
                ) {
                    Text("Clear", color = AdZeroError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricCol(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AdZeroMutedText)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = JetBrainsMonoFamily,
            color = color
        )
    }
}

@Composable
private fun ActivityBarChart(
    buckets: List<TimeBucketProjection>,
    modifier: Modifier = Modifier
) {
    val maxTotal = (buckets.maxOfOrNull { it.blockedCount + it.allowedCount } ?: 1L).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        val count = buckets.size.coerceAtLeast(1)
        val gap = 8.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(6.dp.toPx())
        val chartHeight = size.height - 8.dp.toPx()

        buckets.forEachIndexed { index, bucket ->
            val x = index * (barWidth + gap)
            val totalHeight = ((bucket.blockedCount + bucket.allowedCount).toFloat() / maxTotal.toFloat()) * chartHeight
            val blockedHeight = (bucket.blockedCount.toFloat() / maxTotal.toFloat()) * chartHeight

            // Allowed background bar
            if (totalHeight > 0f) {
                drawRoundRect(
                    color = AdZeroPurpleSoft.copy(alpha = 0.35f),
                    topLeft = Offset(x, size.height - totalHeight),
                    size = Size(barWidth, totalHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
            // Blocked portion
            if (blockedHeight > 0f) {
                drawRoundRect(
                    color = AdZeroPurple,
                    topLeft = Offset(x, size.height - blockedHeight),
                    size = Size(barWidth, blockedHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
    }
}
