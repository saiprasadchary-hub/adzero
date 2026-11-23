package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class DeviceAppRecord(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val uid: Int
)

/**
 * Discovers installed user-launchable applications using Play-policy compliant <queries>
 * intent filtering (ACTION_MAIN + CATEGORY_LAUNCHER) plus explicit WhatsApp/YouTube checks.
 */
class InstalledAppsRepository(private val context: Context) {

    private val iconCache = ConcurrentHashMap<String, Drawable>()

    suspend fun loadInstalledApps(): List<DeviceAppRecord> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val map = LinkedHashMap<String, DeviceAppRecord>()
        for (info in resolved) {
            val appInfo = info.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName
            if (pkg == context.packageName) continue
            if (map.containsKey(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            map[pkg] = DeviceAppRecord(
                packageName = pkg,
                appName = label.ifBlank { pkg },
                isSystemApp = isSystem,
                uid = appInfo.uid
            )
        }

        // Also explicitly check key privacy-managed apps declared in <queries>
        listOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.youtube").forEach { pkg ->
            if (!map.containsKey(pkg)) {
                runCatching {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    val label = pm.getApplicationLabel(appInfo).toString()
                    map[pkg] = DeviceAppRecord(
                        packageName = pkg,
                        appName = label.ifBlank { pkg },
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        uid = appInfo.uid
                    )
                }
            }
        }

        map.values.sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(packageName: String): Drawable? {
        iconCache[packageName]?.let { return it }
        val loaded = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        if (loaded != null) {
            iconCache[packageName] = loaded
        }
        return loaded
    }

    fun resolveAppName(packageName: String): String {
        if (packageName == "unknown" || packageName.isBlank()) return "System / Unknown"
        return runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
