package com.example.project2.ui.control

import com.example.project2.domain.model.ControlState

data class ControlUiState(
    val controlState: ControlState = ControlState(),
    val errorMessage: String? = null
)