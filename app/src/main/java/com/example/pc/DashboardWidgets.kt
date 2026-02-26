package com.example.pc

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView

class ControlsWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {

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

    override fun updateData(stats: PCStats) { /* Не требуется */ }
}

class AudioMixerWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {

    private val textView: TextView

    init {
        setContentPadding(50, 50, 50, 50)
        textView = TextView(context).apply { text = "Audio Mixer Widget" }
        addView(textView)
    }

    override fun updateData(stats: PCStats) {
        val sessionCount = stats.audio_sessions.size
        textView.text = "Audio Mixer: $sessionCount active sessions"
    }
}

// --- ИЗМЕНЕННЫЙ КЛАСС WIDGET'А ХРАНИЛИЩА ---
class StorageWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {

    private val disksContainer: LinearLayout
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    init {
        // "Надуваем" основной макет виджета
        val view = inflater.inflate(R.layout.widget_storage, this, true)
        disksContainer = view.findViewById(R.id.disks_container)
    }

    @SuppressLint("SetTextI18n")
    override fun updateData(stats: PCStats) {
        // Очищаем контейнер перед добавлением новых данных
        disksContainer.removeAllViews()

        // Для каждого диска из статистики создаем и добавляем его View
        for (disk in stats.disks) {
            val diskView = inflater.inflate(R.layout.item_widget_disk, disksContainer, false)

            val diskName = diskView.findViewById<TextView>(R.id.disk_name)
            val diskValue = diskView.findViewById<TextView>(R.id.disk_value)
            val diskProgress = diskView.findViewById<ProgressBar>(R.id.disk_progress)

            diskName.text = disk.dev.replace("\\", "")
            diskValue.text = "${disk.used} / ${disk.total} GB"
            diskProgress.progress = disk.percent.toInt()

            disksContainer.addView(diskView)
        }
    }
}
// ---------------------------------------------

class CoolingWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {
    private val textView: TextView
    init {
        setContentPadding(50, 50, 50, 50)
        textView = TextView(context).apply { text = "Cooling Widget" }
        addView(textView)
    }
    override fun updateData(stats: PCStats) {
        val fanSpeed = stats.fans.firstOrNull()?.rpm ?: "N/A"
        textView.text = "Cooling: Fan at $fanSpeed RPM"
    }
}

class TopProcessesWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {
    private val textView: TextView
    init {
        setContentPadding(50, 50, 50, 50)
        textView = TextView(context).apply { text = "Top Processes Widget" }
        addView(textView)
    }
    override fun updateData(stats: PCStats) {
        val topProcess = stats.procs.firstOrNull()?.name ?: "None"
        textView.text = "Top Process: $topProcess"
    }
}
