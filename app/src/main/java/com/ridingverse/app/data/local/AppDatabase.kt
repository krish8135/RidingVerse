package com.ridingverse.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Offline cache: saved routes + GPX waypoints, recorded track points,
 * and the last-known convoy roster. Everything the map and cockpit need
 * when the ride goes beyond cellular coverage.
 */
@Database(
    entities = [
        RouteEntity::class,
        GpxWaypointEntity::class,
        TrackPointEntity::class,
        RiderEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun riderDao(): RiderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ridingverse.db"
                )
                    // v1 -> v2 adds RiderEntity.colorArgb; pre-release, so a
                    // destructive migration is acceptable.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
