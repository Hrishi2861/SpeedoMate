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
        const val ACTION_SPEED_LIMIT_ALERT = "com.speedomate.SPEED_LIMIT_ALERT"
        const val EXTRA_SPEED    = "speed"
        const val EXTRA_MAX      = "max"
        const val EXTRA_AVG      = "avg"
        const val EXTRA_TRIP     = "trip"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_BEARING  = "bearing"

        private val _speedMs      = MutableStateFlow(0f)
        val speedMs: StateFlow<Float> = _speedMs

        private val _maxSpeed     = MutableStateFlow(0f)
        val maxSpeed: StateFlow<Float> = _maxSpeed

        private val _avgSpeed     = MutableStateFlow(0f)
        val avgSpeed: StateFlow<Float> = _avgSpeed

        private val _tripDistance = MutableStateFlow(0.0)
        val tripDistance: StateFlow<Double> = _tripDistance

        private val _altitude     = MutableStateFlow(0.0)
        val altitude: StateFlow<Double> = _altitude

        private val _bearing      = MutableStateFlow(0f)
        val bearing: StateFlow<Float> = _bearing

        private val _speedLimitAlert = MutableStateFlow(false)
        val speedLimitAlert: StateFlow<Boolean> = _speedLimitAlert

        var speedSum     = 0f
        var speedCount   = 0f
        var minAlt       = Double.MAX_VALUE
        var maxAlt       = Double.MIN_VALUE
        var lastLocation: android.location.Location? = null
        var tripStartTime = System.currentTimeMillis()
        var hasAlertedThisCrossing = false
        var currentSpeedLimitThreshold = 0

        val speedHistory    = mutableListOf<Float>()
        val altitudeHistory = mutableListOf<Double>()

        fun saveAndResetTrip(
            prefs: PrefsManager,
            scope: CoroutineScope,
            database: TripDatabase,
            onDone: (() -> Unit)? = null
        ) {
            scope.launch {
                if (speedCount > 0f || _tripDistance.value > 0.0) {
                    val speedJson = JSONArray().apply {
                        speedHistory.forEach { put(it) }
                    }.toString()
                    val altJson = JSONArray().apply {
                        altitudeHistory.forEach { put(it) }
                    }.toString()

                    database.tripDao().insertTrip(
                        TripEntity(
                            startTime      = tripStartTime,
                            endTime        = System.currentTimeMillis(),
                            distanceKm     = _tripDistance.value,
                            maxSpeedMs     = _maxSpeed.value,
                            avgSpeedMs     = if (speedCount > 0) speedSum / speedCount else 0f,
                            minAltitude    = if (minAlt == Double.MAX_VALUE) 0.0 else minAlt,
                            maxAltitude    = if (maxAlt == Double.MIN_VALUE) 0.0 else maxAlt,
                            speedPoints    = speedJson,
                            altitudePoints = altJson
                        )
                    )
                }
                doReset(prefs)
                onDone?.invoke()
            }
        }

        fun discardAndResetTrip(
            prefs: PrefsManager,
            scope: CoroutineScope,
            onDone: (() -> Unit)? = null
        ) {
            scope.launch {
                doReset(prefs)
                onDone?.invoke()
            }
        }

        private suspend fun doReset(prefs: PrefsManager) {
            _maxSpeed.value      = 0f
            _avgSpeed.value      = 0f
            _tripDistance.value  = 0.0
            _altitude.value      = 0.0
            _bearing.value       = 0f
            _speedLimitAlert.value = false
            speedSum             = 0f
            speedCount           = 0f
            minAlt               = Double.MAX_VALUE
            maxAlt               = Double.MIN_VALUE
            lastLocation         = null
            tripStartTime        = System.currentTimeMillis()
            hasAlertedThisCrossing = false
            speedHistory.clear()
            altitudeHistory.clear()
            prefs.resetTripData()
        }

        fun resetTrip(prefs: PrefsManager, scope: CoroutineScope) {
            scope.launch { doReset(prefs) }
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
            currentSpeedLimitThreshold = prefs.speedLimitThreshold.first()
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
                val location = result.lastLocation ?: return
                val speed = if (location.hasSpeed()) location.speed else 0f
                val alt   = if (location.hasAltitude()) location.altitude else 0.0
                val bear  = if (location.hasBearing()) location.bearing else 0f

                _speedMs.value   = speed
                _altitude.value  = alt
                _bearing.value   = bear

                if (speed > _maxSpeed.value) _maxSpeed.value = speed
                if (alt < minAlt) minAlt = alt
                if (alt > maxAlt) maxAlt = alt

                if (currentSpeedLimitThreshold > 0) {
                    val speedKmh = speed * 3.6f
                    if (speedKmh > currentSpeedLimitThreshold) {
                        if (!hasAlertedThisCrossing) {
                            hasAlertedThisCrossing = true
                            _speedLimitAlert.value = true
                            triggerAlert()
                        }
                    } else {
                        hasAlertedThisCrossing = false
                        _speedLimitAlert.value = false
                    }
                }

                if (speed > _maxSpeed.value) _maxSpeed.value = speed
                if (alt < minAlt) minAlt = alt
                if (alt > maxAlt) maxAlt = alt

                lastLocation?.let { prev ->
                    _tripDistance.value += prev.distanceTo(location) / 1000.0
                }
                lastLocation = location

                speedSum += speed
                speedCount++
                _avgSpeed.value = speedSum / speedCount

                if (speedHistory.size < 500) {
                    speedHistory.add(speed)
                    altitudeHistory.add(alt)
                }

                lifecycleScope.launch {
                    prefs.saveTripData(
                        _tripDistance.value,
                        _maxSpeed.value,
                        speedSum,
                        speedCount
                    )
                }

                // Broadcast to widget
                sendBroadcast(Intent(ACTION_SPEED_UPDATE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SPEED,    speed)
                    putExtra(EXTRA_MAX,      _maxSpeed.value)
                    putExtra(EXTRA_AVG,      _avgSpeed.value)
                    putExtra(EXTRA_TRIP,     _tripDistance.value.toFloat())
                    putExtra(EXTRA_ALTITUDE, alt.toFloat())
                    putExtra(EXTRA_BEARING,  bear)
                })
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
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

    private fun triggerAlert() {
        sendBroadcast(Intent(ACTION_SPEED_LIMIT_ALERT).apply { setPackage(packageName) })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}