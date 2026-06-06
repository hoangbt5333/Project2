package com.example.project2.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class TypeCount(val type: String, val cnt: Int)

@Dao
interface AlertDao {
    @Insert
    suspend fun insert(alert: AlertEntity)

    // MỤC 1: timeline cảnh báo
    @Query("SELECT * FROM lich_su_canh_bao ORDER BY timestamp DESC LIMIT 50")
    fun getRecentAlerts(): Flow<List<AlertEntity>>

    // MỤC 3: đếm số cảnh báo theo loại trong tuần
    @Query(
        """
        SELECT type, COUNT(*) AS cnt FROM lich_su_canh_bao
        WHERE severity = 'WARNING' AND timestamp >= :sinceMillis
        GROUP BY type
        """
    )
    fun getAlertCountsByType(sinceMillis: Long): Flow<List<TypeCount>>

    // Phục vụ chống spam: lấy bản ghi gần nhất của 1 loại
    @Query("SELECT * FROM lich_su_canh_bao WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAlertOfType(type: String): AlertEntity?

    @Query("SELECT * FROM lich_su_canh_bao WHERE severity = 'WARNING' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastWarning(): AlertEntity?
}
