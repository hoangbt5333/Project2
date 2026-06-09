package com.example.project2.domain.ai

import com.example.project2.domain.model.SensorData
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SmartFarmAi {

    private data class NpkSoilPattern(
        val name: String,
        val n: Int,
        val p: Int,
        val k: Int,
        val comment: String
    )

    private val npkPatterns = listOf(
        NpkSoilPattern(
            name = "Đất cát nghèo dinh dưỡng",
            n = 25,
            p = 15,
            k = 30,
            comment = "Dễ thiếu dinh dưỡng, giữ phân kém."
        ),
        NpkSoilPattern(
            name = "Đất bạc màu / đất lâu không bón",
            n = 35,
            p = 25,
            k = 45,
            comment = "Thiếu NPK rõ, cần cải tạo dinh dưỡng."
        ),
        NpkSoilPattern(
            name = "Đất trong chậu lâu ngày",
            n = 45,
            p = 35,
            k = 50,
            comment = "Đất đã suy dinh dưỡng, cần bổ sung phân."
        ),
        NpkSoilPattern(
            name = "Đất thịt nhẹ",
            n = 75,
            p = 55,
            k = 80,
            comment = "Mức thấp đến trung bình, có thể trồng cây dễ tính."
        ),
        NpkSoilPattern(
            name = "Đất thịt trung bình",
            n = 95,
            p = 70,
            k = 100,
            comment = "Khá ổn cho nhiều cây trồng thông dụng."
        ),
        NpkSoilPattern(
            name = "Đất phù sa",
            n = 115,
            p = 85,
            k = 125,
            comment = "Dinh dưỡng tốt và tương đối cân bằng."
        ),
        NpkSoilPattern(
            name = "Đất sét",
            n = 80,
            p = 55,
            k = 140,
            comment = "Kali thường cao hơn, cần chú ý thoát nước."
        ),
        NpkSoilPattern(
            name = "Đất trồng rau đã chăm",
            n = 130,
            p = 105,
            k = 135,
            comment = "Tốt cho rau màu, dinh dưỡng khá đầy đủ."
        ),
        NpkSoilPattern(
            name = "Đất hữu cơ / đất trộn compost",
            n = 145,
            p = 120,
            k = 150,
            comment = "Dinh dưỡng cao, phù hợp cải tạo đất."
        ),
        NpkSoilPattern(
            name = "Đất mới bón phân",
            n = 170,
            p = 155,
            k = 180,
            comment = "NPK rất cao, có thể dư nếu kéo dài."
        )
    )

    fun analyze(data: SensorData): AiResult {
        if (isInvalidSensorData(data)) {
            return buildSensorErrorResult(data)
        }

        val waterScore = calculateRangeScore(
            value = data.soilMoisture.toDouble(),
            min = 45.0,
            max = 75.0,
            penaltyPerUnit = 3.5
        )

        val tempScore = calculateRangeScore(
            value = data.airTemperature,
            min = 20.0,
            max = 32.0,
            penaltyPerUnit = 4.0
        )

        val humidityScore = calculateRangeScore(
            value = data.airHumidity,
            min = 45.0,
            max = 85.0,
            penaltyPerUnit = 2.5
        )

        val climateScore = ((tempScore + humidityScore) / 2).coerceIn(0, 100)

        val phScore = calculatePhScore(data.soilPh)

        val nutrientScore = calculateNutrientScore(data.npkN, data.npkP, data.npkK)

        val nutrientPattern = detectNutrientPattern(
            n = data.npkN,
            p = data.npkP,
            k = data.npkK
        )

        val effectiveNutrientScore = applyPhPenaltyToNutrients(
            nutrientScore = nutrientScore,
            ph = data.soilPh
        )

        val soilScore = (
                waterScore * 0.25 +
                        climateScore * 0.20 +
                        effectiveNutrientScore * 0.30 +
                        phScore * 0.25
                ).roundToInt()
            .coerceIn(0, 100)

        val decision = decide(
            data = data,
            waterScore = waterScore,
            climateScore = climateScore,
            nutrientScore = nutrientScore,
            phScore = phScore
        )

        val cropSuggestions = rankCrops(data)
        val recommended = cropSuggestions.take(3)
        val notRecommended = cropSuggestions
            .filter { it.score < 60 }
            .takeLast(2)

        val phStatus = getPhStatus(data.soilPh)
        val phExplanation = getPhExplanation(data.soilPh)

        val soilStatusText = getSoilStatusText(soilScore)

        val immediateActions = buildImmediateActions(
            data = data,
            decision = decision,
            nutrientPattern = nutrientPattern,
            phScore = phScore,
            nutrientScore = nutrientScore
        )

        val insightAnalysis = buildInsightAnalysis(
            data = data,
            soilScore = soilScore,
            waterScore = waterScore,
            climateScore = climateScore,
            nutrientScore = nutrientScore,
            phScore = phScore,
            nutrientPattern = nutrientPattern,
            phStatus = phStatus
        )

        val homeSummary = buildHomeSummary(
            soilScore = soilScore,
            nutrientPattern = nutrientPattern,
            phStatus = phStatus,
            decision = decision
        )

        val homeRecommendation = buildHomeRecommendation(
            decision = decision,
            nutrientPattern = nutrientPattern,
            phScore = phScore
        )

        return AiResult(
            soilScore = soilScore,
            waterScore = waterScore,
            climateScore = climateScore,
            nutrientScore = nutrientScore,
            phScore = phScore,
            confidence = calculateConfidence(data),

            decision = decision,
            soilStatusText = soilStatusText,

            summary = homeSummary,
            recommendation = homeRecommendation,
            cropSuggestion = recommended.joinToString(", ") { "${it.name} (${it.score}%)" },

            statusN = levelText(data.npkN),
            statusP = levelText(data.npkP),
            statusK = levelText(data.npkK),

            phStatus = phStatus,
            phExplanation = phExplanation,

            nutrientPattern = nutrientPattern,

            insightTitle = buildInsightTitle(soilScore, decision),
            insightAnalysis = insightAnalysis,
            immediateActions = immediateActions,
            cropSuggestions = recommended,
            notRecommendedCrops = notRecommended,

            isWarning = soilScore < 60 || decision != FarmDecision.NORMAL
        )
    }

    private fun isInvalidSensorData(data: SensorData): Boolean {
        return data.airTemperature < -10.0 ||
                data.airTemperature > 70.0 ||
                data.airHumidity < 0.0 ||
                data.airHumidity > 100.0 ||
                data.soilMoisture < 0 ||
                data.soilMoisture > 100 ||
                data.soilPh < 0.0 ||
                data.soilPh > 14.0 ||
                data.npkN < 0 ||
                data.npkP < 0 ||
                data.npkK < 0
    }

    private fun buildSensorErrorResult(data: SensorData): AiResult {
        val pattern = NutrientPatternResult(
            name = "Không xác định",
            matchScore = 0,
            comment = "Dữ liệu cảm biến không hợp lệ.",
            explanation = "AI phát hiện một hoặc nhiều giá trị nằm ngoài miền hợp lệ.",
            action = "Kiểm tra cảm biến, dây nối, nguồn cấp và dữ liệu trên Firebase."
        )

        return AiResult(
            soilScore = 0,
            waterScore = 0,
            climateScore = 0,
            nutrientScore = 0,
            phScore = 0,
            confidence = 10,
            decision = FarmDecision.SENSOR_ERROR,
            soilStatusText = "Dữ liệu bất thường",
            summary = "Dữ liệu cảm biến không hợp lệ.",
            recommendation = "Kiểm tra lại cảm biến trước khi ra quyết định.",
            cropSuggestion = "Chưa thể gợi ý cây trồng.",
            statusN = "Không xác định",
            statusP = "Không xác định",
            statusK = "Không xác định",
            phStatus = "Không xác định",
            phExplanation = "pH hiện tại không nằm trong miền hợp lệ.",
            nutrientPattern = pattern,
            insightTitle = "Không thể phân tích do dữ liệu bất thường",
            insightAnalysis = "Một số giá trị cảm biến nằm ngoài miền hợp lệ, vì vậy AI không đưa ra khuyến nghị canh tác.",
            immediateActions = listOf(
                "Kiểm tra lại dữ liệu Firebase.",
                "Kiểm tra nguồn cấp và dây nối cảm biến.",
                "Chỉ bật thiết bị ở chế độ thủ công nếu đã kiểm tra an toàn."
            ),
            cropSuggestions = emptyList(),
            notRecommendedCrops = emptyList(),
            isWarning = true
        )
    }

    private fun calculateRangeScore(
        value: Double,
        min: Double,
        max: Double,
        penaltyPerUnit: Double
    ): Int {
        return when {
            value in min..max -> 100
            value < min -> (100 - (min - value) * penaltyPerUnit).roundToInt().coerceIn(0, 100)
            else -> (100 - (value - max) * penaltyPerUnit).roundToInt().coerceIn(0, 100)
        }
    }

    private fun calculatePhScore(ph: Double): Int {
        return when {
            ph in 6.0..7.0 -> 100
            ph < 6.0 -> (100 - (6.0 - ph) * 25).roundToInt().coerceIn(0, 100)
            else -> (100 - (ph - 7.0) * 25).roundToInt().coerceIn(0, 100)
        }
    }

    private fun calculateNutrientScore(n: Int, p: Int, k: Int): Int {
        val nScore = nutrientSingleScore(n)
        val pScore = nutrientSingleScore(p)
        val kScore = nutrientSingleScore(k)

        val average = (nScore + pScore + kScore) / 3

        val spread = maxOf(n, p, k) - minOf(n, p, k)
        val imbalancePenalty = when {
            spread > 100 -> 20
            spread > 70 -> 12
            spread > 45 -> 6
            else -> 0
        }

        return (average - imbalancePenalty).coerceIn(0, 100)
    }

    private fun nutrientSingleScore(value: Int): Int {
        return when {
            value < 30 -> 20
            value < 50 -> 35
            value < 90 -> 60
            value < 140 -> 85
            value < 180 -> 100
            else -> 80
        }
    }

    private fun applyPhPenaltyToNutrients(nutrientScore: Int, ph: Double): Int {
        val penalty = when {
            ph < 5.0 -> 25
            ph < 5.5 -> 15
            ph <= 7.5 -> 0
            ph <= 8.0 -> 12
            else -> 25
        }

        return (nutrientScore - penalty).coerceIn(0, 100)
    }

    private fun detectNutrientPattern(n: Int, p: Int, k: Int): NutrientPatternResult {
        val bestPattern = npkPatterns.minBy { pattern ->
            normalizedDistance(n, p, k, pattern.n, pattern.p, pattern.k)
        }

        val distance = normalizedDistance(n, p, k, bestPattern.n, bestPattern.p, bestPattern.k)
        val matchScore = (100 - distance * 100).roundToInt().coerceIn(0, 100)

        val levels = listOf(levelOf(n), levelOf(p), levelOf(k))
        val lowest = lowestNutrientName(n, p, k)
        val spread = maxOf(n, p, k) - minOf(n, p, k)

        val explanation = buildString {
            append("Bộ NPK hiện tại là N=$n, P=$p, K=$k. ")
            append("Mẫu gần nhất là \"${bestPattern.name}\" với độ khớp $matchScore%. ")
            append(bestPattern.comment)

            if (levels.all { it == NutrientLevel.VERY_LOW || it == NutrientLevel.LOW }) {
                append(" Cả ba chỉ số đều thấp, cho thấy đất nghèo dinh dưỡng tổng thể.")
            }

            if (spread > 70) {
                append(" Các chỉ số NPK lệch nhau khá mạnh, đất có dấu hiệu mất cân bằng dinh dưỡng.")
            }

            append(" Chỉ số yếu nhất hiện tại là $lowest.")
        }

        val action = when {
            levels.all { it == NutrientLevel.VERY_LOW || it == NutrientLevel.LOW } ->
                "Ưu tiên cải tạo đất bằng phân hữu cơ hoai mục hoặc NPK cân đối. Không nên chỉ bổ sung một chất riêng lẻ."

            spread > 70 ->
                "Cần bổ sung theo chất đang thiếu, tránh tiếp tục bón chất đang cao."

            minOf(n, p, k) >= 150 ->
                "Không nên bón thêm phân ngay. Tiếp tục theo dõi để tránh dư dinh dưỡng."

            else ->
                "Có thể duy trì chăm sóc hiện tại, bổ sung nhẹ nếu cây có dấu hiệu sinh trưởng chậm."
        }

        return NutrientPatternResult(
            name = bestPattern.name,
            matchScore = matchScore,
            comment = bestPattern.comment,
            explanation = explanation,
            action = action
        )
    }

    private fun normalizedDistance(
        n1: Int,
        p1: Int,
        k1: Int,
        n2: Int,
        p2: Int,
        k2: Int
    ): Double {
        val dn = (n1 - n2) / 200.0
        val dp = (p1 - p2) / 200.0
        val dk = (k1 - k2) / 200.0

        return sqrt(dn * dn + dp * dp + dk * dk).coerceIn(0.0, 1.0)
    }

    private fun levelOf(value: Int): NutrientLevel {
        return when {
            value <= 50 -> NutrientLevel.VERY_LOW
            value <= 90 -> NutrientLevel.LOW
            value <= 140 -> NutrientLevel.MEDIUM
            value <= 180 -> NutrientLevel.HIGH
            else -> NutrientLevel.VERY_HIGH
        }
    }

    private fun levelText(value: Int): String {
        return when (levelOf(value)) {
            NutrientLevel.VERY_LOW -> "Rất thấp"
            NutrientLevel.LOW -> "Thấp"
            NutrientLevel.MEDIUM -> "Trung bình"
            NutrientLevel.HIGH -> "Cao"
            NutrientLevel.VERY_HIGH -> "Rất cao"
        }
    }

    private fun lowestNutrientName(n: Int, p: Int, k: Int): String {
        return when (minOf(n, p, k)) {
            n -> "Đạm N"
            p -> "Lân P"
            else -> "Kali K"
        }
    }

    private fun getPhStatus(ph: Double): String {
        return when {
            ph < 5.5 -> "Đất chua"
            ph < 6.0 -> "Đất hơi chua"
            ph <= 7.0 -> "pH phù hợp"
            ph <= 7.5 -> "Đất hơi kiềm"
            else -> "Đất kiềm cao"
        }
    }

    private fun getPhExplanation(ph: Double): String {
        return when {
            ph < 5.5 ->
                "pH=$ph cho thấy đất đang chua. Một số cây có thể khó hấp thụ dinh dưỡng tốt, đặc biệt khi NPK cũng thấp."

            ph < 6.0 ->
                "pH=$ph hơi chua. Một số cây vẫn trồng được, nhưng chưa tối ưu cho đa số rau màu."

            ph <= 7.0 ->
                "pH=$ph nằm trong vùng thuận lợi cho nhiều loại rau và cây trồng phổ biến."

            ph <= 7.5 ->
                "pH=$ph hơi kiềm. Cần theo dõi nếu cây vàng lá hoặc phát triển chậm."

            else ->
                "pH=$ph cao, đất có xu hướng kiềm mạnh. Một số dinh dưỡng có thể khó được cây hấp thụ."
        }
    }

    private fun decide(
        data: SensorData,
        waterScore: Int,
        climateScore: Int,
        nutrientScore: Int,
        phScore: Int
    ): FarmDecision {
        return when {
            data.soilMoisture < 30 -> FarmDecision.NEED_WATER
            data.soilMoisture > 85 -> FarmDecision.STOP_WATERING
            data.airTemperature > 35.0 -> FarmDecision.COOLING_NEEDED
            phScore < 55 -> FarmDecision.PH_PROBLEM
            nutrientScore < 60 -> FarmDecision.NEED_FERTILIZER
            waterScore >= 70 && climateScore >= 70 && nutrientScore >= 70 && phScore >= 70 ->
                FarmDecision.NORMAL

            else -> FarmDecision.NORMAL
        }
    }

    private fun rankCrops(data: SensorData): List<CropSuggestion> {
        return CropProfile.ALL.map { crop ->
            val phScore = calculateRangeScore(
                value = data.soilPh,
                min = crop.phMin,
                max = crop.phMax,
                penaltyPerUnit = 30.0
            )

            val waterScore = calculateRangeScore(
                value = data.soilMoisture.toDouble(),
                min = crop.soilMoistureMin.toDouble(),
                max = crop.soilMoistureMax.toDouble(),
                penaltyPerUnit = 3.0
            )

            val tempScore = calculateRangeScore(
                value = data.airTemperature,
                min = crop.tempMin,
                max = crop.tempMax,
                penaltyPerUnit = 4.0
            )

            val humidityScore = calculateRangeScore(
                value = data.airHumidity,
                min = crop.humidityMin,
                max = crop.humidityMax,
                penaltyPerUnit = 2.0
            )

            val climateScore = (tempScore + humidityScore) / 2

            val nutrientScore = cropNutrientScore(
                data = data,
                crop = crop
            )

            val finalScore = (
                    phScore * 0.35 +
                            waterScore * 0.25 +
                            climateScore * 0.20 +
                            nutrientScore * 0.20
                    ).roundToInt()
                .coerceIn(0, 100)

            val reason = buildCropReason(
                crop = crop,
                phScore = phScore,
                waterScore = waterScore,
                climateScore = climateScore,
                nutrientScore = nutrientScore
            )

            CropSuggestion(
                name = crop.name,
                score = finalScore,
                reason = reason
            )
        }.sortedByDescending { it.score }
    }

    private fun cropNutrientScore(data: SensorData, crop: CropProfile): Int {
        val nScore = minOf(100, (data.npkN.toDouble() / crop.minN * 100).roundToInt())
        val pScore = minOf(100, (data.npkP.toDouble() / crop.minP * 100).roundToInt())
        val kScore = minOf(100, (data.npkK.toDouble() / crop.minK * 100).roundToInt())

        return ((nScore + pScore + kScore) / 3).coerceIn(0, 100)
    }

    private fun buildCropReason(
        crop: CropProfile,
        phScore: Int,
        waterScore: Int,
        climateScore: Int,
        nutrientScore: Int
    ): String {
        val weakPoints = mutableListOf<String>()

        if (phScore < 60) weakPoints.add("pH chưa phù hợp")
        if (waterScore < 60) weakPoints.add("độ ẩm chưa phù hợp")
        if (climateScore < 60) weakPoints.add("khí hậu chưa tối ưu")
        if (nutrientScore < 60) weakPoints.add("NPK còn thấp")

        return if (weakPoints.isEmpty()) {
            "${crop.note} Các điều kiện hiện tại tương đối phù hợp."
        } else {
            "${crop.note} Cần chú ý: ${weakPoints.joinToString(", ")}."
        }
    }

    private fun getSoilStatusText(score: Int): String {
        return when {
            score >= 80 -> "Đất tốt"
            score >= 65 -> "Đất khá ổn"
            score >= 50 -> "Đất cần cải thiện"
            else -> "Đất xấu / nghèo dinh dưỡng"
        }
    }

    private fun buildInsightTitle(
        soilScore: Int,
        decision: FarmDecision
    ): String {
        return when {
            decision == FarmDecision.SENSOR_ERROR -> "Dữ liệu chưa đủ tin cậy"
            soilScore >= 80 -> "Đất đang ở trạng thái tốt"
            soilScore >= 65 -> "Đất khá ổn, có thể duy trì chăm sóc"
            soilScore >= 50 -> "Đất cần cải thiện trước khi trồng cây khó tính"
            else -> "Đất đang yếu, cần xử lý sớm"
        }
    }

    private fun buildHomeSummary(
        soilScore: Int,
        nutrientPattern: NutrientPatternResult,
        phStatus: String,
        decision: FarmDecision
    ): String {
        val actionText = when (decision) {
            FarmDecision.NEED_WATER -> "Cần tưới nước."
            FarmDecision.STOP_WATERING -> "Không nên tưới thêm."
            FarmDecision.COOLING_NEEDED -> "Cần làm mát hoặc che nắng."
            FarmDecision.NEED_FERTILIZER -> "Cần bổ sung dinh dưỡng."
            FarmDecision.PH_PROBLEM -> "Cần chú ý pH đất."
            FarmDecision.SENSOR_ERROR -> "Cần kiểm tra cảm biến."
            FarmDecision.NORMAL -> "Có thể tiếp tục theo dõi."
        }

        return "Điểm đất $soilScore/100. ${nutrientPattern.name}. $phStatus. $actionText"
    }

    private fun buildHomeRecommendation(
        decision: FarmDecision,
        nutrientPattern: NutrientPatternResult,
        phScore: Int
    ): String {
        return when (decision) {
            FarmDecision.NEED_WATER ->
                "Đất đang thiếu ẩm. Nên bật tưới nhẹ và theo dõi lại độ ẩm."

            FarmDecision.STOP_WATERING ->
                "Độ ẩm đang cao. Không nên tưới thêm để tránh úng."

            FarmDecision.COOLING_NEEDED ->
                "Nhiệt độ cao. Nên bật quạt, che nắng hoặc phun sương nhẹ."

            FarmDecision.NEED_FERTILIZER ->
                nutrientPattern.action

            FarmDecision.PH_PROBLEM ->
                if (phScore < 50) {
                    "pH lệch nhiều so với vùng phù hợp. Cần điều chỉnh pH trước khi trồng cây nhạy cảm."
                } else {
                    "pH hơi lệch, nên theo dõi thêm cùng với tình trạng NPK."
                }

            FarmDecision.SENSOR_ERROR ->
                "Dữ liệu bất thường. Kiểm tra cảm biến và Firebase."

            FarmDecision.NORMAL ->
                "Điều kiện hiện tại tương đối ổn. Tiếp tục theo dõi định kỳ."
        }
    }

    private fun buildImmediateActions(
        data: SensorData,
        decision: FarmDecision,
        nutrientPattern: NutrientPatternResult,
        phScore: Int,
        nutrientScore: Int
    ): List<String> {
        val actions = mutableListOf<String>()

        when (decision) {
            FarmDecision.NEED_WATER ->
                actions.add("Bật tưới nhẹ vì độ ẩm đất hiện tại chỉ ${data.soilMoisture}%.")

            FarmDecision.STOP_WATERING ->
                actions.add("Tạm dừng tưới vì độ ẩm đất đang cao, tránh úng rễ.")

            FarmDecision.COOLING_NEEDED ->
                actions.add("Bật quạt hoặc che nắng vì nhiệt độ hiện tại ${data.airTemperature}°C khá cao.")

            FarmDecision.NEED_FERTILIZER ->
                actions.add(nutrientPattern.action)

            FarmDecision.PH_PROBLEM ->
                actions.add("Kiểm tra lại pH đất. Nếu pH lệch kéo dài, cần cải tạo trước khi trồng cây nhạy cảm.")

            FarmDecision.SENSOR_ERROR ->
                actions.add("Kiểm tra cảm biến và dữ liệu Firebase trước khi điều khiển tự động.")

            FarmDecision.NORMAL ->
                actions.add("Tiếp tục giám sát, chưa cần can thiệp mạnh.")
        }

        if (nutrientScore < 60) {
            actions.add("Không nên trồng ngay cây cần nhiều dinh dưỡng như cà chua hoặc dưa leo.")
        }

        if (phScore < 60) {
            actions.add("Ưu tiên chọn cây chịu được pH hiện tại hoặc điều chỉnh pH trước khi trồng.")
        }

        if (actions.size < 3) {
            actions.add("Theo dõi lại sau vài chu kỳ tưới để đánh giá xu hướng độ ẩm.")
        }

        return actions.distinct()
    }

    private fun buildInsightAnalysis(
        data: SensorData,
        soilScore: Int,
        waterScore: Int,
        climateScore: Int,
        nutrientScore: Int,
        phScore: Int,
        nutrientPattern: NutrientPatternResult,
        phStatus: String
    ): String {
        return buildString {
            append("AI đánh giá điểm đất hiện tại là $soilScore/100. ")
            append("Điểm nước đạt $waterScore/100 với độ ẩm đất ${data.soilMoisture}%. ")
            append("Điểm khí hậu đạt $climateScore/100 dựa trên nhiệt độ ${data.airTemperature}°C và độ ẩm không khí ${data.airHumidity}%. ")
            append("Điểm dinh dưỡng đạt $nutrientScore/100. ")
            append(nutrientPattern.explanation)
            append(" Về pH, đất được phân loại là \"$phStatus\" với điểm pH $phScore/100. ")
            append(getPhExplanation(data.soilPh))
        }
    }

    private fun calculateConfidence(data: SensorData): Int {
        var confidence = 100

        if (data.npkN == 0 && data.npkP == 0 && data.npkK == 0) {
            confidence -= 35
        }

        if (data.soilPh == 6.5) {
            confidence -= 10
        }

        if (data.airTemperature == 0.0 && data.airHumidity == 0.0) {
            confidence -= 30
        }

        return confidence.coerceIn(0, 100)
    }
}