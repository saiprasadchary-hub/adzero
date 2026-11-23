package com.example.core.network

import com.example.data.model.AppDataUsage
import com.example.data.model.LiveTrafficSample
import java.util.ArrayDeque

/**
 * Pure-Kotlin real-time network traffic telemetry calculator & bounded sliding-window ring buffer.
 * Computes honest download/upload throughput from monotonic OS byte counters, tracks per-second
 * blocked ad/tracker/allowed DNS deltas, and calculates per-UID transfer rates without fabricating data.
 */
class TrafficTelemetryEngine(
    private val maxSamples: Int = DEFAULT_WINDOW_SECONDS
) {
    private val samples = ArrayDeque<LiveTrafficSample>(maxSamples)

    private var lastSampleTimestampMs: Long = 0L
    private var lastTotalRxBytes: Long = -1L
    private var lastTotalTxBytes: Long = -1L

    private var sessionRxAccumulated: Long = 0L
    private var sessionTxAccumulated: Long = 0L
    private var peakDownloadBps: Long = 0L

    private var lastSessionAds: Long = -1L
    private var lastSessionTrackersOther: Long = -1L
    private var lastSessionTotalDns: Long = -1L

    private var lastPerAppSnapshotTimeMs: Long = 0L
    private var lastPerAppTotalBytes: Map<String, Long> = emptyMap()

    data class TickResult(
        val downloadBytesPerSec: Long,
        val uploadBytesPerSec: Long,
        val peakDownloadBytesPerSec: Long,
        val sessionRxBytes: Long,
        val sessionTxBytes: Long,
        val samples: List<LiveTrafficSample>
    )

    @Synchronized
    fun recordTick(
        nowMs: Long,
        totalRxBytes: Long,
        totalTxBytes: Long,
        sessionBlockedAds: Long,
        sessionBlockedTrackersAndOther: Long,
        sessionTotalDnsRequests: Long
    ): TickResult {
        var dlBps = 0L
        var ulBps = 0L

        val validRx = totalRxBytes >= 0L
        val validTx = totalTxBytes >= 0L

        if (lastSampleTimestampMs > 0L && nowMs > lastSampleTimestampMs) {
            val elapsedMs = (nowMs - lastSampleTimestampMs).coerceAtLeast(100L)
            if (validRx && lastTotalRxBytes >= 0L && totalRxBytes >= lastTotalRxBytes) {
                val deltaRx = totalRxBytes - lastTotalRxBytes
                sessionRxAccumulated += deltaRx
                dlBps = (deltaRx * 1000L) / elapsedMs
            }
            if (validTx && lastTotalTxBytes >= 0L && totalTxBytes >= lastTotalTxBytes) {
                val deltaTx = totalTxBytes - lastTotalTxBytes
                sessionTxAccumulated += deltaTx
                ulBps = (deltaTx * 1000L) / elapsedMs
            }
        }

        if (validRx) lastTotalRxBytes = totalRxBytes
        if (validTx) lastTotalTxBytes = totalTxBytes
        lastSampleTimestampMs = nowMs

        if (dlBps > peakDownloadBps) {
            peakDownloadBps = dlBps
        }

        // Compute per-interval DNS deltas cleanly (handling session resets when VPN restarts)
        val adsDelta = if (lastSessionAds >= 0L && sessionBlockedAds >= lastSessionAds) {
            (sessionBlockedAds - lastSessionAds).toInt()
        } else {
            0
        }
        val trackersDelta = if (lastSessionTrackersOther >= 0L && sessionBlockedTrackersAndOther >= lastSessionTrackersOther) {
            (sessionBlockedTrackersAndOther - lastSessionTrackersOther).toInt()
        } else {
            0
        }
        val totalDelta = if (lastSessionTotalDns >= 0L && sessionTotalDnsRequests >= lastSessionTotalDns) {
            (sessionTotalDnsRequests - lastSessionTotalDns).toInt()
        } else {
            0
        }
        val allowedDelta = (totalDelta - adsDelta - trackersDelta).coerceAtLeast(0)

        lastSessionAds = sessionBlockedAds
        lastSessionTrackersOther = sessionBlockedTrackersAndOther
        lastSessionTotalDns = sessionTotalDnsRequests

        if (samples.size >= maxSamples) {
            samples.removeFirst()
        }
        samples.addLast(
            LiveTrafficSample(
                timestampMs = nowMs,
                downloadBytesPerSec = dlBps,
                uploadBytesPerSec = ulBps,
                blockedAdsDelta = adsDelta,
                blockedTrackersDelta = trackersDelta,
                allowedDnsDelta = allowedDelta
            )
        )

        return TickResult(
            downloadBytesPerSec = dlBps,
            uploadBytesPerSec = ulBps,
            peakDownloadBytesPerSec = peakDownloadBps,
            sessionRxBytes = sessionRxAccumulated,
            sessionTxBytes = sessionTxAccumulated,
            samples = samples.toList()
        )
    }

    @Synchronized
    fun computePerAppLiveRates(
        nowMs: Long,
        rawUsageByPackage: Map<String, AppDataUsage>
    ): Map<String, AppDataUsage> {
        val elapsedMs = if (lastPerAppSnapshotTimeMs > 0L && nowMs > lastPerAppSnapshotTimeMs) {
            (nowMs - lastPerAppSnapshotTimeMs).coerceAtLeast(250L)
        } else {
            0L
        }

        val updated = LinkedHashMap<String, AppDataUsage>(rawUsageByPackage.size)
        val nextTotals = HashMap<String, Long>(rawUsageByPackage.size)

        for ((pkg, usage) in rawUsageByPackage) {
            val currentTotal = usage.totalBytesToday
            nextTotals[pkg] = currentTotal
            val prevTotal = lastPerAppTotalBytes[pkg]
            val liveRate = if (elapsedMs > 0L && prevTotal != null && currentTotal > prevTotal) {
                ((currentTotal - prevTotal) * 1000L) / elapsedMs
            } else {
                0L
            }
            updated[pkg] = usage.copy(liveBytesPerSec = liveRate)
        }

        lastPerAppSnapshotTimeMs = nowMs
        lastPerAppTotalBytes = nextTotals
        return updated
    }

    @Synchronized
    fun reset() {
        samples.clear()
        lastSampleTimestampMs = 0L
        lastTotalRxBytes = -1L
        lastTotalTxBytes = -1L
        sessionRxAccumulated = 0L
        sessionTxAccumulated = 0L
        peakDownloadBps = 0L
        lastSessionAds = -1L
        lastSessionTrackersOther = -1L
        lastSessionTotalDns = -1L
        lastPerAppSnapshotTimeMs = 0L
        lastPerAppTotalBytes = emptyMap()
    }

    companion object {
        const val DEFAULT_WINDOW_SECONDS = 30
    }
}
