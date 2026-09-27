package com.ridingverse.app.presentation.navigation

/** Maneuver types surfaced by the routing engine for the guidance overlay. */
enum class ManeuverIcon {
    STRAIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    LEFT,
    RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT,
    ARRIVE
}

/** A single upcoming maneuver on the active route. */
data class Maneuver(
    val icon: ManeuverIcon,
    /** Human-readable instruction, e.g. "Turn left onto Skyline Blvd". */
    val instruction: String,
    /** Distance at which this maneuver occurs, measured from route start. */
    val distanceMeters: Double
)

/** Live progress snapshot rendered by [TurnByTurnOverlay]. */
data class RouteProgress(
    val nextManeuver: Maneuver,
    val distanceToTurnMeters: Double,
    val destinationName: String,
    val remainingMeters: Double,
    /** Pre-formatted ETA, e.g. "14:32". */
    val etaText: String
) {
    val remainingKm: Double get() = remainingMeters / 1000.0
}
