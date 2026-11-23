package com.example.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.design.AdZeroCard
import com.example.core.design.AdZeroShieldLogo
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
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
                    Text("AdZero Privacy Commitment", style = MaterialTheme.typography.titleMedium, color = AdZeroNavyText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "AdZero is designed from the ground up to run entirely on your Android device. There is no account registration, no cloud profile, no advertising SDK, and no third-party analytics SDK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                }
            }

            item {
                PolicySection(
                    title = "1. Local VPN & DNS Traffic",
                    body = "AdZero uses Android's VpnService strictly on-device to inspect DNS requests against your local blocklists and allowlists. Allowed DNS queries are forwarded directly to the DNS resolver you select (e.g., Cloudflare, Quad9, AdGuard, Google, or Custom) using DoH, DoT, or UDP. AdZero never operates or routes traffic through remote VPN proxy servers."
                )
            }
            item {
                PolicySection(
                    title = "2. WhatsApp Ghost Inbox Data",
                    body = "If you explicitly enable Notification Access and Ghost Inbox capture, AdZero reads incoming WhatsApp / WhatsApp Business notification previews locally, encrypts sender names and preview text with an Android Keystore AES-256-GCM key, and stores them only in app-private storage. Notification contents are never transmitted off your device and can be deleted individually or cleared entirely at any time."
                )
            }
            item {
                PolicySection(
                    title = "3. Optional YouTube Skip Assistant",
                    body = "If you explicitly grant consent and enable Android AccessibilityService for AdZero, it listens strictly to the YouTube package (com.google.android.youtube) solely to detect and activate an available Skip Ad button. It never collects passwords, messages, or unrelated screen content, and sends zero data over the network."
                )
            }
            item {
                PolicySection(
                    title = "4. Retention & User Control",
                    body = "DNS activity logs stay strictly on your device and are automatically pruned according to your chosen retention window (1, 7, or 30 days). You can export your logs to CSV or wipe all local logs, inbox previews, and caches at any time from Settings."
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAdZeroScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About AdZero") },
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
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AdZeroShieldLogo(size = 56.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("AdZero Ultimate", style = MaterialTheme.typography.headlineSmall, color = AdZeroNavyText)
                        Text("Version 1.0.0", style = MaterialTheme.typography.labelMedium, color = AdZeroPurple)
                        Spacer(Modifier.height(4.dp))
                        Text("Block Ads. Protect Privacy. Take Control.", style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
                    }
                }
            }

            item {
                PolicySection(
                    title = "Release Notes — v1.0.0",
                    body = "• Local Android VpnService DNS filtering engine (IPv4 & IPv6)\n" +
                        "• Smart DNS support for DoH (RFC 8484), DoT (port 853), and UDP with live latency probes\n" +
                        "• Per-App Firewall with separate Internet Block and DNS Exclusion controls\n" +
                        "• WhatsApp Ghost Mode, AES-256-GCM encrypted Ghost Inbox, Biometric Lock & Ghost Scheduler\n" +
                        "• Optional consent-gated YouTube Skip Assistant AccessibilityService\n" +
                        "• Local Network Analytics, Canvas charts, SAF CSV Export, Quick Settings Tile & WorkManager Blocklist Updater"
                )
            }

            item {
                PolicySection(
                    title = "Honest Technical Boundaries",
                    body = "• DNS filtering cannot remove ads served from the exact same domain as primary content (such as YouTube in-stream video ads).\n" +
                        "• WhatsApp does not provide an API for per-contact one-tick delivery receipt control; Ghost Mode blocks WhatsApp connectivity globally.\n" +
                        "• Android permits only one active VPN at a time; enabling Strict Packet Isolation pauses system-wide DNS filtering until Standard mode is restored."
                )
            }

            item {
                PolicySection(
                    title = "Open Source Libraries & Licenses",
                    body = "• Jetpack Compose, Material 3, Room, DataStore, WorkManager, Biometric, Navigation (Apache License 2.0)\n" +
                        "• Kotlin Coroutines & Standard Library (Apache License 2.0)\n" +
                        "• OkHttp by Square (Apache License 2.0)\n" +
                        "• Community Blocklist Sources: AdAway Hosts, HaGeZi DNS Blocklists, URLHaus (Abuse.ch)"
                )
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    AdZeroCard {
        Text(title, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
    }
}
