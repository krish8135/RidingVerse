package com.ridingverse.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved / planned route, available offline. */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val distanceMeters: Double,
    val profile: String,
    /** Elevation samples as JSON doubles, via [Converters]. */
    val elevationProfileJson: String? = null
)

/** Ordered GPX waypoint belonging to a route. */
@Entity(
    tableName = "gpx_waypoints",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routeId")]
)
data class GpxWaypointEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val routeId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val elevationM: Double? = null
)

/** A GPS breadcrumb recorded during a ride. */
@Entity(
    tableName = "track_points",
    indices = [Index("rideId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val rideId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float,
    val bearingDeg: Float
)

/** Cached convoy roster row, refreshed from live telemetry. */
@Entity(tableName = "riders")
data class RiderEntity(
    @PrimaryKey val riderId: String,
    val displayName: String,
    /** Stored as [com.ridingverse.app.domain.model.RiderRole] name. */
    val role: String,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastSeen: Long = 0L,
    /** ARGB pin color chosen by the rider. */
    val colorArgb: Int = 0xFF00E676.toInt()
)
