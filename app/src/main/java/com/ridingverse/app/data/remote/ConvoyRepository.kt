package com.ridingverse.app.data.remote

import com.ridingverse.app.data.local.RiderDao
import com.ridingverse.app.data.local.RiderEntity
import com.ridingverse.app.data.settings.AppSettings
import com.ridingverse.app.domain.model.Rider
import com.ridingverse.app.domain.model.RiderRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/**
 * Squadron convoy room: room-code networking ("RV-9042"), role hierarchy
 * (LEAD / SWEEP / MEDIC / PACK) and an in-memory rider registry fed by the
 * [TelemetrySocket]. Rider identity (name/color) and the relay URL come from
 * Settings (DataStore); roster snapshots are cached in Room for offline use.
 */
class ConvoyRepository(
    private val socket: TelemetrySocket,
    private val riderDao: RiderDao,
    private val settings: AppSettings
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _riders = MutableStateFlow<Map<String, Rider>>(emptyMap())
    /** Live registry keyed by riderId. */
    val riders: StateFlow<Map<String, Rider>> = _riders.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var selfId: String? = null
    private var selfColor: Int = 0xFF00E676.toInt()
    private var collectJob: Job? = null

    init {
        // Keep the socket pointed at the configured relay and mirror its state.
        scope.launch {
            settings.relayUrl.collect { url ->
                if (socket.serverUrl != url) {
                    val wasInRoom = _roomCode.value != null
                    socket.disconnect()
                    socket.serverUrl = url
                    if (wasInRoom) socket.connect()
                } else {
                    socket.serverUrl = url
                }
            }
        }
        scope.launch {
            socket.connected.collect { _connected.value = it }
        }
    }

    /** Generates a shareable room code, e.g. "RV-9042". */
    fun generateRoomCode(): String = "RV-" + Random.nextInt(1000, 10000)

    /** Joins a convoy room using the rider identity stored in Settings. */
    fun joinRoom(code: String, role: RiderRole) {
        val normalized = code.trim().uppercase()
        require(ROOM_CODE_REGEX.matches(normalized)) { "Room code must look like RV-9042" }
        scope.launch {
            val name = settings.riderName.first()
            selfColor = settings.riderColor.first()
            val id = selfId ?: UUID.randomUUID().toString().also { selfId = it }
            _roomCode.value = normalized
            upsertLocal(id, name, role, selfColor, null, null)
            socket.connect()
            socket.send(
                TelemetryPacket(
                    type = "join",
                    riderId = id,
                    room = normalized,
                    name = name,
                    color = selfColor
                )
            )
            collectJob?.cancel()
            collectJob = scope.launch {
                socket.packets.collect { handlePacket(it) }
            }
        }
    }

    fun assignRole(riderId: String, role: RiderRole) {
        _riders.update { current ->
            current[riderId]?.let { rider ->
                current + (riderId to rider.copy(role = role))
            } ?: current
        }
        scope.launch {
            _riders.value[riderId]?.let { riderDao.upsert(it.toEntity()) }
        }
    }

    /** Broadcast our current position to the convoy. No-op when not in a room. */
    fun broadcastLocation(lat: Double, lng: Double, speedMps: Float, bearingDeg: Float) {
        val id = selfId ?: return
        if (_roomCode.value == null) return
        socket.send(
            TelemetryPacket(
                type = "location",
                riderId = id,
                lat = lat,
                lng = lng,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                color = selfColor
            )
        )
    }

    /** Broadcast an SOS so the convoy sees the distressed rider's pin. */
    fun broadcastSos(lat: Double?, lng: Double?) {
        val id = selfId ?: return
        if (_roomCode.value == null) return
        socket.send(
            TelemetryPacket(
                type = "sos",
                riderId = id,
                lat = lat,
                lng = lng,
                color = selfColor
            )
        )
    }

    fun leaveRoom() {
        selfId?.let { socket.send(TelemetryPacket(type = "leave", riderId = it)) }
        socket.disconnect()
        collectJob?.cancel()
        _roomCode.value = null
        _riders.value = emptyMap()
    }

    private fun handlePacket(packet: TelemetryPacket) {
        when (packet.type) {
            "location", "telemetry" -> {
                val existing = _riders.value[packet.riderId]
                val rider = Rider(
                    riderId = packet.riderId,
                    displayName = packet.name
                        ?: existing?.displayName
                        ?: packet.riderId.take(6),
                    role = existing?.role ?: RiderRole.PACK,
                    lat = packet.lat,
                    lng = packet.lng,
                    lastSeenMillis = packet.timestamp,
                    colorArgb = packet.color ?: existing?.colorArgb
                        ?: 0xFF00E676.toInt()
                )
                _riders.update { it + (packet.riderId to rider) }
                scope.launch { riderDao.upsert(rider.toEntity()) }
            }
            "join" -> {
                val existing = _riders.value[packet.riderId]
                if (existing == null) {
                    val rider = Rider(
                        riderId = packet.riderId,
                        displayName = packet.name ?: packet.riderId.take(6),
                        role = RiderRole.PACK,
                        lat = packet.lat,
                        lng = packet.lng,
                        lastSeenMillis = packet.timestamp,
                        colorArgb = packet.color ?: 0xFF00E676.toInt()
                    )
                    _riders.update { it + (packet.riderId to rider) }
                    scope.launch { riderDao.upsert(rider.toEntity()) }
                }
            }
            "leave" -> {
                _riders.update { it - packet.riderId }
            }
            // "heartbeat" and "roster" need no registry change here.
        }
    }

    private fun upsertLocal(
        riderId: String,
        displayName: String,
        role: RiderRole,
        colorArgb: Int,
        lat: Double?,
        lng: Double?
    ) {
        val rider = Rider(
            riderId, displayName, role, lat, lng,
            System.currentTimeMillis(), colorArgb
        )
        _riders.update { it + (riderId to rider) }
        scope.launch { riderDao.upsert(rider.toEntity()) }
    }

    private fun Rider.toEntity() = RiderEntity(
        riderId = riderId,
        displayName = displayName,
        role = role.name,
        lastLat = lat,
        lastLng = lng,
        lastSeen = lastSeenMillis,
        colorArgb = colorArgb
    )

    companion object {
        private val ROOM_CODE_REGEX = Regex("^RV-\\d{4}$")
    }
}
