package com.example.project2.ui.insight

import com.example.project2.data.local.SensorLogEntity
import com.example.project2.domain.ai.AiResult

data class AiInsightUiState(
    val latestLog: SensorLogEntity? = null,
    val latestAiResult: AiResult? = null,
    val recentLogs: List<SensorLogEntity> = emptyList(),
    val averageScore: Int = 0,
    val warningCount: Int = 0
)