package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AdZeroDatabase
import com.example.core.database.AppRuleEntity
import com.example.core.database.DnsLogEntity
import com.example.core.datastore.AdZeroPreferences
import com.example.core.network.DnsFilterEngine
import com.example.core.network.UpstreamDnsResolver
import com.example.core.security.CryptoManager
import com.example.data.repository.AdZeroRepository
import com.example.data.repository.DnsLogBatcher
import com.example.data.repository.ProtectionStateHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdZeroRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AdZeroDatabase
    private lateinit var repo: AdZeroRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AdZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AdZeroRepository(
            database = db,
            preferences = AdZeroPreferences(context),
            cryptoManager = CryptoManager(),
            filterEngine = DnsFilterEngine(),
            upstreamResolver = UpstreamDnsResolver(),
            logBatcher = DnsLogBatcher(db.dnsLogDao()),
            protectionStateHolder = ProtectionStateHolder()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name string resource matches AdZero`() {
        assertEquals("AdZero", context.getString(R.string.app_name))
    }

    @Test
    fun `crypto manager encrypts and decrypts ghost inbox previews with unique ivs`() {
        val crypto = CryptoManager()
        val p1 = crypto.encrypt("Hello from WhatsApp")
        val p2 = crypto.encrypt("Hello from WhatsApp")

        assertNotEquals(p1.ivBase64, p2.ivBase64)
        assertNotEquals(p1.ciphertextBase64, "Hello from WhatsApp")
        assertEquals("Hello from WhatsApp", crypto.decrypt(p1))
        assertEquals("Hello from WhatsApp", crypto.decrypt(p2))
    }

    @Test
    fun `room analytics queries aggregate dns logs accurately and export csv`() = runTest {
        val now = System.currentTimeMillis()
        db.dnsLogDao().insertAll(
            listOf(
                DnsLogEntity(
                    timestamp = now - 1000,
                    domain = "doubleclick.net",
                    packageName = "com.android.chrome",
                    action = "BLOCKED",
                    category = "AD",
                    matchedRule = "Blocklist: doubleclick.net",
                    latencyMs = 2
                ),
                DnsLogEntity(
                    timestamp = now - 500,
                    domain = "mixpanel.com",
                    packageName = "com.android.chrome",
                    action = "BLOCKED",
                    category = "TRACKER",
                    matchedRule = "Blocklist: mixpanel.com",
                    latencyMs = 3
                ),
                DnsLogEntity(
                    timestamp = now - 200,
                    domain = "developer.android.com",
                    packageName = "unknown",
                    action = "ALLOWED",
                    category = "ALLOWED",
                    matchedRule = "Passed",
                    latencyMs = 18
                )
            )
        )

        val overview = db.dnsLogDao().observeOverviewSince(now - 60_000).first()
        assertEquals(3L, overview.totalRequests)
        assertEquals(2L, overview.blockedRequests)
        assertEquals(1L, overview.allowedRequests)
        assertEquals(1L, overview.blockedAds)
        assertEquals(1L, overview.blockedTrackers)
        assertEquals(3L, overview.uniqueDomains)
        assertEquals(1L, overview.activeApps) // "unknown" excluded from activeApps count

        val csv = repo.buildAnalyticsCsv()
        assertTrue(csv.contains("timestamp,domain,package_name,action,category,matched_rule,latency_ms"))
        assertTrue(csv.contains("\"doubleclick.net\""))
        assertTrue(csv.contains("\"unknown\""))
    }

    @Test
    fun `encrypted ghost inbox messages persist and decrypt through repository`() = runTest {
        repo.saveEncryptedGhostMessage(
            senderName = "Alice",
            messagePreview = "Meet at 5pm?",
            sourcePackage = DnsFilterEngine.WHATSAPP_PKG
        )

        val messages = repo.ghostMessagesFlow.first()
        assertEquals(1, messages.size)
        assertEquals("Alice", messages.first().senderName)
        assertEquals("Meet at 5pm?", messages.first().messagePreview)
        assertEquals("WhatsApp", messages.first().sourceLabel)

        repo.deleteGhostMessage(messages.first().id)
        assertTrue(repo.ghostMessagesFlow.first().isEmpty())
    }

    @Test
    fun `app rule persistence enforces mutual clarity between internet block and dns exclusion`() = runTest {
        repo.updateAppRule("com.example.demo", internetBlocked = true, dnsExcluded = true)
        val saved: AppRuleEntity? = db.appRuleDao().getByPackage("com.example.demo")
        assertTrue(saved?.internetBlocked == true)
        assertTrue(saved?.dnsExcluded == false)
    }
}
