package com.ridingverse.app.presentation.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Interactive world map: the full Leaflet experience (search panel, route
 * profiles, start-ride handoff) with live convoy rider pins pushed from Kotlin.
 */
@Composable
fun WorldMapScreen(
    ridersJson: String = "[]",
    onRouteSelected: (RouteSelection) -> Unit
) {
    var controller by remember { mutableStateOf<MapController?>(null) }

    RidingMapView(
        modifier = Modifier.fillMaxSize(),
        panelVisible = true,
        onReady = { controller = it },
        onRouteSelected = onRouteSelected
    )

    LaunchedEffect(ridersJson) {
        controller?.updateRiders(ridersJson)
    }
}
