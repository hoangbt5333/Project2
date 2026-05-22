package com.example.project2

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lich_su_moi_truong")
data class ThongSoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val temperature: Double,
    val humidity: Double,
    val timestamp: Long
)
