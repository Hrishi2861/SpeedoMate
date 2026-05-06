// ui/SettingsActivity.kt
package com.speedomate.ui

import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.speedomate.R
import com.speedomate.data.ThemeColors
import com.speedomate.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val vm: SpeedViewModel by viewModels()
    private val swatchSize = 40
    private val swatchSpacing = 4
    private val swatchWrapperMap = mutableMapOf<String, LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            vm.isMetric.collectLatest { isMetric ->
                binding.toggleUnit.isChecked = isMetric
                binding.toggleUnit.text = if (isMetric) "km/h" else "mph"
                val maxLimit = if (isMetric) 180 else 112
                binding.seekSpeedLimit.max = maxLimit
                binding.tvMaxSeekValue.text = maxLimit.toString()
            }
        }

        binding.toggleUnit.setOnCheckedChangeListener { _, isChecked ->
            vm.setMetric(isChecked)
        }

        lifecycleScope.launch {
            vm.displayedSpeedLimit.collectLatest { threshold ->
                binding.seekSpeedLimit.progress = threshold
                binding.tvSpeedLimitValue.text = if (threshold > 0) "$threshold" else "Off"
                val bgRes = if (threshold > 0) R.drawable.limit_badge_bg else R.drawable.limit_badge_bg_off
                binding.tvSpeedLimitValue.background = ContextCompat.getDrawable(this@SettingsActivity, bgRes)
                val textColor = if (threshold > 0) "#0D0D0D" else "#888888"
                binding.tvSpeedLimitValue.setTextColor(Color.parseColor(textColor))
            }
        }

        binding.seekSpeedLimit.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val isMetric = vm.isMetric.value
                    val kmValue = if (isMetric) progress else (progress / 0.621371).roundToInt().coerceAtMost(180)
                    vm.setSpeedLimitThreshold(kmValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        setupThemePicker()
        applyAccentColor()
        setupCustomScrollbar()
        
        binding.swatchScrollView.post {
            val scrollAmount = (resources.displayMetrics.density * 100).toInt()
            binding.swatchScrollView.smoothScrollBy(scrollAmount, 0)
        }
    }
    
    private fun setupCustomScrollbar() {
        val scrollView = binding.swatchScrollView
        val scrollbar = binding.customScrollbar
        
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val maxScroll = scrollView.getChildAt(0).width - scrollView.width
            if (maxScroll > 0) {
                val scrollRatio = scrollView.scrollX.toFloat() / maxScroll
                val thumbWidth = (scrollView.width.toFloat() / scrollView.getChildAt(0).width.toFloat()) * scrollView.width
                val thumbLeft = (scrollRatio * (scrollView.width - thumbWidth)).toInt()
                val params = scrollbar.layoutParams
                params.width = thumbWidth.toInt()
                scrollbar.layoutParams = params
                scrollbar.translationX = thumbLeft.toFloat()
                scrollbar.setBackgroundColor(Color.parseColor("#888888"))
            } else {
                scrollbar.translationX = 0f
                scrollbar.setBackgroundColor(Color.parseColor("#333333"))
            }
        }
    }

    private fun applyAccentColor() {
        lifecycleScope.launch {
            vm.accentColorHex.collectLatest { hex ->
                val color = Color.parseColor(hex)

                binding.ivSettingsIcon.setColorFilter(color)
                binding.topAccentBar.setBackgroundColor(color)

                binding.tvSpeedLimitValue.setTextColor(Color.parseColor("#0D0D0D"))
                val badgeDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    cornerRadius = 12f * resources.displayMetrics.density
                }
                binding.tvSpeedLimitValue.background = badgeDrawable

                binding.toggleUnit.setTextColor(color)
                try {
                    val trackDrawable = binding.toggleUnit.trackDrawable
                    trackDrawable?.setTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 128)))
                } catch (_: Exception) {}

                val seekBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#1E1E1E"))
                    cornerRadius = 24f
                }
                val seekProgress = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    cornerRadius = 24f
                }
                val seekLayer = android.graphics.drawable.LayerDrawable(
                    arrayOf(
                        android.graphics.drawable.ClipDrawable(seekBg, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL).apply { level = 10000 },
                        android.graphics.drawable.ClipDrawable(seekProgress, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
                    )
                )
                seekLayer.setId(0, android.R.id.background)
                seekLayer.setId(1, android.R.id.progress)
                binding.seekSpeedLimit.progressDrawable = seekLayer
                binding.seekSpeedLimit.thumb?.setTint(Color.WHITE)

                val themeDrawable = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.ic_palette)?.mutate()
                themeDrawable?.setTint(color)
            }
        }
    }

    private fun setupThemePicker() {
        val container = binding.swatchContainer
        container.removeAllViews()
        swatchWrapperMap.clear()

        val colorsWithLabels = ThemeColors.PRESET_COLORS.map { it.first to it.second }.toMutableList()

        val monetColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getMonetAccentColor()
        } else null

        monetColor?.let {
            if (!colorsWithLabels.map { it.first }.contains(it)) {
                colorsWithLabels.add(it to "Monet")
            }
        }

        val density = resources.displayMetrics.density
        val sizePx = (swatchSize * density).toInt()
        val spacingPx = (swatchSpacing * density).toInt()

        colorsWithLabels.forEach { (hex, label) ->
            val swatch = createColorSwatch(hex, label, sizePx, spacingPx, monetColor != null)
            swatchWrapperMap[hex] = swatch
            container.addView(swatch)
        }

        lifecycleScope.launch {
            vm.accentColorHex.collectLatest { hex ->
                swatchWrapperMap.forEach { (color, wrapper) ->
                    updateSwatchState(wrapper, color == hex)
                }
            }
        }
    }

    private fun createColorSwatch(hex: String, label: String, sizePx: Int, spacingPx: Int, hasMonet: Boolean): LinearLayout {
        val density = resources.displayMetrics.density
        val wrapperWidth = (sizePx + (60 * density)).toInt()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val params = LinearLayout.LayoutParams(
            wrapperWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(spacingPx / 2, 0, spacingPx / 2, 0)
        }
        wrapper.layoutParams = params
        wrapper.tag = hex

        val circle = View(this)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
        }
        circle.background = drawable
        val circleParams = FrameLayout.LayoutParams(sizePx, sizePx)
        circle.layoutParams = circleParams

        val checkmark = ImageView(this)
        val checkSize = (18 * resources.displayMetrics.density).toInt()
        checkmark.setImageResource(R.drawable.ic_check)
        val checkParams = FrameLayout.LayoutParams(checkSize, checkSize, Gravity.CENTER)
        checkmark.layoutParams = checkParams
        checkmark.visibility = View.GONE

        val circleFrame = FrameLayout(this)
        circleFrame.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        circleFrame.addView(circle)
        circleFrame.addView(checkmark)

        wrapper.addView(circleFrame)

        val labelText = if (label == "Monet") "Monet\n(Android 12+)" else label
        val labelView = TextView(this).apply {
            text = labelText
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        wrapper.addView(labelView)

        wrapper.setOnClickListener {
            vm.setAccentColor(hex)
        }

        return wrapper
    }

    private fun updateSwatchState(swatch: LinearLayout, isSelected: Boolean) {
        val circleFrame = swatch.getChildAt(0) as FrameLayout
        val circle = circleFrame.getChildAt(0)
        val checkmark = circleFrame.getChildAt(1) as ImageView
        val drawable = circle.background as GradientDrawable
        val strokeWidth = (2 * resources.displayMetrics.density).toInt()

        if (isSelected) {
            drawable.setStroke(strokeWidth, Color.WHITE)
            checkmark.visibility = View.VISIBLE
            checkmark.setColorFilter(Color.WHITE)
        } else {
            drawable.setStroke(0, Color.TRANSPARENT)
            checkmark.visibility = View.GONE
        }
    }

    private fun getMonetAccentColor(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val wm = getSystemService(WALLPAPER_SERVICE) as WallpaperManager
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val primary = colors?.primaryColor ?: return null
            val c = primary.toArgb()
            "#%06X".format(0xFFFFFF and c)
        } catch (_: Throwable) {
            null
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_down)
    }
}
