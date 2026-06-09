package com.example.project2.domain.ai

data class CropProfile(
    val name: String,

    val phMin: Double,
    val phMax: Double,

    val soilMoistureMin: Int,
    val soilMoistureMax: Int,

    val tempMin: Double,
    val tempMax: Double,

    val humidityMin: Double,
    val humidityMax: Double,

    val minN: Int,
    val minP: Int,
    val minK: Int,

    val note: String
) {
    companion object {
        val VEGETABLE = CropProfile(
            name = "Rau cải / Rau ăn lá",
            phMin = 6.0,
            phMax = 7.0,
            soilMoistureMin = 55,
            soilMoistureMax = 75,
            tempMin = 18.0,
            tempMax = 30.0,
            humidityMin = 50.0,
            humidityMax = 85.0,
            minN = 90,
            minP = 60,
            minK = 70,
            note = "Phù hợp khi đất đủ ẩm, pH gần trung tính và dinh dưỡng từ trung bình trở lên."
        )

        val TOMATO = CropProfile(
            name = "Cà chua",
            phMin = 6.0,
            phMax = 6.8,
            soilMoistureMin = 60,
            soilMoistureMax = 80,
            tempMin = 20.0,
            tempMax = 30.0,
            humidityMin = 55.0,
            humidityMax = 80.0,
            minN = 100,
            minP = 70,
            minK = 100,
            note = "Cần đất tơi, thoát nước tốt và Kali tương đối cao."
        )

        val CHILI = CropProfile(
            name = "Ớt",
            phMin = 6.0,
            phMax = 7.0,
            soilMoistureMin = 50,
            soilMoistureMax = 75,
            tempMin = 22.0,
            tempMax = 32.0,
            humidityMin = 45.0,
            humidityMax = 80.0,
            minN = 80,
            minP = 60,
            minK = 90,
            note = "Hợp với đất hơi khô vừa phải, không úng, cần Kali tốt."
        )

        val CUCUMBER = CropProfile(
            name = "Dưa leo",
            phMin = 6.0,
            phMax = 7.0,
            soilMoistureMin = 65,
            soilMoistureMax = 85,
            tempMin = 22.0,
            tempMax = 32.0,
            humidityMin = 60.0,
            humidityMax = 90.0,
            minN = 110,
            minP = 75,
            minK = 100,
            note = "Cần nhiều nước và dinh dưỡng, không phù hợp nếu đất nghèo NPK."
        )

        val BEAN = CropProfile(
            name = "Đậu",
            phMin = 6.0,
            phMax = 7.5,
            soilMoistureMin = 45,
            soilMoistureMax = 70,
            tempMin = 18.0,
            tempMax = 30.0,
            humidityMin = 45.0,
            humidityMax = 80.0,
            minN = 55,
            minP = 55,
            minK = 60,
            note = "Nhu cầu đạm không quá cao, phù hợp hơn khi đất ở mức trung bình."
        )

        val SWEET_POTATO = CropProfile(
            name = "Khoai lang",
            phMin = 5.5,
            phMax = 6.8,
            soilMoistureMin = 35,
            soilMoistureMax = 65,
            tempMin = 22.0,
            tempMax = 35.0,
            humidityMin = 40.0,
            humidityMax = 80.0,
            minN = 45,
            minP = 40,
            minK = 60,
            note = "Chịu đất nghèo hơn nhiều loại rau, nhưng vẫn cần cải tạo nếu NPK quá thấp."
        )

        val WATER_SPINACH = CropProfile(
            name = "Rau muống",
            phMin = 5.5,
            phMax = 7.0,
            soilMoistureMin = 70,
            soilMoistureMax = 95,
            tempMin = 24.0,
            tempMax = 35.0,
            humidityMin = 60.0,
            humidityMax = 95.0,
            minN = 80,
            minP = 50,
            minK = 60,
            note = "Ưa ẩm, phù hợp khi đất/nước đủ ẩm và nhiệt độ cao vừa phải."
        )

        val ALL = listOf(
            VEGETABLE, TOMATO, CHILI, CUCUMBER, BEAN, SWEET_POTATO, WATER_SPINACH
        )
    }
}