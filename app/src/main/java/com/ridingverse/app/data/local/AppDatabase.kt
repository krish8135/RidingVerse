package com.ridingverse.app.data.local

import android.content.Context

/**
 * Offline cache: saved routes + GPX waypoints, recorded track points,
 * and the last-known convoy roster. Everything the map and cockpit need
 * when the ride goes beyond cellular coverage.
 *
 * Backed by [RidingVerseDbHelper] (plain SQLiteOpenHelper).
 */
class AppDatabase private constructor(context: Context) {

    private val helper = RidingVerseDbHelper(context.applicationContext)

    fun routeDao(): RouteDao = RouteDao(helper)
    fun trackPointDao(): TrackPointDao = TrackPointDao(helper)
    fun riderDao(): RiderDao = RiderDao(helper)

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context).also { instance = it }
            }
    }
}
