package com.example.project2.domain.ai

interface AiRecommendationEngine {
    fun analyze(soilMoist: Int, temp: Double, humid: Double, n: Int, p: Int, k: Int): AiResult
}