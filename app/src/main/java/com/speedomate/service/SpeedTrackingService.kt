package com.speedomate.service

import android.app.*
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.speedomate.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpeedTrackingService : LifecycleService() {

    companion object {
        private val _speedMs = MutableStateFlow(0f)
        val speedMs: StateFlow<Float> = _speedMs

        private val _maxSpeed = MutableStateFlow(0f)
        val maxSpeed: StateFlow<Float> = _maxSpeed

        private val _avgSpeed = MutableStateFlow(0f)
        val avgSpeed: StateFlow<Float> = _avgSpeed

        private val _tripDistance = MutableStateFlow(0.0)
        val tripDistance: StateFlow<Double> = _tripDistance

        var speedSum = 0f
        var speedCount = 0f
        var lastLocation: android.location.Location? = null

        fun resetTrip(prefs: PrefsManager, scope: CoroutineScope) {
            _maxSpeed.value    = 0f
            _avgSpeed.value    = 0f
            _tripDistance.value = 0.0
            speedSum           = 0f
            speedCount         = 0f
            lastLocation       = null
            scope.launch { prefs.resetTripData() }
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        startForeground(1, buildNotification())

        // Restore saved trip data before starting GPS
        lifecycleScope.launch {
            _tripDistance.value = prefs.savedTripDistance.first()
            _maxSpeed.value     = prefs.savedMaxSpeed.first()
            speedSum            = prefs.savedSpeedSum.first()
            speedCount          = prefs.savedSpeedCount.first()
            if (speedCount > 0f) {
                _avgSpeed.value = speedSum / speedCount
            }
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 500L
        )
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(500L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val speed = if (location.hasSpeed()) location.speed else 0f
                _speedMs.value = speed

                if (speed > _maxSpeed.value) _maxSpeed.value = speed

                lastLocation?.let { prev ->
                    val dist = prev.distanceTo(location) / 1000.0
                    _tripDistance.value += dist
                }
                lastLocation = location

                speedSum += speed
                speedCount++
                _avgSpeed.value = speedSum / speedCount

                // Persist to DataStore on every update
                lifecycleScope.launch {
                    prefs.saveTripData(
                        _tripDistance.value,
                        _maxSpeed.value,
                        speedSum,
                        speedCount
                    )
                }
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, mainLooper
        )
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            "speed_channel", "Speed Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return Notification.Builder(this, "speed_channel")
            .setContentTitle("SpeedoMate Active")
            .setContentText("Tracking your speed...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}