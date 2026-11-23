package com.example.domain.usecase

import com.example.data.model.GhostSchedule
import java.util.Calendar
import java.util.TimeZone

data class ScheduleEvaluation(
    val shouldGhostModeBeActive: Boolean,
    val activeSchedule: GhostSchedule?,
    val nextTransitionTimeMillis: Long?,
    val nextTransitionActivatesGhost: Boolean,
    val nextScheduleLabel: String?
)

/**
 * Pure, timezone-aware Ghost Mode schedule evaluator.
 * Supports same-day windows (e.g. 09:00–17:00) and overnight windows (e.g. 22:00–08:00),
 * weekday bitmasks (Bit 0 = Monday .. Bit 6 = Sunday), and DST/timezone transitions.
 */
object GhostScheduleCalculator {

    /**
     * Converts Calendar.DAY_OF_WEEK (Sunday=1, Monday=2..Saturday=7) to Monday=0..Sunday=6.
     */
    fun calendarDayToBitIndex(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    fun isDayIncluded(daysMask: Int, dayBitIndex: Int): Boolean {
        return (daysMask and (1 shl dayBitIndex)) != 0
    }

    fun isScheduleActiveAt(
        schedule: GhostSchedule,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (!schedule.enabled || schedule.daysMask == 0) return false
        if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) return false

        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayBit = calendarDayToBitIndex(cal.get(Calendar.DAY_OF_WEEK))
        val yesterdayBit = (todayBit + 6) % 7

        return if (schedule.startMinuteOfDay < schedule.endMinuteOfDay) {
            // Same-day window (e.g., 09:00 to 17:00)
            isDayIncluded(schedule.daysMask, todayBit) &&
                currentMinute >= schedule.startMinuteOfDay &&
                currentMinute < schedule.endMinuteOfDay
        } else {
            // Overnight window (e.g., 22:00 to 08:00 next morning)
            val inEveningPart = isDayIncluded(schedule.daysMask, todayBit) &&
                currentMinute >= schedule.startMinuteOfDay
            val inMorningPart = isDayIncluded(schedule.daysMask, yesterdayBit) &&
                currentMinute < schedule.endMinuteOfDay
            inEveningPart || inMorningPart
        }
    }

    fun evaluate(
        schedules: List<GhostSchedule>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): ScheduleEvaluation {
        val enabled = schedules.filter { it.enabled && it.daysMask != 0 && it.startMinuteOfDay != it.endMinuteOfDay }
        if (enabled.isEmpty()) {
            return ScheduleEvaluation(
                shouldGhostModeBeActive = false,
                activeSchedule = null,
                nextTransitionTimeMillis = null,
                nextTransitionActivatesGhost = false,
                nextScheduleLabel = null
            )
        }

        val activeSchedule = enabled.firstOrNull { isScheduleActiveAt(it, nowMillis, timeZone) }

        // Find the soonest future boundary (start or end) over the next 8 days
        var bestTime: Long? = null
        var bestActivates = false
        var bestLabel: String? = null

        val baseCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (dayOffset in 0..7) {
            val dayCal = (baseCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayBit = calendarDayToBitIndex(dayCal.get(Calendar.DAY_OF_WEEK))
            val prevDayBit = (dayBit + 6) % 7

            for (schedule in enabled) {
                // Potential start on this day
                if (isDayIncluded(schedule.daysMask, dayBit)) {
                    val startMillis = atMinute(dayCal, schedule.startMinuteOfDay)
                    if (startMillis > nowMillis && (bestTime == null || startMillis < bestTime)) {
                        bestTime = startMillis
                        bestActivates = true
                        bestLabel = schedule.label
                    }
                }
                // Potential end on this day
                val endAppliesToday = if (schedule.startMinuteOfDay < schedule.endMinuteOfDay) {
                    isDayIncluded(schedule.daysMask, dayBit)
                } else {
                    isDayIncluded(schedule.daysMask, prevDayBit)
                }
                if (endAppliesToday) {
                    val endMillis = atMinute(dayCal, schedule.endMinuteOfDay)
                    if (endMillis > nowMillis && (bestTime == null || endMillis < bestTime)) {
                        bestTime = endMillis
                        bestActivates = false
                        bestLabel = schedule.label
                    }
                }
            }
        }

        return ScheduleEvaluation(
            shouldGhostModeBeActive = activeSchedule != null,
            activeSchedule = activeSchedule,
            nextTransitionTimeMillis = bestTime,
            nextTransitionActivatesGhost = bestActivates,
            nextScheduleLabel = bestLabel
        )
    }

    private fun atMinute(dayCal: Calendar, minuteOfDay: Int): Long {
        val c = dayCal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        c.set(Calendar.MINUTE, minuteOfDay % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val h24 = (minuteOfDay / 60).coerceIn(0, 23)
        val m = (minuteOfDay % 60).coerceIn(0, 59)
        val amPm = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format("%d:%02d %s", h12, m, amPm)
    }

    fun formatDaysMask(daysMask: Int): String {
        if (daysMask == GhostSchedule.ALL_DAYS_MASK) return "Every day"
        if (daysMask == GhostSchedule.WEEKDAYS_MASK) return "Weekdays (Mon–Fri)"
        if (daysMask == GhostSchedule.WEEKENDS_MASK) return "Weekends (Sat–Sun)"
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val selected = labels.filterIndexed { index, _ -> isDayIncluded(daysMask, index) }
        return if (selected.isEmpty()) "No days" else selected.joinToString(", ")
    }
}
