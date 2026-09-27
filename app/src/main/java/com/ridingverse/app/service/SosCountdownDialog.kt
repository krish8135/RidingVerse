package com.ridingverse.app.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ridingverse.app.presentation.theme.OffWhite
import com.ridingverse.app.presentation.theme.SignalRed
import com.ridingverse.app.presentation.theme.VoidBlack

/**
 * Full-screen crash alarm: loud visual alert, live coordinates, countdown
 * ring and a glove-friendly ABORT button. Shown while [SosState.active].
 */
@Composable
fun SosCountdownDialog(
    state: SosState,
    onAbort: () -> Unit
) {
    if (!state.active) return
    Dialog(
        onDismissRequest = { /* must explicitly abort */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VoidBlack.copy(alpha = 0.96f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "⚠ CRASH DETECTED",
                    style = MaterialTheme.typography.displayMedium,
                    color = SignalRed,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Are you OK? SOS auto-dispatches when the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OffWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (state.lat != null && state.lng != null)
                        "%.5f, %.5f".format(state.lat, state.lng)
                    else "Locating…",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OffWhite
                )
                Spacer(Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.secondsLeft / SosManager.COUNTDOWN_SECONDS.toFloat() },
                        modifier = Modifier.size(160.dp),
                        color = SignalRed,
                        trackColor = SignalRed.copy(alpha = 0.2f),
                        strokeWidth = 12.dp
                    )
                    Text(
                        "${state.secondsLeft}s",
                        style = MaterialTheme.typography.displayMedium,
                        color = OffWhite
                    )
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onAbort,
                    modifier = Modifier
                        .size(width = 220.dp, height = 72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalRed,
                        contentColor = OffWhite
                    )
                ) {
                    Text("I'M OK — ABORT", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
