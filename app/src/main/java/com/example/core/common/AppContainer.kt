package com.example.core.common

import android.content.Context
import com.example.core.database.AdZeroDatabase
import com.example.core.datastore.AdZeroPreferences
import com.example.core.network.BlocklistUpdateManager
import com.example.core.network.DnsFilterEngine
import com.example.core.network.UpstreamDnsResolver
import com.example.core.security.CryptoManager
import com.example.data.repository.AdZeroRepository
import com.example.data.repository.DnsLogBatcher
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.NetworkTrafficMonitorStateHolder
import com.example.data.repository.ProtectionStateHolder
import com.example.service.scheduler.GhostScheduleManager

/**
 * Clean, constructor-injected application dependency graph.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: AdZeroDatabase by lazy { AdZeroDatabase.getInstance(appContext) }
    val preferences: AdZeroPreferences by lazy { AdZeroPreferences(appContext) }
    val cryptoManager: CryptoManager by lazy { CryptoManager() }
    val filterEngine: DnsFilterEngine by lazy { DnsFilterEngine() }
    val upstreamResolver: UpstreamDnsResolver by lazy { UpstreamDnsResolver() }
    val protectionStateHolder: ProtectionStateHolder by lazy { ProtectionStateHolder() }
    val trafficMonitorStateHolder: NetworkTrafficMonitorStateHolder by lazy { NetworkTrafficMonitorStateHolder() }
    val logBatcher: DnsLogBatcher by lazy { DnsLogBatcher(database.dnsLogDao()) }
    val installedAppsRepository: InstalledAppsRepository by lazy { InstalledAppsRepository(appContext) }
    val blocklistUpdateManager: BlocklistUpdateManager by lazy { BlocklistUpdateManager(appContext) }
    val ghostScheduleManager: GhostScheduleManager by lazy { GhostScheduleManager(appContext) }

    val repository: AdZeroRepository by lazy {
        AdZeroRepository(
            database = database,
            preferences = preferences,
            cryptoManager = cryptoManager,
            filterEngine = filterEngine,
            upstreamResolver = upstreamResolver,
            logBatcher = logBatcher,
            protectionStateHolder = protectionStateHolder
        )
    }
}
