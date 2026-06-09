package com.example.project2.ui.insight

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.project2.data.local.AppDatabase
import com.example.project2.data.local.SensorLogDao
import com.example.project2.data.local.toSensorData
import com.example.project2.domain.ai.SmartFarmAi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AiInsightViewModel(
    private val sensorLogDao: SensorLogDao,
    private val SmartFarmAi: SmartFarmAi = SmartFarmAi()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiInsightUiState())
    val uiState: StateFlow<AiInsightUiState> = _uiState.asStateFlow()

    init {
        observeLogs()
    }

    private fun observeLogs() {
        viewModelScope.launch {
            combine(
                sensorLogDao.observeLatestLog(),
                sensorLogDao.observeRecentLogs(10)
            ) { latest, recent ->
                val averageScore = if (recent.isNotEmpty()) {
                    recent.map { it.soilScore }.average().toInt()
                } else {
                    0
                }

                val warningCount = recent.count {
                    it.decision != "NORMAL"
                }

                val latestAiResult = latest?.let {
                    SmartFarmAi.analyze(it.toSensorData())
                }

                AiInsightUiState(
                    latestLog = latest,
                    latestAiResult = latestAiResult,
                    recentLogs = recent,
                    averageScore = averageScore,
                    warningCount = warningCount
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    return AiInsightViewModel(db.sensorLogDao()) as T
                }
            }
        }
    }
}