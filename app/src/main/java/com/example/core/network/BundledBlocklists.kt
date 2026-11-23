package com.example.core.network

import com.example.core.database.BlocklistDomainEntity
import com.example.core.database.BlocklistSourceEntity
import com.example.data.model.DomainCategory

object BundledBlocklists {

    const val SOURCE_ADS_CORE = "adzero_ads_core"
    const val SOURCE_TRACKERS_CORE = "adzero_trackers_core"
    const val SOURCE_MALWARE_CORE = "adzero_malware_core"
    const val SOURCE_STRICT_TELEMETRY = "adzero_strict_telemetry"

    val defaultSources: List<BlocklistSourceEntity>
        get() = listOf(
        BlocklistSourceEntity(
            id = SOURCE_ADS_CORE,
            name = "AdZero Core Ads (StevenBlack / AdGuard)",
            description = "Major mobile and web ad networks, ad exchanges, and banner servers.",
            category = DomainCategory.AD.name,
            url = "https://adaway.org/hosts.txt",
            enabled = true,
            domainCount = bundledAdDomains.size,
            lastUpdatedAt = 0L,
            status = "BUNDLED",
            versionTag = "2026.11-core"
        ),
        BlocklistSourceEntity(
            id = SOURCE_TRACKERS_CORE,
            name = "Tracker & Analytics Shield",
            description = "Cross-app SDK telemetry, behavioral profiling, and attribution trackers.",
            category = DomainCategory.TRACKER.name,
            url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/light.txt",
            enabled = true,
            domainCount = bundledTrackerDomains.size,
            lastUpdatedAt = 0L,
            status = "BUNDLED",
            versionTag = "2026.11-core"
        ),
        BlocklistSourceEntity(
            id = SOURCE_MALWARE_CORE,
            name = "Malware & Phishing Guard (URLHaus / Threat)",
            description = "Known phishing domains, drive-by malware hosts, and scam redirects.",
            category = DomainCategory.MALWARE.name,
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            enabled = true,
            domainCount = bundledMalwareDomains.size,
            lastUpdatedAt = 0L,
            status = "BUNDLED",
            versionTag = "2026.11-core"
        ),
        BlocklistSourceEntity(
            id = SOURCE_STRICT_TELEMETRY,
            name = "Strict Telemetry & OEM Metrics",
            description = "Aggressive OEM telemetry, smart-TV beacons, and secondary ad SDKs (Strict Mode).",
            category = DomainCategory.TRACKER.name,
            url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/tif.mini.txt",
            enabled = false,
            domainCount = bundledStrictDomains.size,
            lastUpdatedAt = 0L,
            status = "BUNDLED",
            versionTag = "2026.11-strict"
        )
    )

    val bundledAdDomains: List<String> = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "ads.google.com",
        "admob.com",
        "app-measurement.com",
        "adnxs.com",
        "adsrvr.org",
        "advertising.com",
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "bidswitch.net",
        "moatads.com",
        "unityads.unity3d.com",
        "ads.vungle.com",
        "applovin.com",
        "d.applovin.com",
        "rt.applovin.com",
        "inmobi.com",
        "ads.inmobi.com",
        "chartboost.com",
        "ironsrc.com",
        "supersonicads.com",
        "mopub.com",
        "ads.mopub.com",
        "startappservice.com",
        "fyber.com",
        "tapjoy.com",
        "tapjoyads.com",
        "adcolony.com",
        "liftoff.io",
        "smartadserver.com",
        "yieldmo.com",
        "media.net",
        "revcontent.com",
        "mgid.com",
        "propellerads.com",
        "popads.net",
        "exoclick.com",
        "adroll.com",
        "ads.yahoo.com",
        "ads.twitter.com",
        "ads-api.twitter.com",
        "ads.linkedin.com",
        "ads.pinterest.com",
        "ads.tiktok.com",
        "ads-sg.tiktok.com",
        "an.facebook.com",
        "ads.reddit.com",
        "events.redditmedia.com"
    )

    val bundledTrackerDomains: List<String> = listOf(
        "google-analytics.com",
        "ssl.google-analytics.com",
        "www.google-analytics.com",
        "analytics.google.com",
        "firebase-settings.crashlytics.com",
        "app.adjust.com",
        "adjust.com",
        "appsflyer.com",
        "launches.appsflyer.com",
        "inapps.appsflyer.com",
        "branch.io",
        "api2.branch.io",
        "mixpanel.com",
        "api.mixpanel.com",
        "amplitude.com",
        "api2.amplitude.com",
        "segment.io",
        "api.segment.io",
        "flurry.com",
        "data.flurry.com",
        "scorecardresearch.com",
        "sb.scorecardresearch.com",
        "quantserve.com",
        "pixel.quantserve.com",
        "hotjar.com",
        "static.hotjar.com",
        "fullstory.com",
        "rs.fullstory.com",
        "newrelic.com",
        "bam.nr-data.net",
        "bugsnag.com",
        "notify.bugsnag.com",
        "braze.com",
        "sdk.iad-01.braze.com",
        "onesignal.com",
        "clevertap.com",
        "kochava.com",
        "control.kochava.com",
        "singular.net",
        "demdex.net",
        "omtrdc.net",
        "2o7.net",
        "bluekai.com",
        "krxd.net",
        "rlcdn.com",
        "adsymptotic.com",
        "mathtag.com",
        "pixel.facebook.com",
        "graph.instagram.com",
        "log-va.tiktokv.com",
        "analytics.s3.amazonaws.com",
        "telemetry.mozilla.org",
        "events.data.microsoft.com",
        "vortex.data.microsoft.com"
    )

    val bundledMalwareDomains: List<String> = listOf(
        "malware-test.adzero.invalid",
        "phishing-test.adzero.invalid",
        "coinhive.com",
        "coin-hive.com",
        "authedmine.com",
        "cryptoloot.pro",
        "minero.cc",
        "jsecoin.com",
        "browsermine.com",
        "malicious-redirect-example.invalid",
        "fake-login-verify.invalid",
        "credential-harvest-example.invalid"
    )

    val bundledStrictDomains: List<String> = listOf(
        "metrics.icloud.com",
        "samsungcloudsolution.com",
        "config.samsungads.com",
        "events.samsungads.com",
        "tracking.miui.com",
        "data.mistat.xiaomi.com",
        "logservice.hicloud.com",
        "metrics.data.hicloud.com",
        "analytics.oneplus.cn",
        "open.oneplus.net",
        "telemetry.dropbox.com",
        "log.snapchat.com",
        "app-analytics-v2.snapchat.com",
        "telemetry.sdk.dualstack.us-east-1.amazonaws.com"
    )

    fun bundledEntitiesForSource(sourceId: String): List<BlocklistDomainEntity> {
        val (domains, category) = when (sourceId) {
            SOURCE_ADS_CORE -> bundledAdDomains to DomainCategory.AD.name
            SOURCE_TRACKERS_CORE -> bundledTrackerDomains to DomainCategory.TRACKER.name
            SOURCE_MALWARE_CORE -> bundledMalwareDomains to DomainCategory.MALWARE.name
            SOURCE_STRICT_TELEMETRY -> bundledStrictDomains to DomainCategory.TRACKER.name
            else -> emptyList<String>() to DomainCategory.AD.name
        }
        return domains.distinct().map {
            BlocklistDomainEntity(sourceId = sourceId, domain = it.lowercase(), category = category)
        }
    }

    private val domainRegex = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
    private val ignoredHosts = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "ip6-localhost",
        "ip6-loopback",
        "0.0.0.0"
    )

    /**
     * Parses hosts files (`0.0.0.0 ads.example.com`), plain domain lists (`ads.example.com`),
     * and AdBlock-style rules (`||ads.example.com^`) safely while discarding malformed lines.
     */
    fun parseDomainListText(rawText: String, maxDomains: Int = 50_000): Set<String> {
        val result = LinkedHashSet<String>()
        rawText.lineSequence().forEach { rawLine ->
            if (result.size >= maxDomains) return@forEach
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach

            val candidate = when {
                line.startsWith("||") && line.contains("^") -> {
                    line.removePrefix("||").substringBefore("^").substringBefore("/")
                }
                line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1") || line.startsWith("::") -> {
                    line.split(Regex("\\s+")).getOrNull(1).orEmpty()
                }
                !line.contains(" ") && !line.contains("/") -> line
                else -> ""
            }.trim().trimEnd('.').lowercase()

            if (candidate.isNotEmpty() &&
                candidate.length <= 253 &&
                candidate !in ignoredHosts &&
                domainRegex.matches(candidate)
            ) {
                result.add(candidate)
            }
        }
        return result
    }
}
