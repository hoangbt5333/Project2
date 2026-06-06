package com.example.project2

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// THÊM index timestamp để các truy vấn thống kê theo khoảng thời gian chạy nhanh.
// THÊM 2 cột soilScore + soilStatusText để phục vụ "Nhật ký AI" và "Sức khỏe đất theo ngày".
@Entity(tableName = "lich_su_moi_truong", indices = [Index("timestamp")])
data class ThongSoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val airTemperature: Double,
    val airHumidity: Double,
    val soilMoisture: Int,
    val npkN: Int,
    val npkP: Int,
    val npkK: Int,
    val soilScore: Int = 100,          // MỚI: điểm sức khỏe đất do AI chấm
    val soilStatusText: String = "",   // MỚI: trạng thái đất (Tối ưu / Đất khô / Úng nước...)
    val timestamp: Long
)
