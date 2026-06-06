package com.example.project2.domain.ai

import com.example.project2.domain.model.SensorData
import kotlin.math.abs

class SmartFarmAi {

    fun analyze(
        data: SensorData,
        cropProfile: CropProfile = CropProfile.VEGETABLE
    ): AiResult {
        if (isInvalidSensorData(data)) {
            return AiResult(
                soilScore = 0,
                waterScore = 0,
                climateScore = 0,
                nutrientScore = 0,
                confidence = 20,
                decision = FarmDecision.SENSOR_ERROR,
                soilStatusText = "Dữ liệu cảm biến bất thường",
                summary = "AI phát hiện dữ liệu cảm biến có thể không hợp lệ.",
                recommendation = "Kiểm tra dây cảm biến, nguồn cấp và kết nối Firebase.",
                cropSuggestion = cropProfile.name,
                statusN = "Không xác định",
                statusP = "Không xác định",
                statusK = "Không xác định",
                isWarning = true
            )
        }

        val waterScore = calculateRangeScore(
            value = data.soilMoisture.toDouble(),
            min = cropProfile.idealSoilMin.toDouble(),
            max = cropProfile.idealSoilMax.toDouble()
        )

        val tempScore = calculateRangeScore(
            value = data.airTemperature,
            min = cropProfile.idealTempMin,
            max = cropProfile.idealTempMax
        )

        val humidityScore = calculateRangeScore(
            value = data.airHumidity,
            min = cropProfile.idealHumidityMin,
            max = cropProfile.idealHumidityMax
        )

        val climateScore = ((tempScore + humidityScore) / 2).coerceIn(0, 100)

        val nScore = calculateMinimumScore(data.npkN, cropProfile.idealNMin)
        val pScore = calculateMinimumScore(data.npkP, cropProfile.idealPMin)
        val kScore = calculateMinimumScore(data.npkK, cropProfile.idealKMin)

        val nutrientScore = ((nScore + pScore + kScore) / 3).coerceIn(0, 100)

        val soilScore = (
                waterScore * 0.4 +
                        climateScore * 0.25 +
                        nutrientScore * 0.35
                ).toInt().coerceIn(0, 100)

        val decision = decide(data, cropProfile, nutrientScore)

        val soilStatusText = when {
            soilScore >= 80 -> "Đất tốt"
            soilScore >= 60 -> "Đất ổn định"
            soilScore >= 40 -> "Đất cần theo dõi"
            else -> "Đất xấu / cần xử lý"
        }

        val recommendation = buildRecommendation(data, cropProfile, decision)

        return AiResult(
            soilScore = soilScore,
            waterScore = waterScore,
            climateScore = climateScore,
            nutrientScore = nutrientScore,
            confidence = calculateConfidence(data),
            decision = decision,
            soilStatusText = soilStatusText,
            summary = "Điểm đất $soilScore/100. Nước: $waterScore, khí hậu: $climateScore, dinh dưỡng: $nutrientScore.",
            recommendation = recommendation,
            cropSuggestion = cropProfile.name,
            statusN = nutrientStatus(data.npkN, cropProfile.idealNMin),
            statusP = nutrientStatus(data.npkP, cropProfile.idealPMin),
            statusK = nutrientStatus(data.npkK, cropProfile.idealKMin),
            isWarning = soilScore < 50 || decision != FarmDecision.NORMAL
        )
    }

    private fun isInvalidSensorData(data: SensorData): Boolean {
        return data.airTemperature < -10 ||
                data.airTemperature > 70 ||
                data.airHumidity < 0 ||
                data.airHumidity > 100 ||
                data.soilMoisture < 0 ||
                data.soilMoisture > 100 ||
                data.npkN < 0 ||
                data.npkP < 0 ||
                data.npkK < 0
    }

    private fun calculateRangeScore(value: Double, min: Double, max: Double): Int {
        return when {
            value in min..max -> 100
            value < min -> (100 - abs(min - value) * 4).toInt().coerceIn(0, 100)
            else -> (100 - abs(value - max) * 4).toInt().coerceIn(0, 100)
        }
    }

    private fun calculateMinimumScore(value: Int, idealMin: Int): Int {
        return when {
            value >= idealMin -> 100
            idealMin == 0 -> 100
            else -> ((value.toDouble() / idealMin) * 100).toInt().coerceIn(0, 100)
        }
    }

    private fun decide(
        data: SensorData,
        cropProfile: CropProfile,
        nutrientScore: Int
    ): FarmDecision {
        return when {
            data.soilMoisture < cropProfile.idealSoilMin -> FarmDecision.NEED_WATER
            data.soilMoisture > cropProfile.idealSoilMax + 10 -> FarmDecision.STOP_WATERING
            data.airTemperature > cropProfile.idealTempMax + 3 -> FarmDecision.COOLING_NEEDED
            nutrientScore < 60 -> FarmDecision.NEED_FERTILIZER
            else -> FarmDecision.NORMAL
        }
    }

    private fun buildRecommendation(
        data: SensorData,
        cropProfile: CropProfile,
        decision: FarmDecision
    ): String {
        return when (decision) {
            FarmDecision.NEED_WATER ->
                "Độ ẩm đất ${data.soilMoisture}% thấp hơn ngưỡng phù hợp cho ${cropProfile.name}. Nên bật bơm tưới."

            FarmDecision.STOP_WATERING ->
                "Độ ẩm đất đang cao. Không nên tưới thêm để tránh úng rễ."

            FarmDecision.COOLING_NEEDED ->
                "Nhiệt độ ${data.airTemperature}°C cao hơn mức phù hợp. Nên bật quạt hoặc che nắng."

            FarmDecision.NEED_FERTILIZER ->
                "Chỉ số NPK đang thấp so với profile cây. Nên bổ sung dinh dưỡng phù hợp."

            FarmDecision.SENSOR_ERROR ->
                "Dữ liệu cảm biến bất thường. Cần kiểm tra lại cảm biến trước khi ra quyết định."

            FarmDecision.NORMAL ->
                "Điều kiện hiện tại ổn định. Tiếp tục giám sát định kỳ."
        }
    }

    private fun calculateConfidence(data: SensorData): Int {
        var confidence = 100

        if (data.npkN == 0 && data.npkP == 0 && data.npkK == 0) {
            confidence -= 30
        }

        if (data.airTemperature == 0.0 && data.airHumidity == 0.0) {
            confidence -= 30
        }

        return confidence.coerceIn(0, 100)
    }

    private fun nutrientStatus(value: Int, idealMin: Int): String {
        return when {
            value >= idealMin -> "Đủ"
            value >= idealMin * 0.6 -> "Trung bình"
            else -> "Thiếu"
        }
    }
}