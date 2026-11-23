package com.example

import com.example.core.common.FormattingUtils
import com.example.core.network.TrafficTelemetryEngine
import com.example.data.model.AppDataUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficTelemetryEngineTest {

    @Test
    fun `calculates download upload rates session totals and dns deltas across ticks`() {
        val engine = TrafficTelemetryEngine(maxSamples = 5)

        // Baseline tick establishes counters without fabricating a spike
        val t0 = engine.recordTick(
            nowMs = 1_000L,
            totalRxBytes = 10_000L,
            totalTxBytes = 2_000L,
            sessionBlockedAds = 0L,
            sessionBlockedTrackersAndOther = 0L,
            sessionTotalDnsRequests = 0L
        )
        assertEquals(0L, t0.downloadBytesPerSec)
        assertEquals(0L, t0.uploadBytesPerSec)
        assertEquals(1, t0.samples.size)

        // 1 second later: +50 KB RX, +10 KB TX, 3 ads blocked, 2 trackers blocked, 10 total DNS (5 allowed)
        val t1 = engine.recordTick(
            nowMs = 2_000L,
            totalRxBytes = 61_200L,
            totalTxBytes = 12_240L,
            sessionBlockedAds = 3L,
            sessionBlockedTrackersAndOther = 2L,
            sessionTotalDnsRequests = 10L
        )
        assertEquals(51_200L, t1.downloadBytesPerSec)
        assertEquals(10_240L, t1.uploadBytesPerSec)
        assertEquals(51_200L, t1.peakDownloadBytesPerSec)
        assertEquals(51_200L, t1.sessionRxBytes)
        assertEquals(10_240L, t1.sessionTxBytes)

        val latest = t1.samples.last()
        assertEquals(3, latest.blockedAdsDelta)
        assertEquals(2, latest.blockedTrackersDelta)
        assertEquals(5, latest.allowedDnsDelta)
    }

    @Test
    fun `handles TrafficStats unsupported negative values and counter resets safely`() {
        val engine = TrafficTelemetryEngine(maxSamples = 3)

        engine.recordTick(
            nowMs = 1_000L,
            totalRxBytes = 100_000L,
            totalTxBytes = 50_000L,
            sessionBlockedAds = 5L,
            sessionBlockedTrackersAndOther = 2L,
            sessionTotalDnsRequests = 12L
        )

        // Simulate TrafficStats.UNSUPPORTED (-1L) and VPN session counter reset to 0
        val unsupportedTick = engine.recordTick(
            nowMs = 2_000L,
            totalRxBytes = -1L,
            totalTxBytes = -1L,
            sessionBlockedAds = 0L,
            sessionBlockedTrackersAndOther = 0L,
            sessionTotalDnsRequests = 0L
        )
        assertEquals(0L, unsupportedTick.downloadBytesPerSec)
        assertEquals(0L, unsupportedTick.uploadBytesPerSec)
        assertEquals(0, unsupportedTick.samples.last().blockedAdsDelta)
        assertEquals(0, unsupportedTick.samples.last().allowedDnsDelta)

        // Simulate counter drop (e.g., interface reset)
        val resetTick = engine.recordTick(
            nowMs = 3_000L,
            totalRxBytes = 500L,
            totalTxBytes = 100L,
            sessionBlockedAds = 1L,
            sessionBlockedTrackersAndOther = 0L,
            sessionTotalDnsRequests = 2L
        )
        assertEquals(0L, resetTick.downloadBytesPerSec)
        assertEquals(1, resetTick.samples.last().blockedAdsDelta)
        assertEquals(1, resetTick.samples.last().allowedDnsDelta)
    }

    @Test
    fun `ring buffer enforces maximum window size`() {
        val engine = TrafficTelemetryEngine(maxSamples = 4)
        var lastResult = engine.recordTick(1_000L, 0L, 0L, 0L, 0L, 0L)
        for (i in 2..10) {
            lastResult = engine.recordTick(i * 1_000L, i * 1_000L, 0L, i.toLong(), 0L, i.toLong())
        }
        assertEquals(4, lastResult.samples.size)
        assertEquals(7_000L, lastResult.samples.first().timestampMs)
        assertEquals(10_000L, lastResult.samples.last().timestampMs)
    }

    @Test
    fun `computes live per-app byte rates from consecutive snapshots`() {
        val engine = TrafficTelemetryEngine()

        val first = engine.computePerAppLiveRates(
            nowMs = 1_000L,
            rawUsageByPackage = mapOf(
                "com.example.browser" to AppDataUsage("com.example.browser", 10100, 100_000L, 20_000L),
                "com.example.idle" to AppDataUsage("com.example.idle", 10101, 50_000L, 5_000L)
            )
        )
        assertEquals(0L, first["com.example.browser"]?.liveBytesPerSec)

        // 2 seconds later, browser transferred +40_000 bytes total -> 20_000 B/s
        val second = engine.computePerAppLiveRates(
            nowMs = 3_000L,
            rawUsageByPackage = mapOf(
                "com.example.browser" to AppDataUsage("com.example.browser", 10100, 130_000L, 30_000L),
                "com.example.idle" to AppDataUsage("com.example.idle", 10101, 50_000L, 5_000L)
            )
        )
        assertEquals(20_000L, second["com.example.browser"]?.liveBytesPerSec)
        assertEquals(0L, second["com.example.idle"]?.liveBytesPerSec)
    }

    @Test
    fun `byte and speed formatters produce readable output`() {
        assertEquals("0 B", FormattingUtils.formatBytes(0L))
        assertEquals("512 B", FormattingUtils.formatBytes(512L))
        assertEquals("1.5 KB", FormattingUtils.formatBytes(1_536L))
        assertEquals("2.5 MB", FormattingUtils.formatBytes(2_621_440L))
        assertTrue(FormattingUtils.formatBytes(2_147_483_648L).endsWith("GB"))

        assertEquals("0 B/s", FormattingUtils.formatSpeed(0L))
        assertEquals("10.0 KB/s", FormattingUtils.formatSpeed(10_240L))
        assertEquals("1.50 MB/s", FormattingUtils.formatSpeed(1_572_864L))
    }
}
