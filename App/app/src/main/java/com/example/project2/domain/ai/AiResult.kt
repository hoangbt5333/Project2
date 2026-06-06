package com.example.project2.domain.ai

enum class FarmDecision {
    NORMAL,
    NEED_WATER,
    STOP_WATERING,
    COOLING_NEEDED,
    NEED_FERTILIZER,
    SENSOR_ERROR
}

data class AiResult(
    val soilScore: Int,
    val waterScore: Int,
    val climateScore: Int,
    val nutrientScore: Int,
    val confidence: Int,
    val decision: FarmDecision,
    val soilStatusText: String,
    val summary: String,
    val recommendation: String,
    val cropSuggestion: String,
    val statusN: String,
    val statusP: String,
    val statusK: String,
    val isWarning: Boolean
)