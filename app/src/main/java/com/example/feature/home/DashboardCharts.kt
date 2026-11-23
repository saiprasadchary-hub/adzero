package com.example.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.core.common.FormattingUtils
import com.example.core.database.TimeBucketProjection
import com.example.core.design.AdZeroCard
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LiveTrafficSample
import com.example.data.model.LiveTrafficSnapshot
import com.example.data.model.NetworkTransportType
import com.example.data.repository.InstalledAppsRepository
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroLavenderSurface
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroSuccess
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdChartMode(val label: String) {
    LIVE_30S("Live 30s"),
    TODAY_HOURLY("Today")
}

@Composable
fun LiveNetworkTrafficCard(
    traffic: LiveTrafficSnapshot,
    isVpnActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transportIcon = when (traffic.transportType) {
        NetworkTransportType.WIFI -> Icons.Filled.Wifi
        NetworkTransportType.CELLULAR -> Icons.Filled.SignalCellularAlt
        NetworkTransportType.OFFLINE -> Icons.Filled.WifiOff
        else -> Icons.Filled.DataUsage
    }
    val transportTone = when (traffic.transportType) {
        NetworkTransportType.OFFLINE -> StatusTone.ERROR
        NetworkTransportType.CELLULAR -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    val transportLabel = buildString {
        append(traffic.transportLabel)
        if (traffic.isMetered && traffic.transportType != NetworkTransportType.OFFLINE) {
            append(" • Metered")
        }
    }

    AdZeroCard(
        modifier = modifier.testTag("home_live_traffic_card"),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AdZeroLavenderSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = transportIcon,
                        contentDescription = null,
                        tint = AdZeroPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Live Network Traffic",
                        style = MaterialTheme.typography.titleMedium,
                        color = AdZeroNavyText
                    )
                    Text(
                        text = if (isVpnActive) "Filtered tunnel • 1s live feed" else "System traffic • 1s live feed",
                        style = MaterialTheme.typography.labelSmall,
                        color = AdZeroMutedText
                    )
                }
            }
            StatusPill(label = transportLabel, tone = transportTone)
        }

        Spacer(Modifier.height(14.dp))

        // Real-time numeric throughput strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AdZeroLavenderBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThroughputMetric(
                label = "Download",
                value = FormattingUtils.formatSpeed(traffic.downloadBytesPerSec),
                accent = AdZeroPurple,
                icon = Icons.Filled.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
            ThroughputMetric(
                label = "Upload",
                value = FormattingUtils.formatSpeed(traffic.uploadBytesPerSec),
                accent = AdZeroSuccess,
                icon = Icons.Filled.ArrowUpward,
                modifier = Modifier.weight(1f)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Session",
                    style = MaterialTheme.typography.labelSmall,
                    color = AdZeroMutedText
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = FormattingUtils.formatBytes(traffic.sessionRxBytes + traffic.sessionTxBytes),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = AdZeroNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Real-time dual-series area & line chart
        LiveThroughputLineChart(
            samples = traffic.samples,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .testTag("home_live_traffic_chart")
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendDot(color = AdZeroPurple, label = "Download")
                ChartLegendDot(color = AdZeroSuccess, label = "Upload")
            }
            Text(
                text = "Peak ↓ ${FormattingUtils.formatSpeed(traffic.peakDownloadBytesPerSec)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = AdZeroMutedText
            )
        }
    }
}

@Composable
private fun ThroughputMetric(
    label: String,
    value: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AdZeroMutedText
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFamily,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LiveThroughputLineChart(
    samples: List<LiveTrafficSample>,
    modifier: Modifier = Modifier
) {
    val windowSize = 30
    val maxObserved = samples.maxOfOrNull { maxOf(it.downloadBytesPerSec, it.uploadBytesPerSec) } ?: 0L
    val yCeiling = maxOf(maxObserved, 4_096L).toFloat()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AdZeroLavenderBg)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val topPad = 14.dp.toPx()
            val bottomPad = 6.dp.toPx()
            val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

            // Dashed horizontal grid lines (0%, 50%, 100%)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = topPad + plotH * fraction
                drawLine(
                    color = AdZeroLavenderBorder,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (fraction < 1f) dashEffect else null
                )
            }

            if (samples.isEmpty()) return@Canvas

            val paddedCount = maxOf(windowSize, samples.size)
            val startOffset = paddedCount - samples.size
            val stepX = if (paddedCount > 1) w / (paddedCount - 1).toFloat() else w

            fun buildCurvePoints(selector: (LiveTrafficSample) -> Long): List<Offset> {
                return samples.mapIndexed { idx, sample ->
                    val x = (startOffset + idx) * stepX
                    val ratio = (selector(sample).toFloat() / yCeiling).coerceIn(0f, 1f)
                    val y = topPad + plotH * (1f - ratio)
                    Offset(x, y)
                }
            }

            fun drawSeries(
                points: List<Offset>,
                lineColor: Color,
                fillTopAlpha: Float,
                strokeDp: Float
            ) {
                if (points.isEmpty()) return
                if (points.size == 1) {
                    val p = points.first()
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = p)
                    return
                }

                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val midX = (prev.x + curr.x) * 0.5f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }

                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, topPad + plotH)
                    lineTo(points.first().x, topPad + plotH)
                    close()
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = fillTopAlpha),
                            lineColor.copy(alpha = 0.01f)
                        ),
                        startY = topPad,
                        endY = topPad + plotH
                    )
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = strokeDp.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                val latest = points.last()
                drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 6.dp.toPx(), center = latest)
                drawCircle(color = lineColor, radius = 3.2.dp.toPx(), center = latest)
            }

            // Draw upload first, download on top
            drawSeries(
                points = buildCurvePoints { it.uploadBytesPerSec },
                lineColor = AdZeroSuccess,
                fillTopAlpha = 0.14f,
                strokeDp = 1.8f
            )
            drawSeries(
                points = buildCurvePoints { it.downloadBytesPerSec },
                lineColor = AdZeroPurple,
                fillTopAlpha = 0.22f,
                strokeDp = 2.4f
            )
        }

        // Top-right auto-scaled ceiling label
        Text(
            text = FormattingUtils.formatSpeed(yCeiling.toLong()),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = AdZeroMutedText,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        Text(
            text = "0 B/s",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = AdZeroMutedText,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
fun LiveAdBlockChartCard(
    traffic: LiveTrafficSnapshot,
    hourlyBuckets: List<TimeBucketProjection>,
    adsBlockedToday: Long,
    trackersBlockedToday: Long,
    dnsFilteredToday: Long,
    isVpnActive: Boolean,
    onOpenAnalytics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chartMode by remember { mutableStateOf(AdChartMode.TODAY_HOURLY) }

    val totalBlockedToday = adsBlockedToday + trackersBlockedToday
    val allowedToday = (dnsFilteredToday - totalBlockedToday).coerceAtLeast(0L)
    val blockPercent = if (dnsFilteredToday > 0L) {
        ((totalBlockedToday * 100L) / dnsFilteredToday).toInt()
    } else {
        0
    }

    AdZeroCard(
        modifier = modifier.testTag("home_adblock_chart_card"),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blocked Ads & DNS Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = AdZeroNavyText
                )
                Text(
                    text = if (dnsFilteredToday > 0L) {
                        "$blockPercent% blocked today • Real-time counts"
                    } else {
                        "Live counts update as DNS traffic is filtered"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AdZeroMutedText
                )
            }
            StatusPill(
                label = "$blockPercent% Blocked",
                tone = if (blockPercent > 0) StatusTone.PRIMARY else StatusTone.NEUTRAL
            )
        }

        Spacer(Modifier.height(12.dp))

        // Mode switcher (Today Hourly vs Live 30s)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdChartMode.entries.forEach { mode ->
                    val selected = chartMode == mode
                    Surface(
                        onClick = { chartMode = mode },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) AdZeroLavenderSurface else AdZeroLavenderBg,
                        border = BorderStroke(
                            1.dp,
                            if (selected) AdZeroPurple.copy(alpha = 0.4f) else AdZeroLavenderBorder
                        ),
                        modifier = Modifier.testTag("chart_mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) AdZeroPurple else AdZeroMutedText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            TextButton(onClick = onOpenAnalytics) {
                Text("Full Analytics")
            }
        }

        Spacer(Modifier.height(10.dp))

        when (chartMode) {
            AdChartMode.LIVE_30S -> {
                val hasLiveDns = traffic.samples.any {
                    it.blockedAdsDelta > 0 || it.blockedTrackersDelta > 0 || it.allowedDnsDelta > 0
                }
                LiveDnsStackedBarChart(
                    samples = traffic.samples,
                    hasData = hasLiveDns,
                    isVpnActive = isVpnActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .testTag("home_live_dns_chart")
                )
            }
            AdChartMode.TODAY_HOURLY -> {
                HourlyAdBlockBarChart(
                    buckets = hourlyBuckets,
                    isVpnActive = isVpnActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .testTag("home_hourly_adblock_chart")
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(
                color = AdZeroPurple,
                label = "Ads (${FormattingUtils.formatCompactCount(adsBlockedToday)})"
            )
            ChartLegendDot(
                color = AdZeroError,
                label = "Trackers (${FormattingUtils.formatCompactCount(trackersBlockedToday)})"
            )
            ChartLegendDot(
                color = Color(0xFFD2CEE8),
                label = "Allowed (${FormattingUtils.formatCompactCount(allowedToday)})"
            )
        }
    }
}

@Composable
private fun LiveDnsStackedBarChart(
    samples: List<LiveTrafficSample>,
    hasData: Boolean,
    isVpnActive: Boolean,
    modifier: Modifier = Modifier
) {
    val allowedBarColor = Color(0xFFD2CEE8)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AdZeroLavenderBg)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!hasData) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isVpnActive) "Listening for live DNS requests…" else "Protection is currently OFF",
                    style = MaterialTheme.typography.labelLarge,
                    color = AdZeroNavyText
                )
                Text(
                    text = if (isVpnActive) {
                        "Blocked ads and DNS traffic appear here in real time every second."
                    } else {
                        "Turn ON Protection above to visualize live blocked ads."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AdZeroMutedText
                )
            }
        } else {
            val maxTotal = samples.maxOfOrNull {
                it.blockedAdsDelta + it.blockedTrackersDelta + it.allowedDnsDelta
            }?.coerceAtLeast(1) ?: 1

            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = 30
                val startOffset = (count - samples.size).coerceAtLeast(0)
                val gap = 3.dp.toPx()
                val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
                val plotH = size.height - 6.dp.toPx()

                // Baseline
                drawLine(
                    color = AdZeroLavenderBorder,
                    start = Offset(0f, plotH),
                    end = Offset(size.width, plotH),
                    strokeWidth = 1.dp.toPx()
                )

                samples.forEachIndexed { idx, sample ->
                    val total = sample.blockedAdsDelta + sample.blockedTrackersDelta + sample.allowedDnsDelta
                    if (total <= 0) return@forEachIndexed
                    val x = (startOffset + idx) * (barW + gap)
                    val totalH = (total.toFloat() / maxTotal.toFloat()) * (plotH - 8.dp.toPx())
                    val adsH = (sample.blockedAdsDelta.toFloat() / total) * totalH
                    val trackersH = (sample.blockedTrackersDelta.toFloat() / total) * totalH
                    val allowedH = (sample.allowedDnsDelta.toFloat() / total) * totalH

                    var currentBottom = plotH
                    if (adsH > 0f) {
                        drawRoundRect(
                            color = AdZeroPurple,
                            topLeft = Offset(x, currentBottom - adsH),
                            size = Size(barW, adsH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                        currentBottom -= adsH
                    }
                    if (trackersH > 0f) {
                        drawRoundRect(
                            color = AdZeroError,
                            topLeft = Offset(x, currentBottom - trackersH),
                            size = Size(barW, trackersH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                        currentBottom -= trackersH
                    }
                    if (allowedH > 0f) {
                        drawRoundRect(
                            color = allowedBarColor,
                            topLeft = Offset(x, currentBottom - allowedH),
                            size = Size(barW, allowedH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyAdBlockBarChart(
    buckets: List<TimeBucketProjection>,
    isVpnActive: Boolean,
    modifier: Modifier = Modifier
) {
    val allowedBarColor = Color(0xFFD2CEE8)
    val hourFormatter = remember { SimpleDateFormat("ha", Locale.getDefault()) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AdZeroLavenderBg)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (buckets.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isVpnActive) "No DNS queries recorded today yet" else "No DNS activity recorded today",
                    style = MaterialTheme.typography.labelLarge,
                    color = AdZeroNavyText
                )
                Text(
                    text = "Hourly blocked ad & tracker bars populate automatically from real on-device DNS logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdZeroMutedText
                )
            }
        } else {
            val visible = buckets.takeLast(12)
            val maxTotal = visible.maxOfOrNull { it.blockedCount + it.allowedCount }.coerceAtLeastOrOne()
            val hourMillis = java.util.concurrent.TimeUnit.HOURS.toMillis(1)

            Column(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val gap = 8.dp.toPx()
                    val count = visible.size.coerceAtLeast(1)
                    val barW = ((size.width - gap * (count - 1)) / count).coerceIn(6.dp.toPx(), 26.dp.toPx())
                    val totalBarsWidth = barW * count + gap * (count - 1)
                    val startX = ((size.width - totalBarsWidth) / 2f).coerceAtLeast(0f)
                    val plotH = size.height - 4.dp.toPx()

                    drawLine(
                        color = AdZeroLavenderBorder,
                        start = Offset(0f, plotH),
                        end = Offset(size.width, plotH),
                        strokeWidth = 1.dp.toPx()
                    )

                    visible.forEachIndexed { idx, bucket ->
                        val x = startX + idx * (barW + gap)
                        val total = bucket.blockedCount + bucket.allowedCount
                        val totalH = (total.toFloat() / maxTotal.toFloat()) * (plotH - 6.dp.toPx())
                        val blockedH = if (total > 0L) {
                            (bucket.blockedCount.toFloat() / total.toFloat()) * totalH
                        } else {
                            0f
                        }
                        val allowedH = (totalH - blockedH).coerceAtLeast(0f)

                        if (allowedH > 0f) {
                            drawRoundRect(
                                color = allowedBarColor,
                                topLeft = Offset(x, plotH - totalH),
                                size = Size(barW, allowedH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        if (blockedH > 0f) {
                            drawRoundRect(
                                color = AdZeroPurple,
                                topLeft = Offset(x, plotH - blockedH),
                                size = Size(barW, blockedH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val firstLabel = hourFormatter.format(Date(visible.first().bucketIndex * hourMillis)).lowercase()
                    val lastLabel = hourFormatter.format(Date(visible.last().bucketIndex * hourMillis)).lowercase()
                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = AdZeroMutedText
                    )
                    Text(
                        text = "Peak: $maxTotal req/hr",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = AdZeroMutedText
                    )
                    Text(
                        text = lastLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = AdZeroMutedText
                    )
                }
            }
        }
    }
}

private fun Long?.coerceAtLeastOrOne(): Long = (this ?: 1L).coerceAtLeast(1L)

@Composable
fun TopAppTrafficQuickControlCard(
    apps: List<InstalledAppInfo>,
    hasUsageStatsAccess: Boolean,
    isVpnActive: Boolean,
    totalTrackedRxBytes: Long,
    totalTrackedTxBytes: Long,
    installedAppsRepository: InstalledAppsRepository,
    onToggleAppInternet: (packageName: String, appName: String, blockInternet: Boolean) -> Unit,
    onGrantUsageAccess: () -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    AdZeroCard(
        modifier = modifier.testTag("home_top_apps_card"),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Per-App Traffic & Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = AdZeroNavyText
                )
                Text(
                    text = if (hasUsageStatsAccess) {
                        "Today: ↓ ${FormattingUtils.formatBytes(totalTrackedRxBytes)} • ↑ ${FormattingUtils.formatBytes(totalTrackedTxBytes)}"
                    } else {
                        "Toggle internet access or grant Usage Access for per-app bytes"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AdZeroMutedText
                )
            }
            TextButton(
                onClick = onOpenAllApps,
                modifier = Modifier.testTag("dashboard_manage_all_apps_button")
            ) {
                Text("All Apps")
            }
        }

        if (!hasUsageStatsAccess) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AdZeroLavenderBg,
                border = BorderStroke(1.dp, AdZeroLavenderBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Per-App Usage Access",
                            style = MaterialTheme.typography.labelLarge,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = "Lets Android's NetworkStatsManager report exact Wi-Fi & Cellular data per app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onGrantUsageAccess,
                        modifier = Modifier.testTag("dashboard_grant_usage_access_button")
                    ) {
                        Text("Enable")
                    }
                }
            }
        }

        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            apps.forEachIndexed { idx, app ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = AdZeroLavenderBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                DashboardAppTrafficRow(
                    app = app,
                    installedAppsRepository = installedAppsRepository,
                    onToggleInternet = { allow ->
                        onToggleAppInternet(app.packageName, app.appName, !allow)
                    },
                    onClickRow = onOpenAllApps
                )
            }
        }

        if (!isVpnActive && apps.any { it.internetBlocked }) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Turn ON Protection above to enforce saved per-app internet blocks.",
                style = MaterialTheme.typography.labelSmall,
                color = AdZeroMutedText
            )
        }
    }
}

@Composable
private fun DashboardAppTrafficRow(
    app: InstalledAppInfo,
    installedAppsRepository: InstalledAppsRepository,
    onToggleInternet: (Boolean) -> Unit,
    onClickRow: () -> Unit
) {
    val bitmap = remember(app.packageName) {
        runCatching {
            installedAppsRepository.getAppIcon(app.packageName)?.toBitmap(72, 72)?.asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AdZeroLavenderBg),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    tint = AdZeroPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AdZeroNavyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.liveBytesPerSec > 0L) {
                    StatusPill(
                        label = FormattingUtils.formatSpeed(app.liveBytesPerSec),
                        tone = StatusTone.SUCCESS
                    )
                }
            }
            val usageLabel = if (app.totalBytesToday > 0L) {
                "↓ ${FormattingUtils.formatBytes(app.rxBytesToday)} • ↑ ${FormattingUtils.formatBytes(app.txBytesToday)}"
            } else if (app.requestCount > 0L) {
                "${app.requestCount} DNS req • ${app.blockedCount} blocked"
            } else {
                "0 B today • ${if (app.internetBlocked) "Blocked" else "Allowed"}"
            }
            Text(
                text = usageLabel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFamily,
                color = AdZeroMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = !app.internetBlocked,
            onCheckedChange = onToggleInternet,
            modifier = Modifier.testTag("dashboard_app_switch_${app.packageName}")
        )
    }
}

@Composable
private fun ChartLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AdZeroMutedText
        )
    }
}
