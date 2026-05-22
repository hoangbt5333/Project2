package com.example.project2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThongSoDao {
    @Insert
    suspend fun insert(thongSo: ThongSoEntity)

    @Query("SELECT * FROM lich_su_moi_truong ORDER BY timestamp DESC LIMIT 20")
    fun getRecentHistory(): Flow<List<ThongSoEntity>>
}
