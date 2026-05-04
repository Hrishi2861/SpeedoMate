package com.speedomate.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speedomate.data.PrefsManager
import com.speedomate.data.TripDatabase
import com.speedomate.data.TripEntity
import com.speedomate.service.SpeedTrackingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SpeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsManager(app)
    val database = TripDatabase.getDatabase(app)

    val isMetric: StateFlow<Boolean> = prefs.isMetric
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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

    // Altitude in meters (always metric for altitude)
    val altitude: Flow<Double> = SpeedTrackingService.altitude

    val allTrips: Flow<List<TripEntity>> = database.tripDao().getAllTrips()

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
}