package com.example.project2.ui.insight

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.project2.R
import com.example.project2.data.local.SensorLogEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiInsightFragment : Fragment(R.layout.fragment_ai_insight) {

    private val viewModel: AiInsightViewModel by viewModels {
        AiInsightViewModel.provideFactory(requireContext().applicationContext)
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())

    // --- The "Ket qua moi nhat" ---
    private lateinit var txtEmpty: TextView
    private lateinit var cardLatest: View
    private lateinit var txtLatestTime: TextView
    private lateinit var txtLatestScore: TextView
    private lateinit var txtLatestDecision: TextView
    private lateinit var txtLatestSummary: TextView
    private lateinit var txtLatestRecommendation: TextView

    // --- The "Thong ke nhanh" ---
    private lateinit var txtAverageScore: TextView
    private lateinit var txtWarningCount: TextView

    // --- The "Nhat ky gan day" ---
    private lateinit var txtRecentLogs: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        observeUiState()
    }

    private fun bindViews(view: View) {
        txtEmpty = view.findViewById(R.id.txtEmpty)
        cardLatest = view.findViewById(R.id.cardLatest)
        txtLatestTime = view.findViewById(R.id.txtLatestTime)
        txtLatestScore = view.findViewById(R.id.txtLatestScore)
        txtLatestDecision = view.findViewById(R.id.txtLatestDecision)
        txtLatestSummary = view.findViewById(R.id.txtLatestSummary)
        txtLatestRecommendation = view.findViewById(R.id.txtLatestRecommendation)

        txtAverageScore = view.findViewById(R.id.txtAverageScore)
        txtWarningCount = view.findViewById(R.id.txtWarningCount)

        txtRecentLogs = view.findViewById(R.id.txtRecentLogs)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderLatestInsight(state.latestLog)
                    renderSummary(
                        averageScore = state.averageScore,
                        warningCount = state.warningCount
                    )
                    renderRecentLogs(state.recentLogs)
                }
            }
        }
    }

    private fun renderLatestInsight(log: SensorLogEntity?) {
        if (log == null) {
            txtLatestSummary.text = "Chưa có dữ liệu AI."
            return
        }

        txtEmpty.visibility = View.GONE
        cardLatest.visibility = View.VISIBLE

        txtLatestTime.text = dateFormat.format(Date(log.timestamp))
        txtLatestScore.text = "${log.soilScore}/100"
        txtLatestDecision.text = decisionLabel(log.decision)
        txtLatestDecision.setTextColor(decisionColor(log.decision))
        txtLatestSummary.text = log.aiSummary
        txtLatestRecommendation.text = log.recommendation
    }

    private fun renderSummary(averageScore: Int, warningCount: Int) {
        txtAverageScore.text = "$averageScore/100"
        txtWarningCount.text = if (warningCount > 0)
            "$warningCount cảnh báo gần đây"
        else
            "Không có cảnh báo gần đây"
        txtWarningCount.setTextColor(
            if (warningCount > 0) Color.parseColor("#D84315") else Color.parseColor("#2E7D32")
        )
    }

    private fun renderRecentLogs(logs: List<SensorLogEntity>) {
        // Cách gọn nhất: render thành TextView nhiều dòng.
        // Nếu muốn đẹp hơn thì làm RecyclerView adapter nhỏ.

        val text = logs.joinToString(separator = "\n\n") { log ->
            val time = dateFormat.format(Date(log.timestamp))

            """
            $time
            Điểm đất: ${log.soilScore}/100
            Quyết định: ${log.decision}
            ${log.recommendation}
            """.trimIndent()
        }

        // txtRecentLogs.text = text
    }

    private fun decisionLabel(decision: String): String = when (decision) {
        "NORMAL" -> "Bình thường"
        "NEED_WATER" -> "Cần tưới nước"
        "STOP_WATERING" -> "Ngừng tưới nước"
        "COOLING_NEEDED" -> "Cần làm mát"
        "NEED_FERTILIZER" -> "Cần bón phân"
        "SENSOR_ERROR" -> "Lỗi cảm biến"
        else -> decision
    }

    private fun decisionColor(decision: String): Int = when (decision) {
        "NORMAL" -> Color.parseColor("#2E7D32")
        "SENSOR_ERROR" -> Color.parseColor("#9E9E9E")
        else -> Color.parseColor("#D84315")
    }
}