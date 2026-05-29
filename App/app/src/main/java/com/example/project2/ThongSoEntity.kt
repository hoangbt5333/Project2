package com.example.project2

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lich_su_moi_truong")
data class ThongSoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val airTemperature: Double,
    val airHumidity: Double,
    val soilMoisture: Int,
    val npkN: Int,
    val npkP: Int,
    val npkK: Int,
    val timestamp: Long // Mốc thời gian lưu (miliseconds) để làm trục X cho biểu đồ
)