package com.ridingverse.app.presentation.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ridingverse.app.data.settings.AppSettings
import com.ridingverse.app.presentation.map.MapController
import com.ridingverse.app.presentation.map.RouteSelection
import com.ridingverse.app.presentation.navigation.RouteNavigator
import com.ridingverse.app.presentation.navigation.RouteProgress
import com.ridingverse.app.presentation.navigation.deriveManeuversFromGeometry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Throttle / engine-braking profiles, mirrored on the TFT cluster. */
enum class RideMode { SPORT, TRACK, TOURING, RAIN }

/** Lifecycle of a ride inside the cockpit. */
enum class RideState { IDLE, PLANNING, NAVIGATING, FINISHED }

/** Everything the cockpit HUD renders. Gear 0 = neutral. */
data class CockpitUiState(
    val speedKmh: Float = 0f,
    val rpm: Int = 0,
    val gear: Int = 0,
    val rideMode: RideMode = RideMode.SPORT,
    val tripAKm: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val elevationM: Int = 0,
    val bearingDeg: Int = 0,
    val isRiding: Boolean = false,
    val rideState: RideState = RideState.IDLE,
    /** Live turn-by-turn snapshot while NAVIGATING. */
    val routeProgress: RouteProgress? = null,
    /** When true, position/progress come from real GPS fixes, not the sim. */
    val useRealGps: Boolean = false,
    /** JSON [[lat,lng],...] of the active route, pushed to the mini-map. */
    val routePolylineJson: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
) {
    /** 16-point compass label for the bearing readout. */
    val cardinalHeading: String
        get() = CARDINALS[((bearingDeg % 360 + 360) % 360 / 22.5f).toInt() % 16]

    companion object {
        private val CARDINALS = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
    }
}

/**
 * Feeds the cockpit. Two position sources:
 * - Simulated ride (default): a canyon-carve speed profile drives the virtual
 *   bike along the active route polyline, producing live turn-by-turn.
 * - Real GPS: call [onGpsLocation] from the foreground service; the fix is
 *   projected onto the route and progress is computed the same way.
 */
class CockpitViewModel(private val settings: AppSettings) : ViewModel() {

    private val _uiState = MutableStateFlow(CockpitUiState())
    val uiState: StateFlow<CockpitUiState> = _uiState.asStateFlow()

    private var simTimeSec = 0.0
    private var simDistanceM = 0.0
    private var navigator: RouteNavigator? = null
    private var mapController: MapController? = null

    init {
        viewModelScope.launch {
            while (true) {
                simulatedTick()
                delay(TICK_MS)
            }
        }
    }

    fun setRideMode(mode: RideMode) {
        _uiState.update { it.copy(rideMode = mode) }
    }

    fun setUseRealGps(enabled: Boolean) {
        _uiState.update { it.copy(useRealGps = enabled) }
    }

    /** The cockpit mini-map hands us its controller when the WebView is ready. */
    fun attachMapController(controller: MapController?) {
        mapController = controller
        controller?.let { c ->
            _uiState.value.routePolylineJson?.let { c.showRoute(it) }
        }
    }

    /**
     * Starts navigation on the route picked in the world map. OSRM steps are
     * preferred for maneuvers; geometry-derived turns are the fallback.
     */
    fun startRideWithRoute(selection: RouteSelection) {
        if (selection.polyline.size < 2) return
        val maneuvers = selection.maneuvers.ifEmpty {
            deriveManeuversFromGeometry(selection.polyline)
        }
        navigator = RouteNavigator(
            points = selection.polyline,
            maneuvers = maneuvers,
            totalDistanceMeters = selection.distanceMeters,
            destinationName = selection.toName
        )
        simDistanceM = 0.0
        simTimeSec = 0.0
        val polyJson = selection.polyline.joinToString(",", "[", "]") {
            "[${it.first},${it.second}]"
        }
        _uiState.update {
            it.copy(
                rideState = RideState.NAVIGATING,
                routeProgress = null,
                routePolylineJson = polyJson,
                isRiding = true,
                tripAKm = 0f,
                maxSpeedKmh = 0f
            )
        }
        mapController?.showRoute(polyJson)
    }

    /**
     * Real-GPS entry point — call from [com.ridingverse.app.service.RideForegroundService]
     * on each location fix. Updates speed and, while navigating with
     * [CockpitUiState.useRealGps], projects the fix onto the route.
     */
    fun onGpsLocation(lat: Double, lng: Double, speedMps: Float) {
        val speedKmh = speedMps * 3.6f
        _uiState.update { it.copy(lat = lat, lng = lng, speedKmh = speedKmh) }
        val s = _uiState.value
        if (s.rideState == RideState.NAVIGATING && s.useRealGps) {
            navigator?.let { nav ->
                val along = nav.project(lat, lng)
                val (plat, plng) = nav.positionAt(along)
                _uiState.update { it.copy(routeProgress = nav.progress(along, speedKmh)) }
                mapController?.centerOn(plat, plng)
                if (along >= nav.totalDistanceMeters - 5.0) finishRide()
            }
        }
    }

    fun toggleRide() {
        val riding = _uiState.value.isRiding
        if (riding && _uiState.value.rideState == RideState.NAVIGATING) {
            finishRide()
        } else {
            _uiState.update { it.copy(isRiding = !riding) }
            if (!riding) simTimeSec = 0.0
        }
    }

    fun stopRide() {
        if (_uiState.value.rideState == RideState.NAVIGATING) finishRide()
        else _uiState.update { it.copy(isRiding = false) }
    }

    private fun finishRide() {
        navigator = null
        _uiState.update {
            it.copy(rideState = RideState.FINISHED, isRiding = false, routeProgress = null)
        }
        mapController?.clearRoute()
    }

    private fun simulatedTick() {
        val s = _uiState.value
        if (!s.isRiding) {
            // Parked: everything decays to zero, gear drops to neutral.
            _uiState.update {
                it.copy(
                    speedKmh = 0f,
                    rpm = (it.rpm * 0.8f).toInt(),
                    gear = 0,
                    avgSpeedKmh = 0f
                )
            }
            return
        }
        simTimeSec += TICK_MS / 1000.0

        // Canyon-carve speed profile: 40–150 km/h with a couple of harmonics.
        val modeCap = when (s.rideMode) {
            RideMode.SPORT -> 1.0f
            RideMode.TRACK -> 1.15f
            RideMode.TOURING -> 0.85f
            RideMode.RAIN -> 0.6f
        }
        val speed = (95f + 55f * sin(simTimeSec * 0.25).toFloat() * modeCap
            + 18f * sin(simTimeSec * 0.9).toFloat()).coerceIn(0f, 165f)

        // Gear from speed bands; rpm climbs inside each gear.
        val gear = when {
            speed < 8f -> 0
            speed < 45f -> 1
            speed < 70f -> 2
            speed < 95f -> 3
            speed < 120f -> 4
            speed < 145f -> 5
            else -> 6
        }
        val gearFloor = floatArrayOf(0f, 8f, 45f, 70f, 95f, 120f, 145f)[gear]
        val gearCeil = floatArrayOf(8f, 45f, 70f, 95f, 120f, 145f, 170f)[gear]
        val inGear = ((speed - gearFloor) / (gearCeil - gearFloor)).coerceIn(0f, 1f)
        val rpm = if (gear == 0) 1400 else (3200 + inGear * 8800).toInt()

        val dtHours = TICK_MS / 3_600_000f
        val trip = s.tripAKm + speed * dtHours
        val avg = if (trip > 0.001f) (s.avgSpeedKmh * s.tripAKm + speed * speed * dtHours) / trip else 0f

        _uiState.update {
            it.copy(
                speedKmh = speed,
                rpm = rpm,
                gear = gear,
                tripAKm = trip,
                avgSpeedKmh = avg,
                maxSpeedKmh = maxOf(it.maxSpeedKmh, speed),
                elevationM = (620 + 90 * sin(simTimeSec * 0.05)).toInt(),
                bearingDeg = ((simTimeSec * 6) % 360).toInt()
            )
        }

        // Advance the virtual bike along the route in simulated mode.
        val nav = navigator
        if (s.rideState == RideState.NAVIGATING && !s.useRealGps && nav != null) {
            simDistanceM += (speed / 3.6f) * (TICK_MS / 1000f)
            val (plat, plng) = nav.positionAt(simDistanceM)
            _uiState.update {
                it.copy(
                    lat = plat,
                    lng = plng,
                    routeProgress = nav.progress(simDistanceM, speed)
                )
            }
            mapController?.centerOn(plat, plng)
            if (simDistanceM >= nav.totalDistanceMeters) finishRide()
        }
    }

    class Factory(private val settings: AppSettings) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CockpitViewModel(settings) as T
    }

    companion object {
        private const val TICK_MS = 120L
    }
}
