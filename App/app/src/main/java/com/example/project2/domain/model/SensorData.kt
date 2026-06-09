package com.example.project2.domain.model

data class SensorData(
    val airTemperature: Double = 0.0,
    val airHumidity: Double = 0.0,
    val soilMoisture: Int = 0,
    val soilPh: Double = 6.5,
    val npkN: Int = 0,
    val npkP: Int = 0,
    val npkK: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)