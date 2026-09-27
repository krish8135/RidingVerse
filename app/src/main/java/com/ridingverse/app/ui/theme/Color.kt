package com.ridingverse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridingverse.app.R
import com.ridingverse.app.ui.screens.DashboardScreen
import com.ridingverse.app.ui.screens.MapScreen
import com.ridingverse.app.ui.screens.SquadronScreen
import com.ridingverse.app.ui.screens.SafetyScreen

@Composable
fun RidingVerseApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        "Cockpit" to R.drawable.ic_dashboard,
        "Map" to R.drawable.ic_map,
        "Squadron" to R.drawable.ic_group,
        "Safety" to R.drawable.ic_safety
    )

    Scaffold(
        containerColor = Color(0xFF0A0C0E),
        contentColor = Color.White,
        bottomBar = {
            BottomAppBar(
                containerColor = Color(0xFF111821),
                contentColor = Color.White,
                tonalElevation = 12.dp,
                modifier = Modifier.height(72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, (label, iconRes) ->
                        val selected = index == selectedTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = index },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) Color(0xFF0ACF83) else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = label,
                                        tint = if (selected) Color.Black else Color(0xFF9DB1BF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = if (selected) Color.White else Color(0xFF9DB1BF),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color(0xFF9DB1BF),
                                unselectedTextColor = Color(0xFF9DB1BF),
                                indicatorColor = Color(0xFF0A0C0E)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen()
                1 -> MapScreen()
                2 -> SquadronScreen()
                else -> SafetyScreen()
            }
        }
    }
}
