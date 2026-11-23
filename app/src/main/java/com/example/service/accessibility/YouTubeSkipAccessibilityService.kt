package com.example.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.AdZeroApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Optional, user-consented AccessibilityService strictly scoped to `com.google.android.youtube`.
 * Detects an available YouTube Skip Ad button and activates it locally in real time.
 * Never reads passwords, personal content, or non-YouTube screens, and never uploads data.
 */
class YouTubeSkipAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastSkipClickMillis = 0L

    @Volatile
    private var isEnabledAndConsented = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isConnected.value = true
        val app = application as? AdZeroApplication ?: return
        scope.launch {
            app.container.preferences.settingsFlow.collect { settings ->
                isEnabledAndConsented = settings.youtubeSkipConsentGranted && settings.youtubeSkipEnabled
            }
        }
        scope.launch {
            app.container.repository.logServiceEvent("YOUTUBE", "YouTube Skip Assistant service connected.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isEnabledAndConsented || event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != YOUTUBE_PACKAGE) return

        val now = System.currentTimeMillis()
        if (now - lastSkipClickMillis < SKIP_DEBOUNCE_MS) return

        val root = rootInActiveWindow ?: return
        val targetNode = findSkipAdNode(root) ?: return
        val clickableNode = findClickableAncestor(targetNode) ?: return
        if (clickableNode.isEnabled && clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            lastSkipClickMillis = now
            val app = application as? AdZeroApplication ?: return
            scope.launch {
                app.container.preferences.incrementYouTubeSkipCount(now)
                app.container.repository.logServiceEvent("YOUTUBE", "Activated YouTube Skip Ad control.")
            }
        }
    }

    private fun findSkipAdNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (resId in SKIP_VIEW_IDS) {
            val matches = root.findAccessibilityNodeInfosByViewId(resId)
            if (!matches.isNullOrEmpty()) {
                val candidate = matches.firstOrNull { node ->
                    node.isVisibleToUser && node.isEnabled
                }
                if (candidate != null) return candidate
            }
        }

        for (label in SKIP_LABELS) {
            val matches = root.findAccessibilityNodeInfosByText(label)
            if (!matches.isNullOrEmpty()) {
                val exact = matches.firstOrNull { node ->
                    val txt = node.text?.toString()?.trim().orEmpty()
                    val desc = node.contentDescription?.toString()?.trim().orEmpty()
                    val matchesExact = txt.equals(label, ignoreCase = true) ||
                        desc.equals(label, ignoreCase = true)
                    matchesExact && node.isVisibleToUser && node.isEnabled
                }
                if (exact != null) return exact
            }
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        // No persistent audio/speech feedback to interrupt
    }

    override fun onDestroy() {
        _isConnected.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val SKIP_DEBOUNCE_MS = 1800L
        private val SKIP_VIEW_IDS = listOf(
            "com.google.android.youtube:id/skip_ad_button",
            "com.google.android.youtube:id/skip_ad_button_container",
            "com.google.android.youtube:id/skip_ad_button_text",
            "com.google.android.youtube:id/modern_skip_ad_text"
        )
        private val SKIP_LABELS = listOf("Skip", "Skip ad", "Skip ads")
    }
}
