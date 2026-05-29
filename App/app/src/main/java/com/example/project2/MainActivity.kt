package com.example.project2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        com.google.firebase.FirebaseApp.initializeApp(this)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        // 1. Mặc định khi vừa mở App, hiển thị luôn màn hình Home (Dashboard)
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // 2. Bắt sự kiện click chọn các Tab trên Bottom Navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_history -> {
                    replaceFragment(HistoryFragment())
                    true
                }
                R.id.nav_control -> {
                    replaceFragment(ControlFragment())
                    true
                }
                else -> false
            }
        }
    }

    // Hàm tiện ích hỗ trợ thay thế Fragment mượt mà
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}