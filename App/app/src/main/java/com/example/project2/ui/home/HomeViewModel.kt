package com.example.project2.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.project2.data.firebase.FirebaseRepository
import com.example.project2.data.local.AppDatabase
import com.example.project2.data.local.SensorLogDao
import com.example.project2.data.local.createSensorLogEntity
import com.example.project2.data.local.toSensorData
import com.example.project2.domain.ai.SmartFarmAi
import com.example.project2.domain.model.SensorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class HomeViewModel(
    private val firebaseRepository: FirebaseRepository,
    private val sensorLogDao: SensorLogDao,
    private val smartFarmAi: SmartFarmAi = SmartFarmAi()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastSavedTime = 0L
    private var lastSavedSoilMoisture: Int? = null

    private val saveIntervalMs = 5_000L
    private val soilDeltaLimit = 1

    init {
        observeSensorData()
        observeRecentLogs()
    }

    private fun observeSensorData() {
        viewModelScope.launch {
            firebaseRepository.observeSensorData()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Không đọc được dữ liệu Firebase"
                        )
                    }
                }
                .collect { sensorData ->
                    val aiResult = smartFarmAi.analyze(sensorData)

                    _uiState.update { current ->
                        current.copy(
                            sensorData = sensorData,
                            aiResult = aiResult,
                            isLoading = false,
                            errorMessage = null
                        )
                    }

                    saveLogIfNeeded(sensorData)
                }
        }
    }

    private fun observeRecentLogs() {
        viewModelScope.launch {
            sensorLogDao.observeRecentLogs(20)
                .map { logs ->
                    logs
                        .asReversed() // DAO DESC -> đảo lại cũ đến mới cho chart
                        .map { it.toSensorData() }
                }
                .distinctUntilChangedBy { logs ->
                    logs.joinToString("|") {
                        "${it.timestamp}:${it.soilMoisture}"
                    }
                }
                .collect { sensorLogs ->
                    _uiState.update { current ->
                        current.copy(recentLogs = sensorLogs)
                    }
                }
        }
    }

    private suspend fun saveLogIfNeeded(sensorData: SensorData) {
        val now = System.currentTimeMillis()
        val lastSoil = lastSavedSoilMoisture

        val isTimePassed = now - lastSavedTime >= saveIntervalMs
        val isSoilChanged = lastSoil == null ||
                abs(sensorData.soilMoisture - lastSoil) >= soilDeltaLimit

        if (!isTimePassed && !isSoilChanged) return

        lastSavedTime = now
        lastSavedSoilMoisture = sensorData.soilMoisture

        val aiResult = smartFarmAi.analyze(sensorData)
        val log = createSensorLogEntity(sensorData, aiResult)

        withContext(Dispatchers.IO) {
            sensorLogDao.insert(log)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(context.applicationContext)

                    return HomeViewModel(
                        firebaseRepository = FirebaseRepository(),
                        sensorLogDao = db.sensorLogDao()
                    ) as T
                }
            }
        }
    }
}