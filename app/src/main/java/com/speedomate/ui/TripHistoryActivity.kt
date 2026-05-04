package com.speedomate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.speedomate.R
import com.speedomate.data.TripEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
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
}

class TripAdapter(
    private val onDelete: (TripEntity) -> Unit,
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
        return TripViewHolder(card, onDelete, isMetric)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount() = trips.size

    class TripViewHolder(
        private val card: androidx.cardview.widget.CardView,
        private val onDelete: (TripEntity) -> Unit,
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

            // Date + delete row
            val headerRow = LinearLayout(card.context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val dateText = TextView(card.context).apply {
                text = "🗓 $startStr → $endStr  (${dur}m)"
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#00E5FF"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val deleteBtn = Button(card.context).apply {
                text = "🗑"
                textSize = 14f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(android.graphics.Color.parseColor("#FF4444"))
                setOnClickListener { onDelete(trip) }
            }
            headerRow.addView(dateText)
            headerRow.addView(deleteBtn)

            // Stats & ALt row
            val altRange = if (trip.maxAltitude > 0)
                "  ↑ ${trip.minAltitude.toInt()}–${trip.maxAltitude.toInt()}m"
            else ""

            val statsText = TextView(card.context).apply {
                text = "📍 $dist   🏎 Max: $maxSpd   ⌀ Avg: $avgSpd$altRange"
                // ... rest same
            }

            // Speed graph
            // Replace the graphView block in bind() with:
            val graphView = SpeedGraphView(card.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 200
                )
                val speeds = mutableListOf<Float>()
                val alts   = mutableListOf<Double>()
                try {
                    val arr = JSONArray(trip.speedPoints)
                    for (i in 0 until arr.length()) speeds.add(arr.getDouble(i).toFloat())
                } catch (e: Exception) {}
                try {
                    val arr = JSONArray(trip.altitudePoints)
                    for (i in 0 until arr.length()) alts.add(arr.getDouble(i))
                } catch (e: Exception) {}

                val duration = trip.endTime - trip.startTime
                setData(speeds, alts, if (isMetric) 3.6f else 2.237f, duration)
            }

            container.addView(headerRow)
            container.addView(statsText)
            container.addView(graphView)
            card.addView(container)
        }
    }
}