package com.example.project2

import kotlin.math.abs

class RuleBasedAiImpl : AiRecommendationEngine {

    override fun analyze(soilMoist: Int, temp: Double, humid: Double, n: Int, p: Int, k: Int): AiResult {

        // --- KHỞI TẠO BỘ ĐỆM ĐẦU RA ---
        val analysisBuilder = StringBuilder()
        var waterTitle = "Độ ẩm ổn định"
        var waterSub = "Không cần tưới nước cho cây"
        var fertilizerTitle = "Dinh dưỡng ổn định"
        var fertilizerSub = "Duy trì chế độ chăm sóc hiện tại"
        var cropType = "Đang phân tích..."
        var soilStatusText = "Bình thường"
        var isWarning = false
        var score = 100 // Điểm sức khỏe đất ban đầu là tối đa 100

        // ======================================================================
        // CẤU TRÚC KIẾN TRÚC SUY LUẬN AI 12 BƯỚC HOÀN CHỈNH
        // ======================================================================

        // 1. PHÂN TÍCH ĐÁNH GIÁ THANG CHUẨN N - P - K (0 - 200 mg/kg)
        val statusN = when {
            n < 25 -> "Rất thiếu"
            n < 50 -> "Thiếu"
            n < 100 -> "Trung bình"
            n < 150 -> "Tốt"
            else -> "Rất giàu"
        }

        val statusP = when {
            p < 10 -> "Rất thiếu"
            p < 25 -> "Thiếu"
            p < 50 -> "Trung bình"
            p < 100 -> "Tốt"
            else -> "Rất giàu"
        }

        val statusK = when {
            k < 40 -> "Rất thiếu"
            k < 80 -> "Thiếu"
            k < 120 -> "Trung bình"
            k < 180 -> "Tốt"
            else -> "Rất giàu"
        }

        // 2. PHÂN TÍCH ĐỘ ẨM ĐẤT THÔNG MINH MỞ RỘNG
        when {
            soilMoist < 20 -> {
                isWarning = true
                soilStatusText = "Khô nguy hiểm!"
                waterTitle = "CẤP CỨU HẠN KHÔ"
                waterSub = "Bật máy bơm tưới đẫm khẩn cấp!"
                analysisBuilder.append("• Đất đang ở trạng thái khô cằn nghiêm trọng, đe dọa trực tiếp đến sự sống tế bào rễ.\n")
                score -= 20
            }
            soilMoist < 35 -> {
                soilStatusText = "Đất khô"
                waterTitle = "Cần tưới nước"
                waterSub = "Bật máy bơm tưới bổ sung độ ẩm"
                analysisBuilder.append("• Đất thiếu ẩm, cây bắt đầu có dấu hiệu héo lá nhẹ.\n")
                score -= 20
            }
            soilMoist < 50 -> {
                soilStatusText = "Hơi khô"
                waterTitle = "Tưới nhẹ"
                waterSub = "Cân nhắc tưới lượng nước nhỏ"
                analysisBuilder.append("• Độ ẩm dưới mức tối ưu một chút, đất hơi se bề mặt.\n")
            }
            soilMoist <= 70 -> {
                soilStatusText = "Tối ưu"
                waterTitle = "Không cần tưới"
                waterSub = "Độ ẩm lý tưởng cho rễ phát triển"
                analysisBuilder.append("• Cơ cấu độ ẩm đất hoàn hảo cho việc hấp thụ dinh dưỡng.\n")
            }
            soilMoist <= 85 -> {
                soilStatusText = "Hơi ẩm"
                waterTitle = "Không cần tưới"
                waterSub = "Hạn chế tưới để giữ độ thoáng khí"
                analysisBuilder.append("• Đất giữ nhiều nước, cần ngưng tưới để tránh bít khí.\n")
            }
            else -> {
                isWarning = true
                soilStatusText = "Úng nước!"
                waterTitle = "CẢNH BÁO NGẬP ÚNG"
                waterSub = "Xả nước, khơi thông rãnh thoát ngay!"
                analysisBuilder.append("• Đất ngập úng nặng, rễ cây có nguy cơ thối rữa do thiếu Oxy.\n")
                score -= 25
            }
        }

        // 3. KẾT HỢP BIẾN NHIỆT ĐỘ VÀO NGỮ CẢNH
        if (soilMoist < 35 && temp > 35.0) {
            waterTitle = "KHÔ HẠN NGHIÊM TRỌNG"
            waterSub = "Tưới đẫm kết hợp phun sương hạ nhiệt!"
            analysisBuilder.append("• Hiệu ứng kép: Thời tiết oi bức ($temp°C) cộng với đất khô làm tăng tốc độ chết héo của cây.\n")
        }
        if (soilMoist > 50 && temp > 35.0) {
            analysisBuilder.append("• Lưu ý: Khí hậu nóng gắt ($temp°C) thúc đẩy quá trình thoát hơi nước mạnh, đất sẽ nhanh mất ẩm.\n")
        }
        if (temp < 18.0 && soilMoist > 80) {
            analysisBuilder.append("• Nguy cơ: Tổ hợp đất quá lạnh ($temp°C) và ẩm ướt dễ kích thích nấm bệnh rễ phát triển.\n")
        }

        // 4. KẾT HỢP BIẾN ĐỘ ẨM KHÔNG KHÍ VÀO NGỮ CẢNH
        if (humid < 40.0) {
            analysisBuilder.append("• Không khí khô ($humid%) thúc đẩy lá cây thoát hơi nước dồn dập.\n")
        }
        if (humid > 80.0) {
            analysisBuilder.append("• Độ ẩm khí quá cao ($humid%), làm giảm khả năng thoát hơi nước tự nhiên, tăng nguy cơ nấm lá.\n")
        }

        // 5. LUẬT KIỂM TRA CÂN BẰNG VÀ THỪA/THIẾU CHẤT NPK
        val maxNpk = maxOf(n, p, k)
        val minNpk = minOf(n, p, k)

        if (maxNpk - minNpk > 80) {
            analysisBuilder.append("• Cảnh báo: Dinh dưỡng mất cân bằng nghiêm trọng giữa các nguyên tố.\n")
            score -= 10
        }
        if (n > p * 2 && n > k * 2) {
            analysisBuilder.append("• Hiện tượng: Thừa Đạm (N). Cây dễ vống lá quá mức, thân mềm rũ, giảm năng suất quả.\n")
        }
        if (k < n / 2) {
            analysisBuilder.append("• Hiện tượng: Thiếu Kali nghiêm trọng so với tỷ lệ Đạm, khiến thân cây giòn dễ gãy đổ.\n")
        }

        // Khấu trừ điểm Soil Score dựa vào ngưỡng thô thiếu chất
        if (n < 50) score -= 15
        if (p < 25) score -= 15
        if (k < 80) score -= 15

        // Tránh điểm bị âm trong kịch bản đất siêu xấu
        if (score < 0) score = 0

        // 6. ĐÁNH GIÁ CHẤT LƯỢNG ĐẤT THEO THANG ĐIỂM SOIL SCORE
        val soilScoreEvaluation = when {
            score >= 90 -> "Đất rất tốt"
            score >= 75 -> "Đất tốt"
            score >= 60 -> "Đất trung bình"
            score >= 40 -> "Đất kém"
            else -> "Đất rất kém"
        }

        // 7. ĐỀ XUẤT PHÂN BÓN (FERTILIZER STEPS)
        val listGoiYPhan = ArrayList<String>()
        if (n < 50) listGoiYPhan.add("Phân Đạm/Urê")
        if (p < 25) listGoiYPhan.add("Phân Lân (Supe lân)")
        if (k < 80) listGoiYPhan.add("Phân Kali Clorua")

        if (listGoiYPhan.isNotEmpty()) {
            fertilizerTitle = "CẦN BỔ SUNG PHÂN BÓN"
            fertilizerSub = "Bón ngay: ${listGoiYPhan.joinToString(" + ")}"
        } else if (maxNpk - minNpk > 80) {
            fertilizerTitle = "ĐIỀU CHỈNH CÂN BẰNG"
            fertilizerSub = "Hạn chế chất đang cao, bổ sung vi lượng"
        } else {
            fertilizerTitle = "Dinh dưỡng lý tưởng"
            fertilizerSub = "Duy trì bón phân hữu cơ định kỳ"
        }

        // 8. ĐỀ XUẤT LUẬT SUY LUẬN CÂY TRỒNG PHÙ HỢP
        cropType = when {
            n < 50 && p < 25 && k < 80 -> "Cây chịu cằn: Sắn, Khoai lang, Đậu xanh"
            (n in 50..120) && (p in 25..60) && (k in 80..120) -> "Cây ngắn ngày: Rau cải, Xà lách, Hành lá"
            n > 150 && p > 100 && k > 150 -> "Cây ăn quả lâu năm: Sầu riêng, Bưởi, Cam, Chanh"
            n > 120 && p > 60 && k > 120 -> "Cây hoa quả: Cà chua, Dưa leo, Ớt, Dâu tây"
            else -> "Cây hoa màu thông thường" // Kịch bản lai giữa các ngưỡng
        }

        // Trả về cấu trúc kết quả đóng gói hoàn chỉnh cho UI hiển thị
        return AiResult(
            analysisText = analysisBuilder.toString().trim(),
            waterTitle = waterTitle,
            waterSub = waterSub,
            fertilizerTitle = fertilizerTitle,
            fertilizerSub = fertilizerSub,
            cropType = cropType,
            soilStatusText = soilStatusText,
            isWarning = isWarning,
            soilScore = score,
            soilScoreEvaluation = soilScoreEvaluation,
            statusN = statusN,
            statusP = statusP,
            statusK = statusK
        )
    }
}