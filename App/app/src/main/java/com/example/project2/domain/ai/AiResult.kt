package com.example.project2.domain.ai

enum class FarmDecision {
    NORMAL,
    NEED_WATER,
    STOP_WATERING,
    COOLING_NEEDED,
    NEED_FERTILIZER,
    PH_PROBLEM,
    SENSOR_ERROR
}

enum class NutrientLevel {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

data class NutrientPatternResult(
    val name: String,
    val matchScore: Int,
    val comment: String,
    val explanation: String,
    val action: String
)

data class CropSuggestion(
    val name: String,
    val score: Int,
    val reason: String
)

data class AiResult(
    val soilScore: Int,
    val waterScore: Int,
    val climateScore: Int,
    val nutrientScore: Int,
    val phScore: Int,
    val confidence: Int,

    val decision: FarmDecision,
    val soilStatusText: String,

    // Giữ tên cũ để HomeFragment ít phải sửa
    val summary: String,
    val recommendation: String,
    val cropSuggestion: String,

    val statusN: String,
    val statusP: String,
    val statusK: String,

    val phStatus: String,
    val phExplanation: String,

    val nutrientPattern: NutrientPatternResult,

    // Dùng cho trang AI Insight
    val insightTitle: String,
    val insightAnalysis: String,
    val immediateActions: List<String>,
    val cropSuggestions: List<CropSuggestion>,
    val notRecommendedCrops: List<CropSuggestion>,

    val isWarning: Boolean
)