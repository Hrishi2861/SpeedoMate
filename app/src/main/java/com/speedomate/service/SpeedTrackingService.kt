// service/SpeedTrackingService.kt
package com.speedomate.service

import android.app.*
import android.content.Intent
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpeedTrackingService : LifecycleService() {

    companion object {
        // Shared state — both phone UI and Auto screen read from here
        private val _speedMs = MutableStateFlow(0f)
        val speedMs: StateFlow<Float> = _speedMs

        private val _maxSpeed = MutableStateFlow(0f)
        val maxSpeed: StateFlow<Float> = _maxSpeed

        private val _avgSpeed = MutableStateFlow(0f)
        val avgSpeed: StateFlow<Float> = _avgSpeed

        private val _tripDistance = MutableStateFlow(0.0)
        val tripDistance: StateFlow<Double> = _tripDistance

        fun resetTrip() {
            _maxSpeed.value = 0f
            _avgSpeed.value = 0f
            _tripDistance.value = 0.0
            speedReadings.clear()
            lastLocation = null
        }

        private val speedReadings = mutableListOf<Float>()
        var lastLocation: android.location.Location? = null
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(1, buildNotification())
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 500L
        )
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(500L)  // ← add this line
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // Speed from GPS (m/s) — same method Google Maps uses
                val speed = if (location.hasSpeed()) location.speed else 0f
                _speedMs.value = speed

                // Update max speed
                if (speed > _maxSpeed.value) _maxSpeed.value = speed

                // Update trip distance (Haversine via Android Location API)
                lastLocation?.let { prev ->
                    val dist = prev.distanceTo(location) / 1000.0 // km
                    _tripDistance.value += dist
                }
                lastLocation = location

                // Rolling average speed
                speedReadings.add(speed)
                _avgSpeed.value = speedReadings.average().toFloat()
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            "speed_channel", "Speed Tracking", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, "speed_channel")
            .setContentTitle("SpeedoMate Active")
            .setContentText("Tracking your speed...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent) = super.onBind(intent)
}