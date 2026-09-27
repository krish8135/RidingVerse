package com.ridingverse.app.presentation.navigation

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Tracks live progress along an active route. Works for both drive modes:
 * - Simulated ride: advance with [positionAt] using the distance the virtual
 *   bike has travelled.
 * - Real GPS: project each fix onto the route with [project] to get the
 *   distance-along, then call [progress].
 */
class RouteNavigator(
    val points: List<Pair<Double, Double>>,
    val maneuvers: List<Maneuver>,
    val totalDistanceMeters: Double,
    val destinationName: String
) {
    private val cumulative = DoubleArray(points.size).also { cum ->
        for (i in 1 until points.size) {
            cum[i] = cum[i - 1] + haversine(points[i - 1], points[i])
        }
    }

    /** Interpolated (lat, lng) at [distanceMeters] along the route. */
    fun positionAt(distanceMeters: Double): Pair<Double, Double> {
        val d = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        var i = 1
        while (i < cumulative.size && cumulative[i] < d) i++
        if (i >= cumulative.size) return points.last()
        val segLen = cumulative[i] - cumulative[i - 1]
        if (segLen <= 0) return points[i]
        val t = (d - cumulative[i - 1]) / segLen
        val (lat1, lng1) = points[i - 1]
        val (lat2, lng2) = points[i]
        return (lat1 + (lat2 - lat1) * t) to (lng1 + (lng2 - lng1) * t)
    }

    /**
     * Projects a GPS fix onto the route and returns the distance-along in
     * meters. Nearest-vertex projection is plenty accurate at 2 s fix rates.
     */
    fun project(lat: Double, lng: Double): Double {
        var best = 0.0
        var bestDist = Double.MAX_VALUE
        for (i in points.indices) {
            val d = haversine(points[i], lat to lng)
            if (d < bestDist) {
                bestDist = d
                best = cumulative[i]
            }
        }
        return best
    }

    /** Builds the [RouteProgress] snapshot rendered by [TurnByTurnOverlay]. */
    fun progress(distanceAlongMeters: Double, speedKmh: Float): RouteProgress {
        val along = distanceAlongMeters.coerceIn(0.0, totalDistanceMeters)
        val next = maneuvers.firstOrNull { it.distanceMeters > along + 1.0 }
            ?: maneuvers.lastOrNull()
            ?: Maneuver(ManeuverIcon.ARRIVE, "You have arrived", totalDistanceMeters)
        val remaining = (totalDistanceMeters - along).coerceAtLeast(0.0)
        val etaSeconds =
            if (speedKmh > 3f) (remaining / 1000.0 / speedKmh * 3600.0).toLong() else 0L
        val etaText = if (etaSeconds > 0) {
            LocalTime.now().plusSeconds(etaSeconds)
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            "--:--"
        }
        return RouteProgress(
            nextManeuver = next,
            distanceToTurnMeters = (next.distanceMeters - along).coerceAtLeast(0.0),
            destinationName = destinationName,
            remainingMeters = remaining,
            etaText = etaText
        )
    }
}
