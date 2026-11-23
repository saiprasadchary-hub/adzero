package com.example.feature.dns

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.SectionHeader
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.model.DnsProtocol
import com.example.data.model.DnsProviderPresets
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroSuccess
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDnsScreen(
    uiState: SmartDnsUiState,
    onBack: () -> Unit,
    onSaveConfig: (String, DnsProtocol, String?, String?, String?) -> Unit,
    onResetDefault: () -> Unit,
    onTestConnection: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var customIpv4 by remember(uiState.settings.customDnsIpv4) { mutableStateOf(uiState.settings.customDnsIpv4) }
    var customDot by remember(uiState.settings.customDotHostname) { mutableStateOf(uiState.settings.customDotHostname) }
    var customDoh by remember(uiState.settings.customDohUrl) { mutableStateOf(uiState.settings.customDohUrl) }

    val selectedPreset = DnsProviderPresets.byId(uiState.settings.dnsProviderId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart DNS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onResetDefault) {
                        Text("Reset")
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
            if (!uiState.feedbackMessage.isNullOrBlank()) {
                item {
                    InfoBanner(
                        icon = Icons.Outlined.Info,
                        title = "DNS Updated",
                        message = uiState.feedbackMessage,
                        tone = StatusTone.SUCCESS
                    )
                }
            }

            // Active DNS Status & Connection Probe Card
            item {
                AdZeroCard(containerColor = AdZeroLavenderBg) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedPreset.name, style = MaterialTheme.typography.titleMedium, color = AdZeroNavyText)
                            Text(
                                text = when (uiState.settings.dnsProtocol) {
                                    DnsProtocol.DOH -> if (selectedPreset.id == "custom") uiState.settings.customDohUrl else selectedPreset.dohUrl
                                    DnsProtocol.DOT -> if (selectedPreset.id == "custom") uiState.settings.customDotHostname else selectedPreset.dotHostname
                                    DnsProtocol.UDP -> if (selectedPreset.id == "custom") uiState.settings.customDnsIpv4 else selectedPreset.ipv4Primary
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = AdZeroMutedText
                            )
                        }
                        StatusPill(
                            label = uiState.settings.dnsProtocol.name,
                            tone = if (uiState.settings.dnsProtocol != DnsProtocol.UDP) StatusTone.SUCCESS else StatusTone.WARNING
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val probe = uiState.lastProbeResult
                    if (probe != null) {
                        Text(
                            text = if (probe.success) {
                                "Connected • ${probe.latencyMs} ms • example.com -> ${probe.resolvedIp ?: "OK"} (${FormattingUtils.formatTime(probe.testedAt)})"
                            } else {
                                "Probe failed: ${probe.errorMessage}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (probe.success) AdZeroSuccess else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                    } else if (uiState.settings.lastDnsProbeAt > 0L) {
                        Text(
                            text = "Last test: ${if (uiState.settings.lastDnsProbeSuccess) "${uiState.settings.lastDnsProbeLatencyMs} ms" else "Failed"} at ${FormattingUtils.formatDateTime(uiState.settings.lastDnsProbeAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    Button(
                        onClick = onTestConnection,
                        enabled = !uiState.isProbing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dns_test_button")
                    ) {
                        if (uiState.isProbing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AdZeroWhite, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Testing DNS Latency…")
                        } else {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Test DNS Connection")
                        }
                    }
                }
            }

            // Transport Protocol Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Upstream Transport Protocol",
                        subtitle = "DoH & DoT encrypt upstream queries inside AdZero's local VPN"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DnsProtocol.entries.forEach { protocol ->
                            FilterChip(
                                selected = uiState.settings.dnsProtocol == protocol,
                                onClick = {
                                    onSaveConfig(uiState.settings.dnsProviderId, protocol, null, null, null)
                                },
                                label = { Text(protocol.name) },
                                modifier = Modifier.testTag("dns_protocol_${protocol.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Provider Presets
            item {
                SectionHeader(title = "DNS Providers")
            }

            items(DnsProviderPresets.all, key = { it.id }) { preset ->
                val selected = uiState.settings.dnsProviderId == preset.id
                Surface(
                    onClick = {
                        onSaveConfig(preset.id, uiState.settings.dnsProtocol, null, null, null)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) AdZeroLavenderBg else AdZeroWhite,
                    border = BorderStroke(1.dp, if (selected) AdZeroPurple else AdZeroLavenderBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSaveConfig(preset.id, uiState.settings.dnsProtocol, null, null, null) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                            Text(preset.description, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
                            if (preset.id != "custom") {
                                Text(
                                    text = "${preset.ipv4Primary} • ${preset.dotHostname}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = AdZeroPurple
                                )
                            }
                        }
                        if (selected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = AdZeroPurple)
                        }
                    }
                }
            }

            // Custom Provider Editor
            if (uiState.settings.dnsProviderId == DnsProviderPresets.CUSTOM.id) {
                item {
                    AdZeroCard {
                        Text("Custom Resolver Endpoints", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customIpv4,
                            onValueChange = { customIpv4 = it },
                            label = { Text("IPv4 Address (UDP / DoT IP)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customDot,
                            onValueChange = { customDot = it },
                            label = { Text("DoT Hostname (TLS SNI)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customDoh,
                            onValueChange = { customDoh = it },
                            label = { Text("DoH Endpoint (https://…)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onSaveConfig(
                                    DnsProviderPresets.CUSTOM.id,
                                    uiState.settings.dnsProtocol,
                                    customIpv4,
                                    customDot,
                                    customDoh
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Custom DNS")
                        }
                    }
                }
            }

            // Local DNS Cache Card
            item {
                AdZeroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local DNS Cache", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                            Text(
                                text = "${uiState.cacheSize} cached entries • ${uiState.cacheHits} cache hits (bounded LRU for low battery use)",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                        }
                        OutlinedButton(onClick = onClearCache) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }

            // Android Private DNS Guidance
            item {
                AdZeroCard {
                    Text("Android System Private DNS", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Detected system mode: ${uiState.privateDnsModeLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AdZeroPurple
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Android's Private DNS setting and AdZero's local VpnService are separate mechanisms. If Android Private DNS is set to a strict custom hostname, system queries can bypass local VPN filtering. Keep Private DNS set to Automatic or Off when using AdZero's encrypted DoH/DoT forwarding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { SystemPermissionHelper.openPrivateDnsSettings(context) }) {
                        Text("Open Network & Private DNS Settings")
                    }
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "DNS Limitations",
                    message = "Changing DNS providers improves privacy and resolution speed, but does not by itself remove first-party ads or YouTube video ads.",
                    tone = StatusTone.NEUTRAL
                )
            }
        }
    }
}
