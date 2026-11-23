package com.example.core.network

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

data class ParsedDnsPacket(
    val ipVersion: Int, // 4 or 6
    val sourceAddress: ByteArray,
    val destAddress: ByteArray,
    val sourcePort: Int,
    val destPort: Int,
    val dnsPayload: ByteArray,
    val transactionId: Int,
    val queryDomain: String,
    val queryType: Int, // 1=A, 28=AAAA, 65=HTTPS, etc.
    val queryClass: Int
)

/**
 * Pure Kotlin IPv4 / IPv6 + UDP + RFC 1035 DNS packet parser and response builder.
 * Handles only DNS UDP packets (typically port 53) routed to the local VPN virtual DNS server.
 */
object DnsPacketCodec {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val CLASS_IN = 1

    fun parseIpUdpDns(packet: ByteArray, length: Int): ParsedDnsPacket? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4UdpDns(packet, length)
            6 -> parseIpv6UdpDns(packet, length)
            else -> null
        }
    }

    private fun parseIpv4UdpDns(packet: ByteArray, length: Int): ParsedDnsPacket? {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        return parseUdpAndDns(4, srcIp, dstIp, packet, ihl, length)
    }

    private fun parseIpv6UdpDns(packet: ByteArray, length: Int): ParsedDnsPacket? {
        if (length < 48) return null
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != 17) return null // UDP only
        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)
        return parseUdpAndDns(6, srcIp, dstIp, packet, 40, length)
    }

    private fun parseUdpAndDns(
        ipVersion: Int,
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        packetLength: Int
    ): ParsedDnsPacket? {
        if (packetLength < udpOffset + 8) return null
        val srcPort = readU16(packet, udpOffset)
        val dstPort = readU16(packet, udpOffset + 2)
        val udpLength = readU16(packet, udpOffset + 4)
        val dnsOffset = udpOffset + 8
        val dnsLength = (udpLength - 8).coerceAtMost(packetLength - dnsOffset)
        if (dnsLength < 12) return null

        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val question = parseDnsQuestion(dnsPayload) ?: return null

        return ParsedDnsPacket(
            ipVersion = ipVersion,
            sourceAddress = srcIp,
            destAddress = dstIp,
            sourcePort = srcPort,
            destPort = dstPort,
            dnsPayload = dnsPayload,
            transactionId = readU16(dnsPayload, 0),
            queryDomain = question.first,
            queryType = question.second,
            queryClass = question.third
        )
    }

    /**
     * Parses first question section of a DNS message -> (normalized domain, qType, qClass).
     */
    fun parseDnsQuestion(dns: ByteArray): Triple<String, Int, Int>? {
        if (dns.size < 12) return null
        val qdCount = readU16(dns, 4)
        if (qdCount < 1) return null

        var pos = 12
        val labels = mutableListOf<String>()
        var safety = 0
        while (pos < dns.size && safety < 128) {
            safety++
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) != 0) {
                // Compression pointer not expected in first question; bail safely
                return null
            }
            pos++
            if (pos + len > dns.size) return null
            labels.add(String(dns, pos, len, Charsets.US_ASCII))
            pos += len
        }
        if (labels.isEmpty() || pos + 4 > dns.size) return null
        val qType = readU16(dns, pos)
        val qClass = readU16(dns, pos + 2)
        val domain = labels.joinToString(".").lowercase().trimEnd('.')
        return Triple(domain, qType, qClass)
    }

    /**
     * Builds a minimal DNS query payload for connectivity/latency probing.
     */
    fun buildDnsQuery(domain: String, transactionId: Int = 0x4A2F, qType: Int = TYPE_A): ByteArray {
        val out = ByteArrayOutputStream()
        writeU16(out, transactionId)
        writeU16(out, 0x0100) // Standard query, RD=1
        writeU16(out, 1) // QDCOUNT
        writeU16(out, 0) // ANCOUNT
        writeU16(out, 0) // NSCOUNT
        writeU16(out, 0) // ARCOUNT
        domain.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }
        out.write(0)
        writeU16(out, qType)
        writeU16(out, CLASS_IN)
        return out.toByteArray()
    }

    /**
     * Builds a DNS blocked response:
     * - For A queries: 0.0.0.0 with short TTL (60s)
     * - For AAAA queries: :: with short TTL (60s)
     * - For other record types (e.g. HTTPS/SVCB): NXDOMAIN (RCODE=3)
     */
    fun buildBlockedDnsResponse(requestDns: ByteArray, queryType: Int): ByteArray {
        val questionEnd = findQuestionEnd(requestDns) ?: return buildRcodeDnsResponse(requestDns, rcode = 3)
        if (queryType != TYPE_A && queryType != TYPE_AAAA) {
            return buildRcodeDnsResponse(requestDns, rcode = 3)
        }

        val out = ByteArrayOutputStream()
        // Transaction ID
        out.write(requestDns[0].toInt() and 0xFF)
        out.write(requestDns[1].toInt() and 0xFF)
        // Flags: QR=1, RD copied, RA=1, RCODE=0 (NOERROR)
        val rd = requestDns[2].toInt() and 0x01
        out.write(0x80 or rd)
        out.write(0x80)
        writeU16(out, 1) // QDCOUNT
        writeU16(out, 1) // ANCOUNT
        writeU16(out, 0) // NSCOUNT
        writeU16(out, 0) // ARCOUNT
        // Copy question section verbatim
        out.write(requestDns, 12, questionEnd - 12)
        // Answer section: pointer to offset 12 (0xC00C)
        writeU16(out, 0xC00C)
        writeU16(out, queryType)
        writeU16(out, CLASS_IN)
        writeU32(out, 60) // TTL = 60s
        if (queryType == TYPE_A) {
            writeU16(out, 4)
            out.write(byteArrayOf(0, 0, 0, 0))
        } else {
            writeU16(out, 16)
            out.write(ByteArray(16))
        }
        return out.toByteArray()
    }

    fun buildRcodeDnsResponse(requestDns: ByteArray, rcode: Int): ByteArray {
        val questionEnd = findQuestionEnd(requestDns) ?: requestDns.size.coerceAtLeast(12)
        val out = ByteArrayOutputStream()
        out.write(requestDns[0].toInt() and 0xFF)
        out.write(requestDns[1].toInt() and 0xFF)
        val rd = requestDns[2].toInt() and 0x01
        out.write(0x80 or rd)
        out.write(0x80 or (rcode and 0x0F))
        writeU16(out, if (questionEnd > 12) 1 else 0)
        writeU16(out, 0)
        writeU16(out, 0)
        writeU16(out, 0)
        if (questionEnd > 12) {
            out.write(requestDns, 12, questionEnd - 12)
        }
        return out.toByteArray()
    }

    /**
     * Wraps a DNS response payload into a valid IPv4 or IPv6 UDP response packet
     * with source/destination addresses and ports swapped.
     */
    fun buildIpUdpResponse(request: ParsedDnsPacket, dnsResponse: ByteArray): ByteArray {
        // Ensure transaction ID matches original query
        val payload = dnsResponse.copyOf()
        if (payload.size >= 2) {
            payload[0] = ((request.transactionId ushr 8) and 0xFF).toByte()
            payload[1] = (request.transactionId and 0xFF).toByte()
        }
        return if (request.ipVersion == 4) {
            buildIpv4UdpPacket(
                srcIp = request.destAddress,
                dstIp = request.sourceAddress,
                srcPort = request.destPort,
                dstPort = request.sourcePort,
                payload = payload
            )
        } else {
            buildIpv6UdpPacket(
                srcIp = request.destAddress,
                dstIp = request.sourceAddress,
                srcPort = request.destPort,
                dstPort = request.sourcePort,
                payload = payload
            )
        }
    }

    private fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val buffer = ByteBuffer.allocate(totalLen)

        // IPv4 Header
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // DSCP/ECN
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0.toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Don't fragment
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // UDP
        buffer.putShort(0.toShort()) // Checksum placeholder
        buffer.put(srcIp, 0, 4)
        buffer.put(dstIp, 0, 4)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort()) // UDP checksum placeholder
        buffer.put(payload)

        val raw = buffer.array()
        val ipChecksum = computeChecksum(raw, 0, 20)
        raw[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        raw[11] = (ipChecksum and 0xFF).toByte()

        val udpChecksum = computeIpv4UdpChecksum(srcIp, dstIp, raw, 20, udpLen)
        raw[26] = ((udpChecksum ushr 8) and 0xFF).toByte()
        raw[27] = (udpChecksum and 0xFF).toByte()
        return raw
    }

    private fun buildIpv6UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 40 + udpLen
        val buffer = ByteBuffer.allocate(totalLen)

        // IPv6 Header
        buffer.putInt(0x60000000) // Version 6
        buffer.putShort(udpLen.toShort())
        buffer.put(17.toByte()) // Next Header: UDP
        buffer.put(64.toByte()) // Hop Limit
        buffer.put(srcIp, 0, 16)
        buffer.put(dstIp, 0, 16)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort())
        buffer.put(payload)

        val raw = buffer.array()
        val udpChecksum = computeIpv6UdpChecksum(srcIp, dstIp, raw, 40, udpLen)
        raw[46] = ((udpChecksum ushr 8) and 0xFF).toByte()
        raw[47] = (udpChecksum and 0xFF).toByte()
        return raw
    }

    /**
     * Extracts first IPv4 A record IP from a DNS response for probe display.
     */
    fun extractFirstIpv4Answer(dnsResponse: ByteArray): String? {
        if (dnsResponse.size < 12) return null
        val anCount = readU16(dnsResponse, 6)
        if (anCount < 1) return null
        var pos = findQuestionEnd(dnsResponse) ?: return null
        repeat(anCount) {
            if (pos >= dnsResponse.size) return null
            // Skip NAME (either pointer or labels)
            if ((dnsResponse[pos].toInt() and 0xC0) == 0xC0) {
                pos += 2
            } else {
                while (pos < dnsResponse.size) {
                    val len = dnsResponse[pos].toInt() and 0xFF
                    pos++
                    if (len == 0) break
                    pos += len
                }
            }
            if (pos + 10 > dnsResponse.size) return null
            val type = readU16(dnsResponse, pos)
            val rdLength = readU16(dnsResponse, pos + 8)
            pos += 10
            if (pos + rdLength > dnsResponse.size) return null
            if (type == TYPE_A && rdLength == 4) {
                val ipBytes = dnsResponse.copyOfRange(pos, pos + 4)
                return runCatching { InetAddress.getByAddress(ipBytes).hostAddress }.getOrNull()
            }
            pos += rdLength
        }
        return null
    }

    private fun findQuestionEnd(dns: ByteArray): Int? {
        if (dns.size < 12) return null
        var pos = 12
        var safety = 0
        while (pos < dns.size && safety < 128) {
            safety++
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) != 0) return null
            pos += 1 + len
        }
        return if (pos + 4 <= dns.size) pos + 4 else null
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun computeIpv4UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLen: Int
    ): Int {
        var sum = 0L
        sum += readU16(srcIp, 0) + readU16(srcIp, 2)
        sum += readU16(dstIp, 0) + readU16(dstIp, 2)
        sum += 17L // UDP protocol
        sum += udpLen.toLong()
        sum += sumWords(packet, udpOffset, udpLen)
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val result = (sum.inv() and 0xFFFF).toInt()
        return if (result == 0) 0xFFFF else result
    }

    private fun computeIpv6UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLen: Int
    ): Int {
        var sum = 0L
        for (i in 0 until 16 step 2) {
            sum += readU16(srcIp, i)
            sum += readU16(dstIp, i)
        }
        sum += udpLen.toLong()
        sum += 17L // UDP next header
        sum += sumWords(packet, udpOffset, udpLen)
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val result = (sum.inv() and 0xFFFF).toInt()
        return if (result == 0) 0xFFFF else result
    }

    private fun sumWords(data: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        return sum
    }

    private fun readU16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
