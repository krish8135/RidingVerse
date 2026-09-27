package com.ridingverse.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridingverse.app.presentation.cockpit.CockpitScreen
import com.ridingverse.app.presentation.cockpit.CockpitViewModel
import com.ridingverse.app.presentation.convoy.ConvoyScreen
import com.ridingverse.app.presentation.map.WorldMapScreen
import com.ridingverse.app.presentation.permissions.PermissionsScreen
import com.ridingverse.app.presentation.settings.SettingsScreen
import com.ridingverse.app.presentation.theme.Emerald
import com.ridingverse.app.presentation.theme.RidingVerseTheme
import com.ridingverse.app.presentation.theme.VoidBlack
import com.ridingverse.app.service.RideForegroundService
import com.ridingverse.app.service.SosCountdownDialog
import com.ridingverse.app.service.SosManager
import kotlinx.coroutines.launch

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    BottomTab("cockpit", "Cockpit", Icons.Filled.Dashboard),
    BottomTab("worldmap", "Map", Icons.Filled.Map),
    BottomTab("convoy", "Convoy", Icons.Filled.Group),
    BottomTab("settings", "Settings", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {

    private val app get() = application as RidingVerseApp

    /** Activity-scoped: the world map hands the picked route to the cockpit. */
    private val cockpitViewModel: CockpitViewModel by viewModels {
        CockpitViewModel.Factory(app.settings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forward real GPS fixes from the foreground service into the cockpit.
        lifecycleScope.launch {
            RideForegroundService.locationUpdates.collect { loc ->
                cockpitViewModel.onGpsLocation(loc.latitude, loc.longitude, loc.speed)
            }
        }

        setContent {
            RidingVerseTheme {
                val onboarded by app.settings.permissionsOnboarded
                    .collectAsState(initial = false)
                if (!onboarded) {
                    PermissionsScreen(
                        onComplete = {
                            lifecycleScope.launch {
                                app.settings.setPermissionsOnboarded(true)
                            }
                        }
                    )
                } else {
                    MainScaffold(cockpitViewModel = cockpitViewModel)
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(cockpitViewModel: CockpitViewModel) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as RidingVerseApp }
    val sosManager = remember { SosManager(context, app.settings) }
    val sosState by sosManager.state.collectAsState()

    val navController = rememberNavController()

    // The SOS dialog floats above every tab while a countdown is active.
    SosCountdownDialog(
        state = sosState,
        onAbort = { sosManager.abort() }
    )

    Scaffold(
        containerColor = VoidBlack,
        bottomBar = {
            NavigationBar(containerColor = VoidBlack) {
                val backStack by navController.currentBackStackEntryAsState()
                val current = backStack?.destination
                TABS.forEach { tab ->
                    val selected =
                        current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald,
                            selectedTextColor = Emerald,
                            indicatorColor = VoidBlack
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "cockpit",
            modifier = Modifier.padding(padding)
        ) {
            composable("cockpit") {
                CockpitScreen(
                    onSosClick = {
                        val s = cockpitViewModel.uiState.value
                        sosManager.startCountdown(lat = s.lat, lng = s.lng)
                    },
                    viewModel = cockpitViewModel
                )
            }
            composable("worldmap") {
                WorldMapScreen(
                    onRouteSelected = { selection ->
                        cockpitViewModel.startRideWithRoute(selection)
                        context.startRideService()
                        navController.navigate("cockpit") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("convoy") { ConvoyScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

/** Starts the ride foreground service (GPS + crash detection). */
private fun Context.startRideService() {
    val intent = Intent(this, RideForegroundService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        @Suppress("DEPRECATION")
        startService(intent)
    }
}
