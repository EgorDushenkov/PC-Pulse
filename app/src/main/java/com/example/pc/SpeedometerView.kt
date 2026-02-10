package com.example.pc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Атрибуты и переменные ---
    private var speedometerColor = Color.parseColor("#BB86FC")
    private var speedometerBackgroundColor = Color.parseColor("#33FFFFFF")
    private var titleTextColor = Color.GRAY
    private var valueTextColor = Color.WHITE
    private var titleText = ""
    private var value = 0f
    private var maxValue = 100f

    // --- "Кисти" для рисования ---
    private val backgroundPaint: Paint
    private val progressPaint: Paint
    private val titleTextPaint: Paint
    private val valueTextPaint: Paint

    // --- Размеры и геометрия ---
    private val oval = RectF()
    private var centerX = 0f
    private var centerY = 0f

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SpeedometerView)
        speedometerColor = typedArray.getColor(R.styleable.SpeedometerView_speedometerColor, speedometerColor)
        speedometerBackgroundColor = typedArray.getColor(R.styleable.SpeedometerView_speedometerBackgroundColor, speedometerBackgroundColor)
        titleTextColor = typedArray.getColor(R.styleable.SpeedometerView_titleTextColor, titleTextColor)
        valueTextColor = typedArray.getColor(R.styleable.SpeedometerView_textColor, valueTextColor)
        titleText = typedArray.getString(R.styleable.SpeedometerView_titleText) ?: ""
        value = typedArray.getFloat(R.styleable.SpeedometerView_value, 0f)
        maxValue = typedArray.getFloat(R.styleable.SpeedometerView_maxValue, 100f)
        typedArray.recycle()

        backgroundPaint = Paint().apply {
            color = speedometerBackgroundColor
            style = Paint.Style.STROKE
            strokeWidth = 12f // Потоньше для компактного вида
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        progressPaint = Paint().apply {
            color = speedometerColor
            style = Paint.Style.STROKE
            strokeWidth = 12f // Потоньше для компактного вида
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        titleTextPaint = Paint().apply {
            color = titleTextColor
            textSize = 22f // Меньше
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        valueTextPaint = Paint().apply {
            color = valueTextColor
            textSize = 36f // Меньше
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val radius = min(w, h) / 2f - backgroundPaint.strokeWidth / 2f
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startAngle = -225f
        val sweepAngle = 270f

        // 1. Рисуем фоновую дугу
        canvas.drawArc(oval, startAngle, sweepAngle, false, backgroundPaint)

        // 2. Рисуем дугу прогресса
        val progressSweep = (value / maxValue) * sweepAngle
        canvas.drawArc(oval, startAngle, progressSweep, false, progressPaint)

        // 3. Рисуем текст (заголовок и значение)
        val titleY = centerY - 15
        val valueY = centerY + 30
        canvas.drawText(titleText, centerX, titleY, titleTextPaint)
        canvas.drawText("${value.toInt()}%", centerX, valueY, valueTextPaint)
    }

    fun setValue(newValue: Float) {
        value = if (newValue > maxValue) maxValue else if (newValue < 0) 0f else newValue
        invalidate() // Перерисовать View
    }
}