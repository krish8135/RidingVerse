package com.ridingverse.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.ridingverse.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** State of the post-crash emergency flow. */
data class SosState(
    val active: Boolean = false,
    val secondsLeft: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * 30-second abortable SOS countdown. If the rider doesn't cancel in time,
 * emergency coordinates are dispatched by SMS to the emergency contacts
 * stored in Settings (DataStore).
 */
class SosManager(
    private val context: Context,
    private val settings: AppSettings
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(SosState())
    val state: StateFlow<SosState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    /** Starts the 30 s countdown; ignored if one is already running. */
    fun startCountdown(lat: Double?, lng: Double?) {
        if (_state.value.active) return
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (s in COUNTDOWN_SECONDS downTo 1) {
                _state.value = SosState(active = true, secondsLeft = s, lat = lat, lng = lng)
                delay(1000)
            }
            triggerSosNow()
        }
    }

    /** Rider is OK — cancel the countdown before dispatch. */
    fun abort() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SosState()
    }

    /** Skip the countdown and dispatch immediately (manual SOS button). */
    fun triggerSosNow() {
        val snapshot = _state.value
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SosState()
        dispatchSms(snapshot.lat, snapshot.lng)
    }

    private fun dispatchSms(lat: Double?, lng: Double?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permission must be granted via the runtime permission flow before SOS can send.
            return
        }
        scope.launch {
            val contacts = settings.emergencyContacts.first()
            if (contacts.isEmpty()) return@launch
            val coords = if (lat != null && lng != null) {
                "https://maps.google.com/?q=$lat,$lng"
            } else {
                "unknown"
            }
            val message = "SOS! Motorcycle emergency — RidingVerse crash detection " +
                "triggered. Last coordinates: $coords. Please respond / call emergency services."
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            contacts.forEach { contact ->
                runCatching {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                }
            }
            // TODO: dual-SIM selection — currently the default subscription sends.
        }
    }

    companion object {
        const val COUNTDOWN_SECONDS = 30
    }
}
