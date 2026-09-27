package com.ridingverse.app.presentation.map

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Handle on a [RidingMapView]'s Leaflet map. All calls are posted to the
 * WebView's thread, so they are safe to invoke from a ViewModel.
 */
class MapController internal constructor(private val webView: WebView) {

    private fun js(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    /** Shows/hides the From/To search panel baked into map.html. */
    fun setPanelVisible(visible: Boolean) {
        js("window.setPanelVisible && window.setPanelVisible($visible)")
    }

    /** Renders live convoy pins from a JSON array of {riderId,name,lat,lng,bearing}. */
    fun updateRiders(ridersJson: String) {
        js("window.updateRiders && window.updateRiders(${JSONObject.quote(ridersJson)})")
    }

    /** Draws the active route; [polylineJson] is a JSON array of [lat,lng]. */
    fun showRoute(polylineJson: String) {
        js("window.showRoute && window.showRoute(${JSONObject.quote(polylineJson)})")
    }

    fun clearRoute() {
        js("window.clearRoute && window.clearRoute()")
    }

    /** Pans to a position and moves the "me" marker. */
    fun centerOn(lat: Double, lng: Double, zoom: Int = 15) {
        js("window.centerOn && window.centerOn($lat,$lng,$zoom)")
    }
}

/**
 * Reusable Leaflet map (assets/leaflet/map.html). The world-map screen uses it
 * with [panelVisible] = true; the cockpit embeds it as a live mini-map with
 * the panel hidden.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RidingMapView(
    modifier: Modifier = Modifier,
    panelVisible: Boolean = false,
    onReady: (MapController) -> Unit = {},
    onRouteSelected: (RouteSelection) -> Unit = {}
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MapController?>(null) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            val c = MapController(this)
            controller = c
            addJavascriptInterface(
                MapBridge(
                    onMapReady = {
                        post {
                            c.setPanelVisible(panelVisible)
                            onReady(c)
                        }
                    },
                    onRouteSelected = { selection ->
                        post { onRouteSelected(selection) }
                    }
                ),
                "Android"
            )
            loadUrl("file:///android_asset/leaflet/map.html")
        }
    }

    LaunchedEffect(panelVisible) {
        controller?.setPanelVisible(panelVisible)
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}
