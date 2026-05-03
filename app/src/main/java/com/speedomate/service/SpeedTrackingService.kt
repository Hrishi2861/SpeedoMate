package com.speedomate.service

import android.app.*
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.speedomate.data.PrefsManager
import com.speedomate.data.TripDatabase
import com.speedomate.data.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

class SpeedTrackingService : LifecycleService() {

    companion object {
        const val ACTION_SPEED_UPDATE = "com.speedomate.SPEED_UPDATE"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_MAX = "max"
        const val EXTRA_AVG = "avg"
        const val EXTRA_TRIP = "trip"
        const val EXTRA_UNIT = "unit"
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
        var tripStartTime = System.currentTimeMillis()

        // Speed history for graph
        val speedHistory = mutableListOf<Float>()

        // Save & Reset — saves trip to DB then resets
        fun saveAndResetTrip(
            prefs: PrefsManager,
            scope: CoroutineScope,
            database: TripDatabase,
            onDone: (() -> Unit)? = null
        ) {
            scope.launch {
                // Save trip to Room DB
                if (speedCount > 0f || _tripDistance.value > 0.0) {
                    val speedJson = JSONArray().apply {
                        speedHistory.forEach { put(it) }
                    }.toString()

                    database.tripDao().insertTrip(
                        TripEntity(
                            startTime = tripStartTime,
                            endTime = System.currentTimeMillis(),
                            distanceKm = _tripDistance.value,
                            maxSpeedMs = _maxSpeed.value,
                            avgSpeedMs = if (speedCount > 0) speedSum / speedCount else 0f,
                            speedPoints = speedJson
                        )
                    )
                }
                doReset(prefs, scope)
                onDone?.invoke()
            }
        }

        // Discard Reset — just resets without saving
        fun discardAndResetTrip(
            prefs: PrefsManager,
            scope: CoroutineScope,
            onDone: (() -> Unit)? = null
        ) {
            scope.launch {
                doReset(prefs, scope)
                onDone?.invoke()
            }
        }

        private suspend fun doReset(prefs: PrefsManager, scope: CoroutineScope) {
            _maxSpeed.value     = 0f
            _avgSpeed.value     = 0f
            _tripDistance.value = 0.0
            speedSum            = 0f
            speedCount          = 0f
            lastLocation        = null
            tripStartTime       = System.currentTimeMillis()
            speedHistory.clear()
            prefs.resetTripData()
        }

        // Keep old resetTrip for Auto compat
        fun resetTrip(prefs: PrefsManager, scope: CoroutineScope) {
            scope.launch { doReset(prefs, scope) }
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        startForeground(1, buildNotification())

        lifecycleScope.launch {
            _tripDistance.value = prefs.savedTripDistance.first()
            _maxSpeed.value     = prefs.savedMaxSpeed.first()
            speedSum            = prefs.savedSpeedSum.first()
            speedCount          = prefs.savedSpeedCount.first()
            if (speedCount > 0f) _avgSpeed.value = speedSum / speedCount
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
                // Broadcast to widget
                val intent = Intent(ACTION_SPEED_UPDATE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SPEED, _speedMs.value)
                    putExtra(EXTRA_MAX, _maxSpeed.value)
                    putExtra(EXTRA_AVG, _avgSpeed.value)
                    putExtra(EXTRA_TRIP, _tripDistance.value.toFloat())
                }
                sendBroadcast(intent)

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

                // Record speed history (max 500 points)
                if (speedHistory.size < 500) speedHistory.add(speed)

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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, "speed_channel")
            .setContentTitle("SpeedoMate Active")
            .setContentText("Tracking your speed...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}