package com.ridingverse.app.presentation.map

import android.webkit.JavascriptInterface
import com.ridingverse.app.presentation.navigation.Maneuver
import com.ridingverse.app.presentation.navigation.OsrmManeuverParser
import org.json.JSONObject

/** Route profile chosen on the world map. */
enum class RouteProfile { FASTEST, TWISTIES, SCENIC }

/** Route payload delivered from the Leaflet WebView when the rider taps "Start Ride". */
data class RouteSelection(
    val fromLat: Double,
    val fromLng: Double,
    val toLat: Double,
    val toLng: Double,
    /** Short destination label, e.g. "Stelvio Pass". */
    val toName: String = "Destination",
    val profile: RouteProfile,
    /** Encoded polyline as [lat, lng] pairs from the JS side. */
    val polyline: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** OSRM turn-by-turn steps, parsed into [Maneuver]s (may be empty). */
    val maneuvers: List<Maneuver> = emptyList()
)

/**
 * JS → Kotlin bridge injected into the Leaflet WebView as `window.Android`.
 * All entry points run on a WebView background thread — keep them cheap and
 * hand parsed results back through the provided callbacks.
 */
class MapBridge(
    private val onMapReady: () -> Unit = {},
    private val onRouteSelected: (RouteSelection) -> Unit = {}
) {
    @JavascriptInterface
    fun onMapReady() {
        onMapReady()
    }

    @JavascriptInterface
    fun onRouteSelected(json: String) {
        runCatching {
            val o = JSONObject(json)
            val from = o.getJSONObject("from")
            val to = o.getJSONObject("to")
            val coords = o.getJSONArray("polyline")
            val polyline = buildList {
                for (i in 0 until coords.length()) {
                    val p = coords.getJSONArray(i)
                    add(p.getDouble(0) to p.getDouble(1))
                }
            }
            RouteSelection(
                fromLat = from.getDouble("lat"),
                fromLng = from.getDouble("lng"),
                toLat = to.getDouble("lat"),
                toLng = to.getDouble("lng"),
                toName = o.optString("toName", "Destination").ifBlank { "Destination" },
                profile = RouteProfile.valueOf(o.optString("profile", "FASTEST")),
                polyline = polyline,
                distanceMeters = o.optDouble("distanceMeters", 0.0),
                durationSeconds = o.optDouble("durationSeconds", 0.0),
                maneuvers = o.optJSONArray("steps")?.let { OsrmManeuverParser.parse(it) }
                    ?: emptyList()
            )
        }.onSuccess(onRouteSelected)
    }
}
