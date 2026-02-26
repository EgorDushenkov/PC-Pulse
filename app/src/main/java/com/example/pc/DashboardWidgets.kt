package com.example.pc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

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
    private val container: LinearLayout
    private var onVolumeChange: ((String, Int) -> Unit)? = null

    init {
        container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(context).apply {
            text = "AUDIO MIXER"
            setTextColor(ContextCompat.getColor(context, R.color.accent_neon))
            textSize = 12f
            paint.isFakeBoldText = true
        }
        container.addView(title)
        addView(container)
    }

    fun setCallbacks(onVolumeChange: (String, Int) -> Unit) {
        this.onVolumeChange = onVolumeChange
    }

    override fun updateData(stats: PCStats) {
        // Implementation for volume control will be added later
    }
}

class StorageWidgetView(context: Context) : BaseWidgetView(context) {
    private val disksContainer: LinearLayout
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    init {
        val view = inflater.inflate(R.layout.widget_storage, this, true)
        disksContainer = view.findViewById(R.id.disks_container)
    }

    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        disksContainer.removeAllViews()
        for (disk in stats.disks) {
            val diskView = inflater.inflate(R.layout.item_widget_disk, disksContainer, false)
            val diskName = diskView.findViewById<TextView>(R.id.disk_name)
            val diskValue = diskView.findViewById<TextView>(R.id.disk_value)
            val diskProgress = diskView.findViewById<ProgressBar>(R.id.disk_progress)

            diskName.text = disk.dev.replace("\\", "")
            diskValue.text = "${disk.used.toInt()} / ${disk.total.toInt()} GB"
            diskProgress.progress = disk.percent.toInt()

            disksContainer.addView(diskView)
        }
    }
}

class CoolingWidgetView(context: Context) : BaseWidgetView(context) {
    private val container: LinearLayout
    private val title: TextView
    private val fansText: TextView

    init {
        container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(context).apply {
            text = "COOLING"
            setTextColor(ContextCompat.getColor(context, R.color.color_cooling))
            textSize = 12f
            paint.isFakeBoldText = true
        }
        fansText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        container.addView(title)
        container.addView(fansText)
        addView(container)
    }

    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        val fanInfo = stats.fans.joinToString("\n") { "${it.name}: ${it.rpm} RPM" }
        fansText.text = if (fanInfo.isEmpty()) "No fans detected" else fanInfo
    }
}

class TopProcessesWidgetView(context: Context) : BaseWidgetView(context) {
    private val container: LinearLayout
    private val title: TextView
    private val procsListContainer: LinearLayout
    private var onKill: ((Int) -> Unit)? = null

    init {
        container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(context).apply {
            text = "PROCESSES"
            setTextColor(ContextCompat.getColor(context, R.color.color_procs))
            textSize = 12f
            paint.isFakeBoldText = true
        }
        procsListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        container.addView(title)
        container.addView(procsListContainer)
        addView(container)
    }

    fun setCallbacks(onKill: (Int) -> Unit) {
        this.onKill = onKill
    }

    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        procsListContainer.removeAllViews()
        stats.procs.take(5).forEach { proc ->
            val tv = TextView(context).apply {
                text = "${proc.name} (${proc.cpu}%)"
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(0, 4, 0, 4)
                setOnClickListener { onKill?.invoke(proc.pid) }
            }
            procsListContainer.addView(tv)
        }
    }
}
