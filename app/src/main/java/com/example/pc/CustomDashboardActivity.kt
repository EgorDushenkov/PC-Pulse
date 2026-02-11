package com.example.pc

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class CustomDashboardActivity : AppCompatActivity() {

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var editDashboardButton: Button

    private var isEditMode = false
    private lateinit var testLayout: DashboardLayout
    private val widgetViews = mutableMapOf<View, WidgetConfig>()
    private val resizeHandles = mutableMapOf<View, View>()

    private val gridColumns = 12
    private val gridRows = 8
    private var cellWidth: Int = 0
    private var cellHeight: Int = 0

    private var dX = 0f
    private var dY = 0f
    private var startWidth = 0
    private var startHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_dashboard)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        mainLayout = findViewById(R.id.main)
        editDashboardButton = findViewById(R.id.edit_dashboard_button)

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editDashboardButton.setOnClickListener { toggleEditMode() }

        mainLayout.post {
            cellWidth = mainLayout.width / gridColumns
            cellHeight = mainLayout.height / gridRows
            mainLayout.background = GridDrawable()
            testLayout = createTestDashboardLayout()
            displayDashboard(testLayout)
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        editDashboardButton.text = if (isEditMode) "Готово" else "Редактировать"
        mainLayout.invalidate()
        resizeHandles.values.forEach { it.visibility = if (isEditMode) View.VISIBLE else View.GONE }
    }

    private fun displayDashboard(layout: DashboardLayout) {
        mainLayout.removeAllViews()
        widgetViews.clear()
        resizeHandles.clear()
        mainLayout.addView(editDashboardButton)

        for (widgetConfig in layout.widgets) {
            val widgetView = createWidget(widgetConfig) ?: continue

            widgetView.id = View.generateViewId()
            mainLayout.addView(widgetView, 0)
            widgetViews[widgetView] = widgetConfig

            setupDragAndDrop(widgetView)

            val resizeHandle = createResizeHandle(widgetView)
            mainLayout.addView(resizeHandle)
            resizeHandles[widgetView] = resizeHandle

            applyWidgetLayout(widgetView, widgetConfig, false)
        }
    }

    private fun setupDragAndDrop(view: View) {
        view.setOnTouchListener { v, event ->
            if (isEditMode) {
                handleDragAndDrop(v, event)
                true
            } else false
        }
    }

    private fun createResizeHandle(widgetView: View): View {
        return ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(Color.parseColor("#80FF4081"))
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            // --- ГЛАВНОЕ ИЗМЕНЕНИЕ: Добавляем "возвышение", чтобы ручка была над виджетом ---
            elevation = 10 * resources.displayMetrics.density
            setOnTouchListener { _, event ->
                handleResize(widgetView, event)
                true
            }
        }
    }

    private fun handleDragAndDrop(view: View, event: MotionEvent) {
        val handle = resizeHandles[view]
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = view.x - event.rawX
                dY = view.y - event.rawY
                view.bringToFront()
                handle?.bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX + dX
                val newY = event.rawY + dY
                view.x = newX
                view.y = newY
                handle?.x = newX + view.width - (handle.width)
                handle?.y = newY + view.height - (handle.height)
            }
            MotionEvent.ACTION_UP -> {
                val currentConfig = widgetViews[view] ?: return
                val newGridX = (view.x / cellWidth).roundToInt().coerceIn(0, gridColumns - currentConfig.width)
                val newGridY = (view.y / cellHeight).roundToInt().coerceIn(0, gridRows - currentConfig.height)
                val newConfig = currentConfig.copy(x = newGridX, y = newGridY)

                (testLayout.widgets as MutableList)[testLayout.widgets.indexOf(currentConfig)] = newConfig
                widgetViews[view] = newConfig

                applyWidgetLayout(view, newConfig, true)
            }
        }
    }

    private fun handleResize(widgetView: View, event: MotionEvent) {
        val lp = widgetView.layoutParams
        val handle = resizeHandles[widgetView]
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = event.rawX
                dY = event.rawY
                startWidth = lp.width
                startHeight = lp.height
                widgetView.bringToFront()
                handle?.bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                lp.width = (startWidth + (event.rawX - dX)).toInt().coerceAtLeast(cellWidth)
                lp.height = (startHeight + (event.rawY - dY)).toInt().coerceAtLeast(cellHeight)
                widgetView.layoutParams = lp

                handle?.x = widgetView.x + lp.width - (handle.width)
                handle?.y = widgetView.y + lp.height - (handle.height)
            }
            MotionEvent.ACTION_UP -> {
                val config = widgetViews[widgetView] ?: return
                val newWidthInCells = (lp.width / cellWidth.toFloat()).roundToInt().coerceIn(1, gridColumns - config.x)
                val newHeightInCells = (lp.height / cellHeight.toFloat()).roundToInt().coerceIn(1, gridRows - config.y)
                val newConfig = config.copy(width = newWidthInCells, height = newHeightInCells)

                widgetViews[widgetView] = newConfig
                (testLayout.widgets as MutableList)[testLayout.widgets.indexOf(config)] = newConfig

                applyWidgetLayout(widgetView, newConfig, true)
            }
        }
    }

    private fun applyWidgetLayout(view: View, config: WidgetConfig, animate: Boolean = true) {
        val targetWidth = config.width * cellWidth
        val targetHeight = config.height * cellHeight
        val targetX = (config.x * cellWidth).toFloat()
        val targetY = (config.y * cellHeight).toFloat()

        view.layoutParams.width = targetWidth
        view.layoutParams.height = targetHeight

        val handle = resizeHandles[view]
        handle?.layoutParams?.width = 60
        handle?.layoutParams?.height = 60

        if (animate) {
            view.animate().x(targetX).y(targetY).setDuration(200).start()
            handle?.animate()?.x(targetX + targetWidth - 60)?.y(targetY + targetHeight - 60)?.setDuration(200)?.start()
        } else {
            view.x = targetX
            view.y = targetY
            handle?.x = targetX + targetWidth - 60
            handle?.y = targetY + targetHeight - 60
        }
        view.requestLayout()
        handle?.requestLayout()
    }

    private fun createWidget(config: WidgetConfig): CardView? {
        return when (config.type) {
            WidgetType.CONTROLS -> WidgetFactory.createControlsCard(this, {}, {}, {})
            WidgetType.AUDIO_MIXER -> WidgetFactory.createAudioMixerCard(this, layoutInflater, listOf(MixerSession("Spotify", 75)), mutableSetOf(), { _, _ -> })
            WidgetType.STORAGE -> WidgetFactory.createDisksCard(this, listOf(DiskData("C:", 50.0F, 100.0F, 50.0)))
            WidgetType.COOLING -> WidgetFactory.createFansCard(this, listOf(FanData("CPU Fan", 1200)))
            WidgetType.TOP_PROCESSES -> WidgetFactory.createProcsCard(this, listOf(ProcessData(123, "System", 10.0)), {})
        }
    }

    private fun createTestDashboardLayout(): DashboardLayout {
        val widgets = mutableListOf(
            WidgetConfig(WidgetType.CONTROLS, x = 0, y = 0, width = 4, height = 1),
            WidgetConfig(WidgetType.STORAGE, x = 0, y = 1, width = 4, height = 2),
            WidgetConfig(WidgetType.AUDIO_MIXER, x = 4, y = 0, width = 4, height = 3)
        )
        return DashboardLayout("Test Dashboard", widgets)
    }

    private inner class GridDrawable : android.graphics.drawable.Drawable() {
        private val paint = Paint().apply { color = Color.parseColor("#40FFFFFF"); strokeWidth = 2f; style = Paint.Style.STROKE }
        override fun draw(canvas: Canvas) {
            if (!isEditMode) return
            val cellWidth = bounds.width() / gridColumns.toFloat()
            val cellHeight = bounds.height() / gridRows.toFloat()
            for (i in 1 until gridColumns) { canvas.drawLine(i * cellWidth, 0f, i * cellWidth, bounds.height().toFloat(), paint) }
            for (i in 1 until gridRows) { canvas.drawLine(0f, i * cellHeight, bounds.width().toFloat(), i * cellHeight, paint) }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @SuppressLint("Deprecateda-level")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
