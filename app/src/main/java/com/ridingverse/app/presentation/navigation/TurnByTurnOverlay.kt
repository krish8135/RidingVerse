package com.ridingverse.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TurnLeft
import androidx.compose.material.icons.automirrored.filled.TurnRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ridingverse.app.presentation.theme.Cyan
import com.ridingverse.app.presentation.theme.Emerald
import com.ridingverse.app.presentation.theme.OffWhite
import com.ridingverse.app.presentation.theme.PanelBlack
import com.ridingverse.app.presentation.theme.SteelGrey

private fun ManeuverIcon.toImageVector(): ImageVector = when (this) {
    ManeuverIcon.STRAIGHT -> Icons.Filled.ArrowUpward
    ManeuverIcon.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    ManeuverIcon.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    ManeuverIcon.LEFT -> Icons.AutoMirrored.Filled.TurnLeft
    ManeuverIcon.RIGHT -> Icons.AutoMirrored.Filled.TurnRight
    ManeuverIcon.SHARP_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
    ManeuverIcon.SHARP_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
    ManeuverIcon.UTURN -> Icons.Filled.UTurnLeft
    ManeuverIcon.ROUNDABOUT -> Icons.Filled.Refresh
    ManeuverIcon.ARRIVE -> Icons.Filled.Flag
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)

/**
 * Turn-by-turn guidance overlay shown above the cockpit map: next maneuver,
 * distance to the turn, destination, remaining distance and ETA.
 */
@Composable
fun TurnByTurnOverlay(progress: RouteProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = progress.nextManeuver.icon.toImageVector(),
                contentDescription = progress.nextManeuver.instruction,
                tint = Emerald,
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    "in ${formatDistance(progress.distanceToTurnMeters)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OffWhite
                )
                Text(
                    progress.nextManeuver.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteelGrey
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = SteelGrey)
                Text(progress.destinationName, style = MaterialTheme.typography.titleMedium, color = OffWhite)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("REMAINING", style = MaterialTheme.typography.labelSmall, color = SteelGrey)
                Text(
                    "${formatDistance(progress.remainingMeters)} · ETA ${progress.etaText}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Cyan
                )
            }
        }
    }
}
