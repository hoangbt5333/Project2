package com.example.project2

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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class HomeFragment : Fragment() {

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
    private val myRef = database.getReference("smart_agriculture")
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

                // 3. CHẠY BỘ NÃO RULE-BASED AI
                runRuleBasedAI(soilMoist, temp, humid, n, p, k)

                // 4. THUẬT TOÁN CHỐNG SPAM GHI LỊCH SỬ VÀO ROOM
                val currentTime = System.currentTimeMillis()
                val isTimePassed = (currentTime - lastSavedTime) >= TIME_INTERVAL_LIMIT
                val isSoilChangedSignificant = abs(soilMoist - lastSavedSoilMoist) >= DELTA_SOIL_LIMIT

                if (isTimePassed || isSoilChangedSignificant) {
                    lastSavedSoilMoist = soilMoist
                    lastSavedTime = currentTime

                    // Ghi vào Room database bằng Coroutine theo thói quen tối ưu của bạn
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Tìm đoạn này trong HomeFragment.kt và cập nhật lại:
                        val entity = ThongSoEntity(
                            airTemperature = temp,
                            airHumidity = humid,
                            soilMoisture = soilMoist,
                            npkN = n,
                            npkP = p,
                            npkK = k,
                            timestamp = currentTime
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

    private fun runRuleBasedAI(soilMoist: Int, temp: Double, humid: Double, n: Int, p: Int, k: Int) {
        if (!isAdded) return // Bảo vệ app không bị crash nếu fragment chưa gắn vào view

        // --- 1. BIẾN LƯU TRỮ TRẠNG THÁI TẠM THỜI ---
        var analysisText = ""

        var waterTitle = "Độ ẩm ổn định"
        var waterSub = "Không cần tưới nước cho cây"

        var fertilizerTitle = "Dinh dưỡng tốt"
        var fertilizerSub = "Đất cân bằng, phù hợp nuôi cây"

        var cropType = "Cà chua, Ớt, Rau cải" // Mặc định lý tưởng

        // --- 2. HỆ THỐNG LUẬT PHÂN TÍCH ĐỘ ẨM ĐẤT (WATER LOGIC) ---
        if (soilMoist < 40) {
            waterTitle = "CẦN TƯỚI NGAY!"
            if (temp > 32.0) {
                analysisText = "Đất đang bị khô cằn nghiêm trọng. Nhiệt độ môi trường rất cao ($temp°C) làm tăng tốc độ bốc hơi nước."
                waterSub = "Kích hoạt tưới đẫm/phun sương hạ nhiệt"
            } else {
                analysisText = "Đất đang ở trạng thái khô dưới mức tiêu chuẩn."
                waterSub = "Bật máy bơm tưới nhẹ bổ sung độ ẩm"
            }
        } else if (soilMoist > 80) {
            waterTitle = "NGẬP ÚNG KHẨN CẤP"
            waterSub = "Ngắt toàn bộ bơm, khơi thông rãnh thoát"
            analysisText = "Đất đang bị quá tải nước, hệ rễ có nguy cơ bị thối do thiếu oxy."
        } else {
            // Độ ẩm lý tưởng từ 40% - 80%
            waterTitle = "Không cần tưới"
            waterSub = "Độ ẩm đất hiện tại đủ cho cây"
            if (temp > 32.0) {
                analysisText = "Độ ẩm đất đang ở mức tốt, tuy nhiên thời tiết khá oi bức ($temp°C), cần chú ý theo dõi."
            } else {
                analysisText = "Trạng thái lý tưởng. Khí hậu mát mẻ, độ ẩm đất ổn định."
            }
        }

        // --- 3. HỆ THỐNG LUẬT PHÂN TÍCH DINH DƯỠNG NPK (FERTILIZER & CROP LOGIC) ---
        val listThieu = ArrayList<String>()
        if (n < 60) listThieu.add("Nitơ (Đạm)")
        if (p < 60) listThieu.add("Phốt pho (Lân)")
        if (k < 60) listThieu.add("Kali")

        if (listThieu.isNotEmpty()) {
            // Kịch bản đất bị thiếu chất
            fertilizerTitle = "CẦN BÓN PHÂN"

            val goiyPhan = ArrayList<String>()
            if (n < 60) goiyPhan.add("phân Đạm")
            if (p < 60) goiyPhan.add("phân Lân")
            if (k < 60) goiyPhan.add("phân Kali")

            fertilizerSub = "Bổ sung ngay: ${goiyPhan.joinToString(" + ")}"
            analysisText += " Hàm lượng ${listThieu.joinToString(", ")} trong đất đang ở mức báo động thấp, cây có nguy cơ còi cọc."

            // Đất thiếu chất thì đổi loại cây trồng phù hợp với đất nghèo dinh dưỡng
            cropType = "Khoai lang, Sắn, Cây họ Đậu"
        } else {
            // Đất giàu dinh dưỡng
            if (soilMoist in 40..80) {
                fertilizerTitle = "Đất rất màu mỡ"
                fertilizerSub = "Chỉ số NPK đạt trạng thái cân bằng lý tưởng"
                cropType = "Cà chua, Ớt, Rau cải, Cây ăn quả"
            }
        }

        // --- 4. ĐỒNG BỘ HIỂN THỊ LÊN CÁC ID GIAO DIỆN MỚI XỊN SÒ ---
        val viewLocal = view ?: return

        // Khối text phân tích chung
        viewLocal.findViewById<TextView>(R.id.tvAiAnalysisClean).text = analysisText

        // Cập nhật text lời khuyên cho Khối Nước
        viewLocal.findViewById<TextView>(R.id.tvRecWaterTitle).text = waterTitle
        viewLocal.findViewById<TextView>(R.id.tvRecWaterSub).text = waterSub

        // Cập nhật text lời khuyên cho Khối Phân bón
        viewLocal.findViewById<TextView>(R.id.tvRecFertilizerTitle).text = fertilizerTitle
        viewLocal.findViewById<TextView>(R.id.tvRecFertilizerSub).text = fertilizerSub

        // Cập nhật text Khối Loại cây phù hợp
        viewLocal.findViewById<TextView>(R.id.tvRecCropType).text = cropType

        // --- 5. ĐỔI MÀU SẮC CHỮ ĐỂ CẢNH BÁO THEO QUY TẮC (Tùy chọn nâng cao) ---
        val tvSoilStatus = viewLocal.findViewById<TextView>(R.id.tvSoilStatusText)
        val tvSoilAdvice = viewLocal.findViewById<TextView>(R.id.tvSoilBottomAdvice)

        if (soilMoist < 40 || soilMoist > 80) {
            tvSoilStatus.text = if (soilMoist < 40) "Đất quá khô!" else "Đất úng nước!"
            tvSoilStatus.setTextColor(Color.parseColor("#D84315")) // Màu đỏ cam cảnh báo
            tvSoilAdvice.text = "Cần tác động vật lý để bảo vệ rễ cây!"
            tvSoilAdvice.setBackgroundColor(Color.parseColor("#FFEBEE")) // Nền hồng nhạt nguy hiểm
            tvSoilAdvice.setTextColor(Color.RED)
        } else {
            tvSoilStatus.text = "Độ ẩm tốt"
            tvSoilStatus.setTextColor(Color.parseColor("#2E7D32")) // Màu xanh lá an toàn
            tvSoilAdvice.text = "Độ ẩm đất hiện tại phù hợp cho sự phát triển."
            tvSoilAdvice.setBackgroundColor(Color.parseColor("#E8F5E9")) // Nền xanh lá nhạt
            tvSoilAdvice.setTextColor(Color.parseColor("#2E7D32"))
        }
    }

    private fun updateRadarChart(n: Int, p: Int, k: Int) {
        val entries = ArrayList<com.github.mikephil.charting.data.RadarEntry>()

        // Quy chuẩn tỷ lệ % dinh dưỡng (map từ giá trị 0-200 sang mức 100% đồ thị)
        entries.add(com.github.mikephil.charting.data.RadarEntry((n / 2.0).toFloat()))
        entries.add(com.github.mikephil.charting.data.RadarEntry((p / 2.0).toFloat()))
        entries.add(com.github.mikephil.charting.data.RadarEntry((k / 2.0).toFloat()))

        val dataset = com.github.mikephil.charting.data.RadarDataSet(entries, "Dinh dưỡng").apply {
            color = Color.parseColor("#4CAF50")
            fillColor = Color.parseColor("#81C784")
            setDrawFilled(true) // Tô màu vùng bên trong mạng nhện
            lineWidth = 2f
            valueTextSize = 8f
        }

        radarChartNPK.apply {
            data = com.github.mikephil.charting.data.RadarData(dataset)
            description.isEnabled = false
            xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(arrayOf("N", "P", "K"))
            yAxis.axisMinimum = 0f
            yAxis.axisMaximum = 100f // Ngưỡng kịch khung 100%
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
            nPercent >= 90 -> {
                tvStatusN.text = "Nitơ (N)\nTốt"
                tvStatusN.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            }
            nPercent >= 60 -> {
                tvStatusN.text = "Nitơ (N)\nTrung bình"
                tvStatusN.setTextColor(
                    Color.parseColor("#FFC107")
                )
            }
            else -> {
                tvStatusN.text = "Nitơ (N)\nThiếu"
                tvStatusN.setTextColor(
                    Color.parseColor("#F44336")
                )
            }
        }
    }

    private fun updatePStatus(nPercent: Int) {
        when {
            nPercent >= 90 -> {
                tvStatusP.text = "Phốt Pho (P)\nTốt"
                tvStatusP.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            }
            nPercent >= 60 -> {
                tvStatusP.text = "Phốt Pho (P)\nTrung bình"
                tvStatusP.setTextColor(
                    Color.parseColor("#FFC107")
                )
            }
            else -> {
                tvStatusP.text = "Phốt Pho (P)\nThiếu"
                tvStatusP.setTextColor(
                    Color.parseColor("#F44336")
                )
            }
        }
    }

    private fun updateKStatus(nPercent: Int) {
        when {
            nPercent >= 90 -> {
                tvStatusK.text = "Kali (K)\nTốt"
                tvStatusK.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            }
            nPercent >= 60 -> {
                tvStatusK.text = "Kali (K)\nTrung bình"
                tvStatusK.setTextColor(
                    Color.parseColor("#FFC107")
                )
            }
            else -> {
                tvStatusK.text = "Kali (K)\nThiếu"
                tvStatusK.setTextColor(
                    Color.parseColor("#F44336")
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