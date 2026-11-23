package com.example.data.repository

import com.example.core.database.DnsLogDao
import com.example.core.database.DnsLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Event-driven, battery-friendly DNS log batcher.
 * Zero wakeups while idle; flushes within 400ms of real DNS traffic or immediately upon reaching
 * 15 entries so live dashboards and charts reflect observed DNS traffic in near real time.
 */
class DnsLogBatcher(
    private val dnsLogDao: DnsLogDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<DnsLogEntity>(capacity = 512)
    private val buffer = mutableListOf<DnsLogEntity>()

    @Volatile
    private var debounceFlushJob: Job? = null

    init {
        scope.launch {
            for (entry in channel) {
                var batchToFlush: List<DnsLogEntity>? = null
                synchronized(buffer) {
                    buffer.add(entry)
                    if (buffer.size >= BATCH_SIZE) {
                        batchToFlush = buffer.toList()
                        buffer.clear()
                    }
                }
                if (batchToFlush != null) {
                    debounceFlushJob?.cancel()
                    debounceFlushJob = null
                    writeBatch(batchToFlush!!)
                } else if (debounceFlushJob?.isActive != true) {
                    debounceFlushJob = scope.launch {
                        delay(DEBOUNCE_FLUSH_MS)
                        flushAvailable()
                    }
                }
            }
        }
    }

    fun enqueue(entry: DnsLogEntity) {
        channel.trySend(entry)
    }

    suspend fun flushNow() {
        debounceFlushJob?.cancel()
        debounceFlushJob = null
        flushAvailable()
    }

    private suspend fun flushAvailable() {
        val toWrite = synchronized(buffer) {
            if (buffer.isEmpty()) emptyList()
            else buffer.toList().also { buffer.clear() }
        }
        if (toWrite.isNotEmpty()) {
            writeBatch(toWrite)
        }
    }

    private suspend fun writeBatch(entries: List<DnsLogEntity>) {
        runCatching { dnsLogDao.insertAll(entries) }
    }

    companion object {
        private const val BATCH_SIZE = 15
        private const val DEBOUNCE_FLUSH_MS = 400L
    }
}
