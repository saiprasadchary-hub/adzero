package com.example.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.example.AdZeroApplication
import com.example.MainActivity
import com.example.R
import com.example.core.database.DnsLogEntity
import com.example.core.network.DnsFilterEngine
import com.example.core.network.DnsPacketCodec
import com.example.core.network.UpstreamDnsConfig
import com.example.data.model.DnsAction
import com.example.data.model.DnsProviderPresets
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnEnforcementMode
import com.example.service.quicksettings.AdZeroTileService
import com.example.service.traffic.NetworkTrafficMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-only Android VpnService implementing:
 * 1) Standard DNS Filtering Mode: routes ONLY virtual local DNS traffic (10.88.0.2 / fd88::2)
 *    through the TUN interface, leaving regular app TCP/UDP traffic untouched.
 * 2) Strict Packet Isolation Mode (opt-in): routes 0.0.0.0/0 & ::/0 exclusively for blocked
 *    packages via addAllowedApplication and drops their packets locally, keeping all other apps online.
 */
class AdZeroVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dnsExecutor = Executors.newFixedThreadPool(4)

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    private var tunnelJob: Job? = null
    private val isPaused = AtomicBoolean(false)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val appContainer by lazy { (application as AdZeroApplication).container }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        appContainer.upstreamResolver.bindVpnSocketProtector { sock -> protect(sock) }
        registerNetworkCallback()
        NetworkTrafficMonitorService.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnSession(userInitiated = true)
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                isPaused.set(true)
                appContainer.protectionStateHolder.setState(VpnConnectionState.PAUSED)
                updateForegroundNotification(paused = true)
                AdZeroTileService.requestTileSync(this)
                serviceScope.launch {
                    appContainer.repository.logServiceEvent("VPN", "Protection paused by user.")
                }
                return START_STICKY
            }
            ACTION_RESUME -> {
                isPaused.set(false)
                if (vpnInterface != null) {
                    appContainer.protectionStateHolder.setState(VpnConnectionState.ACTIVE)
                    updateForegroundNotification(paused = false)
                    AdZeroTileService.requestTileSync(this)
                    serviceScope.launch {
                        appContainer.repository.logServiceEvent("VPN", "Protection resumed.")
                    }
                } else {
                    startOrRebuildVpn()
                }
                return START_STICKY
            }
            ACTION_REBUILD -> {
                if (vpnInterface != null || appContainer.protectionStateHolder.snapshot.value.isRunning) {
                    startOrRebuildVpn()
                }
                return START_STICKY
            }
            else -> {
                isPaused.set(false)
                startOrRebuildVpn()
                return START_STICKY
            }
        }
    }

    private fun startOrRebuildVpn() {
        val currentState = appContainer.protectionStateHolder.snapshot.value.state
        if (currentState != VpnConnectionState.ACTIVE) {
            appContainer.protectionStateHolder.setState(VpnConnectionState.CONNECTING)
        }
        startForegroundSafely(buildNotification(paused = false, connecting = true))

        serviceScope.launch {
            try {
                appContainer.repository.reloadFilterEngineSnapshot()
                val settings = appContainer.preferences.settingsFlow.first()
                val filterCfg = appContainer.filterEngine.getConfig()
                val dnsPreset = DnsProviderPresets.byId(settings.dnsProviderId)
                val dnsLabel = "${dnsPreset.name} (${settings.dnsProtocol.name})"

                // Tear down existing TUN cleanly before establishing replacement
                tearDownTunnelOnly()

                val blockedPackages = LinkedHashSet<String>().apply {
                    addAll(filterCfg.blockedAppPackages)
                    if (settings.ghostModeEnabled) {
                        add(DnsFilterEngine.WHATSAPP_PKG)
                        add(DnsFilterEngine.WHATSAPP_BUSINESS_PKG)
                    }
                }

                val useStrictIsolation = settings.enforcementMode == VpnEnforcementMode.STRICT_PACKET_ISOLATION &&
                    blockedPackages.isNotEmpty()

                val builder = Builder()
                    .setSession("AdZero Local Protection")
                    .setMtu(1500)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                if (useStrictIsolation) {
                    // Strict Packet Isolation Mode: isolate ONLY blocked apps into local sinkhole
                    builder.addAddress(LOCAL_IPV4_ADDR, 30)
                    builder.addRoute("0.0.0.0", 0)
                    runCatching {
                        builder.addAddress(LOCAL_IPV6_ADDR, 126)
                        builder.addRoute("::", 0)
                    }
                    var addedCount = 0
                    blockedPackages.forEach { pkg ->
                        runCatching {
                            builder.addAllowedApplication(pkg)
                            addedCount++
                        }
                    }
                    if (addedCount == 0) {
                        // Fallback to standard DNS mode if none of the blocked packages are installed
                        configureStandardDnsBuilder(builder, filterCfg.dnsExcludedAppPackages)
                    }
                } else {
                    configureStandardDnsBuilder(builder, filterCfg.dnsExcludedAppPackages)
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    appContainer.protectionStateHolder.setState(
                        state = VpnConnectionState.ERROR,
                        errorMessage = "VPN interface could not be established. Please confirm VPN permission."
                    )
                    appContainer.preferences.setProtectionDesired(false)
                    appContainer.repository.logServiceEvent("VPN", "Failed to establish VpnService interface.")
                    AdZeroTileService.requestTileSync(this@AdZeroVpnService)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                vpnInterface = pfd
                val effectiveMode = if (useStrictIsolation) {
                    VpnEnforcementMode.STRICT_PACKET_ISOLATION
                } else {
                    VpnEnforcementMode.STANDARD_DNS_FILTERING
                }

                appContainer.preferences.setProtectionDesired(true)
                appContainer.preferences.recordProtectionActivated()
                appContainer.protectionStateHolder.setState(
                    state = VpnConnectionState.ACTIVE,
                    enforcementMode = effectiveMode,
                    activeDnsLabel = dnsLabel,
                    errorMessage = null
                )
                updateForegroundNotification(paused = false)
                AdZeroTileService.requestTileSync(this@AdZeroVpnService)

                appContainer.repository.logServiceEvent(
                    "VPN",
                    if (effectiveMode == VpnEnforcementMode.STRICT_PACKET_ISOLATION) {
                        "VPN active in Strict Packet Isolation mode (${blockedPackages.size} app(s) isolated)."
                    } else {
                        "VPN active in Standard DNS Filtering mode ($dnsLabel)."
                    }
                )

                val upstreamConfig = UpstreamDnsConfig.fromPreferences(
                    providerId = settings.dnsProviderId,
                    protocol = settings.dnsProtocol,
                    customIpv4 = settings.customDnsIpv4,
                    customDot = settings.customDotHostname,
                    customDoh = settings.customDohUrl
                )

                tunnelJob = serviceScope.launch {
                    if (effectiveMode == VpnEnforcementMode.STRICT_PACKET_ISOLATION) {
                        runStrictDropLoop(pfd)
                    } else {
                        runDnsTunnelLoop(pfd, upstreamConfig)
                    }
                }
            } catch (t: Throwable) {
                appContainer.protectionStateHolder.setState(
                    state = VpnConnectionState.ERROR,
                    errorMessage = t.message ?: "Unexpected VPN error."
                )
                appContainer.repository.logServiceEvent("VPN", "VPN start error: ${t.message}")
                AdZeroTileService.requestTileSync(this@AdZeroVpnService)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun configureStandardDnsBuilder(builder: Builder, excludedApps: Set<String>) {
        // Route ONLY virtual DNS addresses so normal TCP/UDP app traffic bypasses the tunnel completely
        builder.addAddress(LOCAL_IPV4_ADDR, 30)
        builder.addDnsServer(LOCAL_IPV4_DNS)
        builder.addRoute(LOCAL_IPV4_DNS, 32)
        runCatching {
            builder.addAddress(LOCAL_IPV6_ADDR, 126)
            builder.addDnsServer(LOCAL_IPV6_DNS)
            builder.addRoute(LOCAL_IPV6_DNS, 128)
        }
        // Always exclude AdZero itself to prevent routing loops
        runCatching { builder.addDisallowedApplication(packageName) }
        excludedApps.forEach { pkg ->
            runCatching { builder.addDisallowedApplication(pkg) }
        }
    }

    private fun runStrictDropLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(4096)
        while (serviceScope.isActive && vpnInterface == pfd) {
            try {
                val len = input.read(buffer)
                if (len < 0) break
                // Drop all packets from isolated apps cleanly so sockets fail/disconnect immediately
            } catch (_: Throwable) {
                break
            }
        }
    }

    private fun runDnsTunnelLoop(pfd: ParcelFileDescriptor, upstreamConfig: UpstreamDnsConfig) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val writeLock = Any()
        val buffer = ByteArray(32767)

        while (serviceScope.isActive && vpnInterface == pfd) {
            val length = try {
                input.read(buffer)
            } catch (_: Throwable) {
                break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            val parsed = DnsPacketCodec.parseIpUdpDns(packetCopy, length) ?: continue
            if (parsed.destPort != 53) continue

            dnsExecutor.execute {
                handleDnsQuery(parsed, upstreamConfig, output, writeLock)
            }
        }
    }

    private fun handleDnsQuery(
        parsed: com.example.core.network.ParsedDnsPacket,
        upstreamConfig: UpstreamDnsConfig,
        output: FileOutputStream,
        writeLock: Any
    ) {
        val startedAt = System.currentTimeMillis()
        val packageName = resolveOriginPackageName(parsed)
        val engine = appContainer.filterEngine

        val paused = isPaused.get()
        val decision = if (paused) {
            com.example.core.network.FilterDecision(
                blocked = false,
                category = com.example.data.model.DomainCategory.ALLOWED,
                matchedRule = "Paused"
            )
        } else {
            engine.evaluate(parsed.queryDomain, packageName)
        }

        val dnsResponse: ByteArray = if (decision.blocked) {
            DnsPacketCodec.buildBlockedDnsResponse(parsed.dnsPayload, parsed.queryType)
        } else {
            val cached = engine.getCachedResponse(parsed.queryDomain, parsed.queryType)
            if (cached != null) {
                cached
            } else {
                val resolved = appContainer.upstreamResolver.resolveSync(
                    dnsQueryPayload = parsed.dnsPayload,
                    config = upstreamConfig,
                    udpProtector = { sock -> protect(sock) },
                    tcpProtector = { sock -> protect(sock) }
                )
                if (resolved != null) {
                    engine.putCachedResponse(parsed.queryDomain, parsed.queryType, resolved)
                    resolved
                } else {
                    DnsPacketCodec.buildRcodeDnsResponse(parsed.dnsPayload, rcode = 2) // SERVFAIL
                }
            }
        }

        val responsePacket = DnsPacketCodec.buildIpUdpResponse(parsed, dnsResponse)
        synchronized(writeLock) {
            runCatching {
                output.write(responsePacket)
                output.flush()
            }
        }

        val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        appContainer.protectionStateHolder.recordDnsEvent(
            blocked = decision.blocked,
            category = decision.category,
            cacheSize = engine.cacheSize(),
            cacheHits = engine.cacheHits()
        )
        appContainer.logBatcher.enqueue(
            DnsLogEntity(
                timestamp = System.currentTimeMillis(),
                domain = parsed.queryDomain,
                packageName = packageName,
                action = if (decision.blocked) DnsAction.BLOCKED.name else DnsAction.ALLOWED.name,
                category = decision.category.name,
                matchedRule = decision.matchedRule,
                latencyMs = latency
            )
        )
    }

    /**
     * Resolves calling app package via ConnectivityManager.getConnectionOwnerUid (Android 10+).
     * Returns "unknown" honestly when OS attribution is unavailable — never fabricates an app name.
     */
    private fun resolveOriginPackageName(parsed: com.example.core.network.ParsedDnsPacket): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unknown"
        return runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unknown"
            val local = InetSocketAddress(InetAddress.getByAddress(parsed.sourceAddress), parsed.sourcePort)
            val remote = InetSocketAddress(InetAddress.getByAddress(parsed.destAddress), parsed.destPort)
            val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
            if (uid <= 0) return "unknown"
            packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "unknown"
        }.getOrDefault("unknown")
    }

    private fun stopVpnSession(userInitiated: Boolean) {
        tearDownTunnelOnly()
        isPaused.set(false)
        appContainer.protectionStateHolder.setState(VpnConnectionState.DISCONNECTED)
        AdZeroTileService.requestTileSync(this)
        serviceScope.launch {
            if (userInitiated) {
                appContainer.preferences.setProtectionDesired(false)
            }
            appContainer.logBatcher.flushNow()
            appContainer.repository.logServiceEvent(
                "VPN",
                if (userInitiated) "Protection stopped by user." else "VPN session revoked or stopped."
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun tearDownTunnelOnly() {
        tunnelJob?.cancel()
        tunnelJob = null
        val pfd = vpnInterface
        vpnInterface = null
        runCatching { pfd?.close() }
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpnSession(userInitiated = false)
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        appContainer.upstreamResolver.bindVpnSocketProtector(null)
        tearDownTunnelOnly()
        dnsExecutor.shutdownNow()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                appContainer.protectionStateHolder.updateNetworkAvailability(true)
                runCatching { setUnderlyingNetworks(null) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                appContainer.protectionStateHolder.updateNetworkAvailability(hasInternet)
            }

            override fun onLost(network: Network) {
                val active = cm.activeNetwork
                val stillConnected = active != null && cm.getNetworkCapabilities(active)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                appContainer.protectionStateHolder.updateNetworkAvailability(stillConnected)
                runCatching { setUnderlyingNetworks(null) }
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
        networkCallback = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(paused: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(paused = paused, connecting = false))
    }

    private fun buildNotification(paused: Boolean, connecting: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = Intent(this, AdZeroVpnService::class.java).apply {
            action = if (paused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePi = PendingIntent.getService(
            this,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdZeroVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            connecting -> "Connecting AdZero…"
            paused -> getString(R.string.vpn_notification_title_paused)
            else -> getString(R.string.vpn_notification_title_active)
        }
        val snap = appContainer.protectionStateHolder.snapshot.value
        val text = when {
            connecting -> "Establishing local on-device DNS interface"
            paused -> "Filtering temporarily paused. Tap Resume to restore."
            snap.enforcementMode == VpnEnforcementMode.STRICT_PACKET_ISOLATION ->
                "Strict Packet Isolation active for blocked apps"
            else -> "Local DNS filtering via ${snap.activeDnsLabel}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_adzero_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPi)
            .addAction(0, if (paused) "Resume" else "Pause", togglePi)
            .addAction(0, "Stop", stopPi)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.adzero.VPN_START"
        const val ACTION_STOP = "com.example.adzero.VPN_STOP"
        const val ACTION_PAUSE = "com.example.adzero.VPN_PAUSE"
        const val ACTION_RESUME = "com.example.adzero.VPN_RESUME"
        const val ACTION_REBUILD = "com.example.adzero.VPN_REBUILD"

        private const val LOCAL_IPV4_ADDR = "10.88.0.1"
        private const val LOCAL_IPV4_DNS = "10.88.0.2"
        private const val LOCAL_IPV6_ADDR = "fd88::1"
        private const val LOCAL_IPV6_DNS = "fd88::2"

        private const val CHANNEL_ID = "adzero_vpn_channel"
        private const val NOTIFICATION_ID = 4401

        fun start(context: Context) {
            val intent = Intent(context, AdZeroVpnService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdZeroVpnService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, AdZeroVpnService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, AdZeroVpnService::class.java).apply { action = ACTION_RESUME }
            context.startService(intent)
        }

        fun rebuildIfRunning(context: Context) {
            val app = context.applicationContext as? AdZeroApplication ?: return
            if (app.container.protectionStateHolder.snapshot.value.isRunning) {
                val intent = Intent(context, AdZeroVpnService::class.java).apply { action = ACTION_REBUILD }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
