package com.example.project2;
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvCurrentThreshold: TextView
    private lateinit var btnToggleBuzzer: Button
    private lateinit var btnUpdateThreshold: Button
    private lateinit var edtThreshold: EditText

    // Khởi tạo kết nối Firebase Database (Cú pháp Kotlin cực kỳ ngắn gọn)
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val myRef: DatabaseReference = database.getReference("iot_project")

    private var buzzerState: Int = 0 // Trạng thái hiện tại của còi (0: tắt, 1: bật)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ View
        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvCurrentThreshold = findViewById(R.id.tvCurrentThreshold)
        btnToggleBuzzer = findViewById(R.id.btnToggleBuzzer)
        btnUpdateThreshold = findViewById(R.id.btnUpdateThreshold)
        edtThreshold = findViewById(R.id.edtThreshold)

        // --- CHIỀU 1: LẮNG NGHE DỮ LIỆU THỜI GIAN THỰC TỪ FIREBASE ---
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Đọc Nhiệt độ
                if (dataSnapshot.hasChild("temperature")) {
                    val temp = dataSnapshot.child("temperature").getValue(Double::class.java)
                    tvTemperature.text = "Nhiệt độ: $temp °C"
                }

                // Đọc Độ ẩm
                if (dataSnapshot.hasChild("humidity")) {
                    val humid = dataSnapshot.child("humidity").getValue(Double::class.java)
                    tvHumidity.text = "Độ ẩm: $humid %"
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
            if (inputStr.isNotEmpty()) {
                val newThreshold = inputStr.toDoubleOrNull()
                if (newThreshold != null) {
                    // Đẩy ngưỡng mới lên Firebase
                    myRef.child("temperature_threshold").setValue(newThreshold)
                    edtThreshold.text.clear()
                    Toast.makeText(this, "Đã cập nhật ngưỡng mới!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Vui lòng nhập số hợp lệ!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Không được để trống ngưỡng!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}