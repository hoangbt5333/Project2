package com.example.project2

class RuleBasedAiImpl : AiRecommendationEngine {

    override fun analyze(soilMoist: Int, temp: Double, humid: Double, n: Int, p: Int, k: Int): AiResult {

        // ======================================================================
        // (A) BƯỚC 0 – KIỂM TRA TÍNH HỢP LỆ CỦA DỮ LIỆU CẢM BIẾN
        // Firebase trả 0.0/0 khi node chưa có hoặc ESP32 ngắt. Nếu không chặn,
        // AI sẽ báo "khô nguy hiểm + thiếu mọi chất" => báo động giả.
        // ======================================================================
        val allZero = soilMoist == 0 && n == 0 && p == 0 && k == 0 && temp == 0.0 && humid == 0.0
        val outOfRange = soilMoist < 0 || soilMoist > 100 || temp < -20 || temp > 80 || humid < 0 || humid > 100
        if (allZero || outOfRange) {
            return AiResult(
                analysisText = "• Chưa nhận được dữ liệu hợp lệ từ cảm biến. Kiểm tra kết nối ESP32 / nguồn điện / WiFi.",
                waterTitle = "Chưa có dữ liệu",
                waterSub = "Đang chờ tín hiệu cảm biến...",
                fertilizerTitle = "Chưa có dữ liệu",
                fertilizerSub = "Đang chờ tín hiệu cảm biến...",
                cropType = "--",
                soilStatusText = "Mất kết nối cảm biến",
                isWarning = true,
                soilScore = 0,
                soilScoreEvaluation = "Không xác định",
                statusN = "--", statusP = "--", statusK = "--"
            )
        }

        // --- KHỞI TẠO BỘ ĐỆM ĐẦU RA ---
        val analysisBuilder = StringBuilder()
        var waterTitle = "Độ ẩm ổn định"
        var waterSub = "Không cần tưới nước cho cây"
        var fertilizerTitle = "Dinh dưỡng ổn định"
        var fertilizerSub = "Duy trì chế độ chăm sóc hiện tại"
        var cropType = "Đang phân tích..."
        var soilStatusText = "Bình thường"
        var isWarning = false
        var score = 100

        // 1. THANG CHUẨN N - P - K (0 - 200 mg/kg)
        val statusN = when {
            n < 25 -> "Rất thiếu"; n < 50 -> "Thiếu"; n < 100 -> "Trung bình"; n < 150 -> "Tốt"; else -> "Rất giàu"
        }
        val statusP = when {
            p < 10 -> "Rất thiếu"; p < 25 -> "Thiếu"; p < 50 -> "Trung bình"; p < 100 -> "Tốt"; else -> "Rất giàu"
        }
        val statusK = when {
            k < 40 -> "Rất thiếu"; k < 80 -> "Thiếu"; k < 120 -> "Trung bình"; k < 180 -> "Tốt"; else -> "Rất giàu"
        }

        // 2. ĐỘ ẨM ĐẤT
        when {
            soilMoist < 20 -> { isWarning = true; soilStatusText = "Khô nguy hiểm!"; waterTitle = "CẤP CỨU HẠN KHÔ"; waterSub = "Bật máy bơm tưới đẫm khẩn cấp!"; analysisBuilder.append("• Đất khô cằn nghiêm trọng, đe doạ trực tiếp sự sống tế bào rễ.\n"); score -= 25 }
            soilMoist < 35 -> { soilStatusText = "Đất khô"; waterTitle = "Cần tưới nước"; waterSub = "Bật máy bơm tưới bổ sung độ ẩm"; analysisBuilder.append("• Đất thiếu ẩm, cây bắt đầu có dấu hiệu héo lá nhẹ.\n"); score -= 15 }
            soilMoist < 50 -> { soilStatusText = "Hơi khô"; waterTitle = "Tưới nhẹ"; waterSub = "Cân nhắc tưới lượng nước nhỏ"; analysisBuilder.append("• Độ ẩm dưới mức tối ưu một chút, đất hơi se bề mặt.\n"); score -= 5 }
            soilMoist <= 70 -> { soilStatusText = "Tối ưu"; waterTitle = "Không cần tưới"; waterSub = "Độ ẩm lý tưởng cho rễ phát triển"; analysisBuilder.append("• Cơ cấu độ ẩm đất hoàn hảo cho việc hấp thụ dinh dưỡng.\n") }
            soilMoist <= 85 -> { soilStatusText = "Hơi ẩm"; waterTitle = "Không cần tưới"; waterSub = "Hạn chế tưới để giữ độ thoáng khí"; analysisBuilder.append("• Đất giữ nhiều nước, cần ngưng tưới để tránh bít khí.\n") }
            else -> { isWarning = true; soilStatusText = "Úng nước!"; waterTitle = "CẢNH BÁO NGẬP ÚNG"; waterSub = "Xả nước, khơi thông rãnh thoát ngay!"; analysisBuilder.append("• Đất ngập úng nặng, rễ cây có nguy cơ thối rửa do thiếu Oxy.\n"); score -= 25 }
        }

        // 3. NHIỆT ĐỘ KẾT HỢP (B) – có trừ điểm cho ngưỡng khắc nghiệt
        when {
            temp > 40.0 -> { isWarning = true; analysisBuilder.append("• Nắng nóng cực đoan ($temp°C): cây dễ sốc nhiệt, cháy lá. Cần che chắn/phun sương.\n"); score -= 10 }
            temp > 35.0 -> { analysisBuilder.append("• Thời tiết oi bức ($temp°C) làm đất nhanh mất ẩm.\n"); score -= 5 }
            temp < 10.0 -> { isWarning = true; analysisBuilder.append("• Nhiệt độ quá thấp ($temp°C): cây nhiệt đới có thể ngừng sinh trưởng / tổn thương lạnh.\n"); score -= 10 }
            temp < 18.0 -> { analysisBuilder.append("• Trời hơi lạnh ($temp°C), cây hấp thụ dinh dưỡng chậm hơn.\n") }
        }
        if (soilMoist < 35 && temp > 35.0) { waterTitle = "KHÔ HẠN NGHIÊM TRỌNG"; waterSub = "Tưới đẫm kết hợp phun sương hạ nhiệt!"; analysisBuilder.append("• Hiệu ứng kép: nóng + đất khô làm cây chết héo nhanh.\n") }
        if (temp < 18.0 && soilMoist > 80) { analysisBuilder.append("• Nguy cơ: đất lạnh + ẩm ướt dễ sinh nấm bệnh rễ.\n") }

        // 4. ĐỘ ẨM KHÔNG KHÍ (C) – có trừ điểm
        when {
            humid < 30.0 -> { analysisBuilder.append("• Không khí rất khô ($humid%): lá thoát hơi nước dồn dập, dễ cháy mép lá.\n"); score -= 5 }
            humid < 40.0 -> { analysisBuilder.append("• Không khí khô ($humid%) thúc đẩy lá cây thoát hơi nước.\n") }
            humid > 90.0 -> { analysisBuilder.append("• Độ ẩm khí quá cao ($humid%): nguy cơ nấm lá / mốc rất lớn.\n"); score -= 5 }
            humid > 80.0 -> { analysisBuilder.append("• Độ ẩm khí cao ($humid%), giảm thoát hơi nước, tăng nguy cơ nấm lá.\n") }
        }

        // 5. Cân bằng + thừa/thiếu NPK
        val maxNpk = maxOf(n, p, k)
        val minNpk = minOf(n, p, k)
        if (maxNpk - minNpk > 80) { analysisBuilder.append("• Cảnh báo: dinh dưỡng mất cân bằng nghiêm trọng giữa các nguyên tố.\n"); score -= 10 }
        if (n > p * 2 && n > k * 2) { analysisBuilder.append("• Thừa Đạm (N): cây vống lá, thân mềm, giảm năng suất quả.\n") }
        if (k < n / 2) { analysisBuilder.append("• Thiếu Kali so với Đạm, thân cây giòn dễ gãy đổ.\n") }

        // (D) DƯ THỪA DINH DƯỞ NG (ngộ độc phân / cháy rễ do nồng độ muối cao)
        var hasExcess = false
        if (n > 180) { analysisBuilder.append("• Dư Đạm (N) quá cao: nguy cơ cháy rễ, tích nitrat. Ngừng bón đạm, tăng tưới rửa.\n"); score -= 10; hasExcess = true }
        if (p > 150) { analysisBuilder.append("• Dư Lân (P): khoá vi lượng (Zn, Fe), cây dễ vàng lá gân xanh.\n"); score -= 5; hasExcess = true }
        if (k > 180) { analysisBuilder.append("• Dư Kali (K): cản trở hấp thụ Magie & Canxi.\n"); score -= 5; hasExcess = true }

        // Khấu trừ điểm khi thiếu chất
        if (n < 50) score -= 15
        if (p < 25) score -= 15
        if (k < 80) score -= 15
        if (score < 0) score = 0
        if (score > 100) score = 100

        // 6. ĐÁNH GIÁ SOIL SCORE
        val soilScoreEvaluation = when {
            score >= 90 -> "Đất rất tốt"; score >= 75 -> "Đất tốt"; score >= 60 -> "Đất trung bình"; score >= 40 -> "Đất kém"; else -> "Đất rất kém"
        }

        // 7. ĐỀ XUẤT PHÂN BÓN
        val listGoiYPhan = ArrayList<String>()
        if (n < 50) listGoiYPhan.add("Phân Đạm/Urê")
        if (p < 25) listGoiYPhan.add("Phân Lân (Supe lân)")
        if (k < 80) listGoiYPhan.add("Phân Kali Clorua")
        when {
            listGoiYPhan.isNotEmpty() -> { fertilizerTitle = "CẦN BỔ SUNG PHÂN BÓN"; fertilizerSub = "Bón ngay: ${listGoiYPhan.joinToString(" + ")}" }
            hasExcess -> { fertilizerTitle = "NGỬNG BÓN - RỬA TRÔI"; fertilizerSub = "Dinh dưỡng đang dư thừa, tăng tưới để rửa bớt muối" }
            maxNpk - minNpk > 80 -> { fertilizerTitle = "ĐIỀU CHỈNH CÂN BẰNG"; fertilizerSub = "Hạn chế chất đang cao, bổ sung vi lượng" }
            else -> { fertilizerTitle = "Dinh dưỡng lý tưởng"; fertilizerSub = "Duy trì bón phân hữu cơ định kỳ" }
        }

        // 8. GỢI Ý CÂY TRỒNG
        cropType = when {
            n < 50 && p < 25 && k < 80 -> "Cây chịu cằn: Sắn, Khoai lang, Đậu xanh"
            (n in 50..120) && (p in 25..60) && (k in 80..120) -> "Cây ngắn ngày: Rau cải, Xà lách, Hành lá"
            n > 150 && p > 100 && k > 150 -> "Cây ăn quả lâu năm: Sầu riêng, Bưởi, Cam, Chanh"
            n > 120 && p > 60 && k > 120 -> "Cây hoa quả: Cà chua, Dưa leo, Ớt, Dâu tây"
            else -> "Cây hoa màu thông thường"
        }

        return AiResult(
            analysisText = analysisBuilder.toString().trim(),
            waterTitle = waterTitle, waterSub = waterSub,
            fertilizerTitle = fertilizerTitle, fertilizerSub = fertilizerSub,
            cropType = cropType, soilStatusText = soilStatusText,
            isWarning = isWarning, soilScore = score, soilScoreEvaluation = soilScoreEvaluation,
            statusN = statusN, statusP = statusP, statusK = statusK
        )
    }
}
