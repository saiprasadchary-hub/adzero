package com.example

import com.example.core.network.BundledBlocklists
import com.example.core.network.DnsFilterEngine
import com.example.core.network.DnsPacketCodec
import com.example.core.network.FilterConfigSnapshot
import com.example.data.model.DomainCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterEngineTest {

    @Test
    fun `blocklist matches exact and parent subdomains`() {
        val engine = DnsFilterEngine()
        engine.updateConfig(
            FilterConfigSnapshot(
                blockedDomains = mapOf(
                    "doubleclick.net" to DomainCategory.AD,
                    "mixpanel.com" to DomainCategory.TRACKER
                )
            )
        )

        val exact = engine.evaluate("doubleclick.net")
        assertTrue(exact.blocked)
        assertEquals(DomainCategory.AD, exact.category)

        val sub = engine.evaluate("stats.g.doubleclick.net")
        assertTrue(sub.blocked)
        assertEquals(DomainCategory.AD, sub.category)

        val clean = engine.evaluate("developer.android.com")
        assertFalse(clean.blocked)
        assertEquals(DomainCategory.ALLOWED, clean.category)
    }

    @Test
    fun `allowlist takes precedence over blocklists and custom blocks`() {
        val engine = DnsFilterEngine()
        engine.updateConfig(
            FilterConfigSnapshot(
                blockedDomains = mapOf("doubleclick.net" to DomainCategory.AD),
                customBlockExact = setOf("ads.example.com"),
                allowExact = setOf("safe.doubleclick.net"),
                allowWildcards = listOf(DnsFilterEngine.compileWildcard("*.example.com")),
                allowWildcardPatterns = listOf("*.example.com")
            )
        )

        val allowedSub = engine.evaluate("safe.doubleclick.net")
        assertFalse(allowedSub.blocked)
        assertTrue(allowedSub.matchedRule.contains("Allowlist"))

        val allowedWildcard = engine.evaluate("ads.example.com")
        assertFalse(allowedWildcard.blocked)
        assertTrue(allowedWildcard.matchedRule.contains("Allowlist wildcard"))

        val stillBlocked = engine.evaluate("ad.doubleclick.net")
        assertTrue(stillBlocked.blocked)
    }

    @Test
    fun `per-app firewall blocks app and per-app exclusion bypasses DNS filtering`() {
        val engine = DnsFilterEngine()
        engine.updateConfig(
            FilterConfigSnapshot(
                blockedDomains = mapOf("doubleclick.net" to DomainCategory.AD),
                blockedAppPackages = setOf("com.blocked.app"),
                dnsExcludedAppPackages = setOf("com.bank.app"),
                ghostModeEnabled = true
            )
        )

        // Blocked app cannot even resolve clean domains
        val blockedAppDecision = engine.evaluate("api.clean.org", packageName = "com.blocked.app")
        assertTrue(blockedAppDecision.blocked)
        assertEquals(DomainCategory.FIREWALL_BLOCK, blockedAppDecision.category)

        // WhatsApp Ghost Mode blocks WhatsApp domains
        val waDecision = engine.evaluate("g.whatsapp.net", packageName = DnsFilterEngine.WHATSAPP_PKG)
        assertTrue(waDecision.blocked)
        assertEquals("WhatsApp Ghost Mode", waDecision.matchedRule)

        // Excluded app bypasses ad blocklist
        val bankDecision = engine.evaluate("doubleclick.net", packageName = "com.bank.app")
        assertFalse(bankDecision.blocked)
        assertEquals("App DNS Excluded", bankDecision.matchedRule)
    }

    @Test
    fun `category toggles control ad tracker malware and strict rules`() {
        val engine = DnsFilterEngine()
        engine.updateConfig(
            FilterConfigSnapshot(
                adBlockingEnabled = false,
                trackerBlockingEnabled = true,
                strictModeEnabled = true,
                blockedDomains = mapOf(
                    "doubleclick.net" to DomainCategory.AD,
                    "mixpanel.com" to DomainCategory.TRACKER
                ),
                strictDomains = setOf("telemetry.oem.invalid")
            )
        )

        assertFalse(engine.evaluate("doubleclick.net").blocked)
        assertTrue(engine.evaluate("mixpanel.com").blocked)
        assertTrue(engine.evaluate("sub.telemetry.oem.invalid").blocked)
    }

    @Test
    fun `bounded LRU cache caches and expires responses`() {
        val engine = DnsFilterEngine(maxCacheEntries = 2, cacheTtlMillis = 1000L)
        val payload = byteArrayOf(1, 2, 3, 4)
        engine.putCachedResponse("a.com", 1, payload, nowMillis = 1000L)
        engine.putCachedResponse("b.com", 1, payload, nowMillis = 1000L)

        assertNotNull(engine.getCachedResponse("a.com", 1, nowMillis = 1500L))
        assertEquals(1L, engine.cacheHits())

        // Adding third entry evicts oldest (b.com because a.com was just accessed)
        engine.putCachedResponse("c.com", 1, payload, nowMillis = 1500L)
        assertEquals(2, engine.cacheSize())
        assertNull(engine.getCachedResponse("b.com", 1, nowMillis = 1600L))

        // Expired entry returns null
        assertNull(engine.getCachedResponse("a.com", 1, nowMillis = 2500L))
    }

    @Test
    fun `dns packet codec builds query parses question and synthesizes blocked response`() {
        val query = DnsPacketCodec.buildDnsQuery("ads.doubleclick.net", transactionId = 0x1234, qType = DnsPacketCodec.TYPE_A)
        val parsed = DnsPacketCodec.parseDnsQuestion(query)
        assertNotNull(parsed)
        assertEquals("ads.doubleclick.net", parsed!!.first)
        assertEquals(DnsPacketCodec.TYPE_A, parsed.second)

        val blockedResp = DnsPacketCodec.buildBlockedDnsResponse(query, DnsPacketCodec.TYPE_A)
        val ip = DnsPacketCodec.extractFirstIpv4Answer(blockedResp)
        assertEquals("0.0.0.0", ip)
    }

    @Test
    fun `blocklist parser extracts hosts plain and abp rules while rejecting invalid entries`() {
        val raw = """
            # Comment line
            ! Adblock comment
            127.0.0.1 localhost
            0.0.0.0 ads.example.com
            ||tracker.sample.org^
            plain.adserver.net
            not a valid domain line
            bad..domain
        """.trimIndent()

        val parsed = BundledBlocklists.parseDomainListText(raw)
        assertEquals(setOf("ads.example.com", "tracker.sample.org", "plain.adserver.net"), parsed)
    }

    @Test
    fun `ghost mode blocks whatsapp domains even when netd masks app package`() {
        val engine = DnsFilterEngine()
        engine.updateConfig(
            FilterConfigSnapshot(
                ghostModeEnabled = true
            )
        )

        val blockedGateway = engine.evaluate("g.whatsapp.net", packageName = "unknown")
        assertTrue(blockedGateway.blocked)
        assertEquals(DomainCategory.FIREWALL_BLOCK, blockedGateway.category)

        val blockedWeb = engine.evaluate("whatsapp.com", packageName = "unknown")
        assertTrue(blockedWeb.blocked)
        assertEquals(DomainCategory.FIREWALL_BLOCK, blockedWeb.category)

        val unrelated = engine.evaluate("developer.android.com", packageName = "unknown")
        assertFalse(unrelated.blocked)

        // Turning Ghost Mode off restores WhatsApp DNS resolution
        engine.updateConfig(
            FilterConfigSnapshot(
                ghostModeEnabled = false
            )
        )
        assertFalse(engine.evaluate("g.whatsapp.net", packageName = "unknown").blocked)
        assertFalse(engine.evaluate("whatsapp.com", packageName = "unknown").blocked)
    }
}
