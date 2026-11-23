package com.example.feature.ghost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.common.FormattingUtils
import com.example.core.common.SystemPermissionHelper
import com.example.core.design.AdZeroCard
import com.example.core.design.InfoBanner
import com.example.core.design.SectionHeader
import com.example.core.design.StatusPill
import com.example.core.design.StatusTone
import com.example.core.security.BiometricHelper
import com.example.data.model.GhostMessage
import com.example.data.model.VpnConnectionState
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroNavyText
import com.example.ui.theme.AdZeroPurple

@Composable
fun GhostInboxScreen(
    uiState: GhostUiState,
    onToggleGhostMode: (Boolean) -> Unit,
    onToggleStrictIsolation: (Boolean) -> Unit,
    onToggleInboxCapture: (Boolean) -> Unit,
    onTogglePreviewMask: (Boolean) -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectContact: (String?) -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onClearInbox: () -> Unit,
    onOpenScheduler: () -> Unit,
    onUnlocked: () -> Unit,
    onRefreshPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!uiState.isUnlocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AdZeroCard(contentPadding = 24.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = AdZeroPurple,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Ghost Inbox Locked",
                        style = MaterialTheme.typography.headlineSmall,
                        color = AdZeroNavyText
                    )
                    Text(
                        text = "Notification previews are encrypted locally using Android Keystore AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                    if (biometricError != null) {
                        Text(
                            text = biometricError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroError
                        )
                    }
                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null && BiometricHelper.canAuthenticate(context)) {
                                BiometricHelper.authenticate(
                                    activity = activity,
                                    onSuccess = {
                                        biometricError = null
                                        onUnlocked()
                                    },
                                    onError = { err -> biometricError = err }
                                )
                            } else {
                                onUnlocked()
                            }
                        },
                        modifier = Modifier.testTag("ghost_unlock_button")
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Authenticate to Unlock")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WhatsApp Ghost",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AdZeroNavyText
                    )
                    Text(
                        text = "Global WhatsApp firewall & encrypted local preview inbox",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                }
                OutlinedButton(
                    onClick = onOpenScheduler,
                    modifier = Modifier.testTag("ghost_open_scheduler_button")
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Schedule")
                }
            }
        }

        // Global WhatsApp Ghost Mode Control Card
        item {
            AdZeroCard(containerColor = AdZeroLavenderBg) {
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
                            Text(
                                text = "WhatsApp Ghost Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = AdZeroNavyText
                            )
                            StatusPill(
                                label = if (uiState.settings.ghostModeEnabled) "Blocked" else "Online",
                                tone = if (uiState.settings.ghostModeEnabled) StatusTone.PRIMARY else StatusTone.SUCCESS
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (uiState.vpnState == VpnConnectionState.ACTIVE) {
                                "Blocks WhatsApp & WA Business via local VPN while other apps stay connected."
                            } else {
                                "Turn ON AdZero Protection on Home to enforce WhatsApp network blocking."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = uiState.settings.ghostModeEnabled,
                        onCheckedChange = onToggleGhostMode,
                        modifier = Modifier.testTag("ghost_mode_switch")
                    )
                }

                HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Packet Isolation",
                            style = MaterialTheme.typography.titleSmall,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = "Drops all TCP/UDP packets for blocked apps (including open sockets). Because Android allows one VPN, system-wide DNS filtering pauses while Strict Isolation is enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = uiState.settings.ghostStrictIsolation,
                        onCheckedChange = onToggleStrictIsolation,
                        modifier = Modifier.testTag("ghost_strict_isolation_switch")
                    )
                }

                if (uiState.scheduleEvaluation.nextTransitionTimeMillis != null) {
                    HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Next schedule: ${if (uiState.scheduleEvaluation.nextTransitionActivatesGhost) "Activate" else "Deactivate"} at ${FormattingUtils.formatDateTime(uiState.scheduleEvaluation.nextTransitionTimeMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdZeroPurple,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenScheduler) {
                            Text("Manage")
                        }
                    }
                }
            }
        }

        // Notification Listener Setup & Inbox Privacy Card
        item {
            AdZeroCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Preview Capture",
                            style = MaterialTheme.typography.titleSmall,
                            color = AdZeroNavyText
                        )
                        Text(
                            text = if (uiState.isNotificationAccessGranted) {
                                "Notification Access granted. Previews are encrypted locally with Android Keystore."
                            } else {
                                "Optional: Grant Notification Access to save incoming WhatsApp notification previews locally."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AdZeroMutedText
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (uiState.isNotificationAccessGranted) {
                        Switch(
                            checked = uiState.settings.ghostInboxCaptureEnabled,
                            onCheckedChange = onToggleInboxCapture,
                            modifier = Modifier.testTag("ghost_capture_switch")
                        )
                    } else {
                        OutlinedButton(
                            onClick = { SystemPermissionHelper.openNotificationListenerSettings(context) },
                            modifier = Modifier.testTag("ghost_grant_notification_access")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enable")
                        }
                    }
                }

                HorizontalDivider(color = AdZeroLavenderBorder, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mask Preview Text", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                        Text("Tap a message to reveal its text", style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
                    }
                    Switch(
                        checked = uiState.settings.ghostPreviewPrivacyMasked,
                        onCheckedChange = onTogglePreviewMask,
                        modifier = Modifier.testTag("ghost_mask_switch")
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Inbox Lock", style = MaterialTheme.typography.titleSmall, color = AdZeroNavyText)
                        Text("Require fingerprint or device PIN to view inbox", style = MaterialTheme.typography.bodySmall, color = AdZeroMutedText)
                    }
                    Switch(
                        checked = uiState.settings.ghostBiometricLockEnabled,
                        onCheckedChange = onToggleBiometricLock,
                        modifier = Modifier.testTag("ghost_biometric_switch")
                    )
                }
            }
        }

        // Honest Technical Limitations Disclosure
        item {
            InfoBanner(
                icon = Icons.Outlined.Info,
                title = "Technical Boundaries & Privacy",
                message = "WhatsApp provides no official API for per-contact one-tick delivery receipt control. Blocking WhatsApp disconnects it globally so new messages generally remain undelivered (one grey tick) until reconnection. Ghost Inbox only displays available Android notification previews—which may be grouped, incomplete, or omitted by WhatsApp—and never uploads data.",
                tone = StatusTone.NEUTRAL
            )
        }

        // Inbox Header, Search, Contact Filter, Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Local Ghost Inbox (${uiState.messages.size})",
                    action = {
                        Row {
                            if (uiState.messages.any { !it.isRead }) {
                                IconButton(onClick = onMarkAllRead) {
                                    Icon(Icons.Filled.DoneAll, contentDescription = "Mark all read")
                                }
                            }
                            if (uiState.messages.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearConfirm = true },
                                    modifier = Modifier.testTag("ghost_clear_all_button")
                                ) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all messages", tint = AdZeroError)
                                }
                            }
                        }
                    }
                )

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ghost_search_input"),
                    placeholder = { Text("Search sender or message preview…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                if (uiState.contacts.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedContact == null,
                                onClick = { onSelectContact(null) },
                                label = { Text("All Contacts") }
                            )
                        }
                        items(uiState.contacts) { contact ->
                            FilterChip(
                                selected = uiState.selectedContact == contact,
                                onClick = { onSelectContact(contact) },
                                label = { Text(contact, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.messages.isEmpty()) {
            item {
                AdZeroCard {
                    Text(
                        text = "No locally captured previews yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = AdZeroNavyText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "When Notification Access and Preview Capture are enabled, incoming WhatsApp notification previews will appear here encrypted at rest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdZeroMutedText
                    )
                }
            }
        } else {
            items(uiState.messages, key = { it.id }) { message ->
                GhostMessageCard(
                    message = message,
                    masked = uiState.settings.ghostPreviewPrivacyMasked,
                    onMarkRead = { onMarkRead(message.id) },
                    onDelete = { onDeleteMessage(message.id) }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Ghost Inbox?") },
            text = { Text("This permanently deletes all locally stored encrypted WhatsApp notification previews from AdZero.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearInbox()
                    }
                ) {
                    Text("Clear All", color = AdZeroError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GhostMessageCard(
    message: GhostMessage,
    masked: Boolean,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    var revealed by remember(message.id, masked) { mutableStateOf(!masked) }

    AdZeroCard(
        containerColor = if (message.isRead) MaterialTheme.colorScheme.surface else AdZeroLavenderBg,
        onClick = {
            revealed = true
            if (!message.isRead) onMarkRead()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold,
                        color = AdZeroNavyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!message.isRead) {
                        StatusPill("New", StatusTone.PRIMARY)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (revealed) message.messagePreview else "Preview hidden • Tap to reveal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (revealed) AdZeroNavyText else AdZeroMutedText
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${message.sourceLabel} • ${FormattingUtils.formatDateTime(message.timestamp)} • ${if (message.isRead) "Read in AdZero" else "Unread"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AdZeroMutedText
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete preview",
                    tint = AdZeroMutedText
                )
            }
        }
    }
}
