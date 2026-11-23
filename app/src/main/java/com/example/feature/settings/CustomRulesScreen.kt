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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.data.model.RuleType
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onAddRule: (String, RuleType) -> Unit,
    onDeleteRule: (Long) -> Unit
) {
    var domainInput by remember { mutableStateOf("") }
    var selectedNewType by remember { mutableStateOf(RuleType.ALLOW) }
    var filterTab by remember { mutableStateOf("ALL") }

    val filtered = uiState.customRules.filter { rule ->
        filterTab == "ALL" || rule.ruleType == filterTab
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Allowlist & Custom Rules") },
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
                    Text("Add Custom Domain Rule", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedNewType == RuleType.ALLOW,
                            onClick = { selectedNewType = RuleType.ALLOW },
                            label = { Text("Allowlist") }
                        )
                        FilterChip(
                            selected = selectedNewType == RuleType.BLOCK,
                            onClick = { selectedNewType = RuleType.BLOCK },
                            label = { Text("Blocklist") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Domain or wildcard (e.g., *.example.com)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_rule_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (domainInput.isNotBlank()) {
                                onAddRule(domainInput, selectedNewType)
                                domainInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_rule_add_button")
                    ) {
                        Text(if (selectedNewType == RuleType.ALLOW) "Add to Allowlist" else "Add to Custom Blocklist")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All (${uiState.customRules.size})", "ALLOW" to "Allowlist", "BLOCK" to "Blocklist").forEach { (key, label) ->
                        FilterChip(
                            selected = filterTab == key,
                            onClick = { filterTab = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    AdZeroCard {
                        Text("No custom rules added yet", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Allowlist rules always take precedence over all blocklists and custom block rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { rule ->
                    AdZeroCard(contentPadding = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.domainPattern,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = AdZeroNavyText
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(
                                        label = if (rule.ruleType == "ALLOW") "Allowed" else "Blocked",
                                        tone = if (rule.ruleType == "ALLOW") StatusTone.SUCCESS else StatusTone.ERROR
                                    )
                                    if (rule.isWildcard) {
                                        StatusPill(label = "Wildcard", tone = StatusTone.PRIMARY)
                                    }
                                }
                            }
                            IconButton(onClick = { onDeleteRule(rule.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = AdZeroError)
                            }
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Rule Priority Order",
                    message = "1. Per-App Firewall Block -> 2. Per-App DNS Exclusion -> 3. Custom Allowlist -> 4. Custom Blocklist -> 5. Strict & Standard Blocklists.",
                    tone = StatusTone.NEUTRAL
                )
            }
        }
    }
}
