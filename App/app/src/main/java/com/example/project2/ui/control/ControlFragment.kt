package com.example.project2.ui.control

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.project2.R
import com.example.project2.domain.model.ControlState
import kotlinx.coroutines.launch

class ControlFragment : Fragment(R.layout.fragment_control) {

    private val viewModel: ControlViewModel by viewModels()

    private var isRendering = false

    private lateinit var switchAutoMode: Switch
    private lateinit var switchPump: Switch
    private lateinit var switchFan: Switch
    private lateinit var seekSoil: SeekBar
    private lateinit var seekTemp: SeekBar
    private lateinit var tvSoilThresholdValue: TextView
    private lateinit var tvTempThresholdValue: TextView
    private lateinit var tvModeHint: TextView
    private lateinit var tvPumpState: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupListeners()
        observeUiState()
    }

    private fun bindViews(view: View) {
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
        seekTemp.max = 60 // 0..60 do C
    }

    private fun setupListeners() {
        switchAutoMode.setOnCheckedChangeListener { _, isChecked ->
            applyModeUi(isChecked)
            if (!isRendering) viewModel.setAutoMode(isChecked)
        }

        switchPump.setOnCheckedChangeListener { _, isChecked ->
            if (!isRendering) viewModel.setPump(isChecked)
        }

        switchFan.setOnCheckedChangeListener { _, isChecked ->
            if (!isRendering) viewModel.setFan(isChecked)
        }

        seekSoil.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                tvSoilThresholdValue.text = "Ngưỡng bật bơm: $value %"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                // chi ghi 1 lan khi tha tay -> do spam Firebase
                if (!isRendering) viewModel.setSoilThreshold(sb?.progress ?: 0)
            }
        })

        seekTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                tvTempThresholdValue.text = "Ngưỡng nhiệt độ: $value °C"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (!isRendering) viewModel.setTempThreshold((sb?.progress ?: 0).toDouble())
            }
        })
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderControlState(state.controlState)
                }
            }
        }
    }

    private fun renderControlState(state: ControlState) {
        isRendering = true

        try {
            switchAutoMode.isChecked = state.autoMode
            switchPump.isChecked = if (state.autoMode) state.pumpOn else state.pump
            switchFan.isChecked = if (state.autoMode) state.fanOn else state.fan

            seekSoil.progress = state.soilThreshold
            seekTemp.progress = state.tempThreshold.toInt()
            tvSoilThresholdValue.text = "Ngưỡng bật bơm: ${state.soilThreshold} %"
            tvTempThresholdValue.text = "Ngưỡng nhiệt độ: ${state.tempThreshold.toInt()} °C"

            applyModeUi(state.autoMode)

            // Trang thai bom thuc do ESP32 bao ve
            tvPumpState.text =
                "Bơm: ${if (state.pumpOn) "CHẠY" else "TẮT"} | " +
                        "Relay 2: ${if (state.fanOn) "CHẠY" else "TẮT"}"
        } finally {
            isRendering = false
        }
    }

    private fun applyModeUi(autoOn: Boolean) {
        // O che do AUTO: ESP32 tu quyet dinh bom -> vo hieu hoa nut bom thu cong
        switchPump.isEnabled = !autoOn
        switchFan.isEnabled = !autoOn

        tvModeHint.text = if (autoOn)
            "Đang ở chế độ TỰ ĐỘNG: bơm tự bật theo độ ẩm đất, Relay 2 tự bật theo nhiệt độ."
        else
            "Đang ở chế độ THỦ CÔNG: bạn tự bật/tắt thiết bị."
    }
}