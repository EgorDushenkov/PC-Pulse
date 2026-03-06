package com.example.pc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

fun Context.getThemeColor(attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

abstract class BaseWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {
    init {
        radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
        elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics)
        val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
        setContentPadding(padding, padding, padding, padding)
    }
}

class ControlsWidgetView(context: Context) : BaseWidgetView(context) {
    private val screenshotButton: Button
    private val sleepButton: Button
    private val shutdownButton: Button
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_controls, this, true)
        screenshotButton = view.findViewById(R.id.screenshot_button)
        sleepButton = view.findViewById(R.id.sleep_button)
        shutdownButton = view.findViewById(R.id.shutdown_button)
    }
    fun setCallbacks(onScreenshot: () -> Unit, onSleep: () -> Unit, onShutdown: () -> Unit) {
        screenshotButton.setOnClickListener { onScreenshot() }
        sleepButton.setOnClickListener { onSleep() }
        shutdownButton.setOnClickListener { onShutdown() }
    }
    override fun updateData(stats: PCStats) {}
}

class AudioMixerWidgetView(context: Context) : BaseWidgetView(context) {
    private val title: TextView
    private val mixerListContainer: LinearLayout
    private var onVolumeChange: ((String, Int) -> Unit)? = null
    private val activeSliders = mutableSetOf<String>()
    init {
        val rootLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(context).apply {
            text = "AUDIO MIXER"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(title)
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        mixerListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(mixerListContainer)
        rootLayout.addView(scrollView)
        addView(rootLayout)
    }
    fun setCallbacks(onVolumeChange: (String, Int) -> Unit) { this.onVolumeChange = onVolumeChange }
    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        val currentApps = stats.audio_sessions.map { it.name }.toSet()
        val existingApps = (0 until mixerListContainer.childCount).map { mixerListContainer.getChildAt(it).tag as String }.toSet()
        if (currentApps != existingApps) {
            mixerListContainer.removeAllViews()
            val themeColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
            for (session in stats.audio_sessions) {
                val view = LayoutInflater.from(context).inflate(R.layout.item_mixer_app, mixerListContainer, false)
                view.tag = session.name
                val slider = view.findViewById<SeekBar>(R.id.appVolumeSlider)
                val percentText = view.findViewById<TextView>(R.id.appVolumePercentText)
                view.findViewById<TextView>(R.id.appNameText).text = session.name
                slider.progress = session.volume
                percentText.text = "${session.volume}%"
                slider.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
                slider.thumbTintList = android.content.res.ColorStateList.valueOf(themeColor)
                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if (f) percentText.text = "$p%" }
                    override fun onStartTrackingTouch(s: SeekBar?) { activeSliders.add(session.name) }
                    override fun onStopTrackingTouch(s: SeekBar?) {
                        activeSliders.remove(session.name)
                        onVolumeChange?.invoke(session.name, slider.progress)
                    }
                })
                mixerListContainer.addView(view)
            }
        } else {
            for (i in 0 until mixerListContainer.childCount) {
                val view = mixerListContainer.getChildAt(i)
                val appName = view.tag as String
                if (appName !in activeSliders) {
                    stats.audio_sessions.find { it.name == appName }?.let {
                        view.findViewById<SeekBar>(R.id.appVolumeSlider).progress = it.volume
                        view.findViewById<TextView>(R.id.appVolumePercentText).text = "${it.volume}%"
                    }
                }
            }
        }
    }
}

class StorageWidgetView(context: Context) : BaseWidgetView(context) {
    private val disksContainer: LinearLayout
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_storage, this, true)
        disksContainer = view.findViewById(R.id.disks_container)
    }
    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        disksContainer.removeAllViews()
        val themeColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        for (disk in stats.disks) {
            val diskView = LayoutInflater.from(context).inflate(R.layout.item_widget_disk, disksContainer, false)
            diskView.findViewById<TextView>(R.id.disk_name).text = disk.dev.replace("\\", "")
            diskView.findViewById<TextView>(R.id.disk_value).text = "${disk.used.toInt()} / ${disk.total.toInt()} GB"
            val pb = diskView.findViewById<ProgressBar>(R.id.disk_progress)
            pb.progress = disk.percent.toInt()
            pb.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
            disksContainer.addView(diskView)
        }
    }
}

class CoolingWidgetView(context: Context) : BaseWidgetView(context) {
    private val title: TextView
    private val fansText: TextView
    init {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(context).apply {
            text = "COOLING"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f
            paint.isFakeBoldText = true
        }
        fansText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 14f }
        container.addView(title)
        container.addView(fansText)
        addView(container)
    }
    override fun updateData(stats: PCStats) {
        val fanInfo = stats.fans.joinToString("\n") { "${it.name}: ${it.rpm} RPM" }
        fansText.text = if (fanInfo.isEmpty()) "No fans detected" else fanInfo
    }
}

class TopProcessesWidgetView(context: Context) : BaseWidgetView(context) {
    private val title: TextView
    private val procsListContainer: LinearLayout
    private var onKill: ((Int) -> Unit)? = null
    init {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(context).apply {
            text = "PROCESSES"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f
            paint.isFakeBoldText = true
        }
        procsListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        container.addView(title)
        container.addView(procsListContainer)
        addView(container)
    }
    fun setCallbacks(onKill: (Int) -> Unit) { this.onKill = onKill }
    override fun updateData(stats: PCStats) {
        procsListContainer.removeAllViews()
        stats.procs.take(5).forEach { proc ->
            val tv = TextView(context).apply {
                text = "${proc.name} (${proc.cpu}%)"
                setTextColor(Color.WHITE); textSize = 11f; setPadding(0, 4, 0, 4)
                setOnClickListener { onKill?.invoke(proc.pid) }
            }
            procsListContainer.addView(tv)
        }
    }
}

abstract class SpeedometerWidgetView(context: Context) : BaseWidgetView(context) {
    protected val speedometer: SpeedometerView
    protected val detailText: TextView
    init {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        speedometer = SpeedometerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120f, resources.displayMetrics).toInt(), 0, 1f)
        }
        detailText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 0) }
        layout.addView(speedometer)
        layout.addView(detailText)
        addView(layout)
    }
}

class CpuWidgetView(context: Context) : SpeedometerWidgetView(context) {
    override fun updateData(stats: PCStats) {
        speedometer.setValue(stats.cpu.usage.toFloat())
        detailText.text = "${stats.cpu.freq.toInt()} MHz | ${stats.cpu.temp}°C"
    }
}

class RamWidgetView(context: Context) : SpeedometerWidgetView(context) {
    override fun updateData(stats: PCStats) {
        speedometer.setValue(stats.ram.usage.toFloat())
        detailText.text = "${stats.ram.used} / ${stats.ram.total} GB"
    }
}

class GpuWidgetView(context: Context) : SpeedometerWidgetView(context) {
    override fun updateData(stats: PCStats) {
        stats.gpu.getOrNull(0)?.let { g ->
            speedometer.setValue(g.load.toFloat())
            detailText.text = "${g.temp}°C | VRAM: ${g.mem_p}%"
        }
    }
}

class NetworkWidgetView(context: Context) : BaseWidgetView(context) {
    private val downText: TextView
    private val upText: TextView
    init {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        layout.addView(TextView(context).apply {
            text = "NETWORK"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f; paint.isFakeBoldText = true; gravity = Gravity.CENTER
        })
        downText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 16, 0, 8) }
        upText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER }
        layout.addView(downText); layout.addView(upText)
        addView(layout)
    }
    override fun updateData(stats: PCStats) {
        downText.text = "↓ ${stats.network.down_kbps.toInt()} KB/s"
        upText.text = "↑ ${stats.network.up_kbps.toInt()} KB/s"
    }
}