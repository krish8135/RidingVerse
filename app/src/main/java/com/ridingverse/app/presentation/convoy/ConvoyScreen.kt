package com.ridingverse.app.presentation.convoy

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ridingverse.app.RidingVerseApp
import com.ridingverse.app.domain.model.Rider
import com.ridingverse.app.domain.model.RiderRole
import com.ridingverse.app.intercom.PttManager
import com.ridingverse.app.presentation.theme.Cyan
import com.ridingverse.app.presentation.theme.Emerald
import com.ridingverse.app.presentation.theme.OffWhite
import com.ridingverse.app.presentation.theme.PanelBlack
import com.ridingverse.app.presentation.theme.SignalRed
import com.ridingverse.app.presentation.theme.SteelGrey
import com.ridingverse.app.presentation.theme.VoidBlack

/**
 * Squadron convoy room: join with a code (RV-####), see live riders with
 * roles and pin colors, and press-and-hold the PTT button for the
 * half-duplex intercom.
 */
@Composable
fun ConvoyScreen() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as RidingVerseApp }
    val repo = remember { app.convoyRepository }

    val riders by repo.riders.collectAsState()
    val roomCode by repo.roomCode.collectAsState()
    val connected by repo.connected.collectAsState()

    val ptt = remember { PttManager(context) }
    DisposableEffect(Unit) { onDispose { ptt.release() } }
    val transmitting by ptt.isTransmitting.collectAsState()
    val suppression by ptt.noiseSuppressionActive.collectAsState()

    var codeDraft by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Connection status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (connected) Emerald else SignalRed)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    roomCode == null -> "Not in a convoy"
                    connected -> "Connected · $roomCode"
                    else -> "Connecting · $roomCode…"
                },
                color = OffWhite,
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (roomCode == null) {
            OutlinedTextField(
                value = codeDraft,
                onValueChange = {
                    codeDraft = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }
                    joinError = null
                },
                label = { Text("Room code (RV-9042)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("RV-…") }
            )
            joinError?.let {
                Text(it, color = SignalRed, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runCatching { repo.joinRoom(codeDraft, RiderRole.PACK) }
                            .onFailure { joinError = "Use a code like RV-9042" }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald, contentColor = VoidBlack
                    )
                ) { Text("Join convoy") }
                Button(
                    onClick = {
                        val code = repo.generateRoomCode()
                        repo.joinRoom(code, RiderRole.LEAD)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan, contentColor = VoidBlack
                    )
                ) { Text("Create room") }
            }
            Text(
                "Share the room code with your squadron. Everyone must point " +
                    "at the same relay (Settings → Relay server).",
                color = SteelGrey,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // Rider roster
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(riders.values.toList(), key = { it.riderId }) { rider ->
                    RiderRow(rider = rider)
                }
            }

            // PTT press-and-hold
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (transmitting) SignalRed else PanelBlack)
                    .pointerInput(roomCode) {
                        detectTapGestures(
                            onPress = {
                                // TODO: wire onFrame -> TelemetrySocket to send voice
                                // over the relay once deployed (see PttManager protocol).
                                ptt.startTransmit(room = roomCode ?: "", riderId = "self")
                                tryAwaitRelease()
                                ptt.stopTransmit()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (transmitting) OffWhite else Emerald,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (transmitting) "TRANSMITTING…" else "HOLD TO TALK",
                            color = if (transmitting) OffWhite else Emerald,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (suppression && transmitting) {
                            Text(
                                "Wind-noise suppression active",
                                color = OffWhite.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { repo.leaveRoom() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelBlack, contentColor = SignalRed
                )
            ) { Text("Leave convoy") }
        }
    }
}

@Composable
private fun RiderRow(rider: Rider) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelBlack)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(rider.colorArgb)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                rider.displayName.take(1).uppercase(),
                color = VoidBlack,
                style = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rider.displayName, color = OffWhite, style = MaterialTheme.typography.titleSmall)
            Text(
                rider.role.name,
                color = when (rider.role) {
                    RiderRole.LEAD -> Cyan
                    RiderRole.SWEEP -> Emerald
                    RiderRole.MEDIC -> SignalRed
                    RiderRole.PACK -> SteelGrey
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            if (rider.lat != null && rider.lng != null) "● live" else "○ no fix",
            color = if (rider.lat != null) Emerald else SteelGrey,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
