package com.example.pc

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

object WidgetFactory {

    // WIDGET CREATION HELPERS
    private fun createWidgetCard(context: Context): CardView {
        return CardView(context).apply {
            radius = 16f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = 24
            layoutParams = params
        }
    }

    private fun createWidgetTitle(context: Context, title: String, colorRes: Int): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, colorRes))
            textSize = 14f
            setPadding(32, 24, 32, 16)
        }
    }

    // WIDGET CREATION FUNCTIONS

    fun createControlsCard(context: Context, screenshotAction: () -> Unit, shutdownAction: () -> Unit, sleepAction: () -> Unit): CardView {
        val card = createWidgetCard(context)
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle(context, "CONTROLS", android.R.color.holo_blue_light))

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 0, 32, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val screenshotButton = Button(context).apply {
            text = "Screenshot"
            setOnClickListener { screenshotAction() }
        }
        val screenshotParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        contentLayout.addView(screenshotButton, screenshotParams)

        val shutdownButton = Button(context).apply {
            text = "Shutdown"
            setOnClickListener { shutdownAction() }
        }
        val shutdownParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8; marginEnd = 8 }
        contentLayout.addView(shutdownButton, shutdownParams)

        val sleepButton = Button(context).apply {
            text = "Sleep"
            setOnClickListener { sleepAction() }
        }
        val sleepParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8 }
        contentLayout.addView(sleepButton, sleepParams)

        layout.addView(contentLayout)
        card.addView(layout)
        return card
    }

    fun createAudioMixerCard(context: Context, inflater: LayoutInflater, sessions: List<MixerSession>, userTouchingSliders: MutableSet<String>, onVolumeChange: (String, Int) -> Unit): CardView? {
        if (sessions.isEmpty()) return null

        val card = createWidgetCard(context)
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle(context, "AUDIO MIXER", R.color.accent_neon))

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (session in sessions) {
            val view = inflater.inflate(R.layout.item_mixer_app, contentLayout, false)
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
                    seekBar?.let { onVolumeChange(session.name, it.progress) }
                }
            })
            contentLayout.addView(view)
        }
        layout.addView(contentLayout)
        card.addView(layout)
        return card
    }

    fun createDisksCard(context: Context, disks: List<DiskData>): CardView? {
        if (disks.isEmpty()) return null

        val card = createWidgetCard(context)
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle(context, "STORAGE", R.color.color_disk))

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (disk in disks) {
            val title = TextView(context).apply {
                text = "${disk.dev} ${disk.used} / ${disk.total} GB"
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
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

    fun createFansCard(context: Context, fans: List<FanData>): CardView? {
        if (fans.isEmpty()) return null

        val card = createWidgetCard(context)
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle(context, "COOLING (RPM)", R.color.color_fan))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 0, 32, 24)
        }

        for (fan in fans) {
            val fanLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                gravity = Gravity.CENTER
            }
            val rpm = TextView(context).apply {
                text = "${fan.rpm}"
                textSize = 18f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            val name = TextView(context).apply {
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

    fun createProcsCard(context: Context, procs: List<ProcessData>, onKillClick: (ProcessData) -> Unit): CardView? {
        if (procs.isEmpty()) return null

        val card = createWidgetCard(context)
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(createWidgetTitle(context, "TOP PROCESSES", android.R.color.darker_gray))

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 24)
        }

        for (proc in procs) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val name = TextView(context).apply {
                text = proc.name
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val usage = TextView(context).apply {
                text = "${proc.cpu}%"
                setTextColor(ContextCompat.getColor(context, R.color.color_cpu))
                minWidth = 120
                gravity = Gravity.END
            }
            val killButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.parseColor("#FF4081"), android.graphics.PorterDuff.Mode.SRC_IN)
                val params = LinearLayout.LayoutParams(60, 60)
                params.marginStart = 24
                layoutParams = params
                setOnClickListener { onKillClick(proc) }
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
}
