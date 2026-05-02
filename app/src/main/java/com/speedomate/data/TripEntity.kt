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
    val speedPoints: String  // JSON array of speed readings over time
)