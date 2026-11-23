package com.example.core.common

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.service.accessibility.YouTubeSkipAccessibilityService
import com.example.service.notification.GhostNotificationListenerService
import com.example.service.quicksettings.AdZeroTileService

object SystemPermissionHelper {

    fun isVpnPermissionGranted(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(context.packageName)) return true
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val expected = ComponentName(context, GhostNotificationListenerService::class.java).flattenToString()
        return flat.contains(expected) || flat.contains(context.packageName)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedFull = ComponentName(context, YouTubeSkipAccessibilityService::class.java).flattenToString()
        val expectedShort = ComponentName(context, YouTubeSkipAccessibilityService::class.java).flattenToShortString()
        return enabledServices.split(':').any { entry ->
            entry.equals(expectedFull, ignoreCase = true) ||
                entry.equals(expectedShort, ignoreCase = true) ||
                entry.contains("YouTubeSkipAccessibilityService", ignoreCase = true)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getPrivateDnsModeLabel(context: Context): String {
        return try {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
            val specifier = Settings.Global.getString(context.contentResolver, "private_dns_specifier")
            when (mode) {
                "hostname" -> "Strict Private DNS (${specifier ?: "custom"})"
                "opportunistic" -> "Automatic (Opportunistic)"
                "off" -> "Off"
                else -> "System Default (Automatic)"
            }
        } catch (_: Throwable) {
            "Unavailable"
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openPrivateDnsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openVpnSettings(context: Context) {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun requestAddQuickSettingsTile(context: Context, onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            if (statusBarManager != null) {
                statusBarManager.requestAddTileService(
                    ComponentName(context, AdZeroTileService::class.java),
                    context.getString(R.string.quick_settings_tile_label),
                    Icon.createWithResource(context, R.drawable.ic_adzero_shield),
                    ContextCompat.getMainExecutor(context)
                ) { resultCode ->
                    val message = when (resultCode) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "AdZero tile added to Quick Settings."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "AdZero tile is already in Quick Settings."
                        else -> "You can add the AdZero tile manually from the Quick Settings edit panel."
                    }
                    onResult(message)
                }
                return
            }
        }
        onResult("Swipe down twice, tap Edit, and drag the AdZero tile into your Quick Settings panel.")
    }
}
