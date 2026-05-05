package com.speedomate.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SpeedGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var speedPoints    = listOf<Float>()
    private var altPoints      = listOf<Double>()
    private var convFactor     = 3.6f
    private var durationMillis = 0L

    // Speed line — cyan
    private val speedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#00E5FF")
        strokeWidth = 3f
        style       = Paint.Style.STROKE
        strokeCap   = Paint.Cap.ROUND
        strokeJoin  = Paint.Join.ROUND
    }
    private val speedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Altitude line — orange
    private val altLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#FF8C00")
        strokeWidth = 2.5f
        style       = Paint.Style.STROKE
        strokeCap   = Paint.Cap.ROUND
        strokeJoin  = Paint.Join.ROUND
        pathEffect  = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val altFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Grid
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#1E1E1E")
        strokeWidth = 1f
        style       = Paint.Style.STROKE
    }
    private val gridDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#252525")
        strokeWidth = 1f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    // Labels
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#555555")
        textSize  = 22f
        textAlign = Paint.Align.RIGHT
        typeface  = Typeface.MONOSPACE
    }
    private val labelRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#FF8C00")
        textSize  = 22f
        textAlign = Paint.Align.LEFT
        typeface  = Typeface.MONOSPACE
    }
    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#444444")
        textSize  = 20f
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.MONOSPACE
    }
    private val legendSpeedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#00E5FF")
        textSize = 22f
    }
    private val legendAltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#FF8C00")
        textSize = 22f
    }

    fun setData(
        speeds: List<Float>,
        altitudes: List<Double>,
        factor: Float,
        durationMs: Long
    ) {
        speedPoints    = speeds
        altPoints      = altitudes
        convFactor     = factor
        durationMillis = durationMs
        invalidate()
    }

    // Keep backward compat
    fun setSpeedPoints(speeds: List<Float>, factor: Float) {
        speedPoints = speeds
        convFactor  = factor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (speedPoints.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            labelPaint.color     = Color.parseColor("#333333")
            canvas.drawText("No data", width / 2f, height / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.RIGHT
            labelPaint.color     = Color.parseColor("#555555")
            return
        }

        val w       = width.toFloat()
        val h       = height.toFloat()
        val padL    = 90f   // left for speed Y axis labels + title
        val padR    = 80f   // right for altitude Y axis labels + title
        val padT    = 56f   // top for legend labels + breathing room
        val padB    = 36f   // bottom for X axis labels
        val graphW  = w - padL - padR
        val graphH  = h - padT - padB

        // ── Speed range ──────────────────────────────────────
        val speedConverted = speedPoints.map { it * convFactor }
        val maxSpd = (speedConverted.maxOrNull() ?: 1f).coerceAtLeast(10f)
        val minSpd = 0f

        // ── Altitude range ───────────────────────────────────
        val hasAlt  = altPoints.isNotEmpty() && altPoints.size == speedPoints.size
        val maxAlt  = if (hasAlt) (altPoints.maxOrNull() ?: 0.0).coerceAtLeast(1.0) else 0.0
        val minAlt  = if (hasAlt) (altPoints.minOrNull() ?: 0.0) else 0.0
        val altRange = (maxAlt - minAlt).coerceAtLeast(1.0)

        // ── Grid lines (4 horizontal) ─────────────────────────
        for (i in 0..4) {
            val y = padT + graphH * (1f - i / 4f)
            canvas.drawLine(padL, y, padL + graphW, y, if (i == 0) gridPaint else gridDashPaint)

            // Speed labels (left)
            val spdVal = minSpd + (maxSpd - minSpd) * (i / 4f)
            canvas.drawText("%.0f".format(spdVal), padL - 6f, y + 7f, labelPaint)

            // Altitude labels (right)
            if (hasAlt) {
                val altVal = minAlt + altRange * (i / 4f)
                canvas.drawText("%.0f".format(altVal), padL + graphW + 6f, y + 7f, labelRightPaint)
            }
        }

        // Vertical grid lines (5)
        for (i in 0..4) {
            val x = padL + graphW * (i / 4f)
            canvas.drawLine(x, padT, x, padT + graphH, gridDashPaint)
        }

        // ── X axis time labels ────────────────────────────────
        val totalSec = if (durationMillis > 0) durationMillis / 1000L else speedPoints.size.toLong()
        for (i in 0..4) {
            val x   = padL + graphW * (i / 4f)
            val sec = totalSec * i / 4
            val label = if (sec < 60) "${sec}s"
            else if (sec < 3600) "${sec / 60}m"
            else "${sec / 3600}h${(sec % 3600) / 60}m"
            canvas.drawText(label, x, h - 4f, xLabelPaint)
        }

        fun xOf(index: Int) = padL + (index.toFloat() / (speedPoints.size - 1).coerceAtLeast(1)) * graphW
        fun yOfSpeed(v: Float) = padT + graphH * (1f - (v - minSpd) / (maxSpd - minSpd).coerceAtLeast(0.001f))
        fun yOfAlt(v: Double)  = padT + graphH * (1f - ((v - minAlt) / altRange).toFloat())

        // ── Altitude fill + line ──────────────────────────────
        if (hasAlt && altPoints.size >= 2) {
            val altPath  = Path()
            val altFill  = Path()

            altFill.moveTo(xOf(0), padT + graphH)
            altFill.lineTo(xOf(0), yOfAlt(altPoints[0]).toFloat())

            altPath.moveTo(xOf(0), yOfAlt(altPoints[0]).toFloat())

            for (i in 1 until altPoints.size) {
                val x  = xOf(i)
                val y  = yOfAlt(altPoints[i]).toFloat()
                // Smooth curve using cubic bezier
                val prevX = xOf(i - 1)
                val prevY = yOfAlt(altPoints[i - 1]).toFloat()
                val cpX   = (prevX + x) / 2f
                altPath.cubicTo(cpX, prevY, cpX, y, x, y)
                altFill.lineTo(x, y)
            }

            altFill.lineTo(xOf(altPoints.size - 1), padT + graphH)
            altFill.close()

            val altShader = LinearGradient(
                0f, padT, 0f, padT + graphH,
                Color.parseColor("#33FF8C00"),
                Color.parseColor("#00FF8C00"),
                Shader.TileMode.CLAMP
            )
            altFillPaint.shader = altShader
            canvas.drawPath(altFill, altFillPaint)
            canvas.drawPath(altPath, altLinePaint)
        }

        // ── Speed fill + line ─────────────────────────────────
        if (speedConverted.size >= 2) {
            val speedPath = Path()
            val speedFill = Path()

            speedFill.moveTo(xOf(0), padT + graphH)
            speedFill.lineTo(xOf(0), yOfSpeed(speedConverted[0]))

            speedPath.moveTo(xOf(0), yOfSpeed(speedConverted[0]))

            for (i in 1 until speedConverted.size) {
                val x     = xOf(i)
                val y     = yOfSpeed(speedConverted[i])
                val prevX = xOf(i - 1)
                val prevY = yOfSpeed(speedConverted[i - 1])
                val cpX   = (prevX + x) / 2f
                speedPath.cubicTo(cpX, prevY, cpX, y, x, y)
                speedFill.lineTo(x, y)
            }

            speedFill.lineTo(xOf(speedConverted.size - 1), padT + graphH)
            speedFill.close()

            val speedShader = LinearGradient(
                0f, padT, 0f, padT + graphH,
                Color.parseColor("#5500E5FF"),
                Color.parseColor("#0000E5FF"),
                Shader.TileMode.CLAMP
            )
            speedFillPaint.shader = speedShader
            canvas.drawPath(speedFill, speedFillPaint)
            canvas.drawPath(speedPath, speedLinePaint)
        }

        // ── Legend ────────────────────────────────────────────
        canvas.drawText("─ Speed", padL + 8f, padT - 16f, legendSpeedPaint)
        if (hasAlt) {
            canvas.drawText("- - Altitude", padL + graphW / 2f, padT - 16f, legendAltPaint)
        }

        // ── Y axis titles ─────────────────────────────────────
        val spdUnit = if (convFactor > 3f) "km/h" else "mph"
        labelPaint.textSize  = 18f
        labelPaint.textAlign = Paint.Align.CENTER
        // Rotate canvas to draw vertical label on left
        canvas.save()
        canvas.rotate(-90f, padL - 38f, padT + graphH / 2)
        canvas.drawText(spdUnit, padL - 38f, padT + graphH / 2, labelPaint)
        canvas.restore()

        if (hasAlt) {
            labelRightPaint.textSize  = 18f
            canvas.save()
            canvas.rotate(90f, w - padR + 38f, padT + graphH / 2)
            canvas.drawText("m asl", w - padR + 38f, padT + graphH / 2, labelRightPaint)
            canvas.restore()
        }
    }
}