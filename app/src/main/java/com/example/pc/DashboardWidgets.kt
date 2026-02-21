package com.example.pc

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.cardview.widget.CardView

/**
 * Кастомный View для виджета Управления. 
 * Реализует UpdatableWidget для обновления данных.
 */
class ControlsWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {

    private val textView: TextView

    init {
        setContentPadding(50, 50, 50, 50)
        textView = TextView(context).apply { text = "Controls Widget" }
        addView(textView)
    }

    override fun updateData(stats: PCStats) {
        textView.text = "Controls for ${stats.pc_name}"
    }
}

/**
 * Кастомный View для виджета Аудиомикшера.
 */
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

/**
 * Кастомный View для виджета Хранилища.
 */
class StorageWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), UpdatableWidget {

    private val textView: TextView

    init {
        setContentPadding(50, 50, 50, 50)
        textView = TextView(context).apply { text = "Storage Widget" }
        addView(textView)
    }

    override fun updateData(stats: PCStats) {
        val diskCount = stats.disks.size
        textView.text = "Storage: $diskCount disks found"
    }
}

/**
 * Кастомный View для виджета Охлаждения.
 */
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

/**
 * Кастомный View для виджета Процессов.
 */
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
