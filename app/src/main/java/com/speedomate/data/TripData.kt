// data/TripData.kt
package com.speedomate.data

data class TripData(
    val distanceKm: Double = 0.0,
    val maxSpeedMs: Float = 0f,
    val avgSpeedMs: Float = 0f,
    val durationMillis: Long = 0L,
    val startTimeMillis: Long = System.currentTimeMillis()
) {
    // Convenience converters
    fun maxSpeedKmh() = maxSpeedMs * 3.6f
    fun maxSpeedMph() = maxSpeedMs * 2.237f

    fun avgSpeedKmh() = avgSpeedMs * 3.6f
    fun avgSpeedMph() = avgSpeedMs * 2.237f

    fun distanceMi() = distanceKm * 0.621371

    fun durationFormatted(): String {
        val seconds = (durationMillis / 1000) % 60
        val minutes = (durationMillis / 60000) % 60
        val hours = durationMillis / 3600000
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}