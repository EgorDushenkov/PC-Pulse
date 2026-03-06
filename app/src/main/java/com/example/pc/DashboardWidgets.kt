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
        radius = 16f.dpToPx(context)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
        elevation = 4f.dpToPx(context)
        val p = 8f.dpToPx(context).toInt()
        setContentPadding(p, p, p, p)
    }

    protected fun Float.dpToPx(context: Context): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, context.resources.displayMetrics)
}

class ControlsWidgetView(context: Context) : BaseWidgetView(context) {
    private val btnScrenshot: Button
    private val btnSleep: Button
    private val btnShutdown: Button
    init {
        val v = LayoutInflater.from(context).inflate(R.layout.widget_controls, this, true)
        btnScrenshot = v.findViewById(R.id.screenshot_button)
        btnSleep = v.findViewById(R.id.sleep_button)
        btnShutdown = v.findViewById(R.id.shutdown_button)
    }
    fun setCallbacks(onScreenshot: () -> Unit, onSleep: () -> Unit, onShutdown: () -> Unit) {
        btnScrenshot.setOnClickListener { onScreenshot() }
        btnSleep.setOnClickListener { onSleep() }
        btnShutdown.setOnClickListener { onShutdown() }
    }
    override fun updateData(stats: PCStats) {}
}

class AudioMixerWidgetView(context: Context) : BaseWidgetView(context) {
    private val container: LinearLayout
    private var onVolumeChange: ((String, Int) -> Unit)? = null
    private val activeSliders = mutableSetOf<String>()

    init {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(context).apply {
            text = "AUDIO MIXER"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, 8f.dpToPx(context).toInt())
        })
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(container)
        root.addView(scroll)
        addView(root)
    }

    fun setCallbacks(onVolumeChange: (String, Int) -> Unit) { this.onVolumeChange = onVolumeChange }

    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        val current = stats.audio_sessions.map { it.name }.toSet()
        val existing = (0 until container.childCount).map { container.getChildAt(it).tag as String }.toSet()

        if (current != existing) {
            container.removeAllViews()
            val themeColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
            stats.audio_sessions.forEach { session ->
                val view = LayoutInflater.from(context).inflate(R.layout.item_mixer_app, container, false)
                view.tag = session.name
                val slider = view.findViewById<SeekBar>(R.id.appVolumeSlider)
                val text = view.findViewById<TextView>(R.id.appVolumePercentText)
                view.findViewById<TextView>(R.id.appNameText).text = session.name
                slider.progress = session.volume
                text.text = "${session.volume}%"
                slider.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
                slider.thumbTintList = android.content.res.ColorStateList.valueOf(themeColor)
                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if (f) text.text = "$p%" }
                    override fun onStartTrackingTouch(s: SeekBar?) { activeSliders.add(session.name) }
                    override fun onStopTrackingTouch(s: SeekBar?) {
                        activeSliders.remove(session.name)
                        onVolumeChange?.invoke(session.name, slider.progress)
                    }
                })
                container.addView(view)
            }
        } else {
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i)
                val name = view.tag as String
                if (name !in activeSliders) {
                    stats.audio_sessions.find { it.name == name }?.let {
                        view.findViewById<SeekBar>(R.id.appVolumeSlider).progress = it.volume
                        view.findViewById<TextView>(R.id.appVolumePercentText).text = "${it.volume}%"
                    }
                }
            }
        }
    }
}

class StorageWidgetView(context: Context) : BaseWidgetView(context) {
    private val container: LinearLayout
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_storage, this, true)
        container = view.findViewById(R.id.disks_container)
    }
    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        container.removeAllViews()
        val themeColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        stats.disks.forEach { disk ->
            val v = LayoutInflater.from(context).inflate(R.layout.item_widget_disk, container, false)
            v.findViewById<TextView>(R.id.disk_name).text = disk.dev.replace("\\", "")
            v.findViewById<TextView>(R.id.disk_value).text = "${disk.used.toInt()} / ${disk.total.toInt()} GB"
            val pb = v.findViewById<ProgressBar>(R.id.disk_progress)
            pb.progress = disk.percent.toInt()
            pb.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
            container.addView(v)
        }
    }
}

class CoolingWidgetView(context: Context) : BaseWidgetView(context) {
    private val fansText: TextView
    init {
        val c = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        c.addView(TextView(context).apply {
            text = "COOLING"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f; paint.isFakeBoldText = true
        })
        fansText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 14f }
        c.addView(fansText)
        addView(c)
    }
    override fun updateData(stats: PCStats) {
        val info = stats.fans.joinToString("\n") { "${it.name}: ${it.rpm} RPM" }
        fansText.text = info.ifEmpty { "No fans detected" }
    }
}

class TopProcessesWidgetView(context: Context) : BaseWidgetView(context) {
    private val container: LinearLayout
    private var onKill: ((Int) -> Unit)? = null
    init {
        val c = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        c.addView(TextView(context).apply {
            text = "PROCESSES"
            setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f; paint.isFakeBoldText = true
        })
        container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        c.addView(container); addView(c)
    }
    fun setCallbacks(onKill: (Int) -> Unit) { this.onKill = onKill }
    override fun updateData(stats: PCStats) {
        container.removeAllViews()
        stats.procs.take(5).forEach { proc ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            row.addView(TextView(context).apply {
                text = "${proc.name} (${proc.cpu}%)"
                setTextColor(Color.WHITE); textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            row.addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.RED)
                textSize = 16f
                setPadding(8f.dpToPx(context).toInt(), 0, 4f.dpToPx(context).toInt(), 0)
                setOnClickListener { onKill?.invoke(proc.pid) }
            })
            container.addView(row)
        }
    }
}

abstract class SpeedometerWidgetView(context: Context) : BaseWidgetView(context) {
    protected val speedometer: SpeedometerView
    protected val detailText: TextView
    init {
        val l = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        speedometer = SpeedometerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(120f.dpToPx(context).toInt(), 0, 1f)
        }
        detailText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 8f.dpToPx(context).toInt(), 0, 0) }
        l.addView(speedometer); l.addView(detailText); addView(l)
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
        val l = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        l.addView(TextView(context).apply {
            text = "NETWORK"; setTextColor(context.getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            textSize = 12f; paint.isFakeBoldText = true; gravity = Gravity.CENTER
        })
        downText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 16f.dpToPx(context).toInt(), 0, 8f.dpToPx(context).toInt()) }
        upText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER }
        l.addView(downText); l.addView(upText); addView(l)
    }
    override fun updateData(stats: PCStats) {
        downText.text = "↓ ${stats.network.down_kbps.toInt()} KB/s"
        upText.text = "↑ ${stats.network.up_kbps.toInt()} KB/s"
    }
}

object WidgetFactory {
    fun create(type: WidgetType, context: Context, onScreenshot: () -> Unit = {}, onSleep: () -> Unit = {}, onShutdown: () -> Unit = {}, onVolumeChange: (String, Int) -> Unit = {_,_ ->}, onKill: (Int) -> Unit = {}): View {
        return when (type) {
            WidgetType.CONTROLS -> ControlsWidgetView(context).apply { setCallbacks(onScreenshot, onSleep, onShutdown) }
            WidgetType.AUDIO_MIXER -> AudioMixerWidgetView(context).apply { setCallbacks(onVolumeChange) }
            WidgetType.STORAGE -> StorageWidgetView(context)
            WidgetType.COOLING -> CoolingWidgetView(context)
            WidgetType.TOP_PROCESSES -> TopProcessesWidgetView(context).apply { setCallbacks(onKill) }
            WidgetType.CPU -> CpuWidgetView(context)
            WidgetType.RAM -> RamWidgetView(context)
            WidgetType.GPU -> GpuWidgetView(context)
            WidgetType.NETWORK -> NetworkWidgetView(context)
        }
    }
}