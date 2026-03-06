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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class CustomDashboardActivity : BaseActivity() {

    private lateinit var dashboardCanvas: FrameLayout
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
        editDashboardButton = findViewById(R.id.edit_dashboard_button)
        addWidgetButton = findViewById(R.id.add_widget_button)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
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

        if (currentApi != null) handler.post(runnable)
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
                    currentStats?.let { stats ->
                        widgetViews.keys.forEach { (it as? UpdatableWidget)?.updateData(stats) }
                    }
                }
            }
            override fun onFailure(call: Call<PCStats>, t: Throwable) { }
        })
    }

    private fun saveDashboardLayout() {
        if (::testLayout.isInitialized) {
            val json = gson.toJson(testLayout)
            getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString(layoutKey, json).apply()
        }
    }

    private fun loadDashboardLayout(): DashboardLayout {
        val json = getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString(layoutKey, null)
        return json?.let { gson.fromJson(it, DashboardLayout::class.java) } ?: DashboardLayout("My Dashboard", emptyList())
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (!isEditMode) saveDashboardLayout()
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

        layout.widgets.forEach { config ->
            val widgetView = createWidget(config) ?: return@forEach
            widgetView.id = View.generateViewId()
            dashboardCanvas.addView(widgetView, 0)
            widgetViews[widgetView] = config

            setupDragAndDrop(widgetView)

            val rh = createHandle(widgetView, android.R.drawable.ic_menu_crop, getThemeColor(androidx.appcompat.R.attr.colorPrimary), 0.5f) { e -> handleResize(widgetView, e) }
            dashboardCanvas.addView(rh)
            resizeHandles[widgetView] = rh

            val dh = createHandle(widgetView, android.R.drawable.ic_menu_delete, Color.parseColor("#80FF4040"), 1f) { showDeleteDialog(widgetView) }
            dashboardCanvas.addView(dh)
            deleteHandles[widgetView] = dh

            applyWidgetLayout(widgetView, config, false)
            currentStats?.let { (widgetView as? UpdatableWidget)?.updateData(it) }
        }
    }

    private fun createHandle(widgetView: View, resId: Int, bgColor: Int, alphaVal: Float, onTouch: (MotionEvent) -> Unit): View {
        return ImageView(this).apply {
            setImageResource(resId)
            setBackgroundColor(bgColor)
            alpha = alphaVal
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            elevation = 10 * resources.displayMetrics.density
            setOnTouchListener { _, event -> onTouch(event); true }
        }
    }

    private fun showDeleteDialog(widgetView: View) {
        AlertDialog.Builder(this).setTitle("Удалить?").setMessage("Удалить виджет?").setPositiveButton("Да") { _, _ ->
            widgetViews[widgetView]?.let { config ->
                testLayout = testLayout.copy(widgets = testLayout.widgets - config)
                displayDashboard(testLayout)
            }
        }.setNegativeButton("Нет", null).show()
    }

    private fun showAddWidgetDialog() {
        val types = WidgetType.entries.toTypedArray()
        val names = types.map { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Добавить виджет").setItems(names) { _, which ->
            val new = WidgetConfig(types[which], 0, 0, 3, 2)
            testLayout = testLayout.copy(widgets = testLayout.widgets + new)
            displayDashboard(testLayout)
        }.setNegativeButton("Отмена", null).show()
    }

    private fun setupDragAndDrop(view: View) {
        view.setOnTouchListener { v, event -> if (isEditMode) { handleDragAndDrop(v, event); true } else false }
    }

    private fun handleDragAndDrop(view: View, event: MotionEvent) {
        val rh = resizeHandles[view]!!
        val dh = deleteHandles[view]!!
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = view.x - event.rawX; dY = view.y - event.rawY
                view.bringToFront(); rh.bringToFront(); dh.bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                val nx = event.rawX + dX; val ny = event.rawY + dY
                view.x = nx; view.y = ny
                rh.x = nx + view.width - rh.width; rh.y = ny + view.height - rh.height
                dh.x = nx; dh.y = ny
            }
            MotionEvent.ACTION_UP -> {
                val cfg = widgetViews[view] ?: return
                val gx = (view.x / cellWidth).roundToInt().coerceIn(0, gridColumns - cfg.width)
                val gy = (view.y / cellHeight).roundToInt().coerceIn(0, gridRows - cfg.height)
                updateWidgetConfig(view, cfg.copy(x = gx, y = gy))
            }
        }
    }

    private fun handleResize(view: View, event: MotionEvent) {
        val lp = view.layoutParams
        val rh = resizeHandles[view]!!
        val dh = deleteHandles[view]!!
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = event.rawX; dY = event.rawY
                startWidth = lp.width; startHeight = lp.height
                view.bringToFront(); rh.bringToFront(); dh.bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                lp.width = (startWidth + (event.rawX - dX)).toInt().coerceAtLeast(cellWidth)
                lp.height = (startHeight + (event.rawY - dY)).toInt().coerceAtLeast(cellHeight)
                view.layoutParams = lp
                rh.x = view.x + lp.width - rh.width; rh.y = view.y + lp.height - rh.height
                dh.x = view.x; dh.y = view.y
            }
            MotionEvent.ACTION_UP -> {
                val cfg = widgetViews[view] ?: return
                val nw = (lp.width / cellWidth.toFloat()).roundToInt().coerceIn(1, gridColumns - cfg.x)
                val nh = (lp.height / cellHeight.toFloat()).roundToInt().coerceIn(1, gridRows - cfg.y)
                updateWidgetConfig(view, cfg.copy(width = nw, height = nh))
            }
        }
    }

    private fun updateWidgetConfig(view: View, new: WidgetConfig) {
        val list = testLayout.widgets.toMutableList()
        val idx = list.indexOf(widgetViews[view])
        if (idx != -1) {
            list[idx] = new
            testLayout = testLayout.copy(widgets = list)
            widgetViews[view] = new
            applyWidgetLayout(view, new, true)
        }
    }

    private fun applyWidgetLayout(view: View, config: WidgetConfig, anim: Boolean) {
        val tw = config.width * cellWidth; val th = config.height * cellHeight
        val tx = (config.x * cellWidth).toFloat(); val ty = (config.y * cellHeight).toFloat()
        view.layoutParams.width = tw; view.layoutParams.height = th
        val h = 60
        val rh = resizeHandles[view]; val dh = deleteHandles[view]
        rh?.layoutParams?.width = h; rh?.layoutParams?.height = h
        dh?.layoutParams?.width = h; dh?.layoutParams?.height = h
        if (anim) {
            view.animate().x(tx).y(ty).setDuration(200).start()
            rh?.animate()?.x(tx + tw - h)?.y(ty + th - h)?.setDuration(200)?.start()
            dh?.animate()?.x(tx)?.y(ty)?.setDuration(200)?.start()
        } else {
            view.x = tx; view.y = ty; rh?.x = tx + tw - h; rh?.y = ty + th - h; dh?.x = tx; dh?.y = ty
        }
        view.requestLayout(); rh?.requestLayout(); dh?.requestLayout()
    }

    private fun createWidget(config: WidgetConfig): CardView? {
        return WidgetFactory.create(config.type, this, ::showScreenshotDialog, ::sendSleepCommand, ::sendShutdownCommand, ::sendMixerVolume) { pid -> killProcess(pid) { fetchStats() } } as? CardView
    }

    private inner class GridDrawable : android.graphics.drawable.Drawable() {
        private val p = Paint().apply { color = getThemeColor(androidx.appcompat.R.attr.colorPrimary); alpha = 40; strokeWidth = 2f; style = Paint.Style.STROKE }
        override fun draw(c: Canvas) {
            if (!isEditMode) return
            val cw = bounds.width() / gridColumns.toFloat(); val ch = bounds.height() / gridRows.toFloat()
            for (i in 1 until gridColumns) c.drawLine(i * cw, 0f, i * cw, bounds.height().toFloat(), p)
            for (i in 1 until gridRows) c.drawLine(0f, i * ch, bounds.width().toFloat(), i * ch, p)
        }
        override fun setAlpha(a: Int) { p.alpha = a }
        override fun setColorFilter(f: android.graphics.ColorFilter?) { p.colorFilter = f }
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}