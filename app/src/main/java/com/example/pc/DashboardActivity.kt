package com.example.pc

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : BaseActivity() {

    private lateinit var dashIpText: TextView
    private lateinit var uptimeText: TextView
    private lateinit var cpuSpeedometer: SpeedometerView
    private lateinit var cpuNameText: TextView
    private lateinit var cpuDetailText: TextView
    private lateinit var ramSpeedometer: SpeedometerView
    private lateinit var ramDetailText: TextView
    private lateinit var gpuSpeedometer: SpeedometerView
    private lateinit var gpuNameText: TextView
    private lateinit var gpuDetailText: TextView
    private lateinit var netDownText: TextView
    private lateinit var netUpText: TextView
    private lateinit var containers: Map<WidgetType, LinearLayout>
    private lateinit var openConstructorButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            fetchStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        hideSystemUI()
        initViews()

        val ip = intent.getStringExtra("DEVICE_IP") ?: ""
        if (ip.isNotEmpty()) {
            dashIpText.text = "IP: $ip"
            handler.post(runnable)

            openConstructorButton.setOnClickListener {
                val intent = Intent(this, CustomDashboardActivity::class.java).apply {
                    putExtra("DEVICE_IP", ip)
                }
                startActivity(intent)
            }
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun initViews() {
        dashIpText = findViewById(R.id.dashIpText)
        uptimeText = findViewById(R.id.uptimeText)
        cpuSpeedometer = findViewById(R.id.cpuSpeedometer)
        cpuNameText = findViewById(R.id.cpuNameText)
        cpuDetailText = findViewById(R.id.cpuDetailText)
        ramSpeedometer = findViewById(R.id.ramSpeedometer)
        ramDetailText = findViewById(R.id.ramDetailText)
        gpuSpeedometer = findViewById(R.id.gpuSpeedometer)
        gpuNameText = findViewById(R.id.gpuNameText)
        gpuDetailText = findViewById(R.id.gpuDetailText)
        netDownText = findViewById(R.id.netDownText)
        netUpText = findViewById(R.id.netUpText)
        openConstructorButton = findViewById(R.id.openConstructorButton)

        containers = mapOf(
            WidgetType.CONTROLS to findViewById(R.id.controlsContainer),
            WidgetType.AUDIO_MIXER to findViewById(R.id.mixerContainer),
            WidgetType.STORAGE to findViewById(R.id.disksContainer),
            WidgetType.COOLING to findViewById(R.id.fansContainer),
            WidgetType.TOP_PROCESSES to findViewById(R.id.procsContainer)
        )
    }

    private fun fetchStats() {
        currentApi?.getStats()?.enqueue(object : Callback<PCStats> {
            override fun onResponse(call: Call<PCStats>, response: Response<PCStats>) {
                if (response.isSuccessful) response.body()?.let { updateUI(it) }
                handler.postDelayed(runnable, 1000) // Рекурсивный вызов через секунду ПОСЛЕ ответа
            }
            override fun onFailure(call: Call<PCStats>, t: Throwable) {
                uptimeText.text = "OFFLINE"
                handler.postDelayed(runnable, 1000)
            }
        })
    }

    private fun updateUI(s: PCStats) {
        uptimeText.text = "Uptime: ${s.uptime} h"
        cpuSpeedometer.setValue(s.cpu.usage.toFloat())
        cpuNameText.text = s.cpu.name.replace("AMD ", "").replace("Intel(R) Core(TM) ", "").replace("Ryzen ", "R ").trim()
        cpuDetailText.text = "${s.cpu.freq.toInt()} MHz | ${s.cpu.temp}°C"
        ramSpeedometer.setValue(s.ram.usage.toFloat())
        ramDetailText.text = "${s.ram.used} / ${s.ram.total} GB"
        s.gpu.getOrNull(0)?.let { g ->
            gpuSpeedometer.setValue(g.load.toFloat())
            gpuNameText.text = g.name.replace("NVIDIA GeForce ", "").replace("AMD Radeon ", "").trim()
            gpuDetailText.text = "${g.temp}°C | VRAM: ${g.mem_p}%"
        }
        netDownText.text = "↓ ${s.network.down_kbps.toInt()} KB/s"
        netUpText.text = "↑ ${s.network.up_kbps.toInt()} KB/s"

        updateDynamicWidget(WidgetType.CONTROLS) { WidgetFactory.create(WidgetConfig(WidgetType.CONTROLS, 0, 0, 1, 1), this, ::showScreenshotDialog, ::sendSleepCommand, ::sendShutdownCommand) }
        updateDynamicWidget(WidgetType.AUDIO_MIXER) { WidgetFactory.create(WidgetConfig(WidgetType.AUDIO_MIXER, 0, 0, 1, 1), this, onVolumeChange = ::sendMixerVolume) }
        updateDynamicWidget(WidgetType.STORAGE) { WidgetFactory.create(WidgetConfig(WidgetType.STORAGE, 0, 0, 1, 1), this) }
        updateDynamicWidget(WidgetType.COOLING) { WidgetFactory.create(WidgetConfig(WidgetType.COOLING, 0, 0, 1, 1), this) }
        updateDynamicWidget(WidgetType.TOP_PROCESSES) { WidgetFactory.create(WidgetConfig(WidgetType.TOP_PROCESSES, 0, 0, 1, 1), this, onKill = { pid -> killProcess(pid) { fetchStats() } }) }

        containers.values.forEach { (it.getChildAt(0) as? UpdatableWidget)?.updateData(s) }
    }

    private fun updateDynamicWidget(type: WidgetType, factory: () -> View) {
        containers[type]?.let {
            if (it.childCount == 0) it.addView(factory())
            it.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}