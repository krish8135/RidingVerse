package com.ridingverse.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A single emergency contact: name + phone number for SMS SOS dispatch. */
@Serializable
data class EmergencyContact(val name: String, val phone: String)

private val Context.settingsDataStore by preferencesDataStore(name = "ridingverse_settings")

/**
 * All user-tunable app settings, persisted with Preferences DataStore.
 * Read as Flows (UI collects them); written from suspend functions.
 */
class AppSettings private constructor(private val appContext: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------
    // Emergency contacts (feeds SosManager)
    // ------------------------------------------------------------------
    val emergencyContacts: Flow<List<EmergencyContact>> =
        appContext.settingsDataStore.data.map { prefs ->
            runCatching {
                json.decodeFromString<List<EmergencyContact>>(prefs[KEY_CONTACTS] ?: "[]")
            }.getOrDefault(emptyList())
        }

    suspend fun addEmergencyContact(contact: EmergencyContact) {
        val current = emergencyContacts.first().toMutableList()
        if (current.none { it.phone == contact.phone }) {
            current.add(contact)
            saveContacts(current)
        }
    }

    suspend fun removeEmergencyContact(phone: String) {
        saveContacts(emergencyContacts.first().filterNot { it.phone == phone })
    }

    private suspend fun saveContacts(contacts: List<EmergencyContact>) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_CONTACTS] = json.encodeToString(contacts)
        }
    }

    // ------------------------------------------------------------------
    // Convoy relay + rider identity (feeds TelemetrySocket / ConvoyRepository)
    // ------------------------------------------------------------------
    val relayUrl: Flow<String> = appContext.settingsDataStore.data.map { prefs ->
        prefs[KEY_RELAY_URL] ?: DEFAULT_RELAY_URL
    }

    suspend fun setRelayUrl(url: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_RELAY_URL] = url.trim().ifBlank { DEFAULT_RELAY_URL }
        }
    }

    val riderName: Flow<String> = appContext.settingsDataStore.data.map { prefs ->
        prefs[KEY_RIDER_NAME] ?: "Rider"
    }

    suspend fun setRiderName(name: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_RIDER_NAME] = name.trim().ifBlank { "Rider" }
        }
    }

    /** ARGB pin color for this rider on convoy maps. */
    val riderColor: Flow<Int> = appContext.settingsDataStore.data.map { prefs ->
        (prefs[KEY_RIDER_COLOR] ?: DEFAULT_RIDER_COLOR).toInt()
    }

    suspend fun setRiderColor(argb: Long) {
        appContext.settingsDataStore.edit { prefs -> prefs[KEY_RIDER_COLOR] = argb }
    }

    // ------------------------------------------------------------------
    // Safety tuning
    // ------------------------------------------------------------------
    /** Impact-shock threshold in G; lower = more sensitive. */
    val crashSensitivityG: Flow<Float> = appContext.settingsDataStore.data.map { prefs ->
        prefs[KEY_CRASH_G] ?: 3.8f
    }

    suspend fun setCrashSensitivityG(g: Float) {
        appContext.settingsDataStore.edit { prefs -> prefs[KEY_CRASH_G] = g }
    }

    // ------------------------------------------------------------------
    // Onboarding
    // ------------------------------------------------------------------
    val permissionsOnboarded: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[KEY_ONBOARDED] ?: false
    }

    suspend fun setPermissionsOnboarded(done: Boolean) {
        appContext.settingsDataStore.edit { prefs -> prefs[KEY_ONBOARDED] = done }
    }

    companion object {
        const val DEFAULT_RELAY_URL = "wss://relay.ridingverse.example.com"
        private const val DEFAULT_RIDER_COLOR = 0xFF00E676L

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }

        private val KEY_CONTACTS = stringPreferencesKey("emergency_contacts_json")
        private val KEY_RELAY_URL = stringPreferencesKey("relay_url")
        private val KEY_RIDER_NAME = stringPreferencesKey("rider_name")
        private val KEY_RIDER_COLOR = longPreferencesKey("rider_color_argb")
        private val KEY_CRASH_G = floatPreferencesKey("crash_sensitivity_g")
        private val KEY_ONBOARDED = booleanPreferencesKey("permissions_onboarded")
    }
}
