package com.speedomate.ui

import android.app.Application
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speedomate.data.PrefsManager
import com.speedomate.data.TripDatabase
import com.speedomate.data.TripEntity
import com.speedomate.service.SpeedTrackingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SpeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsManager(app)
    val database = TripDatabase.getDatabase(app)

    val isMetric: StateFlow<Boolean> = prefs.isMetric
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val speedLimitThreshold: StateFlow<Int> = prefs.speedLimitThreshold
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val displayedSpeedLimit: Flow<Int> = combine(
        prefs.speedLimitThreshold, isMetric
    ) { storedKm, metric ->
        if (storedKm <= 0) 0
        else if (metric) storedKm
        else (storedKm * 0.621371).roundToInt().coerceAtMost(120)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val accentColor: StateFlow<Int> = prefs.accentColorInt
        .stateIn(viewModelScope, SharingStarted.Eagerly, Color.parseColor("#00E5FF"))

    val accentColorHex: StateFlow<String> = prefs.accentColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, "#00E5FF")

    val currentSpeed: Flow<Float> = combine(
        SpeedTrackingService.speedMs, isMetric
    ) { ms, metric -> if (metric) ms * 3.6f else ms * 2.237f }.conflate()

    val maxSpeed: Flow<Float> = combine(
        SpeedTrackingService.maxSpeed, isMetric
    ) { ms, metric -> if (metric) ms * 3.6f else ms * 2.237f }.conflate()

    val avgSpeed: Flow<Float> = combine(
        SpeedTrackingService.avgSpeed, isMetric
    ) { ms, metric -> if (metric) ms * 3.6f else ms * 2.237f }.conflate()

    val tripDistance: Flow<Double> = combine(
        SpeedTrackingService.tripDistance, isMetric
    ) { km, metric -> if (metric) km else km * 0.621371 }.conflate()

    val altitude: Flow<Double> = SpeedTrackingService.altitude

    val heading: Flow<Pair<Float, String>> = SpeedTrackingService.bearing
        .map { bearing ->
            val cardinal = bearingToCardinal(bearing)
            bearing to cardinal
        }.conflate()

    val speedLimitAlert: StateFlow<Boolean> = SpeedTrackingService.speedLimitAlert

    val allTrips: Flow<List<TripEntity>> = database.tripDao().getAllTrips()

    private fun bearingToCardinal(bearing: Float): String {
        return when (bearing) {
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

    fun startService() {
        val intent = Intent(getApplication(), SpeedTrackingService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    fun saveAndResetTrip(onDone: (() -> Unit)? = null) {
        SpeedTrackingService.saveAndResetTrip(prefs, viewModelScope, database, onDone)
    }

    fun discardAndResetTrip(onDone: (() -> Unit)? = null) {
        SpeedTrackingService.discardAndResetTrip(prefs, viewModelScope, onDone)
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch { database.tripDao().deleteTrip(trip) }
    }

    fun setMetric(value: Boolean) {
        viewModelScope.launch { prefs.setMetric(value) }
    }

    fun setSpeedLimitThreshold(value: Int) {
        viewModelScope.launch { prefs.setSpeedLimitThreshold(value) }
    }

    fun setAccentColor(value: String) {
        viewModelScope.launch { prefs.setAccentColor(value) }
    }
}
