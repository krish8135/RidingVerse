package com.ridingverse.app.presentation.cockpit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridingverse.app.presentation.map.MapController
import com.ridingverse.app.presentation.map.RidingMapView
import com.ridingverse.app.presentation.navigation.TurnByTurnOverlay
import com.ridingverse.app.presentation.theme.Amber
import com.ridingverse.app.presentation.theme.Cyan
import com.ridingverse.app.presentation.theme.DimWhite
import com.ridingverse.app.presentation.theme.Emerald
import com.ridingverse.app.presentation.theme.OffWhite
import com.ridingverse.app.presentation.theme.PanelBlack
import com.ridingverse.app.presentation.theme.SignalRed
import com.ridingverse.app.presentation.theme.SteelGrey
import com.ridingverse.app.presentation.theme.VoidBlack

private const val TACHO_SEGMENTS = 24
private const val REDLINE_RPM = 11_500
private const val SHIFT_RPM = 10_500

/**
 * Digital superbike TFT instrument cluster: speedometer, gear box,
 * 24-segment tachometer ribbon, ride modes, telemetry strip, live cockpit
 * mini-map with turn-by-turn guidance, and the manual SOS button.
 *
 * The [viewModel] is activity-scoped (shared with the world map) so a route
 * picked on the map starts navigation here.
 */
@Composable
fun CockpitScreen(
    onSosClick: () -> Unit,
    viewModel: CockpitViewModel
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TachometerRibbon(rpm = state.rpm)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GearIndicatorBox(gear = state.gear, rpm = state.rpm)
            Speedometer(speedKmh = state.speedKmh)
            RideModeIndicator(mode = state.rideMode)
        }

        RideModeSelector(
            selected = state.rideMode,
            onSelect = viewModel::setRideMode
        )

        TelemetryStrip(state = state)

        val progress = state.routeProgress
        if (state.rideState == RideState.NAVIGATING && progress != null) {
            TurnByTurnOverlay(progress = progress)
        }

        CockpitMiniMap(viewModel = viewModel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = viewModel::toggleRide,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRiding) Amber else Emerald,
                    contentColor = VoidBlack
                )
            ) {
                Text(
                    if (state.isRiding) "STOP RIDE" else "START RIDE",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Button(
                onClick = onSosClick,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalRed,
                    contentColor = OffWhite
                )
            ) {
                Text("SOS", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 24-segment horizontal ribbon; zones green → cyan → amber → red, flashing past redline. */
@Composable
private fun TachometerRibbon(rpm: Int) {
    val lit = ((rpm / 12_500f) * TACHO_SEGMENTS).toInt().coerceIn(0, TACHO_SEGMENTS)
    val redline = rpm >= REDLINE_RPM
    val flash = if (redline) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(180), RepeatMode.Reverse)
        ).value
    } else 1f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(TACHO_SEGMENTS) { i ->
                val color = when {
                    i < 12 -> Emerald
                    i < 17 -> Cyan
                    i < 21 -> Amber
                    else -> SignalRed
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .alpha(if (redline && i >= 21) flash else 1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < lit) color else PanelBlack)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RPM",
                style = MaterialTheme.typography.labelSmall,
                color = SteelGrey
            )
            Text(
                "%,d".format(rpm),
                style = MaterialTheme.typography.headlineSmall,
                color = if (redline) SignalRed else OffWhite
            )
        }
    }
}

/** Glowing gear box: emerald "N" at standstill, 1–6 while riding, ▲ SHIFT near redline. */
@Composable
private fun GearIndicatorBox(gear: Int, rpm: Int) {
    val label = if (gear == 0) "N" else gear.toString()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .border(2.dp, Emerald, RoundedCornerShape(12.dp))
                .background(VoidBlack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.displayMedium,
                color = Emerald
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (rpm >= SHIFT_RPM && gear in 1..5) "▲ SHIFT" else "GEAR",
            style = MaterialTheme.typography.labelSmall,
            color = if (rpm >= SHIFT_RPM && gear in 1..5) Amber else SteelGrey
        )
    }
}

@Composable
private fun Speedometer(speedKmh: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "%03d".format(speedKmh.toInt()),
            style = MaterialTheme.typography.displayLarge,
            color = OffWhite,
            textAlign = TextAlign.Center
        )
        Text("km/h", style = MaterialTheme.typography.labelSmall, color = SteelGrey)
    }
}

@Composable
private fun RideModeIndicator(mode: RideMode) {
    val color = when (mode) {
        RideMode.SPORT -> SignalRed
        RideMode.TRACK -> Cyan
        RideMode.TOURING -> Emerald
        RideMode.RAIN -> Amber
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PanelBlack)
                .border(1.dp, color, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(mode.name, style = MaterialTheme.typography.titleMedium, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Text("MODE", style = MaterialTheme.typography.labelSmall, color = SteelGrey)
    }
}

/** Glove-friendly throttle-profile selector. */
@Composable
private fun RideModeSelector(selected: RideMode, onSelect: (RideMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RideMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Emerald else PanelBlack)
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) VoidBlack else SteelGrey
                )
            }
        }
    }
}

/** Live instrument strip: trip, speeds, elevation, bearing + cardinal heading. */
@Composable
private fun TelemetryStrip(state: CockpitUiState) {
    val items = listOf(
        "TRIP A" to "%.1f km".format(state.tripAKm),
        "AVG SPD" to "%d km/h".format(state.avgSpeedKmh.toInt()),
        "MAX SPD" to "%d km/h".format(state.maxSpeedKmh.toInt()),
        "ELEV" to "%d m".format(state.elevationM),
        "BEARING" to "%03d° %s".format(state.bearingDeg, state.cardinalHeading)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelBlack)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = OffWhite)
                Spacer(Modifier.height(2.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = SteelGrey)
            }
        }
    }
}

/**
 * Live cockpit mini-map: the active route polyline plus the bike's position,
 * rendered by the shared Leaflet WebView with its search panel hidden.
 */
@Composable
private fun CockpitMiniMap(viewModel: CockpitViewModel) {
    var controller by remember { mutableStateOf<MapController?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DimWhite, RoundedCornerShape(12.dp))
    ) {
        RidingMapView(
            modifier = Modifier.fillMaxSize(),
            panelVisible = false,
            onReady = { c ->
                controller = c
                viewModel.attachMapController(c)
            }
        )
        if (controller == null) {
            Text(
                "Loading map…",
                style = MaterialTheme.typography.labelSmall,
                color = SteelGrey,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.attachMapController(null) }
    }
}
