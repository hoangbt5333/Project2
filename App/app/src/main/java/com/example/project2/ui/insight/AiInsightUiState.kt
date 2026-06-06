package com.example.project2.ui.insight

import com.example.project2.data.local.SensorLogEntity

data class AiInsightUiState(
    val latestLog: SensorLogEntity? = null,
    val recentLogs: List<SensorLogEntity> = emptyList(),
    val averageScore: Int = 0,
    val warningCount: Int = 0
)