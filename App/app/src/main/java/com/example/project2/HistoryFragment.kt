package com.example.project2

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private val roomDb by lazy { AppDatabase.getDatabase(requireContext()) }

    // RecyclerViews
    private lateinit var rvAlerts: RecyclerView
    private lateinit var rvAiDaily: RecyclerView
    private lateinit var rvWatering: RecyclerView
    private lateinit var rvDataTable: RecyclerView

    // Charts
    private lateinit var chartAlertTypes: BarChart
    private lateinit var chartSoilStatus: PieChart
    private lateinit var chartHealthScore: BarChart

    private val alertAdapter = AlertAdapter()
    private val aiDailyAdapter = AiDailyAdapter()
    private val wateringAdapter = WateringAdapter()
    private val dataRowAdapter = DataRowAdapter()

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayShortFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_history, container, false)

        rvAlerts = v.findViewById(R.id.rvAlerts)
        rvAiDaily = v.findViewById(R.id.rvAiDaily)
        rvWatering = v.findViewById(R.id.rvWatering)
        rvDataTable = v.findViewById(R.id.rvDataTable)
        chartAlertTypes = v.findViewById(R.id.chartAlertTypes)
        chartSoilStatus = v.findViewById(R.id.chartSoilStatus)
        chartHealthScore = v.findViewById(R.id.chartHealthScore)

        setupRecycler(rvAlerts, alertAdapter)
        setupRecycler(rvAiDaily, aiDailyAdapter)
        setupRecycler(rvWatering, wateringAdapter)
        setupRecycler(rvDataTable, dataRowAdapter)

        setupBarChart(chartAlertTypes)
        setupBarChart(chartHealthScore)
        setupPieChart(chartSoilStatus)

        return v
    }

    private fun setupRecycler(rv: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rv.isNestedScrollingEnabled = false // nằm trong NestedScrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val now = System.currentTimeMillis()
        val since7d = now - 7L * 24 * 60 * 60 * 1000
        val since30d = now - 30L * 24 * 60 * 60 * 1000
        val owner = viewLifecycleOwner

        // MỤC 1: timeline cảnh báo
        owner.lifecycleScope.launch {
            roomDb.alertDao().getRecentAlerts().collect { alertAdapter.submit(it) }
        }
        // MỤC 2: nhật ký AI theo ngày
        owner.lifecycleScope.launch {
            roomDb.thongSoDao().getDailyAiDigest(since30d).collect { list ->
                aiDailyAdapter.submit(list.map { AiDailyItem(formatDay(it.day), buildDigest(it)) })
            }
        }
        // MỤC 3: thống kê loại cảnh báo trong tuần
        owner.lifecycleScope.launch {
            roomDb.alertDao().getAlertCountsByType(since7d).collect { renderAlertTypeChart(it) }
        }
        // MỤC 4: phân bố trạng thái đất 30 ngày
        owner.lifecycleScope.launch {
            roomDb.thongSoDao().getStatusDistribution(since30d).collect { renderSoilStatusPie(it) }
        }
        // MỤC 5: lịch sử tưới
        owner.lifecycleScope.launch {
            roomDb.wateringDao().getRecentWatering().collect { wateringAdapter.submit(it) }
        }
        // MỤC 6: bảng dữ liệu
        owner.lifecycleScope.launch {
            roomDb.thongSoDao().getTableRows(50).collect { dataRowAdapter.submit(it) }
        }
        // MỤC 7: sức khỏe đất theo ngày (7 ngày)
        owner.lifecycleScope.launch {
            roomDb.thongSoDao().getDailyScores(since7d).collect { renderHealthScoreChart(it) }
        }
    }

    private fun formatDay(dayKey: String): String = try {
        dayShortFormat.format(dayKeyFormat.parse(dayKey)!!)
    } catch (e: Exception) { dayKey }

    // MUC 2: dựng câu mô tả "thông minh" từ số liệu trung bình ngày
    private fun buildDigest(d: DailyAi): String {
        val parts = ArrayList<String>()
        when {
            d.avgMoist < 35 -> parts.add("đất khô")
            d.avgMoist > 85 -> parts.add("đất ngập úng")
            else -> parts.add("độ ẩm tốt")
        }
        if (d.avgTemp > 35) parts.add("nhiệt độ cao")
        else if (d.avgTemp < 18) parts.add("trời lạnh")
        if (d.avgN < 50) parts.add("thiếu Đạm")
        if (d.avgP < 25) parts.add("thiếu Lân")
        if (d.avgK < 80) parts.add("thiếu Kali")
        if (parts.size == 1) parts.add("NPK cân bằng")
        val s = parts.joinToString(", ")
        return s.replaceFirstChar { it.uppercase() } + ". (Điểm: ${d.avgScore.toInt()})"
    }

    // ===== MỤC 3: Bar chart loại cảnh báo =====
    private fun renderAlertTypeChart(counts: List<TypeCount>) {
        val order = listOf(
            AlertTypes.DRY, AlertTypes.LACK_N, AlertTypes.LACK_P, AlertTypes.LACK_K,
            AlertTypes.FLOOD, AlertTypes.HIGH_TEMP
        )
        val map = counts.associate { it.type to it.cnt }
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        order.forEachIndexed { i, type ->
            entries.add(BarEntry(i.toFloat(), (map[type] ?: 0).toFloat()))
            labels.add(AlertTypes.shortLabel(type))
        }
        val set = BarDataSet(entries, "Số lần cảnh báo (7 ngày)").apply {
            colors = listOf(
                Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"),
                Color.parseColor("#FF9800"), Color.parseColor("#9C27B0"),
                Color.parseColor("#00BCD4"), Color.parseColor("#F44336")
            )
            valueTextSize = 11f
        }
        chartAlertTypes.data = BarData(set).apply { barWidth = 0.6f }
        chartAlertTypes.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chartAlertTypes.xAxis.labelCount = labels.size
        chartAlertTypes.invalidate()
    }

    // ===== MỤC 4: Pie chart trạng thái đất =====
    private fun renderSoilStatusPie(rows: List<StatusCount>) {
        var good = 0; var dry = 0; var flood = 0
        for (r in rows) {
            val t = r.soilStatusText
            when {
                t.contains("úng", true) || t.contains("ẩm", true) && t.contains("Hơi") -> flood += r.cnt
                t.contains("khô", true) -> dry += r.cnt
                t.contains("Úng", true) -> flood += r.cnt
                else -> good += r.cnt
            }
        }
        // Phân loại lại cho chắc chắn: úng riêng, khô riêng, còn lại là tốt
        good = 0; dry = 0; flood = 0
        for (r in rows) {
            val t = r.soilStatusText.lowercase()
            when {
                t.contains("úng") -> flood += r.cnt
                t.contains("khô") -> dry += r.cnt
                else -> good += r.cnt
            }
        }
        val entries = ArrayList<PieEntry>()
        if (good > 0) entries.add(PieEntry(good.toFloat(), "Trạng thái tốt"))
        if (dry > 0) entries.add(PieEntry(dry.toFloat(), "Đất khô"))
        if (flood > 0) entries.add(PieEntry(flood.toFloat(), "Ngập úng"))
        val set = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"), Color.parseColor("#FF9800"), Color.parseColor("#2196F3")
            )
            valueTextColor = Color.WHITE
            valueTextSize = 13f
        }
        chartSoilStatus.data = PieData(set)
        chartSoilStatus.invalidate()
    }

    // ===== MỤC 7: Bar chart điểm sức khỏe theo ngày =====
    private fun renderHealthScoreChart(scores: List<DailyScore>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        scores.forEachIndexed { i, s ->
            entries.add(BarEntry(i.toFloat(), s.avgScore.toFloat()))
            labels.add(formatDay(s.day))
        }
        val set = BarDataSet(entries, "Điểm sức khỏe đất").apply {
            color = Color.parseColor("#43A047")
            valueTextSize = 11f
        }
        chartHealthScore.data = BarData(set).apply { barWidth = 0.5f }
        chartHealthScore.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chartHealthScore.xAxis.labelCount = labels.size
        chartHealthScore.axisLeft.axisMinimum = 0f
        chartHealthScore.axisLeft.axisMaximum = 100f
        chartHealthScore.invalidate()
    }

    private fun setupBarChart(chart: BarChart) {
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setFitBars(true)
        chart.setScaleEnabled(false)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.granularity = 1f
        chart.setNoDataText("Chưa có dữ liệu thống kê")
    }

    private fun setupPieChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 45f
        chart.setUsePercentValues(true)
        chart.setEntryLabelColor(Color.DKGRAY)
        chart.setNoDataText("Chưa có dữ liệu trạng thái")
    }
}
