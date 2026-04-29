package com.speedomate.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentSpeed = 0f
    private var maxDisplaySpeed = 180f // max on dial

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#111111")
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = Color.parseColor("#1A1A1A")
    }

    private val arcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1E1E1E")
    }

    private val arcFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#444444")
        strokeCap = Paint.Cap.ROUND
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#888888")
        strokeCap = Paint.Cap.ROUND
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E5FF")
    }

    private val needleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3000E5FF")
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E5FF")
    }

    private val centerDotInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0D0D0D")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val dangerArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF3D3D")
    }

    // Angle config: 135° to 405° (270° sweep)
    private val startAngle = 135f
    private val sweepAngle = 270f

    var unit = "km/h"
        set(value) {
            field = value
            invalidate()
        }

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0f, maxDisplaySpeed)
        invalidate()
    }

    fun setMaxDisplaySpeed(max: Float) {
        maxDisplaySpeed = max
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) * 0.88f

        // Background circle
        bgPaint.maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, radius, bgPaint)
        bgPaint.maskFilter = null
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Outer ring
        outerRingPaint.strokeWidth = radius * 0.04f
        canvas.drawCircle(cx, cy, radius, outerRingPaint)

        val arcStrokeWidth = radius * 0.06f
        val arcRadius = radius * 0.82f
        val arcRect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)

        // Background arc
        arcBgPaint.strokeWidth = arcStrokeWidth
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, arcBgPaint)

        // Danger zone arc (last 20% = red)
        val dangerStart = startAngle + sweepAngle * 0.8f
        val dangerSweep = sweepAngle * 0.2f
        dangerArcPaint.strokeWidth = arcStrokeWidth
        canvas.drawArc(arcRect, dangerStart, dangerSweep, false, dangerArcPaint)

        // Speed fill arc with gradient
        val speedFraction = currentSpeed / maxDisplaySpeed
        val fillSweep = sweepAngle * speedFraction

        if (fillSweep > 0f) {
            // Glow effect
            arcFillPaint.color = Color.parseColor("#4400E5FF")
            arcFillPaint.strokeWidth = arcStrokeWidth * 2.5f
            arcFillPaint.maskFilter = BlurMaskFilter(arcStrokeWidth, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(arcRect, startAngle, fillSweep, false, arcFillPaint)

            // Main arc
            arcFillPaint.color = Color.parseColor("#00E5FF")
            arcFillPaint.strokeWidth = arcStrokeWidth
            arcFillPaint.maskFilter = null
            canvas.drawArc(arcRect, startAngle, fillSweep, false, arcFillPaint)
        }

        // Tick marks
        val totalTicks = 36
        for (i in 0..totalTicks) {
            val fraction = i.toFloat() / totalTicks
            val angle = Math.toRadians((startAngle + sweepAngle * fraction).toDouble())
            val isMajor = i % 6 == 0
            val tickOuter = if (isMajor) radius * 0.76f else radius * 0.78f
            val tickInner = if (isMajor) radius * 0.65f else radius * 0.72f

            val paint = if (isMajor) majorTickPaint else tickPaint
            paint.strokeWidth = if (isMajor) radius * 0.025f else radius * 0.012f

            canvas.drawLine(
                cx + tickOuter * cos(angle).toFloat(),
                cy + tickOuter * sin(angle).toFloat(),
                cx + tickInner * cos(angle).toFloat(),
                cy + tickInner * sin(angle).toFloat(),
                paint
            )

            // Speed labels at major ticks
            if (isMajor) {
                val labelRadius = radius * 0.56f
                val labelX = cx + labelRadius * cos(angle).toFloat()
                val labelY = cy + labelRadius * sin(angle).toFloat()
                val labelSpeed = (maxDisplaySpeed * fraction).toInt()
                textPaint.textSize = radius * 0.1f
                canvas.drawText(labelSpeed.toString(), labelX, labelY + textPaint.textSize / 3, textPaint)
            }
        }

        // Needle glow
        val needleAngle = Math.toRadians((startAngle + sweepAngle * speedFraction).toDouble())
        val needleLength = radius * 0.68f
        val needleTailLength = radius * 0.15f

        needleGlowPaint.strokeWidth = radius * 0.04f
        canvas.drawLine(
            cx - needleTailLength * cos(needleAngle).toFloat(),
            cy - needleTailLength * sin(needleAngle).toFloat(),
            cx + needleLength * cos(needleAngle).toFloat(),
            cy + needleLength * sin(needleAngle).toFloat(),
            needleGlowPaint
        )

        // Needle
        needlePaint.strokeWidth = radius * 0.022f
        canvas.drawLine(
            cx - needleTailLength * cos(needleAngle).toFloat(),
            cy - needleTailLength * sin(needleAngle).toFloat(),
            cx + needleLength * cos(needleAngle).toFloat(),
            cy + needleLength * sin(needleAngle).toFloat(),
            needlePaint
        )

        // Center dot
        canvas.drawCircle(cx, cy, radius * 0.09f, centerDotPaint)
        canvas.drawCircle(cx, cy, radius * 0.05f, centerDotInnerPaint)

        // Digital speed in center
        speedTextPaint.textSize = radius * 0.32f
        canvas.drawText(
            currentSpeed.toInt().toString(),
            cx, cy + radius * 0.65f,
            speedTextPaint
        )

        // Unit label
        unitTextPaint.textSize = radius * 0.11f
        canvas.drawText(unit, cx, cy + radius * 0.80f, unitTextPaint)
    }
}