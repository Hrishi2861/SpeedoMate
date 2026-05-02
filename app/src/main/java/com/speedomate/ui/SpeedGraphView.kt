package com.speedomate.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SpeedGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points = listOf<Float>()
    private var conversionFactor = 3.6f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textSize = 24f
        textAlign = Paint.Align.LEFT
    }

    fun setSpeedPoints(speedPoints: List<Float>, factor: Float) {
        points = speedPoints
        conversionFactor = factor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No data", width / 2f, height / 2f, labelPaint)
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val maxVal = (points.maxOrNull() ?: 1f) * conversionFactor
        val padding = 8f

        // Grid lines
        for (i in 1..3) {
            val y = h - (h * i / 4f)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        // Build path
        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { index, value ->
            val x = padding + (index.toFloat() / (points.size - 1)) * (w - padding * 2)
            val y = h - padding - ((value * conversionFactor / maxVal) * (h - padding * 2))
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Fill gradient
        fillPath.lineTo(w - padding, h)
        fillPath.close()
        val shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#4400E5FF"),
            Color.parseColor("#0000E5FF"),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = shader
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // Max label
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("%.0f".format(maxVal), 4f, 28f, labelPaint)
    }
}