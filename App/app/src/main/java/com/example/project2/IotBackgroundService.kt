package com.example.project2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class IoTBackgroundService : Service() {

    private val database = FirebaseDatabase.getInstance()
    private val myRef = database.getReference(FirebasePaths.ROOT)
    private var valueEventListener: ValueEventListener? = null

    private val CHANNEL_ID = "IoT_Monitor_Channel"
    private val NOTIFICATION_ID = 999
    private val ALERT_NOTIFICATION_ID = 888

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Đưa Service lên chế độ Tiền cảnh (Foreground) ngay lập tức khi chạy
        val notification = createForegroundNotification("Hệ thống đang giám sát môi trường ngầm...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Bắt đầu lắng nghe Firebase Realtime Database
        startListeningFirebase()

        // START_STICKY: Nếu hệ thống thiếu RAM và kill service, nó sẽ tự động khởi động lại khi có RAM trống
        return START_STICKY
    }

    private fun startListeningFirebase() {
        var tempThreshold = 35.0 // Mặc định

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.hasChild("temperature_threshold")) {
                    tempThreshold = snapshot.child("control").child("temp_threshold").getValue(Double::class.java) ?: 35.0
                }

                if (snapshot.hasChild("temperature")) {
                    val currentTemp = snapshot.child("air_temperature").getValue(Double::class.java) ?: 0.0

                    // Cập nhật nhiệt độ liên tục lên thanh thông báo thường trực
                    updateForegroundNotification("Nhiệt độ hiện tại: $currentTemp °C")

                    // KIỂM TRA VÀ BẮN THÔNG BÁO KHẨN CẤP KHI VƯỢT NGƯỠNG
                    if (currentTemp > tempThreshold) {
                        sendEmergencyAlertNotification(currentTemp, tempThreshold)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        myRef.addValueEventListener(valueEventListener!!)
    }

    // Tạo kênh Notification (Bắt buộc từ Android 8.0 trở lên)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Giám sát Hệ thống IoT",
                NotificationManager.IMPORTANCE_LOW // Để thấp để đỡ phát tiếng "ting ting" liên tục mỗi 3 giây
            ).apply {
                description = "Kênh hiển thị thông số môi trường từ ESP32"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IoT Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true) // Không cho phép người dùng gạt tay để xóa
            .build()
    }

    private fun updateForegroundNotification(newText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createForegroundNotification(newText))
    }

    // Hàm bắn thông báo khẩn cấp (Hú chuông, rung màn hình)
    private fun sendEmergencyAlertNotification(temp: Double, threshold: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ CẢNH BÁO: NHIỆT ĐỘ QUÁ CAO!")
            .setContentText("Nhiệt độ phòng là $temp°C, vượt ngưỡng cấu hình ($threshold°C)!")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Hiển thị dạng popup tràn lên màn hình
            .setDefaults(Notification.DEFAULT_ALL) // Bật cả chuông mặc định và rung công suất lớn
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hủy kết nối Firebase khi dừng Service để tránh rò rỉ bộ nhớ
        valueEventListener?.let { myRef.removeEventListener(it) }
    }
}