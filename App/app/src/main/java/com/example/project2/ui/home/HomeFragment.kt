package com.example.project2.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.project2.R
import com.example.project2.domain.ai.AiResult
import com.example.project2.domain.ai.FarmDecision
import com.example.project2.domain.model.SensorData
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.provideFactory(requireContext().applicationContext)
    }

    private var lastMoistureChartKey: String? = null
    private var moistureChartTimestamps: List<Long> = emptyList()
    private val moistureChartTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var tvLastUpdate: TextView
    private lateinit var tvSoilMoisture: TextView

    private lateinit var progressSoilMoistureCircle: ProgressBar
    private lateinit var progressMiniN: ProgressBar
    private lateinit var progressMiniP: ProgressBar
    private lateinit var progressMiniK: ProgressBar

    private lateinit var tvValuePercentN: TextView
    private lateinit var tvValuePercentP: TextView
    private lateinit var tvValuePercentK: TextView

    private lateinit var tvStatusN: TextView
    private lateinit var tvStatusP: TextView
    private lateinit var tvStatusK: TextView

    private lateinit var lineChartMoistureHome: LineChart

    private lateinit var tvSoilPhValue: TextView
    private lateinit var tvSoilPhStatus: TextView
    private lateinit var tvSoilPhDesc: TextView
    private lateinit var tvSoilStatusText: TextView
    private lateinit var tvSoilBottomAdvice: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        preSetupLineChartProperties()
        observeUiState()
    }

    private fun bindViews(view: View) {
        tvLastUpdate = view.findViewById(R.id.tvLastUpdate)
        tvSoilMoisture = view.findViewById(R.id.tvSoilMoisture)

        progressSoilMoistureCircle = view.findViewById(R.id.progressSoilMoistureCircle)
        progressMiniN = view.findViewById(R.id.progressMiniN)
        progressMiniP = view.findViewById(R.id.progressMiniP)
        progressMiniK = view.findViewById(R.id.progressMiniK)

        tvValuePercentN = view.findViewById(R.id.tvValuePercentN)
        tvValuePercentP = view.findViewById(R.id.tvValuePercentP)
        tvValuePercentK = view.findViewById(R.id.tvValuePercentK)

        tvStatusN = view.findViewById(R.id.tvStatusN)
        tvStatusP = view.findViewById(R.id.tvStatusP)
        tvStatusK = view.findViewById(R.id.tvStatusK)

        lineChartMoistureHome = view.findViewById(R.id.lineChartMoistureHome)

        tvSoilPhValue = view.findViewById(R.id.tvSoilPhValue)
        tvSoilPhStatus = view.findViewById(R.id.tvSoilPhStatus)
        tvSoilPhDesc = view.findViewById(R.id.tvSoilPhDesc)

        tvSoilStatusText = view.findViewById(R.id.tvSoilStatusText)
        tvSoilBottomAdvice = view.findViewById(R.id.tvSoilBottomAdvice)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.sensorData?.let { renderSensorData(it) }
                    state.aiResult?.let { renderAiResult(it) }

                    renderMoistureChartIfChanged(state.recentLogs)

                    state.errorMessage?.let { msg ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Lỗi: $msg", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderSensorData(data: SensorData) {
        val soilMoist = data.soilMoisture
        val n = data.npkN
        val p = data.npkP
        val k = data.npkK
        val soilPh = data.soilPh

        tvSoilMoisture.text = "$soilMoist %"
        progressSoilMoistureCircle.progress = soilMoist

        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        tvLastUpdate.text = "Last update: ${sdf.format(Date(data.timestamp))}"

        // Vong khuyen tron %

        progressMiniN.progress = (n / 2)
        progressMiniP.progress = (p / 2)
        progressMiniK.progress = (k / 2)

        tvValuePercentN.text = "${n / 2}%"
        tvValuePercentP.text = "${p / 2}%"
        tvValuePercentK.text = "${k / 2}%"

        updateNStatus(n / 2)
        updatePStatus(p / 2)
        updateKStatus(k / 2)

        updateSoilMoistureCard(soilMoist)
        updatePhCard(soilPh)
    }

    private fun renderAiResult(result: AiResult) {
        if (!isAdded) return
        val viewLocal = view ?: return

        // 1. Tong quan + goi y cay trong
        viewLocal.findViewById<TextView>(R.id.tvAiAnalysisClean).text = result.summary
        viewLocal.findViewById<TextView>(R.id.tvRecCropType).text = result.cropSuggestion

        // 2. The "Tuoi nuoc / Khi hau": AiResult moi khong con waterTitle/waterSub rieng,
        //    nen suy ra tu decision + diem so (waterScore, climateScore).
        val (waterTitle, waterSub) = when (result.decision) {
            FarmDecision.NEED_WATER -> "Nên tưới nước" to "Điểm nước ${result.waterScore} · khí hậu ${result.climateScore} /100"
            FarmDecision.STOP_WATERING -> "Ngừng tưới nước" to "Điểm nước ${result.waterScore} · khí hậu ${result.climateScore} /100"
            FarmDecision.COOLING_NEEDED -> "Cần làm mát / phun sương" to "Điểm nước ${result.waterScore} · khí hậu ${result.climateScore} /100"
            FarmDecision.SENSOR_ERROR -> "Chưa có dữ liệu" to "Kiểm tra lại cảm biến"
            else -> "Độ ẩm & khí hậu ổn định" to "Điểm nước ${result.waterScore} · khí hậu ${result.climateScore} /100"
        }
        viewLocal.findViewById<TextView>(R.id.tvRecWaterTitle).text = waterTitle
        viewLocal.findViewById<TextView>(R.id.tvRecWaterSub).text = waterSub

        // 3. The "Dinh duong / NPK": map tu decision + nutrientScore + statusN/P/K
        val fertilizerTitle = when (result.decision) {
            FarmDecision.NEED_FERTILIZER -> "Cần bổ sung dinh dưỡng"
            FarmDecision.SENSOR_ERROR -> "Chưa có dữ liệu"
            else -> "Dinh dưỡng ổn định"
        }
        viewLocal.findViewById<TextView>(R.id.tvRecFertilizerTitle).text = fertilizerTitle
        viewLocal.findViewById<TextView>(R.id.tvRecFertilizerSub).text =
            "N: ${result.statusN} · P: ${result.statusP} · K: ${result.statusK} (điểm ${result.nutrientScore})"

        // 4. Trang thai dat + diem + do tin cay
//        val tvStatusText = viewLocal.findViewById<TextView>(R.id.tvSoilStatusText)
//        val tvSoilAdvice = viewLocal.findViewById<TextView>(R.id.tvSoilBottomAdvice)
//
//        tvStatusText.text = "${result.soilStatusText} (${result.soilScore}đ · tin cậy ${result.confidence}%)"
//
//        // 5. Khuyen nghi hanh dong chinh: lay truc tiep tu AI, to mau theo canh bao
//        tvSoilAdvice.text = result.recommendation
//        if (result.isWarning) {
//            tvStatusText.setTextColor(Color.parseColor("#D84315"))
//            tvSoilAdvice.setBackgroundColor(Color.parseColor("#FFEBEE"))
//            tvSoilAdvice.setTextColor(Color.RED)
//        } else {
//            tvStatusText.setTextColor(Color.parseColor("#2E7D32"))
//            tvSoilAdvice.setBackgroundColor(Color.parseColor("#E8F5E9"))
//            tvSoilAdvice.setTextColor(Color.parseColor("#2E7D32"))
//        }
    }

    private fun updateSoilMoistureCard(soilMoisture: Int) {
        val status = when {
            soilMoisture < 30 -> "Đất khô"
            soilMoisture in 30..75 -> "Độ ẩm tốt"
            else -> "Đất quá ẩm"
        }

        val color = when {
            soilMoisture < 30 -> Color.parseColor("#F57C00")
            soilMoisture in 30..75 -> Color.parseColor("#2E7D32")
            else -> Color.parseColor("#1976D2")
        }

        // Phần chữ nhỏ trong vòng tròn: bỏ để tránh lặp.
        tvSoilStatusText.visibility = View.GONE

        // Phần nhận xét dưới card: chỉ để trạng thái ngắn.
        tvSoilBottomAdvice.text = status
        tvSoilBottomAdvice.setTextColor(color)

        val backgroundColor = when {
            soilMoisture < 30 -> Color.parseColor("#FFF3E0")
            soilMoisture in 30..75 -> Color.parseColor("#E8F5E9")
            else -> Color.parseColor("#E3F2FD")
        }

        tvSoilBottomAdvice.setBackgroundColor(backgroundColor)
    }

    private fun updatePhCard(ph: Double) {
        val roundedPh = String.format(Locale.getDefault(), "%.1f", ph)

        val status: String
        val description: String
        val color: Int

        when {
            ph < 5.5 -> {
                status = "Đất chua"
                description = "pH thấp, đất có xu hướng chua. Một số cây khó hấp thụ dinh dưỡng tốt."
                color = Color.parseColor("#D84315")
            }

            ph < 6.0 -> {
                status = "Đất hơi chua"
                description = "Phù hợp với một số cây ưa chua nhẹ, nhưng chưa tối ưu cho đa số rau màu."
                color = Color.parseColor("#F57C00")
            }

            ph <= 7.0 -> {
                status = "pH phù hợp"
                description = "Đất hơi chua đến trung tính, phù hợp với đa số rau màu."
                color = Color.parseColor("#2E7D32")
            }

            ph <= 7.5 -> {
                status = "Đất hơi kiềm"
                description = "pH hơi cao, cần theo dõi nếu cây có dấu hiệu vàng lá hoặc kém phát triển."
                color = Color.parseColor("#F9A825")
            }

            else -> {
                status = "Đất kiềm cao"
                description = "pH cao có thể làm cây khó hấp thụ một số chất dinh dưỡng."
                color = Color.parseColor("#D84315")
            }
        }

        tvSoilPhValue.text = roundedPh
        tvSoilPhValue.setTextColor(color)

        tvSoilPhStatus.text = status
        tvSoilPhStatus.setTextColor(color)

        tvSoilPhDesc.text = description
    }

    private fun preSetupLineChartProperties() {
        lineChartMoistureHome.apply {
            description.isEnabled = false
            legend.isEnabled = false

            setNoDataText("Đang chờ dữ liệu độ ẩm đất...")

            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDragEnabled(false)

            axisRight.isEnabled = false

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textSize = 9f
                setDrawGridLines(true)
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textSize = 9f
                granularity = 1f
                labelCount = 5
            }
        }
    }

    private fun updateMoistureLineChart(list: List<SensorData>) {
        val recentList = list
            .sortedBy { it.timestamp }
            .takeLast(20)

        if (recentList.isEmpty()) return

        moistureChartTimestamps = recentList.map { it.timestamp }

        val entries = recentList.mapIndexed { index, item ->
            Entry(
                index.toFloat(),
                item.soilMoisture.toFloat()
            )
        }

        val existingDataSet = lineChartMoistureHome.data
            ?.getDataSetByIndex(0) as? LineDataSet

        if (existingDataSet == null) {
            val dataSet = LineDataSet(entries, "Độ ẩm đất").apply {
                color = Color.parseColor("#1E88E5")
                lineWidth = 2.5f
                setDrawValues(false)

                // Nếu chỉ có 1 điểm thì cần circle, nếu không sẽ gần như không thấy gì.
                setDrawCircles(recentList.size == 1)
                circleRadius = 4f
                setCircleColor(Color.parseColor("#1E88E5"))

                mode = if (recentList.size >= 3) {
                    LineDataSet.Mode.CUBIC_BEZIER
                } else {
                    LineDataSet.Mode.LINEAR
                }

                cubicIntensity = 0.15f
                setDrawFilled(recentList.size >= 2)
                fillAlpha = 80
                fillColor = Color.parseColor("#42A5F5")
            }

            lineChartMoistureHome.data = LineData(dataSet)
        } else {
            existingDataSet.values = entries
            existingDataSet.setDrawCircles(recentList.size == 1)
            existingDataSet.setDrawFilled(recentList.size >= 2)
            existingDataSet.mode = if (recentList.size >= 3) {
                LineDataSet.Mode.CUBIC_BEZIER
            } else {
                LineDataSet.Mode.LINEAR
            }

            lineChartMoistureHome.data.notifyDataChanged()
            lineChartMoistureHome.notifyDataSetChanged()
        }

        lineChartMoistureHome.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()

                return if (index in moistureChartTimestamps.indices) {
                    moistureChartTimeFormat.format(Date(moistureChartTimestamps[index]))
                } else {
                    ""
                }
            }
        }

        lineChartMoistureHome.invalidate()
    }

    private fun renderMoistureChartIfChanged(list: List<SensorData>) {
        val recentList = list
            .sortedBy { it.timestamp }
            .takeLast(20)

        // Quan trọng:
        // Nếu list rỗng nhưng chart đã có data cũ thì KHÔNG clear chart.
        if (recentList.isEmpty()) {
            if (lineChartMoistureHome.data == null) {
                lineChartMoistureHome.setNoDataText("Đang chờ dữ liệu độ ẩm đất...")
            }
            return
        }

        val chartKey = recentList.joinToString("|") {
            "${it.timestamp}:${it.soilMoisture}"
        }

        if (chartKey == lastMoistureChartKey) {
            return
        }

        lastMoistureChartKey = chartKey
        updateMoistureLineChart(recentList)
    }

    private fun updateNStatus(nPercent: Int) {
        when {
            nPercent < 12.5 -> {
                tvStatusN.text = "Nitơ (N)\nRất thiếu"
                tvStatusN.setTextColor(Color.parseColor("#D32F2F"))
            }
            nPercent < 25 -> {
                tvStatusN.text = "Nitơ (N)\nThiếu"
                tvStatusN.setTextColor(Color.parseColor("#F57C00"))
            }
            nPercent < 50 -> {
                tvStatusN.text = "Nitơ (N)\nTrung bình"
                tvStatusN.setTextColor(Color.parseColor("#FBC02D"))
            }
            nPercent < 75 -> {
                tvStatusN.text = "Nitơ (N)\nTốt"
                tvStatusN.setTextColor(Color.parseColor("#4CAF50"))
            }
            else -> {
                tvStatusN.text = "Nitơ (N)\nRất giàu"
                tvStatusN.setTextColor(Color.parseColor("#2E7D32"))
            }
        }
    }

    private fun updatePStatus(pPercent: Int) {
        when {
            pPercent < 5 -> {
                tvStatusP.text = "Phốt Pho (P)\nRất thiếu"
                tvStatusP.setTextColor(Color.parseColor("#D32F2F"))
            }
            pPercent < 12.5 -> {
                tvStatusP.text = "Phốt Pho (P)\nThiếu"
                tvStatusP.setTextColor(Color.parseColor("#F57C00"))
            }
            pPercent < 25 -> {
                tvStatusP.text = "Phốt Pho (P)\nTrung bình"
                tvStatusP.setTextColor(Color.parseColor("#FBC02D"))
            }
            pPercent < 50 -> {
                tvStatusP.text = "Phốt Pho (P)\nTốt"
                tvStatusP.setTextColor(Color.parseColor("#4CAF50"))
            }
            else -> {
                tvStatusP.text = "Phốt Pho (P)\nRất giàu"
                tvStatusP.setTextColor(Color.parseColor("#2E7D32"))
            }
        }
    }

    private fun updateKStatus(kPercent: Int) {
        when {
            kPercent < 20 -> {
                tvStatusK.text = "Kali (K)\nRất thiếu"
                tvStatusK.setTextColor(Color.parseColor("#D32F2F"))
            }

            kPercent < 40 -> {
                tvStatusK.text = "Kali (K)\nThiếu"
                tvStatusK.setTextColor(Color.parseColor("#F57C00"))
            }

            kPercent < 60 -> {
                tvStatusK.text = "Kali (K)\nTrung bình"
                tvStatusK.setTextColor(Color.parseColor("#FBC02D"))
            }

            kPercent < 90 -> {
                tvStatusK.text = "Kali (K)\nTốt"
                tvStatusK.setTextColor(Color.parseColor("#4CAF50"))
            }

            else -> {
                tvStatusK.text = "Kali (K)\nRất giàu"
                tvStatusK.setTextColor(Color.parseColor("#2E7D32"))
            }
        }
    }
}