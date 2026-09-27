package com.ridingverse.app.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min
import kotlin.math.pow

/** Packet types exchanged with the convoy telemetry relay. */
@Serializable
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
)

/**
 * Low-latency bi-directional convoy channel over OkHttp WebSocket.
 * Reconnects with exponential backoff (1s → 30s cap) after drops.
 */
class TelemetrySocket(
    private val client: OkHttpClient = OkHttpClient()
) {
    /**
     * Relay URL, e.g. "wss://relay.ridingverse.example.com". Set from
     * Settings (DataStore) — [ConvoyRepository] keeps it in sync.
     */
    var serverUrl: String = "wss://relay.ridingverse.example.com"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _packets = MutableSharedFlow<TelemetryPacket>(extraBufferCapacity = 64)
    val packets: SharedFlow<TelemetryPacket> = _packets.asSharedFlow()

    private val _connected = MutableSharedFlow<Boolean>(replay = 1)
    val connected: SharedFlow<Boolean> = _connected.asSharedFlow()

    private var socket: WebSocket? = null
    private var reconnectAttempts = 0
    private var closedByUs = false

    fun connect() {
        closedByUs = false
        val request = Request.Builder().url(serverUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                scope.launch { _connected.emit(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    json.decodeFromString<TelemetryPacket>(text)
                }.onSuccess { packet ->
                    scope.launch { _packets.emit(packet) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { _connected.emit(false) }
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { _connected.emit(false) }
                if (!closedByUs) scheduleReconnect()
            }
        })
    }

    fun send(packet: TelemetryPacket): Boolean {
        val payload = runCatching { json.encodeToString(packet) }.getOrNull() ?: return false
        return socket?.send(payload) == true
    }

    fun disconnect() {
        closedByUs = true
        socket?.close(1000, "client disconnect")
        socket = null
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
}
