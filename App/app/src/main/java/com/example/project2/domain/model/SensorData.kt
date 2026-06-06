package com.example.project2.domain.model

data class SensorData(
    val airTemperature: Double,
    val airHumidity: Double,
    val soilMoisture: Int,
    val npkN: Int,
    val npkP: Int,
    val npkK: Int,
    val timestamp: Long = System.currentTimeMillis()
)