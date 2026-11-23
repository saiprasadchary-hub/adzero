package com.example.core.network

import com.example.data.model.DnsProbeResult
import com.example.data.model.DnsProtocol
import com.example.data.model.DnsProviderPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class UpstreamDnsConfig(
    val providerId: String,
    val protocol: DnsProtocol,
    val ipv4Primary: String,
    val ipv4Secondary: String,
    val dotHostname: String,
    val dohUrl: String
) {
    companion object {
        fun fromPreferences(
            providerId: String,
            protocol: DnsProtocol,
            customIpv4: String,
            customDot: String,
            customDoh: String
        ): UpstreamDnsConfig {
            val preset = DnsProviderPresets.byId(providerId)
            return if (providerId == DnsProviderPresets.CUSTOM.id) {
                UpstreamDnsConfig(
                    providerId = providerId,
                    protocol = protocol,
                    ipv4Primary = customIpv4.ifBlank { "1.1.1.1" },
                    ipv4Secondary = "1.0.0.1",
                    dotHostname = customDot.ifBlank { "one.one.one.one" },
                    dohUrl = customDoh.ifBlank { "https://cloudflare-dns.com/dns-query" }
                )
            } else {
                UpstreamDnsConfig(
                    providerId = preset.id,
                    protocol = protocol,
                    ipv4Primary = preset.ipv4Primary,
                    ipv4Secondary = preset.ipv4Secondary,
                    dotHostname = preset.dotHostname,
                    dohUrl = preset.dohUrl
                )
            }
        }
    }
}

/**
 * Production real-time upstream DNS resolver supporting:
 * - Persistent pooled HTTP/2 DNS-over-HTTPS (RFC 8484) with bootstrap IP pinning (zero DNS deadlock)
 * - DNS-over-TLS (port 853) with strict TLS certificate & SNI hostname verification
 * - UDP (port 53) with primary/secondary failover
 */
class UpstreamDnsResolver {

    /**
     * Prevents DNS bootstrap loops when resolving DoH hostnames inside a running VpnService.
     */
    private val bootstrapDns = object : Dns {
        private val staticHosts = mapOf(
            "cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
            "one.one.one.one" to listOf("1.1.1.1", "1.0.0.1"),
            "dns.quad9.net" to listOf("9.9.9.9", "149.112.112.112"),
            "dns.adguard-dns.com" to listOf("94.140.14.14", "94.140.15.15"),
            "dns.google" to listOf("8.8.8.8", "8.8.4.4")
        )

        override fun lookup(hostname: String): List<InetAddress> {
            val pinned = staticHosts[hostname.lowercase()]
            if (pinned != null) {
                return pinned.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
                    .ifEmpty { Dns.SYSTEM.lookup(hostname) }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    private val defaultHttpClient: OkHttpClient by lazy {
        createHttpClient(socketProtector = null)
    }

    @Volatile
    private var vpnProtectedClient: OkHttpClient? = null

    fun createHttpClient(socketProtector: ((Socket) -> Unit)?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(bootstrapDns)
            .connectionPool(ConnectionPool(6, 2, TimeUnit.MINUTES))
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (socketProtector != null) {
            builder.socketFactory(object : SocketFactory() {
                private val delegate = getDefault()
                override fun createSocket(): Socket = delegate.createSocket().also(socketProtector)
                override fun createSocket(host: String?, port: Int): Socket =
                    delegate.createSocket(host, port).also(socketProtector)
                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                    delegate.createSocket(host, port, localHost, localPort).also(socketProtector)
                override fun createSocket(host: InetAddress?, port: Int): Socket =
                    delegate.createSocket(host, port).also(socketProtector)
                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                    delegate.createSocket(address, port, localAddress, localPort).also(socketProtector)
            })
        }
        return builder.build()
    }

    fun bindVpnSocketProtector(tcpProtector: ((Socket) -> Unit)?) {
        vpnProtectedClient?.connectionPool?.evictAll()
        vpnProtectedClient = if (tcpProtector != null) createHttpClient(tcpProtector) else null
    }

    fun resolveSync(
        dnsQueryPayload: ByteArray,
        config: UpstreamDnsConfig,
        udpProtector: ((DatagramSocket) -> Unit)? = null,
        tcpProtector: ((Socket) -> Unit)? = null
    ): ByteArray? {
        return when (config.protocol) {
            DnsProtocol.DOH -> resolveViaDoh(dnsQueryPayload, config, useVpnClient = tcpProtector != null)
                ?: resolveViaUdp(dnsQueryPayload, config.ipv4Primary, udpProtector)
            DnsProtocol.DOT -> resolveViaDot(dnsQueryPayload, config, tcpProtector)
                ?: resolveViaUdp(dnsQueryPayload, config.ipv4Primary, udpProtector)
            DnsProtocol.UDP -> resolveViaUdp(dnsQueryPayload, config.ipv4Primary, udpProtector)
                ?: resolveViaUdp(dnsQueryPayload, config.ipv4Secondary, udpProtector)
        }
    }

    private fun resolveViaDoh(
        query: ByteArray,
        config: UpstreamDnsConfig,
        useVpnClient: Boolean
    ): ByteArray? {
        val url = config.dohUrl
        if (!url.startsWith("https://")) return null
        return runCatching {
            val client = if (useVpnClient) vpnProtectedClient ?: defaultHttpClient else defaultHttpClient
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-message")
                .post(query.toRequestBody(DOH_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()?.takeIf { it.size >= 12 }
            }
        }.getOrNull()
    }

    private fun resolveViaDot(
        query: ByteArray,
        config: UpstreamDnsConfig,
        tcpProtector: ((Socket) -> Unit)?
    ): ByteArray? {
        val host = config.dotHostname.ifBlank { return null }
        return runCatching {
            val rawSocket = Socket()
            tcpProtector?.invoke(rawSocket)
            rawSocket.connect(InetSocketAddress(config.ipv4Primary, 853), 3500)
            rawSocket.soTimeout = 3500
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, host, 853, true) as SSLSocket
            sslSocket.use { sock ->
                sock.startHandshake()
                val verified = HttpsURLConnection.getDefaultHostnameVerifier().verify(host, sock.session)
                if (!verified) {
                    throw SecurityException("DoT TLS hostname verification failed for $host")
                }
                val dos = DataOutputStream(sock.outputStream)
                dos.writeShort(query.size)
                dos.write(query)
                dos.flush()

                val dis = DataInputStream(sock.inputStream)
                val respLen = dis.readUnsignedShort()
                if (respLen < 12 || respLen > 4096) return@use null
                val resp = ByteArray(respLen)
                dis.readFully(resp)
                resp
            }
        }.getOrNull()
    }

    private fun resolveViaUdp(
        query: ByteArray,
        serverIp: String,
        udpProtector: ((DatagramSocket) -> Unit)?
    ): ByteArray? {
        return runCatching {
            DatagramSocket().use { socket ->
                udpProtector?.invoke(socket)
                socket.soTimeout = 3000
                val address = InetAddress.getByName(serverIp)
                val outPacket = DatagramPacket(query, query.size, address, 53)
                socket.send(outPacket)

                val recvBuf = ByteArray(4096)
                val inPacket = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(inPacket)
                recvBuf.copyOf(inPacket.length).takeIf { it.size >= 12 }
            }
        }.getOrNull()
    }

    suspend fun probeDns(config: UpstreamDnsConfig): DnsProbeResult = withContext(Dispatchers.IO) {
        val preset = DnsProviderPresets.byId(config.providerId)
        val endpoint = when (config.protocol) {
            DnsProtocol.DOH -> config.dohUrl
            DnsProtocol.DOT -> "${config.dotHostname}:853 (${config.ipv4Primary})"
            DnsProtocol.UDP -> "${config.ipv4Primary}:53"
        }
        val testQuery = DnsPacketCodec.buildDnsQuery("example.com")
        val started = System.currentTimeMillis()
        try {
            val response = when (config.protocol) {
                DnsProtocol.DOH -> resolveViaDoh(testQuery, config, useVpnClient = vpnProtectedClient != null)
                DnsProtocol.DOT -> resolveViaDot(testQuery, config, null)
                DnsProtocol.UDP -> resolveViaUdp(testQuery, config.ipv4Primary, null)
            }
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            if (response != null) {
                val resolvedIp = DnsPacketCodec.extractFirstIpv4Answer(response)
                DnsProbeResult(
                    providerName = preset.name,
                    protocol = config.protocol,
                    endpoint = endpoint,
                    success = true,
                    latencyMs = elapsed,
                    resolvedIp = resolvedIp,
                    errorMessage = null,
                    testedAt = System.currentTimeMillis()
                )
            } else {
                DnsProbeResult(
                    providerName = preset.name,
                    protocol = config.protocol,
                    endpoint = endpoint,
                    success = false,
                    latencyMs = elapsed,
                    resolvedIp = null,
                    errorMessage = "Timed out or failed to receive a valid DNS response from $endpoint.",
                    testedAt = System.currentTimeMillis()
                )
            }
        } catch (t: Throwable) {
            DnsProbeResult(
                providerName = preset.name,
                protocol = config.protocol,
                endpoint = endpoint,
                success = false,
                latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L),
                resolvedIp = null,
                errorMessage = t.message ?: "DNS probe failed.",
                testedAt = System.currentTimeMillis()
            )
        }
    }

    companion object {
        private val DOH_MEDIA_TYPE = "application/dns-message".toMediaType()
    }
}
