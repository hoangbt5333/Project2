package com.example.project2.ui.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.project2.FirebasePaths
import com.example.project2.R
import com.google.firebase.database.*

/**
 * Phần ĐIỀU KHIỂN (Manual + Auto) ghi lệnh xuống Firebase để ESP32 đọc và thực thi.
 *
 * Cấu trúc dữ liệu đề xuất trên Firebase Realtime Database:
 * smart_agriculture/
 *   control/
 *     auto_mode      : Boolean   (true = ESP32 tự bật/tắt bơm theo ngưỡng)
 *     pump           : Boolean   (lệnh bật/tắt bơm khi ở chế độ thủ công)
 *     fan            : Boolean   (ví dụ thiết bị thứ 2: quạt / mái che)
 *     soil_threshold : Int       (ngưỡng % độ ẩm đất để auto bật bơm)
 *     temp_threshold : Double    (ngưỡng nhiệt độ để auto bật quạt / cảnh báo)
 *   state/  (tuỳ chọn: ESP32 ghi ngược trạng thái thực tế để app xác nhận)
 *     pump_running   : Boolean
 *
 * LƯU Ý CHỐNG VÒNG LẶP: khi nhận dữ liệu từ Firebase ta set cờ isUpdatingFromCloud=true
 * trước khi gán giá trị cho widget, để listener onChange của widget không ghi đè ngược lên cloud.
 */
class ControlFragment : Fragment() {

    private val database = FirebaseDatabase.getInstance()
    private val controlRef = database.getReference(FirebasePaths.CONTROL)
    private val stateRef = database.getReference(FirebasePaths.STATE)
    private var controlListener: ValueEventListener? = null
    private var stateListener: ValueEventListener? = null

    private lateinit var switchAutoMode: Switch
    private lateinit var switchPump: Switch
    private lateinit var switchFan: Switch
    private lateinit var seekSoil: SeekBar
    private lateinit var seekTemp: SeekBar
    private lateinit var tvSoilThresholdValue: TextView
    private lateinit var tvTempThresholdValue: TextView
    private lateinit var tvModeHint: TextView
    private lateinit var tvPumpState: TextView

    // Cờ tránh vòng lặp ghi/đọc khi đang đồng bộ từ cloud xuống UI
    private var isUpdatingFromCloud = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_control, container, false)

        switchAutoMode = view.findViewById(R.id.switchAutoMode)
        switchPump = view.findViewById(R.id.switchPump)
        switchFan = view.findViewById(R.id.switchFan)
        seekSoil = view.findViewById(R.id.seekSoilThreshold)
        seekTemp = view.findViewById(R.id.seekTempThreshold)
        tvSoilThresholdValue = view.findViewById(R.id.tvSoilThresholdValue)
        tvTempThresholdValue = view.findViewById(R.id.tvTempThresholdValue)
        tvModeHint = view.findViewById(R.id.tvModeHint)
        tvPumpState = view.findViewById(R.id.tvPumpState)

        seekSoil.max = 100
        seekTemp.max = 60 // 0..60 °C

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        listenCloud()
    }

    private fun setupListeners() {
        // CHẾ ĐỘ TỰ ĐỘNG: khi bật auto, khoá điều khiển bơm thủ công
        switchAutoMode.setOnCheckedChangeListener { _, isChecked ->
            applyModeUi(isChecked)
            if (!isUpdatingFromCloud) controlRef.child("auto_mode").setValue(isChecked)
        }

        switchPump.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromCloud) controlRef.child("pump").setValue(isChecked)
        }

        switchFan.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromCloud) controlRef.child("fan").setValue(isChecked)
        }

        seekSoil.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                tvSoilThresholdValue.text = "Ngưỡng bật bơm: $value %"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                // chỉ ghi 1 lần khi thả tay -> đỡ spam Firebase
                if (!isUpdatingFromCloud) controlRef.child("soil_threshold").setValue(sb?.progress ?: 0)
            }
        })

        seekTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                tvTempThresholdValue.text = "Ngưỡng nhiệt độ: $value °C"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (!isUpdatingFromCloud) controlRef.child("temp_threshold").setValue((sb?.progress ?: 0).toDouble())
            }
        })
    }

    private fun applyModeUi(autoOn: Boolean) {
        // Ở chế độ AUTO: ESP32 tự quyết định bơm -> vô hiệu hoá nút bơm thủ công
        switchPump.isEnabled = !autoOn
        tvModeHint.text = if (autoOn)
            "Đang ở chế độ TỰ ĐỘNG: hệ thống tự bật bơm khi độ ẩm đất thấp hơn ngưỡng."
        else
            "Đang ở chế độ THỦ CÔNG: bạn tự bật/tắt thiết bị."
    }

    private fun listenCloud() {
        controlListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                isUpdatingFromCloud = true
                try {
                    val auto = snapshot.child("auto_mode").getValue(Boolean::class.java) ?: false
                    val pump = snapshot.child("pump").getValue(Boolean::class.java) ?: false
                    val fan = snapshot.child("fan").getValue(Boolean::class.java) ?: false
                    val soilTh = snapshot.child("soil_threshold").getValue(Int::class.java) ?: 40
                    val tempTh = snapshot.child("temp_threshold").getValue(Double::class.java) ?: 35.0

                    switchAutoMode.isChecked = auto
                    switchPump.isChecked = pump
                    switchFan.isChecked = fan
                    seekSoil.progress = soilTh
                    seekTemp.progress = tempTh.toInt()
                    tvSoilThresholdValue.text = "Ngưỡng bật bơm: $soilTh %"
                    tvTempThresholdValue.text = "Ngưỡng nhiệt độ: ${tempTh.toInt()} °C"
                    applyModeUi(auto)
                } finally {
                    isUpdatingFromCloud = false
                }
            }
            override fun onCancelled(error: DatabaseError) {
                if (isAdded) Toast.makeText(requireContext(), "Lỗi điều khiển: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        controlRef.addValueEventListener(controlListener!!)

        // Xác nhận trạng thái bơm thực tế do ESP32 báo về (nếu có)
        stateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val running = snapshot.child("pump_running").getValue(Boolean::class.java)
                tvPumpState.text = when (running) {
                    true -> "\uD83D\uDFE2 Bơm đang CHẠY"
                    false -> "\u26AA Bơm đang TẮT"
                    null -> "\u26AA Chưa nhận phản hồi từ thiết bị"
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        stateRef.addValueEventListener(stateListener!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controlListener?.let { controlRef.removeEventListener(it) }
        stateListener?.let { stateRef.removeEventListener(it) }
    }
}
