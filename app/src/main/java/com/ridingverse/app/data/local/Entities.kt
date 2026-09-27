package com.ridingverse.app.data.local

/** A saved / planned route, available offline. */
data class RouteEntity(
    val id: String,
    val name: String,
    val createdAt: Long,
    val distanceMeters: Double,
    val profile: String,
    /** Elevation samples as JSON doubles. */
    val elevationProfileJson: String? = null
)

/** Ordered GPX waypoint belonging to a route. */
data class GpxWaypointEntity(
    val uid: Long = 0,
    val routeId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val elevationM: Double? = null
)

/** A GPS breadcrumb recorded during a ride. */
data class TrackPointEntity(
    val uid: Long = 0,
    val rideId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float,
    val bearingDeg: Float
)

/** Cached convoy roster row, refreshed from live telemetry. */
data class RiderEntity(
    val riderId: String,
    val displayName: String,
    /** Stored as [com.ridingverse.app.domain.model.RiderRole] name. */
    val role: String,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastSeen: Long = 0L,
    /** ARGB pin color chosen by the rider. */
    val colorArgb: Int = 0xFF00E676.toInt()
)
