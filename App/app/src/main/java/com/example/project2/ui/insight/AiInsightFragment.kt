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
import com.example.project2.domain.ai.AiResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiInsightFragment : Fragment(R.layout.fragment_ai_insight) {

    private val viewModel: AiInsightViewModel by viewModels {
        AiInsightViewModel.provideFactory(requireContext().applicationContext)
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())

    private lateinit var txtEmpty: TextView
    private lateinit var cardLatest: View

    private lateinit var txtLatestTime: TextView
    private lateinit var txtLatestScore: TextView
    private lateinit var txtLatestDecision: TextView
    private lateinit var txtLatestSummary: TextView
    private lateinit var txtLatestRecommendation: TextView

    private lateinit var txtActionList: TextView
    private lateinit var txtCropSuggestions: TextView
    private lateinit var txtNotRecommendedCrops: TextView

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

        txtActionList = view.findViewById(R.id.txtActionList)
        txtCropSuggestions = view.findViewById(R.id.txtCropSuggestions)
        txtNotRecommendedCrops = view.findViewById(R.id.txtNotRecommendedCrops)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderLatestLog(state.latestLog)
                    renderAiInsight(state.latestAiResult)
                }
            }
        }
    }

    private fun renderLatestLog(log: SensorLogEntity?) {
        if (log == null) {
            txtEmpty.visibility = View.VISIBLE
            cardLatest.visibility = View.GONE

            txtActionList.text = "Chưa có dữ liệu để đưa ra khuyến nghị."
            txtCropSuggestions.text = "Chưa đủ dữ liệu để gợi ý cây trồng phù hợp."
            txtNotRecommendedCrops.text = "Chưa đủ dữ liệu để đánh giá cây chưa phù hợp."
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


        txtLatestRecommendation.visibility = View.GONE
    }

    private fun renderAiInsight(result: AiResult?) {
        if (result == null) return

        txtLatestScore.text = "${result.soilScore}/100"

        txtLatestDecision.text = decisionLabel(result.decision.name)
        txtLatestDecision.setTextColor(decisionColor(result.decision.name))

        txtLatestSummary.text = buildString {
            append(result.insightTitle)
            append("\n\n")
            append(result.insightAnalysis)
        }

        txtLatestRecommendation.text = result.recommendation

        txtActionList.text = if (result.immediateActions.isNotEmpty()) {
            result.immediateActions.joinToString("\n") { action ->
                "• $action"
            }
        } else {
            "• Tiếp tục theo dõi các chỉ số cảm biến."
        }

        txtCropSuggestions.text = if (result.cropSuggestions.isNotEmpty()) {
            result.cropSuggestions
                .take(3)
                .joinToString("\n\n") { crop ->
                    "• ${crop.name} (${crop.score}/100)\n${crop.reason}"
                }
        } else {
            "Chưa tìm được cây thật sự phù hợp với điều kiện hiện tại."
        }

        txtNotRecommendedCrops.text = if (result.notRecommendedCrops.isNotEmpty()) {
            result.notRecommendedCrops
                .take(3)
                .joinToString("\n\n") { crop ->
                    "• ${crop.name} (${crop.score}/100)\n${crop.reason}"
                }
        } else {
            "Chưa có cây nào bị đánh giá là không phù hợp rõ ràng."
        }
    }

    private fun decisionLabel(decision: String): String = when (decision) {
        "NORMAL" -> "Bình thường"
        "NEED_WATER" -> "Cần tưới nước"
        "STOP_WATERING" -> "Ngừng tưới nước"
        "COOLING_NEEDED" -> "Cần làm mát"
        "NEED_FERTILIZER" -> "Cần bón phân"
        "PH_PROBLEM" -> "Vấn đề pH"
        "SENSOR_ERROR" -> "Lỗi cảm biến"
        else -> decision
    }

    private fun decisionColor(decision: String): Int = when (decision) {
        "NORMAL" -> Color.parseColor("#2E7D32")
        "SENSOR_ERROR" -> Color.parseColor("#9E9E9E")
        else -> Color.parseColor("#D84315")
    }
}