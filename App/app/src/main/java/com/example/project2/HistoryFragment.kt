package com.example.project2

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class HistoryFragment : Fragment() {

    private lateinit var chartClimate: LineChart
    private lateinit var chartNPK: LineChart

    // Khởi tạo Room DB bằng cơ chế lazy
    private val roomDb by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        chartClimate = view.findViewById(R.id.chartClimate)
        chartNPK = view.findViewById(R.id.chartNPK)

        setupChartConfiguration(chartClimate)
        setupChartConfiguration(chartNPK)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // LẮNG NGHE DỮ LIỆU TỪ ROOM DATABASE VÀ CẬP NHẬT BIỂU ĐỒ REALTIME
        viewLifecycleOwner.lifecycleScope.launch {
            roomDb.thongSoDao().getRecentHistory().collect { rawList ->
                if (rawList.isNotEmpty()) {
                    // Đảo ngược danh sách (từ cũ đến mới) để biểu đồ vẽ từ trái qua phải chuẩn dòng thời gian
                    val sortedList = rawList.reversed()

                    updateClimateChart(sortedList)
                    updateNPKChart(sortedList)
                }
            }
        }
    }

    private fun setupChartConfiguration(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM

        // Định dạng định dạng giờ phút giây cho trục X dựa vào timestamp
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                return sdf.format(Date(value.toLong()))
            }
        }
    }

    private fun updateClimateChart(list: List<ThongSoEntity>) {
        val tempEntries = ArrayList<Entry>()
        val humidEntries = ArrayList<Entry>()
        val soilMoistEntries = ArrayList<Entry>()

        for (item in list) {
            val timeX = item.timestamp.toFloat()
            tempEntries.add(Entry(timeX, item.airTemperature.toFloat()))
            humidEntries.add(Entry(timeX, item.airHumidity.toFloat()))
            soilMoistEntries.add(Entry(timeX, item.soilMoisture.toFloat()))
        }

        val tempSet = LineDataSet(tempEntries, "Nhiệt độ khí (°C)").apply {
            color = Color.RED
            setCircleColor(Color.RED)
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }

        val humidSet = LineDataSet(humidEntries, "Độ ẩm khí (%)").apply {
            color = Color.CYAN
            setCircleColor(Color.CYAN)
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }

        val soilSet = LineDataSet(soilMoistEntries, "Độ ẩm đất (%)").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2.5f
            circleRadius = 2f
            setDrawValues(false)
        }

        chartClimate.data = LineData(tempSet, humidSet, soilSet)
        chartClimate.invalidate() // Vẽ lại đồ thị
    }

    private fun updateNPKChart(list: List<ThongSoEntity>) {
        val nEntries = ArrayList<Entry>()
        val pEntries = ArrayList<Entry>()
        val kEntries = ArrayList<Entry>()

        for (item in list) {
            val timeX = item.timestamp.toFloat()
            nEntries.add(Entry(timeX, item.npkN.toFloat()))
            pEntries.add(Entry(timeX, item.npkP.toFloat()))
            kEntries.add(Entry(timeX, item.npkK.toFloat()))
        }

        val nSet = LineDataSet(nEntries, "Nitrogen (N)").apply {
            color = Color.parseColor("#4CAF50") // Xanh lá (Đạm)
            setCircleColor(Color.parseColor("#4CAF50"))
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }

        val pSet = LineDataSet(pEntries, "Phosphorus (P)").apply {
            color = Color.parseColor("#FF9800") // Cam (Lân)
            setCircleColor(Color.parseColor("#FF9800"))
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }

        val kSet = LineDataSet(kEntries, "Potassium (K)").apply {
            color = Color.parseColor("#9C27B0") // Tím (Kali)
            setCircleColor(Color.parseColor("#9C27B0"))
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }

        chartNPK.data = LineData(nSet, pSet, kSet)
        chartNPK.invalidate()
    }
}