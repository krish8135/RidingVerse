package com.ridingverse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridingverse.app.R
import com.ridingverse.app.ui.theme.RidingVerseAccent
import com.ridingverse.app.ui.theme.RidingVerseAmber
import com.ridingverse.app.ui.theme.RidingVerseBackground
import com.ridingverse.app.ui.theme.RidingVerseCard
import com.ridingverse.app.ui.theme.RidingVerseCyan
import com.ridingverse.app.ui.theme.RidingVerseMuted
import com.ridingverse.app.ui.theme.RidingVerseRed
import com.ridingverse.app.ui.theme.RidingVerseText

@Composable
fun DashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RidingVerseBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeaderBar()
        RideStatusCard()
        TachometerPanel()
        TelemetryGrid()
    }
}

@Composable
private fun HeaderBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "RidingVerse",
                color = RidingVerseText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Group Telemetry • Tail of the Dragon",
                color = RidingVerseMuted,
                fontSize = 12.sp
            )
        }

        Button(
            onClick = { },
            colors = ButtonDefaults.buttonColors(
                containerColor = RidingVerseAccent,
                contentColor = RidingVerseBackground
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(text = "Live")
        }
    }
}

@Composable
private fun RideStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RidingVerseCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Current Mode", color = RidingVerseMuted, fontSize = 12.sp)
                Text(text = "TRACK", color = RidingVerseText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "65", color = RidingVerseCyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(text = " mph", color = RidingVerseMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TachometerPanel() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RidingVerseCard),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "RPM", color = RidingVerseMuted, fontSize = 12.sp)
                Text(text = "7,600", color = RidingVerseText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B2430))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val segments = listOf(
                        Color(0xFF2CCF6E), Color(0xFF2CCF6E), Color(0xFF2CCF6E),
                        Color(0xFF39D0F0), Color(0xFF39D0F0), Color(0xFF39D0F0),
                        Color(0xFFFFB020), Color(0xFFFFB020), Color(0xFFFFB020),
                        Color(0xFFFF4D5A), Color(0xFFFF4D5A), Color(0xFFFF4D5A),
                        Color(0xFFFF4D5A)
                    )

                    segments.forEach { segment ->
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(segment)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "GEAR", color = RidingVerseMuted, fontSize = 12.sp)
                    Text(text = "3", color = RidingVerseAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "SHIFT", color = RidingVerseAmber, fontSize = 12.sp)
                    Text(text = "▲", color = RidingVerseAmber, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TelemetryGrid() {
    val items = listOf(
        "TRIP A" to "142.8 mi",
        "AVG SPD" to "58.3 mph",
        "MAX SPD" to "96.2 mph",
        "ELEV" to "4,210 ft",
        "BEARING" to "NW",
        "ETA" to "02:14"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (label, value) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = RidingVerseCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = label, color = RidingVerseMuted, fontSize = 11.sp)
                            Text(text = value, color = RidingVerseText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
