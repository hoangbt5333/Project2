package com.example.project2;
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvCurrentThreshold: TextView
    private lateinit var btnToggleBuzzer: Button
    private lateinit var btnUpdateThreshold: Button
    private lateinit var edtThreshold: EditText
    private lateinit var lineChart: LineChart

    // Khởi tạo kết nối Firebase Database (Cú pháp Kotlin cực kỳ ngắn gọn)
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val myRef: DatabaseReference = database.getReference("iot_project")

    private var buzzerState: Int = 0 // Trạng thái hiện tại của còi (0: tắt, 1: bật)

    // Khởi tạo Room Database
    private val roomDb by lazy { AppDatabase.getDatabase(this) }

    // Biến phục vụ thuật toán CHỐNG SPAM DỮ LIỆU
    private var lastSavedTemp = 0.0
    private var lastSavedHumid = 0.0
    private var lastSavedTime = 0L

    // Cấu hình bộ lọc chống spam
    private val TIME_INTERVAL_LIMIT = 60000 // 1 phút (In miliseconds) mới cho phép ghi đè một lần nếu thông số tĩnh
    private val DELTA_TEMP_LIMIT = 0.5     // Lệch quá 0.5 độ C thì ghi luôn không đợi thời gian
    private val DELTA_HUMID_LIMIT = 2.0    // Lệch quá 2% độ ẩm thì ghi luôn

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        com.google.firebase.FirebaseApp.initializeApp(this)

        // Ánh xạ View
        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvCurrentThreshold = findViewById(R.id.tvCurrentThreshold)
        btnToggleBuzzer = findViewById(R.id.btnToggleBuzzer)
        btnUpdateThreshold = findViewById(R.id.btnUpdateThreshold)
        edtThreshold = findViewById(R.id.edtThreshold)
        lineChart = findViewById(R.id.lineChart)
        setupChart()


        // --- CHIỀU 1: LẮNG NGHE DỮ LIỆU THỜI GIAN THỰC TỪ FIREBASE ---
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                var currentTemp = 0.0
                var currentHumid = 0.0

                // Đọc Nhiệt độ
                if (dataSnapshot.hasChild("temperature")) {
                    currentTemp = dataSnapshot.child("temperature").getValue(Double::class.java) ?: 0.0
                    tvTemperature.text = "Nhiệt độ: $currentTemp °C"
                }

                // Đọc Độ ẩm
                if (dataSnapshot.hasChild("humidity")) {
                    currentHumid = dataSnapshot.child("humidity")
                        .getValue(Double::class.java) ?: 0.0

                    tvHumidity.text = "Độ ẩm: $currentHumid %"
                }

                // Đọc Ngưỡng nhiệt độ cảnh báo
                if (dataSnapshot.hasChild("temperature_threshold")) {
                    val threshold = dataSnapshot.child("temperature_threshold").getValue(Double::class.java)
                    tvCurrentThreshold.text = "Ngưỡng cảnh báo hiện tại: $threshold °C"
                }

                // Đồng bộ trạng thái nút bấm còi Buzzer
                if (dataSnapshot.hasChild("buzzer_trigger")) {
                    buzzerState = dataSnapshot.child("buzzer_trigger").getValue(Int::class.java) ?: 0
                    if (buzzerState == 1) {
                        btnToggleBuzzer.text = "CÒI ĐANG KÊU (BẤM ĐỂ TẮT)"
                        btnToggleBuzzer.setBackgroundColor(Color.RED)
                    } else {
                        btnToggleBuzzer.text = "CÒI ĐANG TẮT (BẤM ĐỂ BẬT)"
                        btnToggleBuzzer.setBackgroundColor(Color.parseColor("#388E3C")) // Màu xanh lá
                    }
                }

                //Chống spam ghi vào ROOM
                val currentTime = System.currentTimeMillis()
                val isTimePassed = (currentTime - lastSavedTime) >= TIME_INTERVAL_LIMIT
                val isTempChangedSignificant = abs(currentTemp - lastSavedTemp) >= DELTA_TEMP_LIMIT
                val isHumidChangedSignificant = abs(currentHumid - lastSavedHumid) >= DELTA_HUMID_LIMIT

                // Điều kiện chuẩn: Đủ thời gian chờ HOẶC có biến động mạnh về thông số môi trường
                if (isTimePassed || isTempChangedSignificant || isHumidChangedSignificant) {
                    // Cập nhật lại mốc so sánh
                    lastSavedTemp = currentTemp
                    lastSavedHumid = currentHumid
                    lastSavedTime = currentTime

                    // Thực hiện ghi vào Room Database bằng luồng phụ (Coroutines)
                    lifecycleScope.launch {
                        val entity = ThongSoEntity(
                            temperature = currentTemp,
                            humidity = currentHumid,
                            timestamp = currentTime
                        )
                        roomDb.thongSoDao().insert(entity)
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity, "Lỗi đọc Firebase: ${databaseError.message}", Toast.LENGTH_SHORT).show();
            }
        })

        // --- CHIỀU 2: GỬI LỆNH ĐIỀU KHIỂN LÊN FIREBASE ---

        // 1. Xử lý bấm nút Bật/Tắt Còi công tắc Lamda ngắn gọn
        btnToggleBuzzer.setOnClickListener {
            val nextState = if (buzzerState == 1)  0 else 1
            myRef.child("buzzer_trigger").setValue(nextState)
        }

        // 2. Xử lý cài đặt Ngưỡng nhiệt độ mới
        btnUpdateThreshold.setOnClickListener {
            val inputStr = edtThreshold.text.toString().trim()
            val newThreshold: Double = if (inputStr.isEmpty()) {
                // Nếu không nhập gì, tự động lấy giá trị mặc định là 35.0
                35.0
            } else {
                // Nếu có nhập, thử ép kiểu sang số thực (trả về null nếu nhập sai ký tự)
                inputStr.toDoubleOrNull() ?: -1.0
            }

            // Kiểm tra tính hợp lệ của giá trị số thực
            if (newThreshold != -1.0) {
                // Đẩy ngưỡng nhiệt độ lên Firebase (dù là nhập vào hay là giá trị mặc định 35.0)
                myRef.child("temperature_threshold").setValue(newThreshold)
                edtThreshold.text.clear()

                if (inputStr.isEmpty()) {
                    Toast.makeText(this, "Để trống - Tự động đặt ngưỡng mặc định là 35°C", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Đã cập nhật ngưỡng mới: $newThreshold°C", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Vui lòng nhập số hợp lệ!", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            roomDb.thongSoDao().getRecentHistory().collect { lichSuList ->
                // Do Room trả về danh sách sắp xếp mới nhất lên đầu (DESC), ta đảo ngược lại để vẽ biểu đồ từ trái qua phải (cũ đến mới)
                val sortedList = lichSuList.reversed()
                updateChartData(sortedList)
            }
        }
    }

    private fun setupChart() {

        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)

        val xAxis = lineChart.xAxis

        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)

        // Giảm số lượng label
        xAxis.labelCount = 3

        // Không ép vẽ thêm label
        xAxis.setAvoidFirstLastClipping(true)

        // Xoay chữ cho đỡ đè
        xAxis.labelRotationAngle = -30f

        // Khoảng cách tối thiểu giữa các label
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        xAxis.valueFormatter = object : ValueFormatter() {

            private val sdf = SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )

            override fun getFormattedValue(value: Float): String {
                return sdf.format(Date(value.toLong()))
            }
        }

        lineChart.axisRight.isEnabled = false
    }

    private fun updateChartData(list: List<ThongSoEntity>) {

        val tempEntries = ArrayList<Entry>()
        val humidEntries = ArrayList<Entry>()

        val baseTime = list.firstOrNull()?.timestamp ?: 0L

        for (item in list) {

            val seconds =
                (item.timestamp - baseTime) / 1000f

            tempEntries.add(
                Entry(seconds, item.temperature.toFloat())
            )

            humidEntries.add(
                Entry(seconds, item.humidity.toFloat())
            )
        }

        val tempDataSet = LineDataSet(
            tempEntries,
            "Nhiệt độ (°C)"
        ).apply {

            color = Color.RED
            setCircleColor(Color.RED)

            lineWidth = 2f
            circleRadius = 3f

            setDrawValues(false)
        }

        val humidDataSet = LineDataSet(
            humidEntries,
            "Độ ẩm (%)"
        ).apply {

            color = Color.BLUE
            setCircleColor(Color.BLUE)

            lineWidth = 2f
            circleRadius = 3f

            setDrawValues(false)
        }

        lineChart.xAxis.valueFormatter =
            object : ValueFormatter() {

                override fun getFormattedValue(
                    value: Float
                ): String {

                    val totalSec = value.toInt()

                    val min = totalSec / 60
                    val sec = totalSec % 60

                    return String.format(
                        "%02d:%02d",
                        min,
                        sec
                    )
                }
            }

        lineChart.data =
            LineData(tempDataSet, humidDataSet)

        lineChart.invalidate()
    }
}