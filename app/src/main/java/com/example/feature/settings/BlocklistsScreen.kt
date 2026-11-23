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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.common.FormattingUtils
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onManualUpdate: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocklists & Updates") },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Daily Updates", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                            Text(
                                text = "Uses WorkManager on unmetered Wi-Fi when battery is not low. Last sync: ${FormattingUtils.formatDateTime(uiState.settings.lastBlocklistUpdateAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.settings.autoBlocklistUpdateEnabled,
                            onCheckedChange = onToggleAutoUpdate,
                            modifier = Modifier.testTag("blocklists_auto_update_switch")
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (uiState.isUpdatingBlocklists) {
                        LinearProgressIndicator(
                            progress = { uiState.updateProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AdZeroPurple
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!uiState.updateStatusText.isNullOrBlank()) {
                        Text(
                            text = uiState.updateStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onManualUpdate,
                        enabled = !uiState.isUpdatingBlocklists,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocklists_update_now_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isUpdatingBlocklists) "Updating…" else "Update Blocklists Now")
                    }
                }
            }

            items(uiState.blocklistSources, key = { it.id }) { source ->
                AdZeroCard {
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
                                Text(source.name, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText, modifier = Modifier.weight(1f, fill = false))
                                StatusPill(
                                    label = source.status,
                                    tone = when (source.status) {
                                        "UPDATED", "BUNDLED" -> StatusTone.SUCCESS
                                        "UPDATING" -> StatusTone.WARNING
                                        else -> StatusTone.ERROR
                                    }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(source.description, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${source.domainCount} domains • ${source.category} • v${source.versionTag}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = AdZeroPurple
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = source.enabled,
                            onCheckedChange = { onToggleSource(source.id, it) }
                        )
                    }
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Failure-Safe Validation",
                    message = "Downloaded lists are strictly validated for valid domain syntax, size limits, and deduplication. If a download fails or returns malformed data, your previous working blocklist stays active.",
                    tone = StatusTone.NEUTRAL
                )
            }
        }
    }
}
