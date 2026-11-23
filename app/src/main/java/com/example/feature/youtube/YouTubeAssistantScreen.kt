package com.example.feature.youtube

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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.feature.settings.SettingsUiState
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAssistantScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSetConsent: (Boolean) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onResetStats: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceConnected = uiState.accessibilityServiceEnabled
    val fullyActive = serviceConnected && uiState.settings.youtubeSkipConsentGranted && uiState.settings.youtubeSkipEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Skip Assistant") },
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
            // Status Card
            item {
                AdZeroCard(containerColor = AdZeroLavenderBg) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Assistant Status", style = MaterialTheme.typography.titleMedium, color = AdZeroNavyText)
                            Text(
                                text = when {
                                    fullyActive -> "Active for YouTube skip controls"
                                    serviceConnected -> "Accessibility service enabled, assistant paused"
                                    else -> "Optional Android AccessibilityService is off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                        }
                        StatusPill(
                            label = if (fullyActive) "Active" else if (serviceConnected) "Ready" else "Off",
                            tone = if (fullyActive) StatusTone.SUCCESS else if (serviceConnected) StatusTone.WARNING else StatusTone.NEUTRAL
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Skip Buttons Activated", style = MaterialTheme.typography.labelSmall, color = AdZeroMutedText)
                            Text(
                                text = uiState.settings.youtubeSkipCount.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = AdZeroPurple
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last Skip", style = MaterialTheme.typography.labelSmall, color = AdZeroMutedText)
                            Text(
                                text = FormattingUtils.formatDateTime(uiState.settings.youtubeLastSkipAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroNavyText
                            )
                            if (uiState.settings.youtubeSkipCount > 0L) {
                                TextButton(onClick = onResetStats) { Text("Reset") }
                            }
                        }
                    }
                }
            }

            // Prominent Accessibility Permission Disclosure & Explicit Consent
            item {
                AdZeroCard {
                    Text("Accessibility Permission Disclosure", style = MaterialTheme.typography.titleMedium, color = AdZeroNavyText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• Strictly scoped to the official YouTube app (com.google.android.youtube).\n" +
                            "• Only inspects UI nodes to locate an available 'Skip' or 'Skip Ad' button.\n" +
                            "• Never reads passwords, chats, form fields, or unrelated apps.\n" +
                            "• Never uploads screen content or automates unrelated taps.\n" +
                            "• Entirely optional and controllable at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uiState.settings.youtubeSkipConsentGranted,
                            onCheckedChange = onSetConsent,
                            modifier = Modifier.testTag("youtube_consent_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "I understand and consent to local YouTube skip-button detection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroNavyText
                        )
                    }

                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Skip Assistant", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                            Text(
                                text = if (uiState.settings.youtubeSkipConsentGranted) {
                                    "Toggle automatic activation of YouTube's Skip Ad button."
                                } else {
                                    "Confirm consent above before enabling."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                        }
                        Switch(
                            checked = uiState.settings.youtubeSkipEnabled,
                            enabled = uiState.settings.youtubeSkipConsentGranted,
                            onCheckedChange = onSetEnabled,
                            modifier = Modifier.testTag("youtube_enable_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (!serviceConnected) {
                        Button(
                            onClick = { SystemPermissionHelper.openAccessibilitySettings(context) },
                            enabled = uiState.settings.youtubeSkipConsentGranted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("youtube_open_accessibility_settings")
                        ) {
                            Icon(Icons.Filled.AccessibilityNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Android Accessibility Settings")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { SystemPermissionHelper.openAccessibilitySettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manage in Android Accessibility Settings")
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Honest Limitations",
                    message = "YouTube Skip Assistant can only tap the Skip button after YouTube makes it clickable. It cannot remove non-skippable video ads, sponsored segments, or feed banners.",
                    tone = StatusTone.WARNING
                )
            }
        }
    }
}
