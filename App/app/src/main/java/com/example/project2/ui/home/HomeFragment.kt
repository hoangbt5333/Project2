package com.example.project2.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.project2.domain.ai.AiRecommendationEngine
import com.example.project2.domain.ai.AiResult
import com.example.project2.data.local.AppDatabase
import com.example.project2.FirebasePaths
import com.example.project2.domain.history.HistoryLogger
import com.example.project2.R
import com.example.project2.domain.ai.RuleBasedAiImpl
import com.example.project2.ThongSoEntity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class HomeFragment : Fragment() {

    // 1. Khai báo bộ não AI ở đầu class HomeFragment
    private val aiEngine: AiRecommendationEngine by lazy { RuleBasedAiImpl() }  // Rule Based AI

    // Khai báo các thành phần UI trong Fragment
    private lateinit var tvSystemStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvSoilMoisture: TextView
    private lateinit var tvAirTemp: TextView
    private lateinit var tvAirHumid: TextView
    private lateinit var tvValueN: TextView
    private lateinit var tvValueP: TextView
    private lateinit var tvValueK: TextView

    private lateinit var tvAiAnalysis: TextView
    private lateinit var tvAiRecommendation: TextView

    private lateinit var radarChartNPK: RadarChart
    private lateinit var lineChartMoistureHome: LineChart
    private lateinit var progressSoilMoistureCircle: ProgressBar
    private lateinit var progressMiniN: ProgressBar
    private lateinit var progressMiniP: ProgressBar
    private lateinit var progressMiniK: ProgressBar

    private lateinit var tvValuePercentN: TextView
    private lateinit var tvValuePercentP: TextView
    private lateinit var tvValuePercentK: TextView

    private lateinit var tvRecWaterTitle: TextView
    private lateinit var tvRecWaterSub: TextView
    private lateinit var tvRecFertilizerTitle: TextView
    private lateinit var tvRecFertilizerSub: TextView
    private lateinit var tvRecCropType: TextView

    private lateinit var tvStatusN: TextView
    private lateinit var tvStatusP: TextView
    private lateinit var tvStatusK: TextView


    // Kết nối Firebase
    private val database = FirebaseDatabase.getInstance()
    private val myRef = database.getReference(FirebasePaths.ROOT)
    private var valueEventListener: ValueEventListener? = null

    // Kết nối Room Database (Phục vụ lưu lịch sử chống spam)
    private val roomDb by lazy { AppDatabase.getDatabase(requireContext()) }

    // Biến phục vụ thuật toán chống spam dữ liệu lịch sử
    private var lastSavedSoilMoist = 0
    private var lastSavedTime = 0L
    private val TIME_INTERVAL_LIMIT = 60000 // 1 phút mới lưu tĩnh một lần
    private val DELTA_SOIL_LIMIT = 5         // Độ ẩm đất lệch quá 5% thì lưu luôn khẩn cấp

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Nạp layout fragment_home vào Fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Ánh xạ các View từ đối tượng 'view' vừa nạp
        tvSystemStatus = view.findViewById(R.id.tvSystemStatus)
        tvLastUpdate = view.findViewById(R.id.tvLastUpdate)
        tvSoilMoisture = view.findViewById(R.id.tvSoilMoisture)
//        tvAirTemp = view.findViewById(R.id.tvAirTemp)
//        tvAirHumid = view.findViewById(R.id.tvAirHumid)
//        tvValueN = view.findViewById(R.id.tvValueN)
//        tvValueP = view.findViewById(R.id.tvValueP)
//        tvValueK = view.findViewById(R.id.tvValueK)

        tvAiAnalysis = view.findViewById(R.id.tvAiAnalysisClean)
        tvRecWaterTitle = view.findViewById(R.id.tvRecWaterTitle)
        tvRecWaterSub = view.findViewById(R.id.tvRecWaterSub)
        tvRecFertilizerTitle = view.findViewById(R.id.tvRecFertilizerTitle)
        tvRecFertilizerSub = view.findViewById(R.id.tvRecFertilizerSub)
        tvRecCropType = view.findViewById(R.id.tvRecCropType)

        radarChartNPK = view.findViewById(R.id.radarChartNPK)
        lineChartMoistureHome = view.findViewById(R.id.lineChartMoistureHome)
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

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preSetupLineChartProperties()

        startListeningFirebase()

        viewLifecycleOwner.lifecycleScope.launch {
            roomDb.thongSoDao().getRecentHistory().collect { rawList ->
                if (rawList.isNotEmpty()) {
                    // Đảo ngược mốc thời gian để dữ liệu cũ ở bên trái, dữ liệu mới chạy dần về bên phải trục X
                    val sortedList = rawList.reversed()

                    // Gọi hàm vẽ đường xu hướng ẩm độ đất
                    updateMoistureLineChart(sortedList)

                }
            }
        }
    }

    private fun startListeningFirebase() {
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return // Kiểm tra nếu Fragment chưa được gắn vào Activity thì bỏ qua để tránh crash

                // 1. Đọc dữ liệu an toàn từ Firebase
                val temp = snapshot.child("air_temperature").getValue(Double::class.java) ?: 0.0
                val humid = snapshot.child("air_humidity").getValue(Double::class.java) ?: 0.0
                val soilMoist = snapshot.child("soil_moisture").getValue(Int::class.java) ?: 0
                val n = snapshot.child("npk_n").getValue(Int::class.java) ?: 0
                val p = snapshot.child("npk_p").getValue(Int::class.java) ?: 0
                val k = snapshot.child("npk_k").getValue(Int::class.java) ?: 0

                // 2. Cập nhập text trên giao diện
                tvSoilMoisture.text = "$soilMoist %"

                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                tvLastUpdate.text = "Last update: ${sdf.format(Date())}"

                // Cập nhật vòng khuyên tròn %
                progressSoilMoistureCircle.progress = soilMoist
                progressMiniN.progress = (n / 2)
                progressMiniP.progress = (p / 2)
                progressMiniK.progress = (k / 2)

                tvValuePercentN.text = "${n/2}%"
                tvValuePercentP.text = "${p/2}%"
                tvValuePercentK.text = "${k/2}%"

                updateNStatus(n / 2)
                updatePStatus(p / 2)
                updateKStatus(k / 2)


                // Vẽ mạng nhện dinh dưỡng
                updateRadarChart(n, p, k)

                // 3. CHẠY BỘ NÃO AI
                val aiResult = aiEngine.analyze(soilMoist, temp, humid, n, p, k)
                renderAiRecommendation(aiResult)

                val now = System.currentTimeMillis()

                // Log cảnh báo vẫn nên chạy mỗi lần có dữ liệu mới,
                // vì HistoryLogger đã có chống spam cảnh báo riêng.
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    HistoryLogger.evaluateAndLog(
                        roomDb.alertDao(),
                        aiResult,
                        soilMoist,
                        temp,
                        n,
                        p,
                        k,
                        now
                    )
                }

                // Chỉ lưu lịch sử môi trường khi đủ điều kiện chống spam.
                val isTimePassed = (now - lastSavedTime) >= TIME_INTERVAL_LIMIT
                val isSoilChangedSignificant = abs(soilMoist - lastSavedSoilMoist) >= DELTA_SOIL_LIMIT

                if (isTimePassed || isSoilChangedSignificant) {
                    lastSavedSoilMoist = soilMoist
                    lastSavedTime = now

                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val entity = ThongSoEntity(
                            airTemperature = temp,
                            airHumidity = humid,
                            soilMoisture = soilMoist,
                            npkN = n,
                            npkP = p,
                            npkK = k,
                            soilScore = aiResult.soilScore,
                            soilStatusText = aiResult.soilStatusText,
                            timestamp = now
                        )

                        roomDb.thongSoDao().insert(entity)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Lỗi Firebase: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        myRef.addValueEventListener(valueEventListener!!)
    }

    private fun renderAiRecommendation(result: AiResult) {
        if (!isAdded) return
        val viewLocal = view ?: return

        // 1. Đồng bộ các khối phân tích và khuyến nghị (ID cũ giữ nguyên)
        viewLocal.findViewById<TextView>(R.id.tvAiAnalysisClean).text = result.analysisText
        viewLocal.findViewById<TextView>(R.id.tvRecWaterTitle).text = result.waterTitle
        viewLocal.findViewById<TextView>(R.id.tvRecWaterSub).text = result.waterSub
        viewLocal.findViewById<TextView>(R.id.tvRecFertilizerTitle).text = result.fertilizerTitle
        viewLocal.findViewById<TextView>(R.id.tvRecFertilizerSub).text = result.fertilizerSub
        viewLocal.findViewById<TextView>(R.id.tvRecCropType).text = result.cropType

        // 2. CẬP NHẬT TRẠNG THÁI CHỮ ĐÁNH GIÁ NPK (MỚI)
        // Các TextView hiển thị text "Trung bình", "Thấp", "Tốt" nằm dưới các vòng khuyên tròn NPK
        // Bạn có thể tìm dựa theo ID text hoặc cấu trúc layout của bạn, ví dụ:
        //val tvStatusN = viewLocal.findViewById<TextView>(R.id.lblN)?.parent as? android.widget.LinearLayout
        // Để gán nhanh và chính xác nhất theo file layout mẫu trước, ta ép text trực tiếp:
        // Bạn hãy chỉnh các TextView mức độ bằng cách bổ sung ID hoặc tìm text tương ứng:

        // Mẹo hiển thị: Lồng chuỗi Soil Score vào tiêu đề hệ thống trực tuyến cho cực xịn
        val tvStatusText = viewLocal.findViewById<TextView>(R.id.tvSoilStatusText)
        val tvSoilAdvice = viewLocal.findViewById<TextView>(R.id.tvSoilBottomAdvice)

        // Cập nhật text kèm điểm số đất thực tế
        tvStatusText.text = "${result.soilStatusText} (${result.soilScore}đ - ${result.soilScoreEvaluation})"

        if (result.isWarning) {
            tvStatusText.setTextColor(Color.parseColor("#D84315"))
            tvSoilAdvice.text = "Cảnh báo hệ thống: Cần can thiệp nông học khẩn cấp!"
            tvSoilAdvice.setBackgroundColor(Color.parseColor("#FFEBEE"))
            tvSoilAdvice.setTextColor(Color.RED)
        } else {
            tvStatusText.setTextColor(Color.parseColor("#2E7D32"))
            tvSoilAdvice.text = "Hệ thống an toàn. Đất đáp ứng tốt các tiêu chí phát triển."
            tvSoilAdvice.setBackgroundColor(Color.parseColor("#E8F5E9"))
            tvSoilAdvice.setTextColor(Color.parseColor("#2E7D32"))
        }
    }

    private fun updateRadarChart(n: Int, p: Int, k: Int) {
        val entries = arrayListOf(
            RadarEntry((n / 2f)),
            RadarEntry((p / 2f)),
            RadarEntry((k / 2f))
        )

        val dataset = RadarDataSet(entries, "").apply {
            color = Color.parseColor("#6FCF97")
            fillColor = Color.parseColor("#6FCF97")
            fillAlpha = 120
            setDrawFilled(true) // Tô màu vùng bên trong mạng nhện
            lineWidth = 2f
            setDrawHighlightIndicators(false)
            setDrawValues(false)
        }

        radarChartNPK.apply {
            data = RadarData(dataset)
            description.isEnabled = false
            legend.isEnabled = false
            setExtraOffsets(16f, 16f, 16f, 16f)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(arrayOf("N", "P", "K"))
                textSize = 14f
                textColor = Color.parseColor("#F4A300")
                axisLineColor = Color.TRANSPARENT
                yOffset = 10f
            }
            yAxis.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                labelCount = 3
                setDrawLabels(false)
                gridColor = Color.parseColor("#E0E0E0")
                axisLineColor = Color.TRANSPARENT
            }
            webLineWidth = 1f
            webColor = Color.parseColor("#EAEAEA")
            webLineWidthInner = 1f
            webColorInner = Color.parseColor("#F0F0F0")
            webAlpha = 80
            invalidate()
        }
    }

    private fun preSetupLineChartProperties() {
        lineChartMoistureHome.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textSize = 9f
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textSize = 9f
            }
        }
    }

    private fun updateMoistureLineChart(list: List<ThongSoEntity>) {
        val recentList = list.takeLast(20)
        val entries = ArrayList<Entry>()

        recentList.forEachIndexed { index, item ->
            entries.add(
                Entry(
                    index.toFloat(),
                    item.soilMoisture.toFloat()
                )
            )
        }

        // Định hình đường vẽ (LineDataSet) theo phong cách Material 3 mượt mà
        val dataSet = LineDataSet(entries, "Độ ẩm đất").apply {

            color = Color.parseColor("#1E88E5")
            lineWidth = 2.5f
            setDrawValues(false)
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            setDrawFilled(true)
            fillAlpha = 80
            fillColor = Color.parseColor("#42A5F5")
        }

        // Cấu hình trục tọa độ và hiển thị tổng quan của LineChart
        lineChartMoistureHome.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index in recentList.indices) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(recentList[index].timestamp))
                } else ""
            }
        }

        // Đổ data sạch vào và gọi lệnh vẽ, loại bỏ hoàn toàn các hàm di chuyển view gây lag giật
        lineChartMoistureHome.data = LineData(dataSet)
        lineChartMoistureHome.invalidate()
    }

    private fun updateNStatus(nPercent: Int) {
        when {
            nPercent < 12.5 -> {
                tvStatusN.text = "Nitơ (N)\nRất thiếu"
                tvStatusN.setTextColor(
                    Color.parseColor("#D32F2F")
                )
            }
            nPercent < 25 -> {
                tvStatusN.text = "Nitơ (N)\nThiếu"
                tvStatusN.setTextColor(
                    Color.parseColor("#F57C00")
                )
            }
            nPercent < 50 -> {
                tvStatusN.text = "Nitơ (N)\nTrung bình"
                tvStatusN.setTextColor(
                    Color.parseColor("#FBC02D")
                )
            }
            nPercent < 75 -> {
                tvStatusN.text = "Nitơ (N)\nTốt"
                tvStatusN.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            }
            else -> {
                tvStatusN.text = "Nitơ (N)\nRất giàu"
                tvStatusN.setTextColor(
                    Color.parseColor("#2E7D32")
                )
            }
        }
    }

    private fun updatePStatus(nPercent: Int) {
        when {
            nPercent < 5 -> {
                tvStatusP.text = "Phốt Pho (P)\nRất thiếu"
                tvStatusP.setTextColor(
                    Color.parseColor("#D32F2F")
                )
            }
            nPercent < 12.5 -> {
                tvStatusP.text = "Phốt Pho (P)\nThiếu"
                tvStatusP.setTextColor(
                    Color.parseColor("#F57C00")
                )
            }
            nPercent < 25 -> {
                tvStatusP.text = "Phốt Pho (P)\nTrung bình"
                tvStatusP.setTextColor(
                    Color.parseColor("#FBC02D")
                )
            }
            nPercent < 50 -> {
                tvStatusP.text = "Phốt Pho (P)\nTốt"
                tvStatusP.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            }
            else -> {
                tvStatusP.text = "Phốt Pho (P)\nRất giàu"
                tvStatusP.setTextColor(
                    Color.parseColor("#2E7D32")
                )
            }
        }
    }

    private fun updateKStatus(nPercent: Int) {
        when {
            nPercent < 20 -> {
                tvStatusK.text = "Kali (K)\nRất thiếu"
                tvStatusK.setTextColor(
                    Color.parseColor("#D32F2F")
                )
            }
            nPercent < 40 -> {
                tvStatusK.text = "Kali (K)\nThiếu"
                tvStatusK.setTextColor(
                    Color.parseColor("#F57C00")
                )
            }
            nPercent < 60 -> {
                tvStatusK.text = "Kali (K)\nTrung bình"
                tvStatusK.setTextColor(
                    Color.parseColor("#FBC02D")
                )
            }
            nPercent < 90 -> {
                tvStatusK.text = "Kali (K)\nTốt"
                tvStatusK.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            }
            else -> {
                tvStatusK.text = "Kali (K)\nRất giàu"
                tvStatusK.setTextColor(
                    Color.parseColor("#2E7D32")
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hủy lắng nghe Firebase khi thoát màn hình Fragment để tránh tràn bộ nhớ (Memory Leak)
        valueEventListener?.let { myRef.removeEventListener(it) }
    }
}