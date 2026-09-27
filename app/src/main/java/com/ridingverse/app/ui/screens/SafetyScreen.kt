package com.ridingverse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridingverse.app.ui.theme.RidingVerseAccent
import com.ridingverse.app.ui.theme.RidingVerseBackground
import com.ridingverse.app.ui.theme.RidingVerseCard
import com.ridingverse.app.ui.theme.RidingVerseMuted
import com.ridingverse.app.ui.theme.RidingVerseText

@Composable
fun SquadronScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RidingVerseBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Squadron Room", color = RidingVerseText, fontSize = 24.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RidingVerseCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Room Code", color = RidingVerseMuted)
                Text("RV-9042", color = RidingVerseAccent, fontSize = 30.sp)
                Text("Lead: Alex • Sweep: Priya • Medic: Sam", color = RidingVerseText)
            }
        }

        val riders = listOf(
            "Alex • LEAD • 12.4 mi",
            "Priya • SWEEP • 11.1 mi",
            "Sam • MEDIC • 9.8 mi",
            "Marco • PACK • 10.3 mi"
        )

        riders.forEach { rider ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RidingVerseCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(rider, color = RidingVerseText)
                    Text("Online", color = RidingVerseAccent)
                }
            }
        }
    }
}
