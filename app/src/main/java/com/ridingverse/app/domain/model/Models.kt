package com.ridingverse.app.domain.model

/** Convoy role hierarchy: LEAD paces, SWEEP tails, MEDIC responds, PACK rides. */
enum class RiderRole { LEAD, SWEEP, MEDIC, PACK }

/** A convoy member with their latest known position. */
data class Rider(
    val riderId: String,
    val displayName: String,
    val role: RiderRole,
    val lat: Double?,
    val lng: Double?,
    val lastSeenMillis: Long,
    /** ARGB pin color chosen by the rider in Settings. */
    val colorArgb: Int = 0xFF00E676.toInt()
)
