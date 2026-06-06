package com.example.project2

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.project2.ui.control.ControlFragment
import com.example.project2.ui.history.HistoryFragment
import com.example.project2.ui.home.HomeFragment

class MyViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> HistoryFragment()
            2 -> ControlFragment()
            else -> HomeFragment()
        }
    }
}