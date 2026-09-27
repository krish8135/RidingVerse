package com.ridingverse.app.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.security.MessageDigest
import javax.net.ssl.SSLSocketFactory
import kotlin.math.min
import kotlin.math.pow

/** Packet types exchanged with the convoy telemetry relay. */
data class TelemetryPacket(
    /**
     * One of: location | telemetry | heartbeat | join | leave | roster |
     * ptt | sos | error
     */
    val type: String,
    val riderId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    /** Rider display name (join/location packets). */
    val name: String? = null,
    /** Rider pin color as ARGB int (join/location packets). */
    val color: Int? = null,
    /** Convoy room code, e.g. "RV-9042" (join packets). */
    val room: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("riderId", riderId)
        if (lat != null) put("lat", lat)
        if (lng != null) put("lng", lng)
        if (speedMps != null) put("speedMps", speedMps.toDouble())
        if (bearingDeg != null) put("bearingDeg", bearingDeg.toDouble())
        if (name != null) put("name", name)
        if (color != null) put("color", color)
        if (room != null) put("room", room)
        put("timestamp", timestamp)
    }.toString()

    companion object {
        fun fromJson(raw: String): TelemetryPacket? = runCatching {
            val o = JSONObject(raw)
            TelemetryPacket(
                type = o.getString("type"),
                riderId = o.getString("riderId"),
                lat = if (o.isNull("lat")) null else o.getDouble("lat"),
                lng = if (o.isNull("lng")) null else o.getDouble("lng"),
                speedMps = if (o.isNull("speedMps")) null else o.getDouble("speedMps").toFloat(),
                bearingDeg = if (o.isNull("bearingDeg")) null else o.getDouble("bearingDeg").toFloat(),
                name = o.optString("name").takeIf { it.isNotEmpty() },
                color = if (o.isNull("color")) null else o.getInt("color"),
                room = o.optString("room").takeIf { it.isNotEmpty() },
                timestamp = o.optLong("timestamp", System.currentTimeMillis())
            )
        }.getOrNull()
    }
}

/**
 * Low-latency bi-directional convoy channel over a raw RFC 6455 WebSocket.
 * Reconnects with exponential backoff (1s → 30s cap) after drops.
 *
 * Implemented on java.net sockets (no third-party HTTP client) so the app
 * has zero native networking dependencies.
 */
class TelemetrySocket {

    /**
     * Relay URL, e.g. "wss://relay.ridingverse.example.com". Set from
     * Settings (DataStore) — [ConvoyRepository] keeps it in sync.
     */
    var serverUrl: String = "wss://relay.ridingverse.example.com"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _packets = MutableSharedFlow<TelemetryPacket>(extraBufferCapacity = 64)
    val packets: SharedFlow<TelemetryPacket> = _packets.asSharedFlow()

    private val _connected = MutableSharedFlow<Boolean>(replay = 1)
    val connected: SharedFlow<Boolean> = _connected.asSharedFlow()

    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private var connectionJob: Job? = null
    private var reconnectAttempts = 0
    private var closedByUs = false
    private val sendLock = Any()

    fun connect() {
        if (connectionJob?.isActive == true) return
        closedByUs = false
        connectionJob = scope.launch { runConnection() }
    }

    fun send(packet: TelemetryPacket): Boolean {
        val payload = runCatching { packet.toJson() }.getOrNull() ?: return false
        return runCatching { sendTextFrame(payload); true }.getOrDefault(false)
    }

    fun disconnect() {
        closedByUs = true
        connectionJob?.cancel()
        connectionJob = null
        runCatching { sendCloseFrame(1000, "client disconnect") }
        runCatching { socket?.close() }
        socket = null
        out = null
    }

    // ------------------------------------------------------------------
    // Connection lifecycle
    // ------------------------------------------------------------------

    private suspend fun runConnection() {
        try {
            val target = parseUrl(serverUrl)
            val raw: Socket = if (target.ssl) {
                SSLSocketFactory.getDefault().createSocket(target.host, target.port)
            } else {
                Socket(target.host, target.port)
            }
            raw.tcpNoDelay = true
            socket = raw
            val input = DataInputStream(raw.getInputStream())
            out = DataOutputStream(raw.getOutputStream())
            doHandshake(input, target)
            reconnectAttempts = 0
            _connected.emit(true)
            readLoop(input)
        } catch (e: Exception) {
            Log.w(TAG, "telemetry connection failed: ${e.message}")
        } finally {
            runCatching { socket?.close() }
            socket = null
            out = null
            _connected.emit(false)
            if (!closedByUs) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (closedByUs) return
        val attempt = reconnectAttempts++
        val backoffMs = min(30_000L, (1000L * 2.0.pow(attempt.coerceAtMost(10))).toLong())
        scope.launch {
            delay(backoffMs)
            if (!closedByUs) connect()
        }
    }

    // ------------------------------------------------------------------
    // RFC 6455 handshake
    // ------------------------------------------------------------------

    private data class Target(
        val host: String,
        val port: Int,
        val path: String,
        val ssl: Boolean
    )

    private fun parseUrl(url: String): Target {
        val ssl = url.startsWith("wss://")
        require(ssl || url.startsWith("ws://")) { "Only ws:// and wss:// URLs are supported" }
        val withoutScheme = url.substringAfter("://")
        val hostPort = withoutScheme.substringBefore("/")
        val pathPart = withoutScheme.substringAfter("/", "")
        val path = "/$pathPart".takeIf { it != "/" } ?: "/"
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "").toIntOrNull()
            ?: if (ssl) 443 else 80
        require(host.isNotBlank()) { "Missing host in $url" }
        return Target(host, port, path, ssl)
    }

    private fun doHandshake(input: DataInputStream, target: Target) {
        val keyBytes = ByteArray(16).also { java.util.Random().nextBytes(it) }
        val key = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
        val request = buildString {
            append("GET ${target.path} HTTP/1.1\r\n")
            append("Host: ${target.host}:${target.port}\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $key\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        val output = out ?: throw IllegalStateException("not connected")
        output.writeBytes(request)
        output.flush()

        val statusLine = readHttpLine(input)
            ?: throw IllegalStateException("Empty handshake response")
        require(statusLine.contains("101")) { "WebSocket upgrade failed: $statusLine" }

        val expectedAccept = sha1Base64(key + WEBSOCKET_GUID)
        var accept: String? = null
        while (true) {
            val line = readHttpLine(input) ?: break
            if (line.isBlank()) break
            if (line.startsWith("Sec-WebSocket-Accept:", ignoreCase = true)) {
                accept = line.substringAfter(":").trim()
            }
        }
        require(accept == expectedAccept) { "Bad Sec-WebSocket-Accept" }
    }

    private fun readHttpLine(input: DataInputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = try {
                input.read()
            } catch (e: Exception) {
                return null
            }
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code || prev != '\r'.code) sb.append(b.toChar())
            prev = b
        }
        return sb.toString()
    }

    private fun sha1Base64(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }

    // ------------------------------------------------------------------
    // RFC 6455 framing
    // ------------------------------------------------------------------

    private suspend fun readLoop(input: DataInputStream) {
        val buffer = ByteArrayOutputStream2()
        while (true) {
            val b0 = input.read()
            if (b0 == -1) break
            val opcode = b0 and 0x0F
            val b1 = input.read()
            if (b1 == -1) break
            val masked = (b1 and 0x80) != 0
            var length = (b1 and 0x7F).toLong()
            if (length == 126L) {
                length = input.readUnsignedShort().toLong()
            } else if (length == 127L) {
                length = input.readLong()
                require(length >= 0) { "Oversize frame" }
            }
            val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
            buffer.reset(length)
            var remaining = length
            val chunk = ByteArray(8192)
            while (remaining > 0) {
                val n = input.read(chunk, 0, min(chunk.size.toLong(), remaining).toInt())
                if (n == -1) throw IllegalStateException("Truncated frame")
                buffer.write(chunk, 0, n)
                remaining -= n
            }
            val payload = buffer.toByteArray()
            if (mask != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }
            when (opcode) {
                OPCODE_TEXT -> {
                    val text = payload.toString(Charsets.UTF_8)
                    TelemetryPacket.fromJson(text)?.let { packet ->
                        _packets.emit(packet)
                    }
                }
                OPCODE_PING -> sendPong(payload)
                OPCODE_CLOSE -> break
                OPCODE_PONG, OPCODE_CONTINUATION, OPCODE_BINARY -> Unit // ignore
                else -> Unit
            }
        }
    }

    private fun sendTextFrame(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        synchronized(sendLock) {
            val output = out ?: throw IllegalStateException("not connected")
            // FIN + text opcode, masked (client MUST mask).
            output.writeByte(0x80 or OPCODE_TEXT)
            writeLength(output, payload.size.toLong(), masked = true)
            val mask = ByteArray(4).also { java.util.Random().nextBytes(it) }
            output.write(mask)
            for (i in payload.indices) {
                output.writeByte((payload[i].toInt() xor mask[i % 4].toInt()) and 0xFF)
            }
            output.flush()
        }
    }

    private fun sendPong(payload: ByteArray) {
        runCatching {
            synchronized(sendLock) {
                val output = out ?: return
                output.writeByte(0x80 or OPCODE_PONG)
                writeLength(output, payload.size.toLong(), masked = true)
                val mask = ByteArray(4).also { java.util.Random().nextBytes(it) }
                output.write(mask)
                for (i in payload.indices) {
                    output.writeByte((payload[i].toInt() xor mask[i % 4].toInt()) and 0xFF)
                }
                output.flush()
            }
        }
    }

    private fun sendCloseFrame(code: Int, reason: String) {
        runCatching {
            synchronized(sendLock) {
                val output = out ?: return
                val reasonBytes = reason.toByteArray(Charsets.UTF_8)
                output.writeByte(0x80 or OPCODE_CLOSE)
                writeLength(output, (2 + reasonBytes.size).toLong(), masked = true)
                val mask = ByteArray(4).also { java.util.Random().nextBytes(it) }
                output.write(mask)
                val body = ByteArray(2 + reasonBytes.size)
                body[0] = ((code shr 8) and 0xFF).toByte()
                body[1] = (code and 0xFF).toByte()
                reasonBytes.copyInto(body, 2)
                for (i in body.indices) {
                    output.writeByte((body[i].toInt() xor mask[i % 4].toInt()) and 0xFF)
                }
                output.flush()
            }
        }
    }

    private fun writeLength(output: DataOutputStream, length: Long, masked: Boolean) {
        val maskBit = if (masked) 0x80 else 0
        when {
            length < 126 -> output.writeByte(maskBit or length.toInt())
            length < 65536 -> {
                output.writeByte(maskBit or 126)
                output.writeShort(length.toInt())
            }
            else -> {
                output.writeByte(maskBit or 127)
                output.writeLong(length)
            }
        }
    }

    /** Growable byte buffer that avoids reallocation churn on big frames. */
    private class ByteArrayOutputStream2 {
        private var buf = ByteArray(8192)
        private var count = 0

        fun reset(capacity: Long) {
            val needed = capacity.coerceAtMost(MAX_FRAME_BYTES).toInt()
            if (buf.size < needed) buf = ByteArray(needed)
            count = 0
        }

        fun write(b: ByteArray, off: Int, len: Int) {
            System.arraycopy(b, off, buf, count, len)
            count += len
        }

        fun toByteArray(): ByteArray = buf.copyOf(count)
    }

    companion object {
        private const val TAG = "TelemetrySocket"
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024L
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA
    }
}
