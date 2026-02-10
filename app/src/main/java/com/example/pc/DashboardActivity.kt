package com.example.pc

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : AppCompatActivity() {

    // Вьюшки
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

    private val handler = Handler(Looper.getMainLooper())
    private var currentApi: ApiService? = null
    private val userTouchingSliders = mutableSetOf<String>()

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
        // Update non-widget views
        uptimeText.text = "Uptime: ${s.uptime} h"
        cpuSpeedometer.setValue(s.cpu.usage.toFloat())
        cpuNameText.text = s.cpu.name.replace("AMD ", "").replace("Intel(R) Core(TM) ", "").replace("Ryzen ", "R ").replace("with Radeon Graphics", "").trim()
        cpuDetailText.text = "${s.cpu.freq.toInt()} MHz | ${s.cpu.temp}°C"
        ramSpeedometer.setValue(s.ram.usage.toFloat())
        ramDetailText.text = "${s.ram.used} / ${s.ram.total} GB"
        s.gpu.getOrNull(0)?.let { g ->
            gpuSpeedometer.setValue(g.load.toFloat())
            gpuNameText.text = g.name.replace("NVIDIA GeForce ", "").replace("AMD Radeon ", "").trim()
            gpuDetailText.text = "${g.temp}°C | VRAM: ${g.mem_p}%"
        } ?: gpuSpeedometer.setValue(0f)
        netDownText.text = "↓ ${s.network.down_kbps} KB/s"
        netUpText.text = "↑ ${s.network.up_kbps} KB/s"

        // Update widget containers
        updateWidgetContainer(controlsContainer, createControlsCard())
        updateWidgetContainer(mixerContainer, createAudioMixerCard(s.audio_sessions))
        updateWidgetContainer(disksContainer, createDisksCard(s.disks))
        updateWidgetContainer(fansContainer, createFansCard(s.fans))
        updateWidgetContainer(procsContainer, createProcsCard(s.procs))
    }

    private fun updateWidgetContainer(container: LinearLayout, widget: View?) {
        container.removeAllViews()
        widget?.let { container.addView(it) }
        container.visibility = if (widget == null) View.GONE else View.VISIBLE
    }

    // WIDGET CREATION HELPERS

    private fun createWidgetCard(): CardView {
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = 24
            layoutParams = params
        }
    }

    private fun createWidgetTitle(title: String, colorRes: Int): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, colorRes))
            textSize = 14f
            setPadding(32, 24, 32, 16)
        }
    }
    
    // WIDGET CREATION FUNCTIONS

    private fun createControlsCard(): CardView {
        val card = createWidgetCard()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle("CONTROLS", android.R.color.holo_blue_light))

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 0, 32, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --- Screenshot Button ---
        val screenshotButton = Button(this).apply {
            text = "Screenshot"
            setOnClickListener { showScreenshotDialog() }
        }
        val screenshotParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        contentLayout.addView(screenshotButton, screenshotParams)

        // --- Shutdown Button ---
        val shutdownButton = Button(this).apply {
            text = "Shutdown"
            setOnClickListener {
                showPowerActionDialog("выключить", "Выключение") { sendShutdownCommand() }
            }
        }
        val shutdownParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8; marginEnd = 8 }
        contentLayout.addView(shutdownButton, shutdownParams)

        // --- Sleep Button ---
        val sleepButton = Button(this).apply {
            text = "Sleep"
            setOnClickListener {
                showPowerActionDialog("отправить в спящий режим", "Сон") { sendSleepCommand() }
            }
        }
        val sleepParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8 }
        contentLayout.addView(sleepButton, sleepParams)

        layout.addView(contentLayout)
        card.addView(layout)
        return card
    }

    private fun createAudioMixerCard(sessions: List<MixerSession>): CardView? {
        if (sessions.isEmpty()) return null

        val card = createWidgetCard()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle("AUDIO MIXER", R.color.accent_neon))

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (session in sessions) {
            val view = layoutInflater.inflate(R.layout.item_mixer_app, contentLayout, false)
            val appNameText = view.findViewById<TextView>(R.id.appNameText)
            val slider = view.findViewById<SeekBar>(R.id.appVolumeSlider)
            val percentText = view.findViewById<TextView>(R.id.appVolumePercentText)

            appNameText.text = session.name
            percentText.text = "${session.volume}%"
            if (!userTouchingSliders.contains(session.name)) {
                slider.progress = session.volume
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { percentText.text = "$progress%" }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userTouchingSliders.add(session.name) }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userTouchingSliders.remove(session.name)
                    seekBar?.let { sendMixerVolume(session.name, it.progress) }
                }
            })
            contentLayout.addView(view)
        }
        layout.addView(contentLayout)
        card.addView(layout)
        return card
    }

    private fun createDisksCard(disks: List<DiskData>): CardView? {
        if (disks.isEmpty()) return null

        val card = createWidgetCard()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle("STORAGE", R.color.color_disk))

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (disk in disks) {
            val title = TextView(this).apply {
                text = "${disk.dev} ${disk.used} / ${disk.total} GB"
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = disk.percent.toInt()
                progressDrawable = ContextCompat.getDrawable(context, R.drawable.bar_progress_disk)
                layoutParams = LinearLayout.LayoutParams(-1, 20).apply { topMargin = 15; bottomMargin = 20 }
            }
            contentLayout.addView(title)
            contentLayout.addView(bar)
        }
        layout.addView(contentLayout)
        card.addView(layout)
        return card
    }

    private fun createFansCard(fans: List<FanData>): CardView? {
        if (fans.isEmpty()) return null

        val card = createWidgetCard()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle("COOLING (RPM)", R.color.color_fan))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 0, 32, 24)
        }

        for (fan in fans) {
            val fanLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                gravity = Gravity.CENTER
            }
            val rpm = TextView(this).apply {
                text = "${fan.rpm}"
                textSize = 18f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            val name = TextView(this).apply {
                text = fan.name
                textSize = 10f
                setTextColor(Color.GRAY)
            }
            fanLayout.addView(rpm)
            fanLayout.addView(name)
            row.addView(fanLayout)
        }
        layout.addView(row)
        card.addView(layout)
        return card
    }

    private fun createProcsCard(procs: List<ProcessData>): CardView? {
        if (procs.isEmpty()) return null

        val card = createWidgetCard()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle("TOP PROCESSES", android.R.color.darker_gray))

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (proc in procs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val name = TextView(this).apply {
                text = proc.name
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val usage = TextView(this).apply {
                text = "${proc.cpu}%"
                setTextColor(ContextCompat.getColor(context, R.color.color_cpu))
                minWidth = 120
                gravity = Gravity.END
            }
            val killButton = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.parseColor("#FF4081"), android.graphics.PorterDuff.Mode.SRC_IN)
                val params = LinearLayout.LayoutParams(60, 60)
                params.marginStart = 24
                layoutParams = params
                setOnClickListener { showKillProcessDialog(proc) }
            }
            row.addView(name)
            row.addView(usage)
            row.addView(killButton)
            contentLayout.addView(row)
        }
        layout.addView(contentLayout)
        card.addView(layout)
        return card
    }

    // DIALOGS AND NETWORK CALLS

    private fun showScreenshotDialog() {
        Toast.makeText(this@DashboardActivity, "Taking Screenshot...", Toast.LENGTH_SHORT).show()
        currentApi?.getScreenshot()?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val view = layoutInflater.inflate(R.layout.dialog_screenshot, null)
                    val imgView = view.findViewById<ImageView>(R.id.screenshotImage)
                    imgView.setImageBitmap(bitmap)

                    AlertDialog.Builder(this@DashboardActivity)
                        .setTitle("PC Screenshot")
                        .setView(view)
                        .setPositiveButton("Close") { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(applicationContext, "Failed to get image", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(applicationContext, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendMixerVolume(appName: String, volume: Int) {
        currentApi?.setMixerVolume(appName, volume)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {}
            override fun onFailure(call: Call<String>, t: Throwable) {}
        })
    }

    private fun showKillProcessDialog(proc: ProcessData) {
        AlertDialog.Builder(this)
            .setTitle("Завершить процесс?")
            .setMessage("Вы уверены, что хотите завершить '${proc.name}' (PID: ${proc.pid})?")
            .setPositiveButton("Да") { _, _ -> killProcess(proc.pid) }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun killProcess(pid: Int) {
        currentApi?.killProcess(pid)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                val message = if (response.isSuccessful) response.body() ?: "Процесс завершен" else "Ошибка: ${response.code()}"
                Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_SHORT).show()
                handler.postDelayed({ fetchStats() }, 250) // Refresh stats after action
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@DashboardActivity, "Сетевая ошибка: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showPowerActionDialog(actionName: String, title: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Вы уверены, что хотите $actionName ПК?")
            .setPositiveButton("Да") { _, _ -> onConfirm() }
            .setNegativeButton("Нет", null)
            .show()
    }
    
    private fun sendShutdownCommand() {
        currentApi?.shutdownPC()?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                Toast.makeText(this@DashboardActivity, "PC is shutting down...", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@DashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun sendSleepCommand() {
        currentApi?.sleepPC()?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                Toast.makeText(this@DashboardActivity, "Putting PC to sleep...", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@DashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}
