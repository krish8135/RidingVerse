package com.ridingverse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<GpxWaypointEntity>)

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: String): RouteEntity?

    @Query("SELECT * FROM gpx_waypoints WHERE routeId = :routeId ORDER BY seq ASC")
    suspend fun waypointsFor(routeId: String): List<GpxWaypointEntity>

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: TrackPointEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun forRide(rideId: String): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE rideId = :rideId")
    suspend fun clearRide(rideId: String)
}

@Dao
interface RiderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rider: RiderEntity)

    @Query("SELECT * FROM riders ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<RiderEntity>>

    @Query("DELETE FROM riders")
    suspend fun clear()
}
