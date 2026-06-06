package com.example.project2

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mỗi dòng = 1 sự kiện cảnh báo / phục hồi do AI sinh ra (dùng cho timeline & thống kê). */
@Entity(tableName = "lich_su_canh_bao", indices = [Index("timestamp"), Index("type")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val type: String,            // DRY, FLOOD, LACK_N, LACK_P, LACK_K, HIGH_TEMP, LOW_TEMP, RECOVERED
    val severity: String,        // WARNING | OK | INFO
    val title: String,           // "Đất quá khô"
    val recommendation: String   // "AI đề xuất tưới nước"
)

object AlertTypes {
    const val DRY = "DRY"
    const val FLOOD = "FLOOD"
    const val LACK_N = "LACK_N"
    const val LACK_P = "LACK_P"
    const val LACK_K = "LACK_K"
    const val HIGH_TEMP = "HIGH_TEMP"
    const val LOW_TEMP = "LOW_TEMP"
    const val RECOVERED = "RECOVERED"

    const val SEV_WARNING = "WARNING"
    const val SEV_OK = "OK"
    const val SEV_INFO = "INFO"

    /** Nhãn hiển thị ngắn cho biểu đồ thống kê loại cảnh báo. */
    fun shortLabel(type: String): String = when (type) {
        DRY -> "Thiếu nước"
        FLOOD -> "Ngập úng"
        LACK_N -> "Thiếu N"
        LACK_P -> "Thiếu P"
        LACK_K -> "Thiếu K"
        HIGH_TEMP -> "Nóng"
        LOW_TEMP -> "Lạnh"
        RECOVERED -> "Phục hồi"
        else -> type
    }
}
