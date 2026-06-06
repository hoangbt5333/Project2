package com.example.project2.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SensorLogEntity)

    @Query("SELECT * FROM sensor_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 10): Flow<List<SensorLogEntity>>

    @Query("SELECT * FROM sensor_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestLog(): Flow<SensorLogEntity?>

    @Query("DELETE FROM sensor_logs WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)

    @Query("DELETE FROM sensor_logs")
    suspend fun clearAll()
}