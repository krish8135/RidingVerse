package com.ridingverse.app

import android.app.Application
import com.ridingverse.app.data.local.AppDatabase
import com.ridingverse.app.data.remote.ConvoyRepository
import com.ridingverse.app.data.remote.TelemetrySocket
import com.ridingverse.app.data.settings.AppSettings

/**
 * Application holder for process-wide singletons: settings, the telemetry
 * socket, the Room database and the convoy repository. Screens, view models
 * and the foreground service all share these so rider identity, room state
 * and the socket connection stay consistent.
 */
class RidingVerseApp : Application() {

    val settings: AppSettings by lazy { AppSettings.getInstance(this) }

    val telemetrySocket: TelemetrySocket by lazy { TelemetrySocket() }

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val convoyRepository: ConvoyRepository by lazy {
        ConvoyRepository(telemetrySocket, database.riderDao(), settings)
    }
}
