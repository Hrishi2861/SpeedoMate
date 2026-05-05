package com.speedomate.ui

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.speedomate.R
import com.speedomate.data.TripEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TripHistoryActivity : AppCompatActivity() {

    private val vm: SpeedViewModel by viewModels()
    private lateinit var adapter: TripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0A0A0A"))
            setPadding(0, 80, 0, 0)
        }

        val title = TextView(this).apply {
            text = "Trip History"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 16, 32, 16)
        }

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@TripHistoryActivity)
        }

        layout.addView(title)
        layout.addView(recycler)
        setContentView(layout)

        adapter = TripAdapter(
            onDelete = { trip ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Trip?")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> vm.deleteTrip(trip) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onShare = { trip -> shareTrip(trip) },
            isMetric = vm.isMetric.value
        )
        recycler.adapter = adapter

        lifecycleScope.launch {
            vm.allTrips.collectLatest { trips ->
                adapter.submitList(trips)
                if (trips.isEmpty()) {
                    title.text = "Trip History (No trips yet)"
                } else {
                    title.text = "Trip History (${trips.size} trips)"
                }
            }
        }
    }

    private fun shareTrip(trip: TripEntity) {
        val isMetric = vm.isMetric.value
        val text = buildTripText(trip, isMetric)
        val bitmap = buildTripImage(trip, isMetric)
        val uri = saveBitmapToCache(bitmap)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Trip"))
    }

    private fun buildTripText(trip: TripEntity, isMetric: Boolean): String {
        val sdf = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
        val startStr = sdf.format(Date(trip.startTime))
        val endStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.endTime))
        val dur = (trip.endTime - trip.startTime) / 60000
        val dist = if (isMetric) "%.2f km".format(trip.distanceKm)
        else "%.2f mi".format(trip.distanceKm * 0.621371)
        val maxSpd = if (isMetric) "%.0f km/h".format(trip.maxSpeedMs * 3.6f)
        else "%.0f mph".format(trip.maxSpeedMs * 2.237f)
        val avgSpd = if (isMetric) "%.0f km/h".format(trip.avgSpeedMs * 3.6f)
        else "%.0f mph".format(trip.avgSpeedMs * 2.237f)
        val altInfo = if (trip.maxAltitude > 0) "\n↑ Altitude: ${trip.minAltitude.toInt()}–${trip.maxAltitude.toInt()}m asl" else ""

        return """SpeedoMate Trip
$startStr → $endStr  (${dur}m)
Distance: $dist
Max Speed: $maxSpd
Avg Speed: $avgSpd$altInfo

Shared via SpeedoMate — https://github.com/Hrishi2861/SpeedoMate"""
    }

    private fun buildTripImage(trip: TripEntity, isMetric: Boolean): Bitmap {
        val width = 1080
        val padding = 48f
        val contentWidth = width - padding * 2

        // Paints
        val bgPaint = Paint().apply { color = Color.parseColor("#0A0A0A") }
        val accentPaint = Paint().apply { color = Color.parseColor("#00E5FF") }
        val dividerPaint = Paint().apply { color = Color.parseColor("#333333"); strokeWidth = 2f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 56f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888"); textSize = 32f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666"); textSize = 28f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 36f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444"); textSize = 26f; textAlign = Paint.Align.CENTER
        }

        // Calculate text heights using metrics
        val labelH = labelPaint.fontMetrics.let { (it.descent - it.ascent) }
        val valueH = valuePaint.fontMetrics.let { (it.descent - it.ascent) }
        val titleH = titlePaint.fontMetrics.let { (it.descent - it.ascent) }
        val subtitleH = subtitlePaint.fontMetrics.let { (it.descent - it.ascent) }

        val gap = 12f
        val sectionGap = 32f
        val rowPairH = labelH + gap + valueH

        // Stats layout: 2 rows + optional Altitude row
        val statRowCount = 2 + if (trip.maxAltitude > 0) 1 else 0
        val statsTotalH = statRowCount * rowPairH + (statRowCount - 1) * sectionGap

        // Header area: title + gap + subtitle + gap + stats
        val headerAreaH = titleH + gap + subtitleH + gap + statsTotalH

        // Graph area
        val graphHeight = 500f

        // Footer area
        val footerH = footerPaint.fontMetrics.let { (it.descent - it.ascent) }
        val footerAreaH = footerH + 2 * padding

        // Total height
        val totalHeight = (padding + 8 + padding + headerAreaH + sectionGap + dividerPaint.strokeWidth + sectionGap + graphHeight + padding + footerAreaH + padding).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Top accent bar
        canvas.drawRect(0f, 0f, width.toFloat(), 8f, accentPaint)

        // Calculate vertical positions
        var y = padding + 8 + padding
        canvas.drawText("SpeedoMate", padding, y + titlePaint.fontMetrics.top * -0.3f, titlePaint)
        y += titleH + gap

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        canvas.drawText(sdf.format(Date(trip.startTime)), padding, y + subtitlePaint.fontMetrics.top * -0.3f, subtitlePaint)
        y += subtitleH + gap

        // Stats grid
        val col1X = padding
        val col2X = width / 2f

        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startStr = sdfTime.format(Date(trip.startTime))
        val endStr = sdfTime.format(Date(trip.endTime))
        val dur = (trip.endTime - trip.startTime) / 60000
        val dist = if (isMetric) "%.2f km".format(trip.distanceKm) else "%.2f mi".format(trip.distanceKm * 0.621371)
        val maxSpd = if (isMetric) "%.0f km/h".format(trip.maxSpeedMs * 3.6f) else "%.0f mph".format(trip.maxSpeedMs * 2.237f)
        val avgSpd = if (isMetric) "%.0f km/h".format(trip.avgSpeedMs * 3.6f) else "%.0f mph".format(trip.avgSpeedMs * 2.237f)

        val statRows = listOf(
            listOf(
                Triple("Time", "$startStr → $endStr  (${dur}m)", col1X),
                Triple("Distance", dist, col2X)
            ),
            listOf(
                Triple("Max Speed", maxSpd, col1X),
                Triple("Avg Speed", avgSpd, col2X)
            )
        )

        for (row in statRows) {
            val labelY = y + labelPaint.fontMetrics.top * -0.3f
            val valueY = labelY + labelH + gap + (valuePaint.fontMetrics.top - labelPaint.fontMetrics.top)
            for (item in row) {
                canvas.drawText(item.first, item.third, labelY, labelPaint)
                canvas.drawText(item.second, item.third, valueY, valuePaint)
            }
            y += rowPairH + sectionGap
        }

        if (trip.maxAltitude > 0) {
            val labelY = y + labelPaint.fontMetrics.top * -0.3f
            val valueY = labelY + labelH + gap + (valuePaint.fontMetrics.top - labelPaint.fontMetrics.top)
            canvas.drawText("Altitude", col1X, labelY, labelPaint)
            canvas.drawText("${trip.minAltitude.toInt()}–${trip.maxAltitude.toInt()}m", col1X, valueY, valuePaint)
            y += rowPairH
        }
        y += sectionGap

        // Divider
        val dividerY = y
        canvas.drawLine(padding, dividerY, width - padding, dividerY, dividerPaint)
        y += dividerPaint.strokeWidth + sectionGap

        // Parse graph data
        val speeds = mutableListOf<Float>()
        val alts = mutableListOf<Double>()
        try {
            val arr = JSONArray(trip.speedPoints)
            for (i in 0 until arr.length()) speeds.add(arr.getDouble(i).toFloat())
        } catch (_: Exception) {}
        try {
            val arr = JSONArray(trip.altitudePoints)
            for (i in 0 until arr.length()) alts.add(arr.getDouble(i))
        } catch (_: Exception) {}

        // Create and draw SpeedGraphView
        val graphView = SpeedGraphView(this).apply {
            val spec = View.MeasureSpec.makeMeasureSpec(contentWidth.toInt(), View.MeasureSpec.EXACTLY)
            val graphSpec = View.MeasureSpec.makeMeasureSpec(graphHeight.toInt(), View.MeasureSpec.EXACTLY)
            measure(spec, graphSpec)
            layout(0, 0, contentWidth.toInt(), graphHeight.toInt())
            setData(speeds, alts, if (isMetric) 3.6f else 2.237f, trip.endTime - trip.startTime)
        }

        canvas.save()
        canvas.translate(padding, y)
        graphView.draw(canvas)
        canvas.restore()

        // Footer
        val footerY = totalHeight - padding
        canvas.drawText("Shared via SpeedoMate", width / 2f, footerY, footerPaint)

        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val file = File(cacheDir, "trip_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(this, "com.speedomate.fileprovider", file)
    }
}

class TripAdapter(
    private val onDelete: (TripEntity) -> Unit,
    private val onShare: (TripEntity) -> Unit,
    private val isMetric: Boolean
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    private var trips = listOf<TripEntity>()

    fun submitList(list: List<TripEntity>) {
        trips = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val card = androidx.cardview.widget.CardView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(24, 12, 24, 12) }
            radius = 16f
            setCardBackgroundColor(android.graphics.Color.parseColor("#111111"))
            cardElevation = 0f
        }
        return TripViewHolder(card, onDelete, onShare, isMetric)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount() = trips.size

    class TripViewHolder(
        private val card: androidx.cardview.widget.CardView,
        private val onDelete: (TripEntity) -> Unit,
        private val onShare: (TripEntity) -> Unit,
        private val isMetric: Boolean
    ) : RecyclerView.ViewHolder(card) {

        fun bind(trip: TripEntity) {
            card.removeAllViews()
            val sdf = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
            val startStr = sdf.format(Date(trip.startTime))
            val endStr   = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.endTime))
            val dur = (trip.endTime - trip.startTime) / 60000
            val dist = if (isMetric) "%.2f km".format(trip.distanceKm)
            else "%.2f mi".format(trip.distanceKm * 0.621371)
            val maxSpd = if (isMetric) "%.0f km/h".format(trip.maxSpeedMs * 3.6f)
            else "%.0f mph".format(trip.maxSpeedMs * 2.237f)
            val avgSpd = if (isMetric) "%.0f km/h".format(trip.avgSpeedMs * 3.6f)
            else "%.0f mph".format(trip.avgSpeedMs * 2.237f)

            val container = LinearLayout(card.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 16)
            }

            // Date + share + delete row
            val headerRow = LinearLayout(card.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val calendarIcon = androidx.appcompat.widget.AppCompatImageView(card.context).apply {
                setImageResource(R.drawable.ic_calendar)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 6, 0) }
            }
            val dateText = TextView(card.context).apply {
                text = "$startStr → $endStr  (${dur}m)"
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#00E5FF"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val shareBtn = androidx.appcompat.widget.AppCompatImageButton(card.context).apply {
                setImageResource(R.drawable.ic_share)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    64, 64
                )
                setOnClickListener { onShare(trip) }
            }
            val deleteBtn = androidx.appcompat.widget.AppCompatImageButton(card.context).apply {
                setImageResource(R.drawable.ic_delete)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    64, 64
                )
                setOnClickListener { onDelete(trip) }
            }
            headerRow.addView(calendarIcon)
            headerRow.addView(dateText)
            headerRow.addView(shareBtn)
            headerRow.addView(deleteBtn)

            // Stats row with icons
            val statsRow = LinearLayout(card.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 0)
            }

            fun makeStatIcon(iconRes: Int, text: String) = TextView(card.context).apply {
                val icon = androidx.core.content.ContextCompat.getDrawable(card.context, iconRes)
                val iconSize = (20 * card.context.resources.displayMetrics.density).toInt()
                icon?.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = 6
                gravity = android.view.Gravity.CENTER_VERTICAL
                this.text = text
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val altRange = if (trip.maxAltitude > 0)
                " ${trip.minAltitude.toInt()}–${trip.maxAltitude.toInt()}m"
            else ""

            val distIcon = makeStatIcon(R.drawable.ic_pin, dist)
            val maxIcon = makeStatIcon(R.drawable.ic_car, "Max: $maxSpd")
            val avgIcon = makeStatIcon(R.drawable.ic_average, "Avg: $avgSpd")

            listOf(distIcon, maxIcon, avgIcon).forEach { view ->
                view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                statsRow.addView(view)
            }

            if (trip.maxAltitude > 0) {
                val altIcon = makeStatIcon(R.drawable.ic_altitude, altRange.trim())
                altIcon.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                statsRow.addView(altIcon)
            }

            // Speed graph
            val graphView = SpeedGraphView(card.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 200
                )
                val speeds = mutableListOf<Float>()
                val alts   = mutableListOf<Double>()
                try {
                    val arr = JSONArray(trip.speedPoints)
                    for (i in 0 until arr.length()) speeds.add(arr.getDouble(i).toFloat())
                } catch (_: Exception) {}
                try {
                    val arr = JSONArray(trip.altitudePoints)
                    for (i in 0 until arr.length()) alts.add(arr.getDouble(i))
                } catch (_: Exception) {}

                val duration = trip.endTime - trip.startTime
                setData(speeds, alts, if (isMetric) 3.6f else 2.237f, duration)
            }

            container.addView(headerRow)
            container.addView(statsRow)
            container.addView(graphView)
            card.addView(container)
        }
    }
}
