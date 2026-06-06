package com.example.project2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ===== POJO kết quả cho các truy vấn thống kê =====
data class DailyScore(val day: String, val avgScore: Double)
data class StatusCount(val soilStatusText: String, val cnt: Int)
data class DailyAi(
    val day: String,
    val avgScore: Double,
    val avgTemp: Double,
    val avgMoist: Double,
    val avgN: Double,
    val avgP: Double,
    val avgK: Double
)

@Dao
interface ThongSoDao {
    @Insert
    suspend fun insert(thongSo: ThongSoEntity)

    // Dùng cho line chart realtime (giữ nguyên)
    @Query("SELECT * FROM lich_su_moi_truong ORDER BY timestamp DESC LIMIT 20")
    fun getRecentHistory(): Flow<List<ThongSoEntity>>

    // MỤC 6: bảng dữ liệu lịch sử (RecyclerView)
    @Query("SELECT * FROM lich_su_moi_truong ORDER BY timestamp DESC LIMIT :limit")
    fun getTableRows(limit: Int): Flow<List<ThongSoEntity>>

    // MỤC 7: điểm sức khỏe đất trung bình theo ngày
    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch', 'localtime') AS day,
               AVG(soilScore) AS avgScore
        FROM lich_su_moi_truong
        WHERE timestamp >= :sinceMillis
        GROUP BY day ORDER BY day ASC
        """
    )
    fun getDailyScores(sinceMillis: Long): Flow<List<DailyScore>>

    // MỤC 4: phân bố trạng thái đất
    @Query(
        """
        SELECT soilStatusText, COUNT(*) AS cnt
        FROM lich_su_moi_truong
        WHERE timestamp >= :sinceMillis AND soilStatusText != ''
        GROUP BY soilStatusText ORDER BY cnt DESC
        """
    )
    fun getStatusDistribution(sinceMillis: Long): Flow<List<StatusCount>>

    // MỤC 2: nhật ký AI theo ngày (tổng hợp trung bình mỗi ngày, dựng câu mô tả ở tầng Kotlin)
    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch', 'localtime') AS day,
               AVG(soilScore) AS avgScore, AVG(airTemperature) AS avgTemp,
               AVG(soilMoisture) AS avgMoist, AVG(npkN) AS avgN,
               AVG(npkP) AS avgP, AVG(npkK) AS avgK
        FROM lich_su_moi_truong
        WHERE timestamp >= :sinceMillis
        GROUP BY day ORDER BY day DESC
        """
    )
    fun getDailyAiDigest(sinceMillis: Long): Flow<List<DailyAi>>
}
