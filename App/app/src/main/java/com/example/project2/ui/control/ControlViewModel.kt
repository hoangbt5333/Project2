package com.example.project2.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project2.data.firebase.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ControlViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    init {
        observeControlState()
    }

    private fun observeControlState() {
        viewModelScope.launch {
            firebaseRepository.observeControlState()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Không đọc được trạng thái điều khiển"
                    )
                }
                .collect { controlState ->
                    _uiState.value = ControlUiState(controlState = controlState)
                }
        }
    }

    fun setAutoMode(enabled: Boolean) {
        firebaseRepository.setAutoMode(enabled)
    }

    fun setPump(enabled: Boolean) {
        firebaseRepository.setPump(enabled)
    }

    fun setFan(enabled: Boolean) {
        firebaseRepository.setFan(enabled)
    }

    fun setSoilThreshold(value: Int) {
        firebaseRepository.setSoilThreshold(value)
    }

    fun setTempThreshold(value: Double) {
        firebaseRepository.setTempThreshold(value)
    }
}