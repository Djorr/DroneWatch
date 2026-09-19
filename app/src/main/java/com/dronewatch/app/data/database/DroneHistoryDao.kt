package com.dronewatch.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DroneHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DroneHistoryEntity)

    @Query("SELECT * FROM drone_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 500): Flow<List<DroneHistoryEntity>>

    @Query("DELETE FROM drone_history")
    suspend fun clearAll()

    @Query("DELETE FROM drone_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
