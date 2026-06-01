package com.example.project2

data class AiResult(
    val analysisText: String,
    val waterTitle: String,
    val waterSub: String,
    val fertilizerTitle: String,
    val fertilizerSub: String,
    val cropType: String,
    val soilStatusText: String,
    val isWarning: Boolean,
    val soilScore: Int,
    val soilScoreEvaluation: String,
    val statusN: String,
    val statusP: String,
    val statusK: String
)
