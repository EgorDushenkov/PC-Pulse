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
import android.widget.ImageButton
import android.widget.ImageView
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

class ActionButtonWidgetView(context: Context) : BaseWidgetView(context) {
    private val button: Button
    init {
        button = Button(context).apply {
            layoutParams = LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
        }
        addView(button)
    }

    fun setup(config: WidgetConfig, onClick: (String) -> Unit) {
        button.text = config.label ?: "Action"
        button.setOnClickListener {
            config.action?.let { onClick(it) }
        }
    }

    override fun updateData(stats: PCStats) {}
}

class ControlsWidgetView(context: Context) : BaseWidgetView(context) {
    private val btnScrenshot: ImageButton
    private val btnSleep: ImageButton
    private val btnShutdown: ImageButton
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

class MediaPlayerWidgetView(context: Context) : BaseWidgetView(context) {
    private val titleText: TextView
    private val artistText: TextView
    private val btnPrev: ImageButton
    private val btnPlayPause: ImageButton
    private val btnNext: ImageButton
    private var onCommand: ((String) -> Unit)? = null

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(-1, -1)
        }

        titleText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        artistText = TextView(context).apply {
            setTextColor(Color.LTGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 12f.dpToPx(context).toInt())
        }

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnSize = 48f.dpToPx(context).toInt()
        val iconPadding = 8f.dpToPx(context).toInt()

        btnPrev = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setImageResource(R.drawable.ic_prev)
            setBackgroundResource(android.R.color.transparent)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        btnPlayPause = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(24f.dpToPx(context).toInt(), 0, 24f.dpToPx(context).toInt(), 0)
            }
            setImageResource(R.drawable.ic_pause)
            setBackgroundResource(android.R.color.transparent)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        btnNext = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setImageResource(R.drawable.ic_prev) // We'll rotate it or use separate drawable
            rotation = 180f
            setBackgroundResource(android.R.color.transparent)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        controls.addView(btnPrev)
        controls.addView(btnPlayPause)
        controls.addView(btnNext)

        root.addView(titleText)
        root.addView(artistText)
        root.addView(controls)
        addView(root)

        btnPrev.setOnClickListener { onCommand?.invoke("prev") }
        btnPlayPause.setOnClickListener { onCommand?.invoke("play_pause") }
        btnNext.setOnClickListener { onCommand?.invoke("next") }
    }

    fun setCallbacks(onCommand: (String) -> Unit) {
        this.onCommand = onCommand
    }

    override fun updateData(stats: PCStats) {
        stats.media?.let { media ->
            titleText.text = media.title.ifEmpty { "No Media" }
            artistText.text = media.artist
            btnPlayPause.setImageResource(if (media.status == 4) R.drawable.ic_pause else R.drawable.ic_play)
            visibility = View.VISIBLE
        } ?: run {
            titleText.text = "No Media"
            artistText.text = ""
            btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }
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
    fun create(config: WidgetConfig, context: Context, onScreenshot: () -> Unit = {}, onSleep: () -> Unit = {}, onShutdown: () -> Unit = {}, onVolumeChange: (String, Int) -> Unit = {_,_ ->}, onKill: (Int) -> Unit = {}, onRunCommand: (String) -> Unit = {}, onMediaCommand: (String) -> Unit = {}): View {
        return when (config.type) {
            WidgetType.CONTROLS -> ControlsWidgetView(context).apply { setCallbacks(onScreenshot, onSleep, onShutdown) }
            WidgetType.AUDIO_MIXER -> AudioMixerWidgetView(context).apply { setCallbacks(onVolumeChange) }
            WidgetType.STORAGE -> StorageWidgetView(context)
            WidgetType.COOLING -> CoolingWidgetView(context)
            WidgetType.TOP_PROCESSES -> TopProcessesWidgetView(context).apply { setCallbacks(onKill) }
            WidgetType.CPU -> CpuWidgetView(context)
            WidgetType.RAM -> RamWidgetView(context)
            WidgetType.GPU -> GpuWidgetView(context)
            WidgetType.NETWORK -> NetworkWidgetView(context)
            WidgetType.ACTION_BUTTON -> ActionButtonWidgetView(context).apply { setup(config, onRunCommand) }
            WidgetType.MEDIA_PLAYER -> MediaPlayerWidgetView(context).apply { setCallbacks(onMediaCommand) }
        }
    }
}