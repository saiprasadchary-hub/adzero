package com.example.feature.settings

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.SectionHeader
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.model.VpnConnectionState
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh diagnostics")
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
                AdZeroCard {
                    DiagnosticRow(
                        label = "Local VPN Service",
                        detail = "${uiState.liveSnapshot.state.name} • ${uiState.liveSnapshot.enforcementMode.name}",
                        tone = if (uiState.liveSnapshot.state == VpnConnectionState.ACTIVE) StatusTone.SUCCESS else StatusTone.NEUTRAL
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "VPN Consent Status",
                        detail = if (uiState.vpnPermissionGranted) "Authorized by Android" else "Prompt required on start",
                        tone = if (uiState.vpnPermissionGranted) StatusTone.SUCCESS else StatusTone.WARNING
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "Network Connectivity",
                        detail = if (uiState.isNetworkConnected) "Online" else "No internet capability detected",
                        tone = if (uiState.isNetworkConnected) StatusTone.SUCCESS else StatusTone.ERROR
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "Android Private DNS",
                        detail = uiState.privateDnsMode,
                        tone = if (uiState.privateDnsMode.startsWith("Strict")) StatusTone.WARNING else StatusTone.SUCCESS
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "Notification Access (Ghost Inbox)",
                        detail = if (uiState.notificationAccessEnabled) "Enabled" else "Optional — Not granted",
                        tone = if (uiState.notificationAccessEnabled) StatusTone.SUCCESS else StatusTone.NEUTRAL
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "Accessibility Service (YouTube Skip)",
                        detail = if (uiState.accessibilityServiceEnabled) "Connected" else "Optional — Off",
                        tone = if (uiState.accessibilityServiceEnabled) StatusTone.SUCCESS else StatusTone.NEUTRAL
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "Exact Alarm Scheduling",
                        detail = if (uiState.exactAlarmsAllowed) "Permitted" else "Using WorkManager fallback",
                        tone = if (uiState.exactAlarmsAllowed) StatusTone.SUCCESS else StatusTone.WARNING
                    )
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 8.dp))
                    DiagnosticRow(
                        label = "Android API Level",
                        detail = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        tone = StatusTone.PRIMARY
                    )
                }
            }

            item {
                AdZeroCard {
                    Text("Troubleshooting Shortcuts", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { SystemPermissionHelper.openVpnSettings(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("VPN Settings")
                        }
                        OutlinedButton(
                            onClick = { SystemPermissionHelper.openPrivateDnsSettings(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Private DNS")
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Service Event Log")
            }

            item {
                AdZeroCard {
                    if (uiState.recentEvents.isEmpty()) {
                        Text("No service events recorded yet.", style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
                    } else {
                        uiState.recentEvents.take(20).forEachIndexed { idx, ev ->
                            if (idx > 0) HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "[${ev.eventType}] ${ev.detail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdZeroNavyText
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = FormattingUtils.formatTime(ev.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = AdZeroMutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    detail: String,
    tone: StatusTone
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(
            label = when (tone) {
                StatusTone.SUCCESS -> "OK"
                StatusTone.WARNING -> "Check"
                StatusTone.ERROR -> "Issue"
                StatusTone.PRIMARY -> "Info"
                StatusTone.NEUTRAL -> "Idle"
            },
            tone = tone
        )
    }
}
