package com.speedomate.ui

import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils

object ThemeApplier {
    fun withAlpha(hex: String, alpha: Float): Int {
        val base = Color.parseColor(hex)
        return ColorUtils.setAlphaComponent(base, (255 * alpha).toInt())
    }

    fun withAlphaInt(colorInt: Int, alpha: Float): Int {
        return ColorUtils.setAlphaComponent(colorInt, (255 * alpha).toInt())
    }

    fun applyToSeekBar(seekBar: android.widget.SeekBar, color: Int, progressDrawableRes: Int? = null) {
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ColorUtils.setAlphaComponent(color, 51))
            cornerRadius = 24f
        }
        val progressShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 24f
        }
        val layerList = android.graphics.drawable.LayerDrawable(
            arrayOf(bgShape, progressShape).mapIndexed { i, d ->
                android.graphics.drawable.ClipDrawable(d, android.view.Gravity.START, ClipDrawable.HORIZONTAL).also { clip ->
                    if (i == 0) clip.level = 10000
                }
            }.toTypedArray()
        )
        layerList.setId(0, android.R.id.background)
        layerList.setId(1, android.R.id.progress)
        seekBar.progressDrawable = layerList
        seekBar.thumb?.setTint(color)
    }
}
