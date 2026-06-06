package com.example.project2.ui.home

import com.example.project2.domain.ai.AiResult
import com.example.project2.domain.model.SensorData

data class HomeUiState(
    val sensorData: SensorData? = null,
    val aiResult: AiResult? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val recentLogs: List<SensorData> = emptyList()
)