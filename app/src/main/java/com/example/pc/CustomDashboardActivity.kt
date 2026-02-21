package com.example.pc

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class CustomDashboardActivity : AppCompatActivity() {

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var editDashboardButton: Button
    private lateinit var addWidgetButton: Button

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

    private val gson = Gson()
    private val prefsName = "DashboardPrefs"
    private val layoutKey = "dashboardLayout"

    private val handler = Handler(Looper.getMainLooper())
    private var currentApi: ApiService? = null
    private var currentStats: PCStats? = null
    private val runnable = object : Runnable {
        override fun run() {
            fetchStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_dashboard)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        mainLayout = findViewById(R.id.main)
        editDashboardButton = findViewById(R.id.edit_dashboard_button)
        addWidgetButton = findViewById(R.id.add_widget_button)

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editDashboardButton.setOnClickListener { toggleEditMode() }
        addWidgetButton.setOnClickListener { showAddWidgetDialog() }

        mainLayout.post {
            cellWidth = mainLayout.width / gridColumns
            cellHeight = mainLayout.height / gridRows
            mainLayout.background = GridDrawable()
            testLayout = loadDashboardLayout()
            displayDashboard(testLayout)
        }

        val ip = intent.getStringExtra("DEVICE_IP")
        if (ip != null) {
            currentApi = RetrofitClient.getClient(ip)
            handler.post(runnable)
        } else {
            // Handle missing IP
        }
    }

    override fun onStop() {
        super.onStop()
        saveDashboardLayout()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    private fun fetchStats() {
        currentApi?.getStats()?.enqueue(object : Callback<PCStats> {
            override fun onResponse(call: Call<PCStats>, response: Response<PCStats>) {
                if (response.isSuccessful) {
                    currentStats = response.body()
                    currentStats?.let { updateAllWidgets(it) } // Обновляем все виджеты
                }
            }
            override fun onFailure(call: Call<PCStats>, t: Throwable) {
                // Handle failure
            }
        })
    }

    private fun updateAllWidgets(stats: PCStats) {
        for (view in widgetViews.keys) {
            if (view is UpdatableWidget) {
                view.updateData(stats)
            }
        }
    }

    private fun saveDashboardLayout() {
        if (::testLayout.isInitialized) {
            val jsonLayout = gson.toJson(testLayout)
            val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().putString(layoutKey, jsonLayout).apply()
        }
    }

    private fun loadDashboardLayout(): DashboardLayout {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val jsonLayout = prefs.getString(layoutKey, null)
        return if (jsonLayout != null) {
            gson.fromJson(jsonLayout, DashboardLayout::class.java)
        } else {
            createTestDashboardLayout()
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (!isEditMode) {
            saveDashboardLayout()
        }
        editDashboardButton.text = if (isEditMode) "Готово" else "Редактировать"
        addWidgetButton.visibility = if (isEditMode) View.VISIBLE else View.GONE
        mainLayout.invalidate()
        resizeHandles.values.forEach { it.visibility = if (isEditMode) View.VISIBLE else View.GONE }
    }

    private fun displayDashboard(layout: DashboardLayout) {
        widgetViews.keys.forEach { mainLayout.removeView(it) }
        resizeHandles.values.forEach { mainLayout.removeView(it) }
        widgetViews.clear()
        resizeHandles.clear()

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

            currentStats?.let { (widgetView as? UpdatableWidget)?.updateData(it) }
        }
    }

    private fun showAddWidgetDialog() {
        val widgetTypes = WidgetType.values()
        val widgetNames = widgetTypes.map {
            it.name.replace('_', ' ').lowercase(Locale.getDefault()).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Добавить виджет")
            .setItems(widgetNames) { _, which ->
                val selectedType = widgetTypes[which]

                val newWidget = WidgetConfig(
                    type = selectedType,
                    x = 0, y = 0, width = 3, height = 2
                )

                val updatedWidgets = testLayout.widgets.toMutableList().apply { add(newWidget) }
                testLayout = testLayout.copy(widgets = updatedWidgets)
                displayDashboard(testLayout)
            }
            .setNegativeButton("Отмена", null)
            .show()
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
                handle?.x = newX + view.width - (handle?.width ?: 0)
                handle?.y = newY + view.height - (handle?.height ?: 0)
            }
            MotionEvent.ACTION_UP -> {
                val currentConfig = widgetViews[view] ?: return
                val newGridX = (view.x / cellWidth).roundToInt().coerceIn(0, gridColumns - currentConfig.width)
                val newGridY = (view.y / cellHeight).roundToInt().coerceIn(0, gridRows - currentConfig.height)
                val newConfig = currentConfig.copy(x = newGridX, y = newGridY)

                val widgets = testLayout.widgets.toMutableList()
                val index = widgets.indexOf(currentConfig)
                if (index != -1) {
                    widgets[index] = newConfig
                    testLayout = testLayout.copy(widgets = widgets)
                    widgetViews[view] = newConfig
                }

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

                handle?.x = widgetView.x + lp.width - (handle?.width ?: 0)
                handle?.y = widgetView.y + lp.height - (handle?.height ?: 0)
            }
            MotionEvent.ACTION_UP -> {
                val config = widgetViews[widgetView] ?: return
                val newWidthInCells = (lp.width / cellWidth.toFloat()).roundToInt().coerceIn(1, gridColumns - config.x)
                val newHeightInCells = (lp.height / cellHeight.toFloat()).roundToInt().coerceIn(1, gridRows - config.y)
                val newConfig = config.copy(width = newWidthInCells, height = newHeightInCells)

                val widgets = testLayout.widgets.toMutableList()
                val index = widgets.indexOf(config)
                if (index != -1) {
                    widgets[index] = newConfig
                    testLayout = testLayout.copy(widgets = widgets)
                    widgetViews[widgetView] = newConfig
                }

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
        val handleSize = 60
        handle?.layoutParams?.width = handleSize
        handle?.layoutParams?.height = handleSize

        if (animate) {
            view.animate().x(targetX).y(targetY).setDuration(200).start()
            handle?.animate()?.x(targetX + targetWidth - handleSize)?.y(targetY + targetHeight - handleSize)?.setDuration(200)?.start()
        } else {
            view.x = targetX
            view.y = targetY
            handle?.x = targetX + targetWidth - handleSize
            handle?.y = targetY + targetHeight - handleSize
        }
        view.requestLayout()
        handle?.requestLayout()
    }

    private fun createWidget(config: WidgetConfig): CardView? {
        return when (config.type) {
            WidgetType.CONTROLS -> WidgetFactory.createControlsCard(this, {}, {}, {})
            WidgetType.AUDIO_MIXER -> WidgetFactory.createAudioMixerCard(this, layoutInflater, mutableSetOf()) { _, _ -> }
            WidgetType.STORAGE -> WidgetFactory.createDisksCard(this)
            WidgetType.COOLING -> WidgetFactory.createFansCard(this)
            WidgetType.TOP_PROCESSES -> WidgetFactory.createProcsCard(this) {}
        }
    }

    private fun createTestDashboardLayout(): DashboardLayout {
        return DashboardLayout("My Dashboard", emptyList())
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
