package com.example.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryPerformanceScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    val nowMillis = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(uiState.liveSnapshot.sessionStartedAt, uiState.liveSnapshot.isRunning) {
        while (uiState.liveSnapshot.isRunning && uiState.liveSnapshot.sessionStartedAt > 0L) {
            nowMillis.longValue = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val uptime = if (uiState.liveSnapshot.sessionStartedAt > 0L && uiState.liveSnapshot.isRunning) {
        FormattingUtils.formatDuration((nowMillis.longValue - uiState.liveSnapshot.sessionStartedAt).coerceAtLeast(0L))
    } else {
        "Not running"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery & Performance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdZeroWhite)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AdZeroCard(containerColor = AdZeroLavenderBg) {
                    Text("Live Engine Telemetry", style = MaterialTheme.typography.titleMedium, color = AdZeroNavyText)
                    Spacer(Modifier.height(10.dp))
                    KeyValueLine("Protection State", uiState.liveSnapshot.state.name)
                    KeyValueLine("Session Uptime", uptime)
                    KeyValueLine("Last VPN Start / Rebuild", FormattingUtils.formatDateTime(uiState.liveSnapshot.lastRestartAt))
                    KeyValueLine("Session DNS Handled", uiState.liveSnapshot.sessionTotalRequests.toString())
                    KeyValueLine("Bounded LRU Cache", "${uiState.liveSnapshot.cacheSize} / 512 entries (${uiState.liveSnapshot.cacheHits} hits)")
                }
            }

            item {
                AdZeroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Android Battery Optimization", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                        StatusPill(
                            label = if (uiState.batteryOptimizationIgnored) "Unrestricted" else "Optimized",
                            tone = if (uiState.batteryOptimizationIgnored) StatusTone.PRIMARY else StatusTone.SUCCESS
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "AdZero does not request battery optimization exemption by default. In Standard DNS mode, only lightweight DNS UDP packets traverse the local interface, logs are batched every 2.5 seconds, and blocklist updates run only on Wi-Fi via WorkManager. If your OEM aggressively kills the VPN in Doze mode, you can optionally adjust battery settings below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { SystemPermissionHelper.openBatteryOptimizationSettings(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Battery Settings")
                        }
                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear DNS Cache")
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "No Fabricated Battery Figures",
                    message = "Android restricts per-app hardware power meters; check Android Settings -> Battery -> Battery usage for OS-measured power consumption.",
                    tone = StatusTone.NEUTRAL
                )
            }
        }
    }
}

@Composable
private fun KeyValueLine(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
        Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = JetBrainsMonoFamily, color = AdZeroNavyText)
    }
    HorizontalDivider(color = AdZeroLavenderBorder)
}
