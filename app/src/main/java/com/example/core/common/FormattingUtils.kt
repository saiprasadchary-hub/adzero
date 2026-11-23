package com.example.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FormattingUtils {
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    fun formatCompactCount(count: Long): String = when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count >= 1_000 -> String.format(Locale.US, "%,d", count)
        else -> count.toString()
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", safe / 1_073_741_824.0)
            safe >= 1_048_576L -> String.format(Locale.US, "%.1f MB", safe / 1_048_576.0)
            safe >= 1_024L -> String.format(Locale.US, "%.1f KB", safe / 1_024.0)
            else -> "$safe B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        val safe = bytesPerSec.coerceAtLeast(0L)
        return when {
            safe >= 1_048_576L -> String.format(Locale.US, "%.2f MB/s", safe / 1_048_576.0)
            safe >= 1_024L -> String.format(Locale.US, "%.1f KB/s", safe / 1_024.0)
            else -> "$safe B/s"
        }
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        return timeFormat.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        return dateTimeFormat.format(Date(timestamp))
    }

    fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "Never"
        val diff = (now - timestamp).coerceAtLeast(0L)
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> shortDateFormat.format(Date(timestamp))
        }
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0m"
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
