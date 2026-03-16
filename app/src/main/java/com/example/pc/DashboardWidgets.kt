package com.example.pc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
    private var config: WidgetConfig? = null
    private var appState = 0 // 0: not running, 1: background, 2: active
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }
    private val rectF = RectF()

    init {
        setWillNotDraw(false)
        button = Button(context).apply {
            layoutParams = LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
        }
        addView(button)
    }

    fun setup(
        config: WidgetConfig,
        onVibrate: () -> Unit,
        onRun: (String) -> Unit,
        onMinimize: () -> Unit,
        onClose: (String) -> Unit
    ) {
        this.config = config
        button.text = config.label ?: "Action"
        
        button.setOnClickListener {
            onVibrate()
            val path = config.action ?: return@setOnClickListener
            if (appState == 2) {
                onMinimize()
            } else {
                onRun(path)
            }
        }

        button.setOnLongClickListener {
            onVibrate()
            config.action?.let { path ->
                val fileName = path.split("\\", "/").last().lowercase()
                onClose(fileName)
            }
            true
        }
    }

    override fun updateData(stats: PCStats) {
        val path = config?.action ?: return
        val fileName = path.split("\\", "/").last().lowercase()

        val newState = when {
            stats.active_app?.lowercase() == fileName -> 2
            stats.running_apps.any { it.lowercase() == fileName } -> 1
            else -> 0
        }

        if (newState != appState) {
            appState = newState
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (appState == 0) return

        borderPaint.color = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val margin = borderPaint.strokeWidth / 2f
        rectF.set(margin, margin, width.toFloat() - margin, height.toFloat() - margin)
        val r = (radius - margin).coerceAtLeast(0f)

        if (appState == 1) {
            // Рамка на половину (снизу) по контуру скругления
            canvas.save()
            canvas.clipRect(0f, height / 2f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rectF, r, r, borderPaint)
            canvas.restore()
        } else if (appState == 2) {
            // Полная рамка по контуру
            canvas.drawRoundRect(rectF, r, r, borderPaint)
        }
    }
}

class ControlsWidgetView(context: Context) : BaseWidgetView(context) {
    private val btnScrenshot: ImageButton
    private val btnMic: ImageButton
    private val btnSleep: ImageButton
    private val btnShutdown: ImageButton
    private var isMuted = false
    
    private var optimisticMute: Boolean? = null
    private var optimisticMuteTime = 0L
    private val OPTIMISTIC_TIMEOUT = 2500L

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.widget_controls, this, true)
        btnScrenshot = v.findViewById(R.id.screenshot_button)
        btnMic = v.findViewById(R.id.mic_button)
        btnSleep = v.findViewById(R.id.sleep_button)
        btnShutdown = v.findViewById(R.id.shutdown_button)
    }
    
    fun setCallbacks(onVibrate: () -> Unit, onScreenshot: () -> Unit, onMicMute: (Boolean) -> Unit, onSleep: () -> Unit, onShutdown: () -> Unit) {
        btnScrenshot.setOnClickListener { onVibrate(); onScreenshot() }
        btnMic.setOnClickListener { 
            onVibrate()
            val newState = !isMuted
            isMuted = newState
            optimisticMute = newState
            optimisticMuteTime = System.currentTimeMillis()
            updateMicUI(newState)
            onMicMute(newState) 
        }
        btnSleep.setOnClickListener { onVibrate(); onSleep() }
        btnShutdown.setOnClickListener { onVibrate(); onShutdown() }
    }

    private fun updateMicUI(muted: Boolean) {
        val themeColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        btnMic.setColorFilter(if (muted) Color.BLACK else themeColor)
    }

    override fun updateData(stats: PCStats) {
        val now = System.currentTimeMillis()
        if (optimisticMute != null && now - optimisticMuteTime < OPTIMISTIC_TIMEOUT) {
            isMuted = optimisticMute!!
            updateMicUI(isMuted)
            return
        }
        optimisticMute = null
        isMuted = stats.mic_muted
        updateMicUI(isMuted)
    }
}

class MediaPlayerWidgetView(context: Context) : BaseWidgetView(context) {
    private val titleText: TextView
    private val artistText: TextView
    private val btnPrev: ImageButton
    private val btnPlayPause: ImageButton
    private val btnNext: ImageButton
    private var onCommand: ((String) -> Unit)? = null
    private var onVibrate: (() -> Unit)? = null
    
    private var currentStatus: Int = 0
    private var optimisticStatus: Int? = null
    private var optimisticStatusTime = 0L
    private val OPTIMISTIC_TIMEOUT = 2500L

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
            setImageResource(R.drawable.ic_prev)
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

        btnPrev.setOnClickListener { onVibrate?.invoke(); onCommand?.invoke("prev") }
        btnPlayPause.setOnClickListener { 
            onVibrate?.invoke()
            // 4 is usually Playing
            val newState = if (currentStatus == 4) 0 else 4 
            currentStatus = newState
            optimisticStatus = newState
            optimisticStatusTime = System.currentTimeMillis()
            updatePlayPauseIcon(newState)
            onCommand?.invoke("play_pause") 
        }
        btnNext.setOnClickListener { onVibrate?.invoke(); onCommand?.invoke("next") }
    }

    private fun updatePlayPauseIcon(status: Int) {
        btnPlayPause.setImageResource(if (status == 4) R.drawable.ic_pause else R.drawable.ic_play)
    }

    fun setCallbacks(onVibrate: () -> Unit, onCommand: (String) -> Unit) {
        this.onVibrate = onVibrate
        this.onCommand = onCommand
    }

    override fun updateData(stats: PCStats) {
        stats.media?.let { media ->
            titleText.text = media.title.ifEmpty { "No Media" }
            artistText.text = media.artist
            
            val now = System.currentTimeMillis()
            if (optimisticStatus != null && now - optimisticStatusTime < OPTIMISTIC_TIMEOUT) {
                currentStatus = optimisticStatus!!
            } else {
                optimisticStatus = null
                currentStatus = media.status
            }
            updatePlayPauseIcon(currentStatus)
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
    private var onVibrate: (() -> Unit)? = null
    private val activeSliders = mutableSetOf<String>()
    
    private val optimisticVolumes = mutableMapOf<String, Pair<Int, Long>>()
    private val OPTIMISTIC_TIMEOUT = 3000L

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

    fun setCallbacks(onVibrate: () -> Unit, onVolumeChange: (String, Int) -> Unit) { 
        this.onVibrate = onVibrate
        this.onVolumeChange = onVolumeChange 
    }

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
                
                val volToShow = getVolToShow(session.name, session.volume)
                slider.progress = volToShow
                text.text = "$volToShow%"
                
                slider.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)
                slider.thumbTintList = android.content.res.ColorStateList.valueOf(themeColor)
                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { 
                        if (f) {
                            text.text = "$p%"
                            // Subtle vibration during movement
                            if (p % 2 == 0) onVibrate?.invoke() 
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) { activeSliders.add(session.name) }
                    override fun onStopTrackingTouch(s: SeekBar?) {
                        activeSliders.remove(session.name)
                        val vol = slider.progress
                        optimisticVolumes[session.name] = Pair(vol, System.currentTimeMillis())
                        onVolumeChange?.invoke(session.name, vol)
                        onVibrate?.invoke()
                    }
                })
                container.addView(view)
            }
        } else {
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i)
                val name = view.tag as String
                if (name !in activeSliders) {
                    stats.audio_sessions.find { it.name == name }?.let { session ->
                        val volToShow = getVolToShow(name, session.volume)
                        view.findViewById<SeekBar>(R.id.appVolumeSlider).progress = volToShow
                        view.findViewById<TextView>(R.id.appVolumePercentText).text = "$volToShow%"
                    }
                }
            }
        }
    }
    
    private fun getVolToShow(name: String, serverVol: Int): Int {
        val now = System.currentTimeMillis()
        val optimistic = optimisticVolumes[name]
        return if (optimistic != null && now - optimistic.second < OPTIMISTIC_TIMEOUT) {
            optimistic.first
        } else {
            optimisticVolumes.remove(name)
            serverVol
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
            
            // Fix for "0 / Total GB" bug: if used is 0 but percent is not, calculate used manually
            val usedValue = if (disk.used > 0.1) disk.used else (disk.total * (disk.percent / 100.0)).toFloat()
            v.findViewById<TextView>(R.id.disk_value).text = "${usedValue.toInt()} / ${disk.total.toInt()} GB"
            
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
    private var onVibrate: (() -> Unit)? = null
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
    fun setCallbacks(onVibrate: () -> Unit, onKill: (Int) -> Unit) { 
        this.onVibrate = onVibrate
        this.onKill = onKill 
    }
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
                setOnClickListener { onVibrate?.invoke(); onKill?.invoke(proc.pid) }
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
        val themeColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        l.addView(TextView(context).apply {
            text = "NETWORK"
            setTextColor(themeColor)
            textSize = 12f; paint.isFakeBoldText = true
        })
        downText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true }
        upText = TextView(context).apply { setTextColor(Color.LTGRAY); textSize = 12f }
        l.addView(downText); l.addView(upText); addView(l)
    }
    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        downText.text = "↓ ${stats.network.down_kbps.toInt()} KB/s"
        upText.text = "↑ ${stats.network.up_kbps.toInt()} KB/s"
    }
}

object WidgetFactory {
    fun create(
        config: WidgetConfig,
        context: Context,
        onVibrate: () -> Unit = {},
        onScreenshot: (() -> Unit)? = null,
        onMicMute: ((Boolean) -> Unit)? = null,
        onSleep: (() -> Unit)? = null,
        onShutdown: (() -> Unit)? = null,
        onVolumeChange: ((String, Int) -> Unit)? = null,
        onKill: ((Int) -> Unit)? = null,
        onRunCommand: ((String) -> Unit)? = null,
        onMediaCommand: ((String) -> Unit)? = null,
        onMinimizeCommand: (() -> Unit)? = null,
        onCloseCommand: ((String) -> Unit)? = null
    ): View {
        return when (config.type) {
            WidgetType.CONTROLS -> ControlsWidgetView(context).apply {
                setCallbacks(
                    onVibrate,
                    onScreenshot ?: {},
                    onMicMute ?: {},
                    onSleep ?: {},
                    onShutdown ?: {}
                )
            }
            WidgetType.AUDIO_MIXER -> AudioMixerWidgetView(context).apply {
                setCallbacks(onVibrate, onVolumeChange ?: { _, _ -> })
            }
            WidgetType.STORAGE -> StorageWidgetView(context)
            WidgetType.COOLING -> CoolingWidgetView(context)
            WidgetType.TOP_PROCESSES -> TopProcessesWidgetView(context).apply {
                setCallbacks(onVibrate, onKill ?: {})
            }
            WidgetType.CPU -> CpuWidgetView(context)
            WidgetType.RAM -> RamWidgetView(context)
            WidgetType.GPU -> GpuWidgetView(context)
            WidgetType.NETWORK -> NetworkWidgetView(context)
            WidgetType.ACTION_BUTTON -> ActionButtonWidgetView(context).apply {
                setup(
                    config, 
                    onVibrate, 
                    onRunCommand ?: {},
                    onMinimizeCommand ?: {},
                    onCloseCommand ?: {}
                )
            }
            WidgetType.MEDIA_PLAYER -> MediaPlayerWidgetView(context).apply {
                setCallbacks(onVibrate, onMediaCommand ?: {})
            }
        }
    }
}