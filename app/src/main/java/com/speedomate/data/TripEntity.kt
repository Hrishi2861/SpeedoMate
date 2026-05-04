package com.speedomate.data

import androidx.room.*

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Double,
    val maxSpeedMs: Float,
    val avgSpeedMs: Float,
    val minAltitude: Double = 0.0,
    val maxAltitude: Double = 0.0,
    val speedPoints: String,    // JSON array
    val altitudePoints: String  // JSON array
)