package com.example

import com.example.data.model.DnsProtocol
import com.example.data.model.DomainCategory
import com.example.data.model.GhostSchedule
import com.example.data.model.VpnConnectionState
import com.example.data.repository.ProtectionStateHolder
import com.example.domain.usecase.GhostScheduleCalculator
import com.example.domain.usecase.PrivacyScoreCalculator
import com.example.domain.usecase.PrivacyScoreInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GhostScheduleAndScoreTest {

    @Test
    fun `same-day weekday schedule activates during work hours and stays off on weekends`() {
        val tz = TimeZone.getTimeZone("UTC")
        val workSchedule = GhostSchedule(
            id = 1,
            label = "Work Hours",
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            daysMask = GhostSchedule.WEEKDAYS_MASK,
            enabled = true
        )

        // Wednesday 2026-11-18 at 10:30 UTC
        val wedMorning = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertTrue(GhostScheduleCalculator.isScheduleActiveAt(workSchedule, wedMorning, tz))

        // Saturday 2026-11-21 at 10:30 UTC
        val satMorning = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 21, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertFalse(GhostScheduleCalculator.isScheduleActiveAt(workSchedule, satMorning, tz))
    }

    @Test
    fun `overnight schedule handles midnight wrap-around and timezone changes`() {
        val utc = TimeZone.getTimeZone("UTC")
        val ist = TimeZone.getTimeZone("Asia/Kolkata") // UTC+5:30

        val nightSchedule = GhostSchedule(
            id = 2,
            label = "Night Focus",
            startMinuteOfDay = 22 * 60, // 22:00
            endMinuteOfDay = 8 * 60,    // 08:00
            daysMask = GhostSchedule.ALL_DAYS_MASK,
            enabled = true
        )

        // 2026-11-18 23:15 UTC -> active in UTC, but 04:45 AM in IST -> also active in IST
        val lateUtc = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 18, 23, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertTrue(GhostScheduleCalculator.isScheduleActiveAt(nightSchedule, lateUtc, utc))
        assertTrue(GhostScheduleCalculator.isScheduleActiveAt(nightSchedule, lateUtc, ist))

        // 2026-11-18 17:00 UTC -> 17:00 in UTC (inactive), but 22:30 in IST (active!)
        val eveningUtc = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 18, 17, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertFalse(GhostScheduleCalculator.isScheduleActiveAt(nightSchedule, eveningUtc, utc))
        assertTrue(GhostScheduleCalculator.isScheduleActiveAt(nightSchedule, eveningUtc, ist))

        val eval = GhostScheduleCalculator.evaluate(listOf(nightSchedule), eveningUtc, utc)
        assertFalse(eval.shouldGhostModeBeActive)
        assertNotNull(eval.nextTransitionTimeMillis)
        assertTrue(eval.nextTransitionActivatesGhost)
    }

    @Test
    fun `privacy score calculates deterministic points up to 100`() {
        val maxScore = PrivacyScoreCalculator.calculate(
            PrivacyScoreInput(
                isVpnActive = true,
                isAdBlockingEnabled = true,
                isTrackerBlockingEnabled = true,
                isMalwareListEnabled = true,
                isStrictModeEnabled = true,
                dnsProtocol = DnsProtocol.DOH,
                hasActiveBlocklistDomains = true,
                isPrivatePreviewOrBiometricEnabled = true,
                isAutoUpdateEnabled = true
            )
        )
        assertEquals(100, maxScore.totalScore)
        assertEquals("Strong Protection", maxScore.ratingLabel)

        val inactiveScore = PrivacyScoreCalculator.calculate(
            PrivacyScoreInput(
                isVpnActive = false,
                isAdBlockingEnabled = false,
                isTrackerBlockingEnabled = false,
                isMalwareListEnabled = false,
                isStrictModeEnabled = false,
                dnsProtocol = DnsProtocol.UDP,
                hasActiveBlocklistDomains = false,
                isPrivatePreviewOrBiometricEnabled = false,
                isAutoUpdateEnabled = false
            )
        )
        assertEquals(0, inactiveScore.totalScore)
        assertEquals("Protection Inactive", inactiveScore.ratingLabel)
    }

    @Test
    fun `protection state holder tracks truthful vpn states and session counters`() {
        val holder = ProtectionStateHolder()
        assertEquals(VpnConnectionState.DISCONNECTED, holder.snapshot.value.state)
        assertFalse(holder.snapshot.value.isRunning)

        holder.setState(VpnConnectionState.CONNECTING)
        assertFalse(holder.snapshot.value.isRunning)

        holder.setState(VpnConnectionState.ACTIVE)
        assertTrue(holder.snapshot.value.isRunning)
        assertTrue(holder.snapshot.value.sessionStartedAt > 0L)

        holder.recordDnsEvent(blocked = true, category = DomainCategory.AD, cacheSize = 5, cacheHits = 2)
        holder.recordDnsEvent(blocked = true, category = DomainCategory.TRACKER, cacheSize = 6, cacheHits = 3)
        holder.recordDnsEvent(blocked = false, category = DomainCategory.ALLOWED, cacheSize = 7, cacheHits = 4)

        val snap = holder.snapshot.value
        assertEquals(3L, snap.sessionTotalRequests)
        assertEquals(1L, snap.sessionBlockedAds)
        assertEquals(1L, snap.sessionBlockedTrackers)
        assertEquals(7, snap.cacheSize)

        holder.setState(VpnConnectionState.DISCONNECTED)
        assertEquals(0L, holder.snapshot.value.sessionTotalRequests)
    }
}
