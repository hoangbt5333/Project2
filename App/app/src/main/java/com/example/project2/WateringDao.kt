package com.example.project2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WateringDao {
    @Insert
    suspend fun insert(watering: WateringEntity)

    // MỤC 5: lịch sử tưới nước
    @Query("SELECT * FROM lich_su_tuoi ORDER BY timestamp DESC LIMIT 50")
    fun getRecentWatering(): Flow<List<WateringEntity>>
}
