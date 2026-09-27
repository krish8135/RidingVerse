package com.ridingverse.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite schema for the offline cache: saved routes + GPX waypoints,
 * recorded track points, and the last-known convoy roster. Everything the
 * map and cockpit need when the ride goes beyond cellular coverage.
 *
 * Implemented directly on [SQLiteOpenHelper] (no ORM) to keep the app free
 * of annotation-processing build dependencies.
 */
internal class RidingVerseDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE routes (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                distanceMeters REAL NOT NULL,
                profile TEXT NOT NULL,
                elevationProfileJson TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE gpx_waypoints (
                uid INTEGER PRIMARY KEY AUTOINCREMENT,
                routeId TEXT NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
                seq INTEGER NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                elevationM REAL
            )"""
        )
        db.execSQL("CREATE INDEX idx_waypoints_route ON gpx_waypoints(routeId)")
        db.execSQL(
            """CREATE TABLE track_points (
                uid INTEGER PRIMARY KEY AUTOINCREMENT,
                rideId TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                speedMps REAL NOT NULL,
                bearingDeg REAL NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_track_ride ON track_points(rideId)")
        db.execSQL(
            """CREATE TABLE riders (
                riderId TEXT PRIMARY KEY,
                displayName TEXT NOT NULL,
                role TEXT NOT NULL,
                lastLat REAL,
                lastLng REAL,
                lastSeen INTEGER NOT NULL,
                colorArgb INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Pre-release schema: destructive migration is acceptable.
        db.execSQL("DROP TABLE IF EXISTS gpx_waypoints")
        db.execSQL("DROP TABLE IF EXISTS track_points")
        db.execSQL("DROP TABLE IF EXISTS riders")
        db.execSQL("DROP TABLE IF EXISTS routes")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DATABASE_NAME = "ridingverse.db"
        const val DATABASE_VERSION = 2
    }
}
