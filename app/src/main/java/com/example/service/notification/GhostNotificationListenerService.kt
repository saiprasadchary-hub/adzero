package com.example.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.AdZeroApplication
import com.example.core.network.DnsFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional NotificationListenerService that captures real-time WhatsApp / WhatsApp Business
 * notification previews (including MessagingStyle conversations and group chats) locally and
 * encrypts them with Android Keystore AES-256-GCM before storing in Room.
 */
class GhostNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastSignature: String = ""

    @Volatile
    private var lastTimestamp: Long = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        val app = application as? AdZeroApplication ?: return
        scope.launch {
            app.container.repository.logServiceEvent("NOTIFICATION", "Ghost Inbox notification listener connected.")
        }
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg != DnsFilterEngine.WHATSAPP_PKG && pkg != DnsFilterEngine.WHATSAPP_BUSINESS_PKG) {
            return
        }

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // Prefer Android MessagingStyle for rich real-time WhatsApp direct & group chat previews
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val latestMsg = messagingStyle?.messages?.lastOrNull()

        val extras = notification.extras
        val fallbackTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val fallbackBigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val fallbackText = (fallbackBigText ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()).orEmpty()

        val conversationTitle = messagingStyle?.conversationTitle?.toString()?.trim().orEmpty()
        val personName = latestMsg?.person?.name?.toString()?.trim().orEmpty()
        val sender = when {
            conversationTitle.isNotEmpty() && personName.isNotEmpty() && conversationTitle != personName ->
                "$conversationTitle ($personName)"
            personName.isNotEmpty() -> personName
            conversationTitle.isNotEmpty() -> conversationTitle
            else -> fallbackTitle
        }
        val preview = latestMsg?.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackText

        if (sender.isEmpty() || preview.isEmpty()) return
        if (preview.contains("Checking for new messages", ignoreCase = true) ||
            preview.contains("WhatsApp Web", ignoreCase = true) ||
            preview.contains("Backup in progress", ignoreCase = true) ||
            preview.contains("new messages", ignoreCase = true) && latestMsg == null
        ) {
            return
        }

        val now = latestMsg?.timestamp?.takeIf { it > 0L }
            ?: sbn.postTime.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val signature = "$pkg|$sender|$preview"
        if (signature == lastSignature && (now - lastTimestamp) < 2500L) {
            return
        }
        lastSignature = signature
        lastTimestamp = now

        val app = application as? AdZeroApplication ?: return
        scope.launch {
            val settings = app.container.preferences.settingsFlow.first()
            if (!settings.ghostInboxCaptureEnabled) return@launch
            app.container.repository.saveEncryptedGhostMessage(
                senderName = sender,
                messagePreview = preview,
                sourcePackage = pkg,
                timestamp = now
            )
        }
    }

    override fun onDestroy() {
        _isConnected.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
