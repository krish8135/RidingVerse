package com.ridingverse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ridingverse.app.RidingVerseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Keeps the ride alive: continuous fused-location tracking and the
 * [CrashDetectionManager] IMU monitor run here even with the screen locked.
 * The microphone service type is declared for the PTT intercom path.
 *
 * Each fix is: stored as [lastLocation] (used for SOS coordinates),
 * broadcast to the convoy room (if joined), and emitted on [locationUpdates]
 * so the cockpit can drive real-GPS navigation.
 */
class RideForegroundService : Service() {

    private lateinit var crashDetection: CrashDetectionManager
    private lateinit var sosManager: SosManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val fused by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLocation = loc
            serviceScope.launch { locationUpdates.emit(loc) }
            (application as RidingVerseApp).convoyRepository.broadcastLocation(
                lat = loc.latitude,
                lng = loc.longitude,
                speedMps = loc.speed,
                bearingDeg = loc.bearing
            )
            // TODO: persist TrackPointEntity breadcrumbs via trackPointDao for the ride recorder.
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as RidingVerseApp
        sosManager = SosManager(this, app.settings)
        crashDetection = CrashDetectionManager(this) {
            val loc = lastLocation
            sosManager.startCountdown(lat = loc?.latitude, lng = loc?.longitude)
        }
        // Crash sensitivity follows Settings live.
        serviceScope.launch {
            app.settings.crashSensitivityG.collect { g ->
                crashDetection.gThresholdG = g
            }
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        startLocationUpdates()
        crashDetection.start()
        return START_STICKY
    }

    override fun onDestroy() {
        crashDetection.stop()
        serviceScope.cancel()
        runCatching {
            fused.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).setMinUpdateDistanceMeters(5f).build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Location permission revoked mid-ride; the service keeps IMU monitoring alive.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ride tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RidingVerse — ride in progress")
            .setContentText("GPS + crash detection active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_STOP = "com.ridingverse.app.action.STOP_RIDE"
        private const val CHANNEL_ID = "ride_tracking"
        private const val NOTIFICATION_ID = 42
        private const val LOCATION_INTERVAL_MS = 2000L

        /** Latest GPS fix, used for SOS coordinates. */
        @Volatile
        var lastLocation: Location? = null
            private set

        /** Hot flow of fixes for the cockpit's real-GPS navigation mode. */
        val locationUpdates = MutableSharedFlow<Location>(extraBufferCapacity = 16)
    }
}
