package com.example.pc

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : AppCompatActivity() {

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
    private lateinit var disksContainer: LinearLayout
    private lateinit var fansContainer: LinearLayout
    private lateinit var procsContainer: LinearLayout
    private lateinit var mixerContainer: LinearLayout
    private lateinit var controlsContainer: LinearLayout
    private lateinit var openConstructorButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var currentApi: ApiService? = null

    private val runnable = object : Runnable {
        override fun run() {
            fetchStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        val ip = intent.getStringExtra("DEVICE_IP") ?: ""
        if (ip.isNotEmpty()) {
            currentApi = RetrofitClient.getClient(ip)
            dashIpText.text = "IP: $ip"
            handler.post(runnable)

            openConstructorButton.setOnClickListener {
                val intent = Intent(this, CustomDashboardActivity::class.java)
                intent.putExtra("DEVICE_IP", ip)
                startActivity(intent)
            }
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
        disksContainer = findViewById(R.id.disksContainer)
        fansContainer = findViewById(R.id.fansContainer)
        procsContainer = findViewById(R.id.procsContainer)
        mixerContainer = findViewById(R.id.mixerContainer)
        controlsContainer = findViewById(R.id.controlsContainer)
        openConstructorButton = findViewById(R.id.openConstructorButton)
    }

    private fun fetchStats() {
        currentApi?.getStats()?.enqueue(object : Callback<PCStats> {
            override fun onResponse(call: Call<PCStats>, response: Response<PCStats>) {
                if (response.isSuccessful) { response.body()?.let { updateUI(it) } }
            }
            override fun onFailure(call: Call<PCStats>, t: Throwable) { uptimeText.text = "OFFLINE" }
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
        netDownText.text = "↓ ${s.network.down_kbps} KB/s"
        netUpText.text = "↑ ${s.network.up_kbps} KB/s"

        updateWidgetContainer(controlsContainer, WidgetFactory.createControlsCard(this, ::showScreenshotDialog, ::sendSleepCommand, ::sendShutdownCommand))
        updateWidgetContainer(mixerContainer, WidgetFactory.createAudioMixerCard(this) { name, vol -> sendMixerVolume(name, vol) })
        updateWidgetContainer(disksContainer, WidgetFactory.createDisksCard(this))
        updateWidgetContainer(fansContainer, WidgetFactory.createFansCard(this))
        updateWidgetContainer(procsContainer, WidgetFactory.createProcsCard(this) { pid -> killProcess(pid) })

        // Update existing widgets if they are already in containers
        (controlsContainer.getChildAt(0) as? UpdatableWidget)?.updateData(s)
        (mixerContainer.getChildAt(0) as? UpdatableWidget)?.updateData(s)
        (disksContainer.getChildAt(0) as? UpdatableWidget)?.updateData(s)
        (fansContainer.getChildAt(0) as? UpdatableWidget)?.updateData(s)
        (procsContainer.getChildAt(0) as? UpdatableWidget)?.updateData(s)
    }

    private fun updateWidgetContainer(container: LinearLayout, widget: View) {
        if (container.childCount == 0) {
            container.addView(widget)
        }
        container.visibility = View.VISIBLE
    }

    private fun showScreenshotDialog() {
        Toast.makeText(this, "Taking Screenshot...", Toast.LENGTH_SHORT).show()
        currentApi?.getScreenshot()?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val view = layoutInflater.inflate(R.layout.dialog_screenshot, null)
                    view.findViewById<ImageView>(R.id.screenshotImage).setImageBitmap(bitmap)
                    AlertDialog.Builder(this@DashboardActivity).setTitle("PC Screenshot").setView(view).setPositiveButton("Close", null).show()
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })
    }

    private fun sendMixerVolume(appName: String, volume: Int) {
        currentApi?.setMixerVolume(appName, volume)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {}
            override fun onFailure(call: Call<String>, t: Throwable) {}
        })
    }

    private fun killProcess(pid: Int) {
        currentApi?.killProcess(pid)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                fetchStats()
            }
            override fun onFailure(call: Call<String>, t: Throwable) {}
        })
    }

    private fun sendShutdownCommand() {
        showPowerActionDialog("выключить", "Выключение") {
            currentApi?.shutdownPC()?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {}
                override fun onFailure(call: Call<String>, t: Throwable) {}
            })
        }
    }

    private fun sendSleepCommand() {
        showPowerActionDialog("отправить в спящий режим", "Сон") {
            currentApi?.sleepPC()?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {}
                override fun onFailure(call: Call<String>, t: Throwable) {}
            })
        }
    }

    private fun showPowerActionDialog(actionName: String, title: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage("Вы уверены, что хотите $actionName ПК?").setPositiveButton("Да") { _, _ -> onConfirm() }.setNegativeButton("Нет", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}
