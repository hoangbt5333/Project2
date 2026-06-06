package com.example.project2.domain.model

data class ControlState(
    val autoMode: Boolean = true,
    val pump: Boolean = false,
    val fan: Boolean = false,
    val soilThreshold: Int = 40,
    val tempThreshold: Double = 35.0,
    val pumpOn: Boolean = false,
    val fanOn: Boolean = false,
    val mode: String = "AUTO"
)