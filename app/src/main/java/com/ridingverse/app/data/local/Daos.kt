package com.ridingverse.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------
// Cursor helpers
// ------------------------------------------------------------------

private fun Cursor.getStringOrNull(column: String): String? {
    val i = getColumnIndex(column)
    return if (i == -1 || isNull(i)) null else getString(i)
}

private fun Cursor.getDoubleOrNull(column: String): Double? {
    val i = getColumnIndex(column)
    return if (i == -1 || isNull(i)) null else getDouble(i)
}

private fun Cursor.getFloat(column: String): Float = getDouble(getColumnIndex(column)).toFloat()

// ------------------------------------------------------------------
// RouteDao
// ------------------------------------------------------------------

class RouteDao internal constructor(private val helper: RidingVerseDbHelper) {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    suspend fun upsert(route: RouteEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", route.id)
            put("name", route.name)
            put("createdAt", route.createdAt)
            put("distanceMeters", route.distanceMeters)
            put("profile", route.profile)
            put("elevationProfileJson", route.elevationProfileJson)
        }
        helper.writableDatabase.insertWithOnConflict(
            "routes", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        changes.tryEmit(Unit)
    }

    suspend fun insertWaypoints(waypoints: List<GpxWaypointEntity>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (w in waypoints) {
                val values = ContentValues().apply {
                    put("routeId", w.routeId)
                    put("seq", w.seq)
                    put("lat", w.lat)
                    put("lng", w.lng)
                    put("elevationM", w.elevationM)
                }
                db.insertWithOnConflict(
                    "gpx_waypoints", null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun observeAll(): Flow<List<RouteEntity>> = flow {
        emit(loadAll())
        changes.collect { emit(loadAll()) }
    }.flowOn(Dispatchers.IO)

    suspend fun getById(id: String): RouteEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase
            .query("routes", null, "id = ?", arrayOf(id), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toRouteEntity() else null }
    }

    suspend fun waypointsFor(routeId: String): List<GpxWaypointEntity> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase
                .query(
                    "gpx_waypoints", null, "routeId = ?", arrayOf(routeId),
                    null, null, "seq ASC"
                )
                .use { c ->
                    buildList {
                        while (c.moveToNext()) add(c.toWaypointEntity())
                    }
                }
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("routes", "id = ?", arrayOf(id))
        changes.tryEmit(Unit)
    }

    private fun loadAll(): List<RouteEntity> =
        helper.readableDatabase
            .query("routes", null, null, null, null, null, "createdAt DESC")
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(c.toRouteEntity())
                }
            }

    private fun Cursor.toRouteEntity() = RouteEntity(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        distanceMeters = getDouble(getColumnIndexOrThrow("distanceMeters")),
        profile = getString(getColumnIndexOrThrow("profile")),
        elevationProfileJson = getStringOrNull("elevationProfileJson")
    )

    private fun Cursor.toWaypointEntity() = GpxWaypointEntity(
        uid = getLong(getColumnIndexOrThrow("uid")),
        routeId = getString(getColumnIndexOrThrow("routeId")),
        seq = getInt(getColumnIndexOrThrow("seq")),
        lat = getDouble(getColumnIndexOrThrow("lat")),
        lng = getDouble(getColumnIndexOrThrow("lng")),
        elevationM = getDoubleOrNull("elevationM")
    )
}

// ------------------------------------------------------------------
// TrackPointDao
// ------------------------------------------------------------------

class TrackPointDao internal constructor(private val helper: RidingVerseDbHelper) {

    suspend fun insert(point: TrackPointEntity) = withContext(Dispatchers.IO) {
        insertInternal(point)
    }

    suspend fun insertAll(points: List<TrackPointEntity>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (p in points) insertInternal(p, db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun forRide(rideId: String): List<TrackPointEntity> = withContext(Dispatchers.IO) {
        helper.readableDatabase
            .query(
                "track_points", null, "rideId = ?", arrayOf(rideId),
                null, null, "timestamp ASC"
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(c.toTrackPointEntity())
                }
            }
    }

    suspend fun clearRide(rideId: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("track_points", "rideId = ?", arrayOf(rideId))
    }

    private fun insertInternal(
        p: TrackPointEntity,
        db: android.database.sqlite.SQLiteDatabase = helper.writableDatabase
    ) {
        val values = ContentValues().apply {
            put("rideId", p.rideId)
            put("timestamp", p.timestamp)
            put("lat", p.lat)
            put("lng", p.lng)
            put("speedMps", p.speedMps.toDouble())
            put("bearingDeg", p.bearingDeg.toDouble())
        }
        db.insertWithOnConflict(
            "track_points", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun Cursor.toTrackPointEntity() = TrackPointEntity(
        uid = getLong(getColumnIndexOrThrow("uid")),
        rideId = getString(getColumnIndexOrThrow("rideId")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        lat = getDouble(getColumnIndexOrThrow("lat")),
        lng = getDouble(getColumnIndexOrThrow("lng")),
        speedMps = getFloat("speedMps"),
        bearingDeg = getFloat("bearingDeg")
    )
}

// ------------------------------------------------------------------
// RiderDao
// ------------------------------------------------------------------

class RiderDao internal constructor(private val helper: RidingVerseDbHelper) {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    suspend fun upsert(rider: RiderEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("riderId", rider.riderId)
            put("displayName", rider.displayName)
            put("role", rider.role)
            put("lastLat", rider.lastLat)
            put("lastLng", rider.lastLng)
            put("lastSeen", rider.lastSeen)
            put("colorArgb", rider.colorArgb.toLong())
        }
        helper.writableDatabase.insertWithOnConflict(
            "riders", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        changes.tryEmit(Unit)
    }

    fun observeAll(): Flow<List<RiderEntity>> = flow {
        emit(loadAll())
        changes.collect { emit(loadAll()) }
    }.flowOn(Dispatchers.IO)

    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("riders", null, null)
        changes.tryEmit(Unit)
    }

    private fun loadAll(): List<RiderEntity> =
        helper.readableDatabase
            .query("riders", null, null, null, null, null, "lastSeen DESC")
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(c.toRiderEntity())
                }
            }

    private fun Cursor.toRiderEntity() = RiderEntity(
        riderId = getString(getColumnIndexOrThrow("riderId")),
        displayName = getString(getColumnIndexOrThrow("displayName")),
        role = getString(getColumnIndexOrThrow("role")),
        lastLat = getDoubleOrNull("lastLat"),
        lastLng = getDoubleOrNull("lastLng"),
        lastSeen = getLong(getColumnIndexOrThrow("lastSeen")),
        colorArgb = getLong(getColumnIndexOrThrow("colorArgb")).toInt()
    )
}

