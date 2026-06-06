package com.example.project2

/**
 * Sinh log cảnh báo từ kết quả AI và ghi lịch sử tưới.
 * - Chống spam bằng throttle theo từng loại (mặc định 10 phút mới ghi lại 1 loại).
 * - Gọi từ HomeFragment (cảnh báo) và ControlFragment/Service (tưới).
 */
object HistoryLogger {

    private const val THROTTLE_MS = 10 * 60 * 1000L // 10 phút

    /** Ghi 1 cảnh báo nếu loại đó chưa được ghi trong khoảng throttle. */
    private suspend fun maybeLog(
        dao: AlertDao, now: Long, type: String, severity: String, title: String, rec: String
    ) {
        val last = dao.getLastAlertOfType(type)
        if (last == null || now - last.timestamp > THROTTLE_MS) {
            dao.insert(
                AlertEntity(timestamp = now, type = type, severity = severity, title = title, recommendation = rec)
            )
        }
    }

    /** Đánh giá toàn bộ trạng thái hiện tại và ghi các cảnh báo phù hợp. */
    suspend fun evaluateAndLog(
        dao: AlertDao, ai: AiResult,
        soilMoist: Int, temp: Double, n: Int, p: Int, k: Int, now: Long
    ) {
        // Bỏ qua khi cảm biến mất kết nối (AI đã đánh dấu trạng thái này)
        if (ai.soilStatusText.contains("Mất kết nối", true)) return

        if (soilMoist < 35) maybeLog(dao, now, AlertTypes.DRY, AlertTypes.SEV_WARNING, "Đất quá khô", "AI đề xuất tưới nước")
        if (soilMoist > 85) maybeLog(dao, now, AlertTypes.FLOOD, AlertTypes.SEV_WARNING, "Đất ngập úng", "AI đề xuất khơi thông thoát nước")
        if (n < 50) maybeLog(dao, now, AlertTypes.LACK_N, AlertTypes.SEV_WARNING, "Thiếu Nitrogen (N)", "AI đề xuất bổ sung phân Đạm")
        if (p < 25) maybeLog(dao, now, AlertTypes.LACK_P, AlertTypes.SEV_WARNING, "Thiếu Phosphorus (P)", "AI đề xuất bổ sung phân Lân")
        if (k < 80) maybeLog(dao, now, AlertTypes.LACK_K, AlertTypes.SEV_WARNING, "Thiếu Kali (K)", "AI đề xuất bổ sung phân Kali")
        if (temp > 35.0) maybeLog(dao, now, AlertTypes.HIGH_TEMP, AlertTypes.SEV_WARNING, "Nhiệt độ quá cao", "AI đề xuất che chắn / phun sương hạ nhiệt")
        if (temp < 10.0) maybeLog(dao, now, AlertTypes.LOW_TEMP, AlertTypes.SEV_WARNING, "Nhiệt độ quá thấp", "AI đề xuất giữ ấm gốc cây")

        // Sự kiện PHỤC HỒI: khi không còn cảnh báo và trước đó từng có cảnh báo gần đây
        if (!ai.isWarning && soilMoist in 35..85) {
            val lastWarning = dao.getLastWarning()
            val lastRecovered = dao.getLastAlertOfType(AlertTypes.RECOVERED)
            val recoveredAfterWarning = lastWarning != null &&
                    (lastRecovered == null || lastRecovered.timestamp < lastWarning.timestamp)
            if (recoveredAfterWarning) {
                dao.insert(
                    AlertEntity(
                        timestamp = now, type = AlertTypes.RECOVERED, severity = AlertTypes.SEV_OK,
                        title = "Độ ẩm đã trở về bình thường", recommendation = "Hệ thống ổn định, tiếp tục theo dõi"
                    )
                )
            }
        }
    }

    /** Ghi 1 lần tưới (gọi khi tắt bơm: durationSeconds = thời gian bơm vừa chạy). */
    suspend fun logWatering(dao: WateringDao, mode: String, durationSeconds: Int, now: Long) {
        if (durationSeconds <= 0) return
        dao.insert(WateringEntity(timestamp = now, mode = mode, durationSeconds = durationSeconds))
    }
}
