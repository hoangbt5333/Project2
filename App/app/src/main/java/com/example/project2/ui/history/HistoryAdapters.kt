package com.example.project2.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.project2.data.local.AlertEntity
import com.example.project2.data.local.AlertTypes
import com.example.project2.R
import com.example.project2.ThongSoEntity
import com.example.project2.data.local.WateringEntity
import java.text.SimpleDateFormat
import java.util.*

private val dtFull = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private val tOnly = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// ====================== MỤC 1: TIMELINE CẢNH BÁO ======================
class AlertAdapter : RecyclerView.Adapter<AlertAdapter.VH>() {
    private val items = ArrayList<AlertEntity>()
    fun submit(list: List<AlertEntity>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView = v.findViewById(R.id.tvAlertIcon)
        val tvTitle: TextView = v.findViewById(R.id.tvAlertTitle)
        val tvRec: TextView = v.findViewById(R.id.tvAlertRec)
        val tvTime: TextView = v.findViewById(R.id.tvAlertTime)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_alert, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val a = items[pos]
        val isOk = a.severity == AlertTypes.SEV_OK
        h.tvIcon.text = if (isOk) "\u2713" else "\u26A0"
        h.tvIcon.setTextColor(if (isOk) Color.parseColor("#2E7D32") else Color.parseColor("#D84315"))
        h.tvTitle.text = a.title
        h.tvTitle.setTextColor(if (isOk) Color.parseColor("#2E7D32") else Color.parseColor("#BF360C"))
        h.tvRec.text = a.recommendation
        h.tvTime.text = dtFull.format(Date(a.timestamp))
    }
}

// ====================== MỤC 2: NHẬT KÝ AI ======================
data class AiDailyItem(val day: String, val digest: String)
class AiDailyAdapter : RecyclerView.Adapter<AiDailyAdapter.VH>() {
    private val items = ArrayList<AiDailyItem>()
    fun submit(list: List<AiDailyItem>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDay: TextView = v.findViewById(R.id.tvAiDay)
        val tvDigest: TextView = v.findViewById(R.id.tvAiDigest)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_ai_daily, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        h.tvDay.text = items[pos].day
        h.tvDigest.text = items[pos].digest
    }
}

// ====================== MỤC 5: LẮCH SỪ TƯỚI ======================
class WateringAdapter : RecyclerView.Adapter<WateringAdapter.VH>() {
    private val items = ArrayList<WateringEntity>()
    fun submit(list: List<WateringEntity>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(R.id.tvWaterTime)
        val tvMode: TextView = v.findViewById(R.id.tvWaterMode)
        val tvDuration: TextView = v.findViewById(R.id.tvWaterDuration)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_watering, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val w = items[pos]
        h.tvTime.text = dtFull.format(Date(w.timestamp))
        val auto = w.mode == "AUTO"
        h.tvMode.text = if (auto) "\uD83E\uDD16 AUTO WATERING" else "\uD83D\uDC46 Manual watering"
        h.tvMode.setTextColor(if (auto) Color.parseColor("#1565C0") else Color.parseColor("#6A1B9A"))
        h.tvDuration.text = "Duration: ${w.durationSeconds}s"
    }
}

// ====================== MỤC 6: BẢNG DỮ LIỆU ======================
class DataRowAdapter : RecyclerView.Adapter<DataRowAdapter.VH>() {
    private val items = ArrayList<ThongSoEntity>()
    fun submit(list: List<ThongSoEntity>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(R.id.tvRowTime)
        val tvMoist: TextView = v.findViewById(R.id.tvRowMoist)
        val tvNpk: TextView = v.findViewById(R.id.tvRowNpk)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_data_row, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val r = items[pos]
        h.tvTime.text = tOnly.format(Date(r.timestamp))
        h.tvMoist.text = "${r.soilMoisture}%"
        h.tvNpk.text = "${r.npkN}-${r.npkP}-${r.npkK}"
    }
}
