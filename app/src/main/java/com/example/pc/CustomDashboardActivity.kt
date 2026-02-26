package com.example.pc

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class CustomDashboardActivity : AppCompatActivity() {

    private lateinit var dashboardCanvas: FrameLayout
    private lateinit var controlPanel: LinearLayout
    private lateinit var editDashboardButton: Button
    private lateinit var addWidgetButton: Button

    private var isEditMode = false
    private lateinit var testLayout: DashboardLayout
    private val widgetViews = mutableMapOf<View, WidgetConfig>()
    private val resizeHandles = mutableMapOf<View, View>()
    private val deleteHandles = mutableMapOf<View, View>()

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
        dashboardCanvas = findViewById(R.id.dashboard_canvas)
        controlPanel = findViewById(R.id.control_panel)
        editDashboardButton = findViewById(R.id.edit_dashboard_button)
        addWidgetButton = findViewById(R.id.add_widget_button)

        val mainLayout = findViewById<ConstraintLayout>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editDashboardButton.setOnClickListener { toggleEditMode() }
        addWidgetButton.setOnClickListener { showAddWidgetDialog() }

        dashboardCanvas.post {
            cellWidth = dashboardCanvas.width / gridColumns
            cellHeight = dashboardCanvas.height / gridRows
            dashboardCanvas.background = GridDrawable()
            testLayout = loadDashboardLayout()
            displayDashboard(testLayout)
        }

        val ip = intent.getStringExtra("DEVICE_IP")
        if (ip != null) {
            currentApi = RetrofitClient.getClient(ip)
            handler.post(runnable)
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
                    currentStats?.let { updateAllWidgets(it) }
                }
            }
            override fun onFailure(call: Call<PCStats>, t: Throwable) { /* Handle failure */ }
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
        dashboardCanvas.invalidate()
        resizeHandles.values.forEach { it.visibility = if (isEditMode) View.VISIBLE else View.GONE }
        deleteHandles.values.forEach { it.visibility = if (isEditMode) View.VISIBLE else View.GONE }
    }

    private fun displayDashboard(layout: DashboardLayout) {
        dashboardCanvas.removeAllViews()
        widgetViews.clear()
        resizeHandles.clear()
        deleteHandles.clear()

        for (widgetConfig in layout.widgets) {
            val widgetView = createWidget(widgetConfig) ?: continue
            widgetView.id = View.generateViewId()
            dashboardCanvas.addView(widgetView, 0)
            widgetViews[widgetView] = widgetConfig

            setupDragAndDrop(widgetView)

            val resizeHandle = createResizeHandle(widgetView)
            dashboardCanvas.addView(resizeHandle)
            resizeHandles[widgetView] = resizeHandle

            val deleteHandle = createDeleteHandle(widgetView)
            dashboardCanvas.addView(deleteHandle)
            deleteHandles[widgetView] = deleteHandle

            applyWidgetLayout(widgetView, widgetConfig, false)

            currentStats?.let { (widgetView as? UpdatableWidget)?.updateData(it) }
        }
    }

    private fun showAddWidgetDialog() {
        val widgetTypes = WidgetType.entries.toTypedArray()
        val widgetNames = widgetTypes.map {
            it.name.replace('_', ' ').lowercase(Locale.getDefault()).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Добавить виджет")
            .setItems(widgetNames) { _, which ->
                val selectedType = widgetTypes[which]
                val newWidget = WidgetConfig(type = selectedType, x = 0, y = 0, width = 3, height = 2)
                val updatedWidgets = testLayout.widgets.toMutableList().apply { add(newWidget) }
                testLayout = testLayout.copy(widgets = updatedWidgets)
                displayDashboard(testLayout)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupDragAndDrop(view: View) {
        view.setOnTouchListener { v, event -> if (isEditMode) { handleDragAndDrop(v, event); true } else false }
    }

    private fun createResizeHandle(widgetView: View): View {
        return ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(Color.parseColor("#80FF4081"))
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            elevation = 10 * resources.displayMetrics.density
            setOnTouchListener { _, event -> handleResize(widgetView, event); true }
        }
    }

    private fun createDeleteHandle(widgetView: View): View {
        return ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(Color.parseColor("#80FF4040"))
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            elevation = 10 * resources.displayMetrics.density
            setOnClickListener { showDeleteConfirmationDialog(widgetView) }
        }
    }

    private fun showDeleteConfirmationDialog(widgetView: View) {
        AlertDialog.Builder(this)
            .setTitle("Удалить виджет?")
            .setMessage("Вы уверены, что хотите удалить этот виджет?")
            .setPositiveButton("Да") { _, _ ->
                val configToRemove = widgetViews[widgetView]
                if (configToRemove != null) {
                    val updatedWidgets = testLayout.widgets.toMutableList().apply { remove(configToRemove) }
                    testLayout = testLayout.copy(widgets = updatedWidgets)
                    displayDashboard(testLayout)
                }
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun handleDragAndDrop(view: View, event: MotionEvent) {
        val resizeHandle = resizeHandles[view]!!
        val deleteHandle = deleteHandles[view]!!
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = view.x - event.rawX
                dY = view.y - event.rawY
                view.bringToFront()
                resizeHandle.bringToFront()
                deleteHandle.bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX + dX
                val newY = event.rawY + dY
                view.x = newX
                view.y = newY
                resizeHandle.x = newX + view.width - resizeHandle.width
                resizeHandle.y = newY + view.height - resizeHandle.height
                deleteHandle.x = newX
                deleteHandle.y = newY
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
        val resizeHandle = resizeHandles[widgetView]!!
        val deleteHandle = deleteHandles[widgetView]!!
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = event.rawX
                dY = event.rawY
                startWidth = lp.width
                startHeight = lp.height
                widgetView.bringToFront()
                resizeHandle.bringToFront()
                deleteHandle.bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                lp.width = (startWidth + (event.rawX - dX)).toInt().coerceAtLeast(cellWidth)
                lp.height = (startHeight + (event.rawY - dY)).toInt().coerceAtLeast(cellHeight)
                widgetView.layoutParams = lp
                resizeHandle.x = widgetView.x + lp.width - resizeHandle.width
                resizeHandle.y = widgetView.y + lp.height - resizeHandle.height
                deleteHandle.x = widgetView.x
                deleteHandle.y = widgetView.y
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

        val handleSize = 60
        val resizeHandle = resizeHandles[view]
        resizeHandle?.layoutParams?.width = handleSize
        resizeHandle?.layoutParams?.height = handleSize

        val deleteHandle = deleteHandles[view]
        deleteHandle?.layoutParams?.width = handleSize
        deleteHandle?.layoutParams?.height = handleSize

        if (animate) {
            view.animate().x(targetX).y(targetY).setDuration(200).start()
            resizeHandle?.animate()?.x(targetX + targetWidth - handleSize)?.y(targetY + targetHeight - handleSize)?.setDuration(200)?.start()
            deleteHandle?.animate()?.x(targetX)?.y(targetY)?.setDuration(200)?.start()
        } else {
            view.x = targetX
            view.y = targetY
            resizeHandle?.x = targetX + targetWidth - handleSize
            resizeHandle?.y = targetY + targetHeight - handleSize
            deleteHandle?.x = targetX
            deleteHandle?.y = targetY
        }
        view.requestLayout()
        resizeHandle?.requestLayout()
        deleteHandle?.requestLayout()
    }

    private fun createWidget(config: WidgetConfig): CardView? {
        return when (config.type) {
            WidgetType.CONTROLS -> WidgetFactory.createControlsCard(this, ::showScreenshotDialog, ::sendSleepCommand, ::sendShutdownCommand)
            WidgetType.AUDIO_MIXER -> WidgetFactory.createAudioMixerCard(this) { name, vol -> sendMixerVolume(name, vol) }
            WidgetType.STORAGE -> WidgetFactory.createDisksCard(this)
            WidgetType.COOLING -> WidgetFactory.createFansCard(this)
            WidgetType.TOP_PROCESSES -> WidgetFactory.createProcsCard(this) { pid -> killProcess(pid) }
        }
    }

    private fun createTestDashboardLayout(): DashboardLayout {
        return DashboardLayout("My Dashboard", emptyList())
    }

    // --- Commands logic ---

    private fun showScreenshotDialog() {
        Toast.makeText(this, "Taking Screenshot...", Toast.LENGTH_SHORT).show()
        currentApi?.getScreenshot()?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val view = layoutInflater.inflate(R.layout.dialog_screenshot, null)
                    val imgView = view.findViewById<ImageView>(R.id.screenshotImage)
                    imgView.setImageBitmap(bitmap)

                    AlertDialog.Builder(this@CustomDashboardActivity)
                        .setTitle("PC Screenshot")
                        .setView(view)
                        .setPositiveButton("Close") { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(applicationContext, "Failed to get image", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(applicationContext, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendShutdownCommand() {
        showPowerActionDialog("выключить", "Выключение") {
            currentApi?.shutdownPC()?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    Toast.makeText(this@CustomDashboardActivity, "PC is shutting down...", Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(this@CustomDashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun sendSleepCommand() {
        showPowerActionDialog("отправить в спящий режим", "Сон") {
            currentApi?.sleepPC()?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    Toast.makeText(this@CustomDashboardActivity, "Putting PC to sleep...", Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(this@CustomDashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showPowerActionDialog(actionName: String, title: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Вы уверены, что хотите $actionName ПК?")
            .setPositiveButton("Да") { _, _ -> onConfirm() }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun killProcess(pid: Int) {
        currentApi?.killProcess(pid)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                val message = if (response.isSuccessful) response.body() ?: "Процесс завершен" else "Ошибка: ${response.code()}"
                Toast.makeText(this@CustomDashboardActivity, message, Toast.LENGTH_SHORT).show()
                handler.postDelayed({ fetchStats() }, 250)
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@CustomDashboardActivity, "Сетевая ошибка: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendMixerVolume(appName: String, volume: Int) {
        currentApi?.setMixerVolume(appName, volume)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {}
            override fun onFailure(call: Call<String>, t: Throwable) {}
        })
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
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
