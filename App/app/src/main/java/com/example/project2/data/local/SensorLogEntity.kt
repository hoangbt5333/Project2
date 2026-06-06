package com.example.project2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.project2.domain.ai.FarmDecision
import com.example.project2.domain.ai.AiResult
import com.example.project2.domain.model.SensorData

@Entity(tableName = "sensor_logs")
data class SensorLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestamp: Long,

    val airTemperature: Double,
    val airHumidity: Double,
    val soilMoisture: Int,

    val npkN: Int,
    val npkP: Int,
    val npkK: Int,

    val soilScore: Int,
    val waterScore: Int,
    val climateScore: Int,
    val nutrientScore: Int,
    val confidence: Int,

    val decision: String,
    val soilStatusText: String,
    val aiSummary: String,
    val recommendation: String
)

fun SensorLogEntity.toSensorData(): SensorData {
    return SensorData(
        airTemperature = airTemperature,
        airHumidity = airHumidity,
        soilMoisture = soilMoisture,
        npkN = npkN,
        npkP = npkP,
        npkK = npkK,
        timestamp = timestamp
    )
}

fun createSensorLogEntity(
    sensorData: SensorData,
    aiResult: AiResult
): SensorLogEntity {
    return SensorLogEntity(
        timestamp = sensorData.timestamp,
        airTemperature = sensorData.airTemperature,
        airHumidity = sensorData.airHumidity,
        soilMoisture = sensorData.soilMoisture,
        npkN = sensorData.npkN,
        npkP = sensorData.npkP,
        npkK = sensorData.npkK,
        soilScore = aiResult.soilScore,
        waterScore = aiResult.waterScore,
        climateScore = aiResult.climateScore,
        nutrientScore = aiResult.nutrientScore,
        confidence = aiResult.confidence,
        decision = aiResult.decision.name,
        soilStatusText = aiResult.soilStatusText,
        aiSummary = aiResult.summary,
        recommendation = aiResult.recommendation
    )
}