package com.example.project2.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Lịch sử mỗi lần tưới nước (AUTO do ESP32 hoặc MANUAL do người dùng bấm). */
@Entity(tableName = "lich_su_tuoi", indices = [Index("timestamp")])
data class WateringEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val mode: String,            // AUTO | MANUAL
    val durationSeconds: Int
)
