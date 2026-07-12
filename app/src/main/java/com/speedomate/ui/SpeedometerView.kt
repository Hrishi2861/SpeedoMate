package com.speedomate.ui

import android.animation.*
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.cos
import kotlin.math.sin
import android.view.animation.OvershootInterpolator
import com.speedomate.R

class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var displayedSpeed = 0f   // what's actually drawn (animated)
    private var targetSpeed = 0f      // what we're animating toward
    private var displayedNumber = 0f  // for digit roll animation
    private var maxDisplaySpeed = 180f
    private var glowAlpha = 0.4f
    private var dangerFlicker = 1f
    private var startupDone = false
    private var displayedBearing = 0f

    var unit = "km/h"
        set(value) { field = value; invalidate() }

    var speedLimitExceeded = false
        set(value) { field = value; invalidate() }

    var speedLimitThreshold = 0f
        set(value) { field = value.coerceAtMost(maxDisplaySpeed); invalidate() }

    var accentColor = Color.parseColor("#00E5FF")
        set(value) {
            field = value
            arcFillPaint.color = value
            needlePaint.color = value
            needleGlowPaint.color = (value and 0x00FFFFFF) or (0x88000000.toInt())
            centerDotPaint.color = value
            unitTextPaint.color = value
            compassArrowPaint.color = value
            invalidate()
        }

    // Animators
    private var needleAnimator: ValueAnimator? = null
    private var numberAnimator: ValueAnimator? = null
    private var glowAnimator: ObjectAnimator? = null
    private var startupAnimator: ValueAnimator? = null
    private var dangerAnimator: ValueAnimator? = null

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#111111")
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
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
        color = Color.parseColor("#00E5FF")
    }
    private val arcGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#333333")
        strokeCap = Paint.Cap.ROUND
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#777777")
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
        color = Color.parseColor("#8800E5FF")
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
        typeface = ResourcesCompat.getFont(context, R.font.dseg7_classic_bold)
            ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val ghostTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        alpha = 30
        typeface = ResourcesCompat.getFont(context, R.font.dseg7_classic_bold)
            ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
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

    private val compassArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E5FF")
    }
    private val compassTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val compassNPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val compassCardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    private val startAngle = 135f
    private val sweepAngle = 270f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!startupDone) {
            startupDone = true
            runStartupAnimation()
        }
        startGlowPulse()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        needleAnimator?.cancel()
        numberAnimator?.cancel()
        glowAnimator?.cancel()
        startupAnimator?.cancel()
        dangerAnimator?.cancel()
    }
    // ── Startup sweep animation ──────────────────────────────────────
    private fun runStartupAnimation() {
        startupAnimator = ValueAnimator.ofFloat(0f, maxDisplaySpeed, 0f).apply {
            duration = 1800
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                displayedSpeed = it.animatedValue as Float
                displayedNumber = displayedSpeed
                invalidate()
            }
            start()
        }
    }

    // ── Continuous glow pulse ────────────────────────────────────────
    private fun startGlowPulse() {
        glowAnimator = ObjectAnimator.ofFloat(this, "glowAlpha", 0.25f, 0.65f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    // Needed for ObjectAnimator
    fun setGlowAlpha(value: Float) {
        glowAlpha = value
        invalidate()
    }
    fun getGlowAlpha() = glowAlpha

    // ── Main speed update with smooth needle + overshoot ────────────
    fun setSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0f, maxDisplaySpeed)
        if (clampedSpeed == targetSpeed) return
        val previousTarget = targetSpeed
        targetSpeed = clampedSpeed

        // Cancel previous animations
        needleAnimator?.cancel()
        numberAnimator?.cancel()

        val isInDangerZone = speedLimitThreshold > 0f && clampedSpeed > speedLimitThreshold
        if (isInDangerZone) startDangerFlicker() else stopDangerFlicker()

        // Needle with overshoot interpolator
        needleAnimator = ValueAnimator.ofFloat(displayedSpeed, clampedSpeed).apply {
            duration = when {
                Math.abs(clampedSpeed - previousTarget) > 30 -> 400L
                else -> 250L
            }
            // Overshoot gives real mechanical feel
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                displayedSpeed = (it.animatedValue as Float).coerceIn(0f, maxDisplaySpeed)
                invalidate()
            }
            start()
        }

        // Number rolls smoothly but without overshoot (no negative digits!)
        numberAnimator = ValueAnimator.ofFloat(displayedNumber, clampedSpeed).apply {
            duration = 300L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                displayedNumber = (it.animatedValue as Float).coerceIn(0f, maxDisplaySpeed)
                invalidate()
            }
            start()
        }
    }

    fun setMaxDisplaySpeed(max: Float) {
        maxDisplaySpeed = max
        invalidate()
    }

    // ── Danger zone flicker ──────────────────────────────────────────
    private fun startDangerFlicker() {
        if (dangerAnimator?.isRunning == true) return
        dangerAnimator = ValueAnimator.ofFloat(1f, 0.3f).apply {
            duration = 400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                dangerFlicker = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopDangerFlicker() {
        dangerAnimator?.cancel()
        dangerFlicker = 1f
    }

    fun setHeading(bearing: Float) {
        if (displayedBearing == bearing) return
        displayedBearing = bearing
        invalidate()
    }

    private fun bearingToCardinal(bearing: Float): String {
        if (!bearing.isFinite()) return ""
        val b = ((bearing % 360f) + 360f) % 360f
        return when (b) {
            in 337.5f..360f, in 0f..22.5f -> "N"
            in 22.5f..67.5f -> "NE"
            in 67.5f..112.5f -> "E"
            in 112.5f..157.5f -> "SE"
            in 157.5f..202.5f -> "S"
            in 202.5f..247.5f -> "SW"
            in 247.5f..292.5f -> "W"
            in 292.5f..337.5f -> "NW"
            else -> ""
        }
    }

    private fun buildGhostString(value: String): String {
        return buildString(value.length) {
            for (c in value) {
                append(if (c.isDigit()) '8' else c)
            }
        }
    }

    // ── Draw ─────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) / 2f * 0.88f

        // Background
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Outer ring
        outerRingPaint.strokeWidth = radius * 0.04f
        canvas.drawCircle(cx, cy, radius, outerRingPaint)

        val arcStroke = radius * 0.06f
        val arcRadius = radius * 0.82f
        val arcRect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)

        // Background arc
        arcBgPaint.strokeWidth = arcStroke
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, arcBgPaint)

        // Danger zone arc with flicker (only when speed limit is set)
        if (speedLimitThreshold > 0f) {
            val dangerFraction = speedLimitThreshold / maxDisplaySpeed
            val dangerStart = startAngle + sweepAngle * dangerFraction
            val dangerSweep = sweepAngle * (1f - dangerFraction)
            dangerArcPaint.strokeWidth = arcStroke
            dangerArcPaint.alpha = (255 * dangerFlicker).toInt()
            canvas.drawArc(arcRect, dangerStart, dangerSweep, false, dangerArcPaint)
            dangerArcPaint.alpha = 255
        }

        // Speed fill arc
        val speedFraction = displayedSpeed / maxDisplaySpeed
        val fillSweep = (sweepAngle * speedFraction).coerceAtLeast(0f)
        val isInDangerZone = speedLimitThreshold > 0f && displayedSpeed > speedLimitThreshold

        if (fillSweep > 0f) {
            val isRed = speedLimitExceeded || isInDangerZone

            // Glow layer
            arcGlowPaint.strokeWidth = arcStroke * 2.5f
            arcGlowPaint.color = if (isRed) {
                Color.argb((255 * glowAlpha * 0.6f).toInt(), 255, 61, 61)
            } else {
                Color.argb(
                    (255 * glowAlpha * 0.6f).toInt(),
                    (accentColor shr 16) and 0xFF,
                    (accentColor shr 8) and 0xFF,
                    accentColor and 0xFF
                )
            }
            arcGlowPaint.maskFilter = BlurMaskFilter(arcStroke * 1.5f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(arcRect, startAngle, fillSweep, false, arcGlowPaint)

            // Main arc
            arcFillPaint.strokeWidth = arcStroke
            arcFillPaint.maskFilter = null
            arcFillPaint.color = if (isRed) Color.parseColor("#FF3D3D") else accentColor
            canvas.drawArc(arcRect, startAngle, fillSweep, false, arcFillPaint)
        }

        // Tick marks
        for (i in 0..36) {
            val fraction = i.toFloat() / 36f
            val angle = Math.toRadians((startAngle + sweepAngle * fraction).toDouble())
            val isMajor = i % 6 == 0
            val outerR = if (isMajor) radius * 0.76f else radius * 0.78f
            val innerR  = if (isMajor) radius * 0.65f else radius * 0.72f
            val paint = if (isMajor) majorTickPaint else tickPaint
            paint.strokeWidth = if (isMajor) radius * 0.025f else radius * 0.012f

            canvas.drawLine(
                cx + outerR * cos(angle).toFloat(), cy + outerR * sin(angle).toFloat(),
                cx + innerR * cos(angle).toFloat(), cy + innerR * sin(angle).toFloat(),
                paint
            )

            if (isMajor) {
                val labelR = radius * 0.56f
                val labelSpeed = (maxDisplaySpeed * fraction).toInt()
                textPaint.textSize = radius * 0.1f
                canvas.drawText(
                    labelSpeed.toString(),
                    cx + labelR * cos(angle).toFloat(),
                    cy + labelR * sin(angle).toFloat() + textPaint.textSize / 3,
                    textPaint
                )
            }
        }

        // Needle glow
        val needleAngle = Math.toRadians((startAngle + sweepAngle * speedFraction).toDouble())
        val needleLen  = radius * 0.68f
        val tailLen    = radius * 0.15f
        val nx = cos(needleAngle).toFloat()
        val ny = sin(needleAngle).toFloat()

        val needleColor = if (speedLimitExceeded) Color.parseColor("#FF3D3D") else accentColor

        needleGlowPaint.strokeWidth = radius * 0.05f
        needleGlowPaint.alpha = (255 * glowAlpha).toInt()
        needleGlowPaint.color = needleColor
        canvas.drawLine(cx - tailLen * nx, cy - tailLen * ny,
            cx + needleLen * nx, cy + needleLen * ny, needleGlowPaint)

        // Needle
        needlePaint.strokeWidth = radius * 0.022f
        needlePaint.color = needleColor
        canvas.drawLine(cx - tailLen * nx, cy - tailLen * ny,
            cx + needleLen * nx, cy + needleLen * ny, needlePaint)

        // Center dots
        centerDotPaint.color = needleColor
        canvas.drawCircle(cx, cy, radius * 0.09f, centerDotPaint)
        canvas.drawCircle(cx, cy, radius * 0.05f, centerDotInnerPaint)

        // Digital speed (rolling number)
        speedTextPaint.textSize = radius * 0.32f
        ghostTextPaint.textSize = speedTextPaint.textSize
        val speedStr = displayedNumber.toInt().toString()
        val ghostStr = buildGhostString(speedStr)
        val textY = cy + radius * 0.65f
        canvas.drawText(ghostStr, cx, textY, ghostTextPaint)
        canvas.drawText(speedStr, cx, textY, speedTextPaint)

        // Unit
        unitTextPaint.textSize = radius * 0.11f
        canvas.drawText(unit, cx, cy + radius * 0.80f, unitTextPaint)

        // ── Compass arrow at top center (fixed pointing UP) ──────────
        if (displayedBearing > 0f) {
            val compassY = cy - radius * 0.88f
            val arrowSize = radius * 0.05f
            val degreeLabel = "${bearingToCardinal(displayedBearing)} ${displayedBearing.toInt()}°"

            compassTextPaint.textSize = radius * 0.085f
            canvas.drawText(degreeLabel, cx, compassY - arrowSize * 3.5f, compassTextPaint)

            val arrowPath = Path()
            arrowPath.moveTo(cx, compassY - arrowSize * 2.5f)
            arrowPath.lineTo(cx - arrowSize, compassY)
            arrowPath.lineTo(cx + arrowSize, compassY)
            arrowPath.close()
            canvas.drawPath(arrowPath, compassArrowPaint)
        }
    }
}