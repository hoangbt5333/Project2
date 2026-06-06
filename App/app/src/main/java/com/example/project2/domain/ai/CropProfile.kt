package com.example.project2.domain.ai

data class CropProfile(
    val name: String,
    val idealSoilMin: Int,
    val idealSoilMax: Int,
    val idealTempMin: Double,
    val idealTempMax: Double,
    val idealHumidityMin: Double,
    val idealHumidityMax: Double,
    val idealNMin: Int,
    val idealPMin: Int,
    val idealKMin: Int
) {
    companion object {
        val VEGETABLE = CropProfile(
            name = "Rau màu",
            idealSoilMin = 55,
            idealSoilMax = 75,
            idealTempMin = 20.0,
            idealTempMax = 32.0,
            idealHumidityMin = 50.0,
            idealHumidityMax = 85.0,
            idealNMin = 80,
            idealPMin = 50,
            idealKMin = 60
        )

        val TOMATO = CropProfile(
            name = "Cà chua",
            idealSoilMin = 60,
            idealSoilMax = 80,
            idealTempMin = 21.0,
            idealTempMax = 30.0,
            idealHumidityMin = 55.0,
            idealHumidityMax = 80.0,
            idealNMin = 90,
            idealPMin = 60,
            idealKMin = 80
        )

        val DROUGHT_TOLERANT = CropProfile(
            name = "Cây chịu hạn",
            idealSoilMin = 35,
            idealSoilMax = 60,
            idealTempMin = 22.0,
            idealTempMax = 36.0,
            idealHumidityMin = 35.0,
            idealHumidityMax = 75.0,
            idealNMin = 50,
            idealPMin = 40,
            idealKMin = 50
        )
    }
}