package com.ridingverse.app.presentation.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ridingverse.app.presentation.theme.Emerald
import com.ridingverse.app.presentation.theme.OffWhite
import com.ridingverse.app.presentation.theme.PanelBlack
import com.ridingverse.app.presentation.theme.SteelGrey
import com.ridingverse.app.presentation.theme.VoidBlack

private data class PermissionGroup(
    val title: String,
    val description: String,
    val permissions: List<String>
)

/**
 * Single runtime-permissions onboarding flow, shown once on first launch.
 * Groups are requested in order — background location is deliberately
 * separate from foreground location, as Android requires.
 */
@Composable
fun PermissionsScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    val groups = remember {
        buildList {
            add(
                PermissionGroup(
                    title = "Location",
                    description = "Live GPS tracking, navigation and convoy telemetry.",
                    permissions = listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(
                    PermissionGroup(
                        title = "Background location",
                        description = "Keeps tracking your ride when the screen is locked.",
                        permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionGroup(
                        title = "Notifications",
                        description = "Ride-status notification for the foreground service.",
                        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
                    )
                )
            }
            add(
                PermissionGroup(
                    title = "Microphone",
                    description = "Push-to-talk walkie-talkie intercom with the convoy.",
                    permissions = listOf(Manifest.permission.RECORD_AUDIO)
                )
            )
            add(
                PermissionGroup(
                    title = "SMS",
                    description = "Sends SOS texts to your emergency contacts after a crash.",
                    permissions = listOf(Manifest.permission.SEND_SMS)
                )
            )
        }
    }

    var granted by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // Prefill with whatever is already granted (e.g. reinstalls).
    LaunchedEffect(Unit) {
        granted = groups.flatMap { it.permissions }.distinct().associateWith { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = granted + result
    }

    val nextPending = groups.firstOrNull { group ->
        group.permissions.any { granted[it] != true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Before you ride",
            style = MaterialTheme.typography.displaySmall,
            color = OffWhite
        )
        Text(
            "RidingVerse needs a few permissions to keep you tracked, " +
                "connected and safe. Grant them in order below.",
            style = MaterialTheme.typography.bodyMedium,
            color = SteelGrey
        )

        groups.forEach { group ->
            val done = group.permissions.all { granted[it] == true }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelBlack)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (done) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) Emerald else SteelGrey,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(group.title, color = OffWhite, style = MaterialTheme.typography.titleSmall)
                    Text(
                        group.description,
                        color = SteelGrey,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (nextPending != null) {
            Button(
                onClick = { launcher.launch(nextPending.permissions.toTypedArray()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald, contentColor = VoidBlack
                )
            ) {
                Text("Grant ${nextPending.title}", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now", color = SteelGrey)
            }
        } else {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald, contentColor = VoidBlack
                )
            ) {
                Text("Start riding", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
