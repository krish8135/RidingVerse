package com.ridingverse.app.presentation.navigation

import org.json.JSONArray
import kotlin.math.abs

/**
 * Converts OSRM route steps (`route/v1/driving/...?steps=true`) into the
 * app's [Maneuver] model. Each OSRM step carries:
 * `maneuver: { type, modifier?, location }, name, distance, duration`.
 */
object OsrmManeuverParser {

    fun parse(steps: JSONArray): List<Maneuver> {
        val out = mutableListOf<Maneuver>()
        var cumulative = 0.0
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            val maneuver = step.optJSONObject("maneuver")
            val type = maneuver?.optString("type").orEmpty()
            val modifier = maneuver?.optString("modifier").orEmpty()
            val name = step.optString("name").takeIf { it.isNotBlank() } ?: "the road"

            val icon = when {
                type == "arrive" -> ManeuverIcon.ARRIVE
                type == "roundabout" || type == "rotary" -> ManeuverIcon.ROUNDABOUT
                else -> modifierToIcon(modifier)
            }
            out.add(
                Maneuver(
                    icon = icon,
                    instruction = instructionFor(type, modifier, name),
                    distanceMeters = cumulative
                )
            )
            cumulative += step.optDouble("distance", 0.0)
        }
        return out
    }

    private fun modifierToIcon(modifier: String): ManeuverIcon = when (modifier) {
        "uturn" -> ManeuverIcon.UTURN
        "sharp right" -> ManeuverIcon.SHARP_RIGHT
        "right" -> ManeuverIcon.RIGHT
        "slight right" -> ManeuverIcon.SLIGHT_RIGHT
        "sharp left" -> ManeuverIcon.SHARP_LEFT
        "left" -> ManeuverIcon.LEFT
        "slight left" -> ManeuverIcon.SLIGHT_LEFT
        else -> ManeuverIcon.STRAIGHT
    }

    private fun instructionFor(type: String, modifier: String, name: String): String {
        if (type == "arrive") return "You have arrived"
        if (type == "depart") return "Head out onto $name"
        if (type == "roundabout" || type == "rotary") {
            return "At the roundabout, take the exit onto $name"
        }
        val turn = when (modifier) {
            "uturn" -> "Make a U-turn"
            "sharp right" -> "Turn sharp right"
            "right" -> "Turn right"
            "slight right" -> "Keep right"
            "sharp left" -> "Turn sharp left"
            "left" -> "Turn left"
            "slight left" -> "Keep left"
            else -> "Continue straight"
        }
        return "$turn onto $name"
    }
}

/**
 * Fallback when OSRM steps are unavailable: derive maneuvers from raw
 * polyline geometry. Emits a maneuver wherever the bearing changes by 30°+
 * between consecutive segments, plus a final ARRIVE.
 */
fun deriveManeuversFromGeometry(points: List<Pair<Double, Double>>): List<Maneuver> {
    if (points.size < 3) {
        return listOf(
            Maneuver(ManeuverIcon.ARRIVE, "You have arrived", totalDistance(points))
        )
    }
    val cumulative = DoubleArray(points.size)
    for (i in 1 until points.size) {
        cumulative[i] = cumulative[i - 1] + haversine(points[i - 1], points[i])
    }
    val out = mutableListOf<Maneuver>()
    var lastBearing = bearing(points[0], points[1])
    var i = 2
    while (i < points.size) {
        val b = bearing(points[i - 1], points[i])
        // Signed turn angle: positive = bearing increased = right turn.
        val diff = ((b - lastBearing + 540.0) % 360.0) - 180.0
        if (abs(diff) >= 30.0) {
            val icon = when {
                abs(diff) >= 150 -> ManeuverIcon.UTURN
                diff > 0 && abs(diff) >= 100 -> ManeuverIcon.SHARP_RIGHT
                diff > 0 && abs(diff) >= 60 -> ManeuverIcon.RIGHT
                diff > 0 -> ManeuverIcon.SLIGHT_RIGHT
                abs(diff) >= 100 -> ManeuverIcon.SHARP_LEFT
                abs(diff) >= 60 -> ManeuverIcon.LEFT
                else -> ManeuverIcon.SLIGHT_LEFT
            }
            out.add(
                Maneuver(
                    icon = icon,
                    instruction = if (diff > 0) "Turn right" else "Turn left",
                    distanceMeters = cumulative[i - 1]
                )
            )
            lastBearing = b
            i += 2 // skip ahead so one curve doesn't fire repeatedly
        }
        i++
    }
    out.add(Maneuver(ManeuverIcon.ARRIVE, "You have arrived", cumulative.last()))
    return out
}
