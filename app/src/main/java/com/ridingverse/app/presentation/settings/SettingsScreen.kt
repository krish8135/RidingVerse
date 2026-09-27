package com.ridingverse.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ridingverse.app.RidingVerseApp
import com.ridingverse.app.data.settings.AppSettings
import com.ridingverse.app.data.settings.EmergencyContact
import com.ridingverse.app.presentation.theme.Emerald
import com.ridingverse.app.presentation.theme.OffWhite
import com.ridingverse.app.presentation.theme.PanelBlack
import com.ridingverse.app.presentation.theme.SignalRed
import com.ridingverse.app.presentation.theme.SteelGrey
import com.ridingverse.app.presentation.theme.VoidBlack
import kotlinx.coroutines.launch

private val PIN_COLORS = listOf(
    0xFF00E676L, // emerald
    0xFF00E5FFL, // cyan
    0xFFFFB300L, // amber
    0xFFFF3D00L, // red
    0xFF7C4DFFL, // violet
    0xFFFFEB3BL  // yellow
)

private val CRASH_PRESETS = listOf(
    "Sensitive" to 3.0f,
    "Standard" to 3.8f,
    "Rugged" to 4.5f
)

/**
 * Settings, all backed by DataStore:
 * - rider profile (display name + convoy map pin color)
 * - relay server URL for convoy telemetry
 * - emergency contacts (SMS SOS recipients)
 * - crash-detection sensitivity
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember {
        (context.applicationContext as RidingVerseApp).settings
    }
    val scope = rememberCoroutineScope()

    val contacts by settings.emergencyContacts.collectAsState(initial = emptyList())
    val relayUrl by settings.relayUrl.collectAsState(initial = AppSettings.DEFAULT_RELAY_URL)
    val riderName by settings.riderName.collectAsState(initial = "Rider")
    val riderColor by settings.riderColor.collectAsState(initial = 0xFF00E676.toInt())
    val crashG by settings.crashSensitivityG.collectAsState(initial = 3.8f)

    var showAddContact by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Rider profile")
        SettingsCard {
            var nameDraft by remember { mutableStateOf(riderName) }
            LaunchedEffect(riderName) { nameDraft = riderName }
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { scope.launch { settings.setRiderName(nameDraft) } },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald, contentColor = VoidBlack
                )
            ) { Text("Save name") }
            Spacer(Modifier.height(12.dp))
            Text("Map pin color", style = MaterialTheme.typography.labelSmall, color = SteelGrey)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PIN_COLORS.forEach { argb ->
                    val selected = riderColor == argb.toInt()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                3.dp,
                                if (selected) OffWhite else Color.Transparent,
                                CircleShape
                            )
                            .clickable {
                                scope.launch { settings.setRiderColor(argb) }
                            }
                    )
                }
            }
        }

        SectionTitle("Relay server")
        SettingsCard {
            var urlDraft by remember { mutableStateOf(relayUrl) }
            LaunchedEffect(relayUrl) { urlDraft = relayUrl }
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text("WebSocket URL (wss://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { scope.launch { settings.setRelayUrl(urlDraft) } },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald, contentColor = VoidBlack
                )
            ) { Text("Save relay URL") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Deploy relay/ (node server.js) and paste its wss:// URL here. " +
                    "Both riders must use the same relay.",
                style = MaterialTheme.typography.bodySmall,
                color = SteelGrey
            )
        }

        SectionTitle("Emergency contacts")
        SettingsCard {
            if (contacts.isEmpty()) {
                Text(
                    "No contacts yet — SOS texts have nowhere to go. Add at least one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalRed
                )
                Spacer(Modifier.height(8.dp))
            }
            contacts.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, color = OffWhite, style = MaterialTheme.typography.titleSmall)
                        Text(contact.phone, color = SteelGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        scope.launch { settings.removeEmergencyContact(contact.phone) }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = SignalRed)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showAddContact = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald, contentColor = VoidBlack
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add contact")
            }
        }

        SectionTitle("Crash detection")
        SettingsCard {
            Text(
                "Impact threshold. Lower values trigger on smaller shocks.",
                style = MaterialTheme.typography.bodySmall,
                color = SteelGrey
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CRASH_PRESETS.forEach { (label, g) ->
                    val selected = crashG == g
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Emerald else VoidBlack)
                            .border(1.dp, SteelGrey, RoundedCornerShape(10.dp))
                            .clickable { scope.launch { settings.setCrashSensitivityG(g) } }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) VoidBlack else OffWhite
                            )
                            Text(
                                "${g}G",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) VoidBlack else SteelGrey
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showAddContact) {
        AddContactDialog(
            onDismiss = { showAddContact = false },
            onAdd = { name, phone ->
                scope.launch {
                    settings.addEmergencyContact(EmergencyContact(name, phone))
                    showAddContact = false
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Emerald,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBlack)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OffWhite,
    unfocusedTextColor = OffWhite,
    focusedBorderColor = Emerald,
    unfocusedBorderColor = SteelGrey,
    focusedLabelColor = Emerald,
    unfocusedLabelColor = SteelGrey,
    cursorColor = Emerald
)

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && phone.filter { it.isDigit() }.length >= 7

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add emergency contact", color = OffWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Phone number") },
                    singleLine = true,
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim(), phone.trim()) }, enabled = valid) {
                Text("Add", color = Emerald)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SteelGrey) }
        },
        containerColor = PanelBlack
    )
}
