package com.example.feature.ghost

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.GhostSchedule
import com.example.domain.usecase.GhostScheduleCalculator
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostSchedulerScreen(
    uiState: GhostUiState,
    onBack: () -> Unit,
    onSaveSchedule: (GhostSchedule) -> Unit,
    onToggleSchedule: (Long, Boolean) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    onClearManualOverride: () -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ghost Mode Scheduler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdZeroWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AdZeroPurple,
                contentColor = AdZeroWhite,
                modifier = Modifier.testTag("add_schedule_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Ghost schedule")
            }
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
            // Automation Status Summary
            item {
                AdZeroCard(containerColor = AdZeroLavenderBg) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Automation Status", style = MaterialTheme.typography.titleMedium, color = AdZeroNavyText)
                        val activeNow = uiState.scheduleEvaluation.activeSchedule != null
                        StatusPill(
                            label = if (activeNow) "Window Active" else "Standby",
                            tone = if (activeNow) StatusTone.PRIMARY else StatusTone.NEUTRAL
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val nextTime = uiState.scheduleEvaluation.nextTransitionTimeMillis
                    Text(
                        text = if (nextTime != null) {
                            val action = if (uiState.scheduleEvaluation.nextTransitionActivatesGhost) "Activate" else "Deactivate"
                            "Next scheduled event: $action (${uiState.scheduleEvaluation.nextScheduleLabel}) on ${FormattingUtils.formatDateTime(nextTime)}"
                        } else {
                            "No schedules are currently enabled. Enable a schedule below or add a custom window."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )

                    val overrideActive = uiState.settings.ghostManualOverrideUntil > System.currentTimeMillis()
                    if (overrideActive) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manual override active until ${FormattingUtils.formatTime(uiState.settings.ghostManualOverrideUntil)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AdZeroPurple
                            )
                            TextButton(onClick = onClearManualOverride) {
                                Text("Resume Auto")
                            }
                        }
                    }
                }
            }

            if (!uiState.canScheduleExactAlarms) {
                item {
                    AdZeroCard {
                        Text("Exact Alarm Timing (Optional)", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "WorkManager will still trigger schedules reliably, though Android Doze may shift exact boundaries by a few minutes unless Exact Alarms are permitted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { SystemPermissionHelper.openExactAlarmSettings(context) }) {
                            Text("Allow Exact Alarms")
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Saved Schedules",
                    subtitle = "Schedules persist across device reboot and timezone changes"
                )
            }

            items(uiState.schedules, key = { it.id }) { schedule ->
                AdZeroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(schedule.label, style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                            Text(
                                text = "${GhostScheduleCalculator.formatMinuteOfDay(schedule.startMinuteOfDay)} – ${GhostScheduleCalculator.formatMinuteOfDay(schedule.endMinuteOfDay)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AdZeroPurple
                            )
                            Text(
                                text = GhostScheduleCalculator.formatDaysMask(schedule.daysMask),
                                style = MaterialTheme.typography.bodySmall,
                                color = AdZeroMutedText
                            )
                            if (schedule.lastTriggeredAt > 0L) {
                                Text(
                                    text = "Last run: ${FormattingUtils.formatDateTime(schedule.lastTriggeredAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AdZeroMutedText
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = schedule.enabled,
                                onCheckedChange = { onToggleSchedule(schedule.id, it) }
                            )
                            IconButton(onClick = { onDeleteSchedule(schedule.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete schedule", tint = AdZeroError)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Schedule History")
            }

            item {
                AdZeroCard {
                    if (uiState.scheduleHistory.isEmpty()) {
                        Text(
                            text = "No schedule activations recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    } else {
                        uiState.scheduleHistory.forEach { ev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ev.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdZeroNavyText,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = FormattingUtils.formatDateTime(ev.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AdZeroMutedText
                                )
                            }
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    title = "Transparent Automation",
                    message = "AdZero never silently alters system network settings. Schedules only toggle WhatsApp Ghost Mode inside AdZero when you enable them.",
                    tone = StatusTone.NEUTRAL
                )
                Spacer(Modifier.height(64.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreateScheduleDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { schedule ->
                onSaveSchedule(schedule)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreateScheduleDialog(
    onDismiss: () -> Unit,
    onSave: (GhostSchedule) -> Unit
) {
    var label by remember { mutableStateOf("Evening Focus") }
    var startHour by remember { mutableIntStateOf(22) }
    var endHour by remember { mutableIntStateOf(7) }
    var daysMask by remember { mutableIntStateOf(GhostSchedule.WEEKDAYS_MASK) }

    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Ghost Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Schedule Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start: ${GhostScheduleCalculator.formatMinuteOfDay(startHour * 60)}")
                    Row {
                        TextButton(onClick = { startHour = (startHour + 23) % 24 }) { Text("-1h") }
                        TextButton(onClick = { startHour = (startHour + 1) % 24 }) { Text("+1h") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("End: ${GhostScheduleCalculator.formatMinuteOfDay(endHour * 60)}")
                    Row {
                        TextButton(onClick = { endHour = (endHour + 23) % 24 }) { Text("-1h") }
                        TextButton(onClick = { endHour = (endHour + 1) % 24 }) { Text("+1h") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = daysMask == GhostSchedule.ALL_DAYS_MASK,
                        onClick = { daysMask = GhostSchedule.ALL_DAYS_MASK },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = daysMask == GhostSchedule.WEEKDAYS_MASK,
                        onClick = { daysMask = GhostSchedule.WEEKDAYS_MASK },
                        label = { Text("Weekdays") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayLabels.forEachIndexed { idx, d ->
                        val selected = GhostScheduleCalculator.isDayIncluded(daysMask, idx)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                daysMask = if (selected) {
                                    daysMask and (1 shl idx).inv()
                                } else {
                                    daysMask or (1 shl idx)
                                }
                            },
                            label = { Text(d) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = daysMask != 0 && startHour != endHour,
                onClick = {
                    onSave(
                        GhostSchedule(
                            label = label.ifBlank { "Custom Schedule" },
                            startMinuteOfDay = startHour * 60,
                            endMinuteOfDay = endHour * 60,
                            daysMask = daysMask,
                            enabled = true
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
