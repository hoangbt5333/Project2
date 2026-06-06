package com.example.project2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Có thể để trống cho đồ án.
            // Nếu muốn, bạn có thể Toast khi user từ chối.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestNotificationPermissionIfNeeded()
        com.google.firebase.FirebaseApp.initializeApp(this)

        viewPager = findViewById(R.id.viewPager)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // 1. Cài đặt Adapter cho ViewPager2
        val adapter = MyViewPagerAdapter(this)
        viewPager.adapter = adapter

        // Tùy chọn: Giữ trạng thái cả 3 Fragment trong bộ nhớ, không bị reload lại giao diện khi lướt qua lại
        viewPager.offscreenPageLimit = 2

        // --- CHIỀU 1: BẤM NÚT ĐÁY -> ĐỔI MÀN HÌNH VIEW PAGER ---
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    viewPager.currentItem = 0 // Cuộn về Fragment số 0
                    true
                }
                R.id.nav_insight -> {
                    viewPager.currentItem = 1 // Cuộn về Fragment số 1
                    true
                }
                R.id.nav_control -> {
                    viewPager.currentItem = 2 // Cuộn về Fragment số 2
                    true
                }
                else -> false
            }
        }

        // --- CHIỀU 2: VUỐT MÀN HÌNH -> TỰ ĐỘNG SÁNG NÚT ĐÁY TƯƠNG ỨNG ---
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Đồng bộ hóa Item ID được chọn trên thanh BottomNav dựa theo vị trí vuốt dừng lại
                when (position) {
                    0 -> bottomNavigation.selectedItemId = R.id.nav_home
                    1 -> bottomNavigation.selectedItemId = R.id.nav_insight
                    2 -> bottomNavigation.selectedItemId = R.id.nav_control
                }
            }
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

