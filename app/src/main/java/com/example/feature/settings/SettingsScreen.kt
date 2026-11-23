package com.example.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.StatusTone
import com.example.data.model.DnsProviderPresets
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onToggleAdBlocking: (Boolean) -> Unit,
    onToggleTrackerBlocking: (Boolean) -> Unit,
    onToggleMalwareBlocking: (Boolean) -> Unit,
    onToggleStrictMode: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onOpenBlocklists: () -> Unit,
    onOpenSmartDns: () -> Unit,
    onOpenCustomRules: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGhost: () -> Unit,
    onOpenGhostScheduler: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearCache: () -> Unit,
    onClearAllData: () -> Unit,
    onExportCsv: suspend () -> String,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = runCatching {
                    val csv = onExportCsv()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                onFeedback(
                    if (res.isSuccess) "Exported local DNS history to CSV."
                    else "Export failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = AdZeroNavyText)
                Text("Local privacy controls, network rules & diagnostics", style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
            }
        }

        if (!uiState.feedbackMessage.isNullOrBlank()) {
            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Status",
                    message = uiState.feedbackMessage,
                    tone = StatusTone.PRIMARY
                )
            }
        }

        // 1. PROTECTION
        item {
            SettingsGroup(title = "Protection") {
                SettingsToggleRow(
                    icon = Icons.Filled.Shield,
                    title = "Ad Blocking",
                    subtitle = "Filter advertising domains system-wide",
                    checked = uiState.settings.adBlockingEnabled,
                    onCheckedChange = onToggleAdBlocking,
                    tag = "settings_ad_blocking_switch"
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Filled.Security,
                    title = "Tracker Protection",
                    subtitle = "Block cross-app analytics and telemetry",
                    checked = uiState.settings.trackerBlockingEnabled,
                    onCheckedChange = onToggleTrackerBlocking,
                    tag = "settings_tracker_switch"
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Filled.Policy,
                    title = "Malware & Phishing Guard",
                    subtitle = "Block known malicious domains",
                    checked = uiState.settings.malwareBlockingEnabled,
                    onCheckedChange = onToggleMalwareBlocking,
                    tag = "settings_malware_switch"
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Filled.Tune,
                    title = "Strict Filtering",
                    subtitle = "Add aggressive OEM & telemetry rules",
                    checked = uiState.settings.strictModeEnabled,
                    onCheckedChange = onToggleStrictMode,
                    tag = "settings_strict_switch"
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Filled.VpnKey,
                    title = "Automatic Protection on Boot",
                    subtitle = "Start local VPN after reboot if consent is active",
                    checked = uiState.settings.autoStartOnBoot,
                    onCheckedChange = onToggleAutoStart,
                    tag = "settings_autostart_switch"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "Blocklist Updates",
                    subtitle = "${uiState.blocklistSources.count { it.enabled }} active list(s)",
                    onClick = onOpenBlocklists,
                    tag = "settings_blocklists_row"
                )
            }
        }

        // 2. NETWORK
        item {
            val presetName = DnsProviderPresets.byId(uiState.settings.dnsProviderId).name
            SettingsGroup(title = "Network") {
                SettingsNavRow(
                    icon = Icons.Filled.Dns,
                    title = "Smart DNS & Providers",
                    subtitle = "$presetName • ${uiState.settings.dnsProtocol.name}",
                    onClick = onOpenSmartDns,
                    tag = "settings_smart_dns_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Rule,
                    title = "Custom Allowlist & Blocklist",
                    subtitle = "${uiState.customRules.size} custom rule(s) (wildcards supported)",
                    onClick = onOpenCustomRules,
                    tag = "settings_custom_rules_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.BugReport,
                    title = "VPN & DNS Diagnostics",
                    subtitle = "State: ${uiState.liveSnapshot.state.name}",
                    onClick = onOpenDiagnostics,
                    tag = "settings_diagnostics_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Shield,
                    title = "Add Quick Settings Tile",
                    subtitle = "Toggle AdZero directly from Android Quick Settings",
                    onClick = {
                        SystemPermissionHelper.requestAddQuickSettingsTile(context) { msg ->
                            onFeedback(msg)
                        }
                    },
                    tag = "settings_add_tile_row"
                )
            }
        }

        // 3. WHATSAPP
        item {
            SettingsGroup(title = "WhatsApp") {
                SettingsNavRow(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Ghost Mode & Ghost Inbox",
                    subtitle = if (uiState.settings.ghostModeEnabled) "WhatsApp Blocked" else "Manage inbox & mode",
                    onClick = onOpenGhost,
                    tag = "settings_ghost_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Schedule,
                    title = "Ghost Mode Scheduler",
                    subtitle = "Time windows, weekdays & reboot persistence",
                    onClick = onOpenGhostScheduler,
                    tag = "settings_ghost_scheduler_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Notifications,
                    title = "Notification Access",
                    subtitle = if (uiState.notificationAccessEnabled) "Granted" else "Not enabled",
                    onClick = { SystemPermissionHelper.openNotificationListenerSettings(context) },
                    tag = "settings_notification_access_row"
                )
            }
        }

        // 4. YOUTUBE
        item {
            SettingsGroup(title = "YouTube") {
                SettingsNavRow(
                    icon = Icons.Filled.FastForward,
                    title = "YouTube Skip Assistant",
                    subtitle = if (uiState.settings.youtubeSkipEnabled) {
                        "Enabled • ${uiState.settings.youtubeSkipCount} skip(s)"
                    } else {
                        "Optional • Consent required"
                    },
                    onClick = onOpenYouTube,
                    tag = "settings_youtube_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.AccessibilityNew,
                    title = "Android Accessibility Settings",
                    subtitle = if (uiState.accessibilityServiceEnabled) "Service connected" else "Service off",
                    onClick = { SystemPermissionHelper.openAccessibilitySettings(context) },
                    tag = "settings_accessibility_row"
                )
            }
        }

        // 5. PERFORMANCE
        item {
            SettingsGroup(title = "Performance") {
                SettingsNavRow(
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Battery & Performance",
                    subtitle = if (uiState.batteryOptimizationIgnored) "Unrestricted" else "Standard Android optimization",
                    onClick = onOpenBattery,
                    tag = "settings_battery_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.CleaningServices,
                    title = "Clear Local DNS Cache",
                    subtitle = "${uiState.liveSnapshot.cacheSize} cached entries • ${uiState.liveSnapshot.cacheHits} hits",
                    onClick = onClearCache,
                    tag = "settings_clear_cache_row"
                )
            }
        }

        // 6. PRIVACY
        item {
            SettingsGroup(title = "Privacy & Local Data") {
                SettingsNavRow(
                    icon = Icons.Filled.Download,
                    title = "Export Local DNS History (CSV)",
                    subtitle = "Save analytics via Android Storage Access Framework",
                    onClick = { exportLauncher.launch("adzero_dns_export.csv") },
                    tag = "settings_export_csv_row"
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.DeleteForever,
                    title = "Clear Local Data",
                    subtitle = "Delete DNS logs, Ghost Inbox messages & cache",
                    onClick = { showClearDataDialog = true },
                    tag = "settings_clear_data_row",
                    tintError = true
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Policy,
                    title = "Privacy Policy",
                    subtitle = "100% local, zero accounts, zero analytics SDKs",
                    onClick = onOpenPrivacyPolicy,
                    tag = "settings_privacy_policy_row"
                )
            }
        }

        // 7. ABOUT
        item {
            SettingsGroup(title = "About") {
                SettingsNavRow(
                    icon = Icons.Filled.Info,
                    title = "About AdZero Ultimate",
                    subtitle = "Version 1.0.0 • Release notes & open-source licenses",
                    onClick = onOpenAbout,
                    tag = "settings_about_row"
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Local Data?") },
            text = { Text("This deletes locally stored DNS history, encrypted Ghost Inbox messages, service event logs, and the in-memory DNS cache. Custom rules and settings are preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataDialog = false
                        onClearAllData()
                    }
                ) {
                    Text("Clear Data", color = AdZeroError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = AdZeroPurple,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        AdZeroCard(contentPadding = 0.dp) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AdZeroPurple, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    tintError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (tintError) AdZeroError else AdZeroPurple,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (tintError) AdZeroError else AdZeroNavyText
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AdZeroMutedText
        )
    }
}
