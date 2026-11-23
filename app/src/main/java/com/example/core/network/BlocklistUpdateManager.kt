package com.example.core.network

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.AdZeroApplication
import com.example.core.database.BlocklistDomainEntity
import com.example.core.database.ServiceEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class BlocklistUpdateOutcome(
    val updatedSources: Int,
    val failedSources: Int,
    val totalDomainsLoaded: Int,
    val message: String
)

/**
 * Downloads, validates, deduplicates, and atomically replaces blocklists from allowlisted HTTPS
 * sources while preserving the last known working blocklist on any network or parsing error.
 */
class BlocklistUpdateManager(
    private val context: Context
) {
    private val allowedHosts = setOf(
        "adaway.org",
        "raw.githubusercontent.com",
        "urlhaus.abuse.ch",
        "big.oisd.nl",
        "small.oisd.nl"
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun schedulePeriodicUpdates(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    suspend fun updateAllEnabledSources(
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): BlocklistUpdateOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext as AdZeroApplication
        val repo = app.container.repository
        val dao = app.container.database.blocklistDao()

        val sources = dao.getSourcesSnapshot().filter { it.enabled }
        if (sources.isEmpty()) {
            return@withContext BlocklistUpdateOutcome(0, 0, 0, "No enabled blocklists to update.")
        }

        var updated = 0
        var failed = 0
        var totalDomains = 0

        sources.forEachIndexed { index, source ->
            val progress = index.toFloat() / sources.size.toFloat()
            onProgress(progress, "Updating ${source.name}…")

            val uri = Uri.parse(source.url)
            if (uri.scheme != "https" || uri.host.orEmpty().lowercase() !in allowedHosts) {
                failed++
                dao.upsertSource(source.copy(status = "ERROR"))
                return@forEachIndexed
            }

            dao.upsertSource(source.copy(status = "UPDATING"))
            val downloaded = runCatching {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "AdZero-Android/1.0")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val bodyBytes = resp.body?.bytes() ?: throw IllegalStateException("Empty body")
                    if (bodyBytes.size > MAX_LIST_BYTES) {
                        throw IllegalStateException("Blocklist exceeds safe size limit")
                    }
                    String(bodyBytes, Charsets.UTF_8)
                }
            }.getOrNull()

            if (downloaded != null) {
                val parsed = BundledBlocklists.parseDomainListText(downloaded, maxDomains = 35_000)
                if (parsed.size >= 10) {
                    val merged = LinkedHashSet<String>(parsed)
                    // Retain bundled core entries so essential rules never vanish
                    BundledBlocklists.bundledEntitiesForSource(source.id).forEach { merged.add(it.domain) }
                    val entities = merged.map { domain ->
                        BlocklistDomainEntity(
                            sourceId = source.id,
                            domain = domain,
                            category = source.category
                        )
                    }
                    dao.replaceDomainsForSource(source.id, entities)
                    dao.upsertSource(
                        source.copy(
                            domainCount = entities.size,
                            lastUpdatedAt = System.currentTimeMillis(),
                            status = "UPDATED",
                            versionTag = "live-${System.currentTimeMillis() / 86_400_000L}"
                        )
                    )
                    updated++
                    totalDomains += entities.size
                } else {
                    // Malformed or empty response -> keep previous working blocklist untouched
                    failed++
                    dao.upsertSource(source.copy(status = "ERROR"))
                }
            } else {
                // Network failure -> preserve last working blocklist
                failed++
                dao.upsertSource(source.copy(status = "ERROR"))
            }
        }

        onProgress(1f, "Refreshing local filter engine…")
        app.container.preferences.recordBlocklistUpdate()
        repo.reloadFilterEngineSnapshot()
        app.container.database.serviceEventDao().insert(
            ServiceEventEntity(
                timestamp = System.currentTimeMillis(),
                eventType = "BLOCKLIST",
                detail = "Updated $updated list(s), $failed failed; active list count refreshed."
            )
        )

        val msg = when {
            updated > 0 && failed == 0 -> "Updated $updated blocklist(s) ($totalDomains validated domains)."
            updated > 0 -> "Updated $updated list(s); $failed failed (previous working lists preserved)."
            else -> "Could not reach remote blocklist servers. Existing local lists remain active."
        }
        BlocklistUpdateOutcome(updated, failed, totalDomains, msg)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "adzero_periodic_blocklist_update"
        private const val MAX_LIST_BYTES = 10 * 1024 * 1024 // 10 MB safety cap
    }
}

class BlocklistUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val manager = BlocklistUpdateManager(applicationContext)
            manager.updateAllEnabledSources()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
