package com.ridingverse.app.presentation.navigation

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle distance between two (lat, lng) pairs, in meters. */
fun haversine(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dLat = Math.toRadians(b.first - a.first)
    val dLng = Math.toRadians(b.second - a.second)
    val la1 = Math.toRadians(a.first)
    val la2 = Math.toRadians(b.first)
    val h = sin(dLat / 2).let { it * it } +
        cos(la1) * cos(la2) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Initial bearing from [a] to [b], in degrees 0–360 (0 = north). */
fun bearing(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dLng = Math.toRadians(b.second - a.second)
    val la1 = Math.toRadians(a.first)
    val la2 = Math.toRadians(b.first)
    val y = sin(dLng) * cos(la2)
    val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/** Total length of a polyline in meters. */
fun totalDistance(points: List<Pair<Double, Double>>): Double {
    var d = 0.0
    for (i in 1 until points.size) d += haversine(points[i - 1], points[i])
    return d
}
