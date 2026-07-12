package com.speedomate.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.speedomate.data.PrefsManager
import com.speedomate.data.TripDatabase
import com.speedomate.data.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

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

        private val _isDeviceStill = MutableStateFlow(false)
        val isDeviceStill: StateFlow<Boolean> = _isDeviceStill

        fun setDeviceStill(still: Boolean) {
            _isDeviceStill.value = still
        }

        var speedSum     = 0f
        var speedCount   = 0f
        var minAlt       = Double.MAX_VALUE
        var maxAlt       = Double.MIN_VALUE
        var lastLocation: android.location.Location? = null
        var tripStartTime = 0L
        var hasAlertedThisCrossing = false
        var currentSpeedLimitThreshold = 0

        var lastMovementTime = 0L

        val speedHistory    = mutableListOf<Float>()
        val altitudeHistory = mutableListOf<Double>()

        private var tripStartTimePersisted = false

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
                            startTime      = if (tripStartTime > 0L) tripStartTime else System.currentTimeMillis(),
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
            tripStartTime        = 0L
            tripStartTimePersisted = false
            hasAlertedThisCrossing = false
            lastMovementTime     = 0L
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
    private var historyUpdateCount = 0
    private val HISTORY_WRITE_INTERVAL = 10
    private var activityRecognitionPendingIntent: PendingIntent? = null

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

            val savedStartTime = prefs.savedTripStartTime.first()
            if (savedStartTime > 0L) {
                tripStartTime = savedStartTime
            }

            val savedMinAlt = prefs.savedMinAltitude.first()
            val savedMaxAlt = prefs.savedMaxAltitude.first()
            if (savedMinAlt != Double.MAX_VALUE) minAlt = savedMinAlt
            if (savedMaxAlt != Double.MIN_VALUE) maxAlt = savedMaxAlt

            val savedSpeedJson = prefs.savedSpeedHistory.first()
            val savedAltJson = prefs.savedAltitudeHistory.first()
            try {
                val speedArr = JSONArray(savedSpeedJson)
                for (i in 0 until speedArr.length()) {
                    speedHistory.add(speedArr.getDouble(i).toFloat())
                }
                val altArr = JSONArray(savedAltJson)
                for (i in 0 until altArr.length()) {
                    altitudeHistory.add(altArr.getDouble(i))
                }
            } catch (_: JSONException) {}

            val savedMovement = prefs.savedLastMovementTime.first()
            if (savedMovement > 0L) lastMovementTime = savedMovement

            if (speedCount > 0f) _avgSpeed.value = speedSum / speedCount

            lifecycleScope.launch {
                prefs.speedLimitThreshold.collect { threshold ->
                    currentSpeedLimitThreshold = threshold
                }
            }

            startLocationUpdates()
            startIdleCheckLoop()
            startActivityRecognition()
        }
    }

    private fun startActivityRecognition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) return

        try {
            val receiverIntent = Intent(this, ActivityRecognitionReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            activityRecognitionPendingIntent = pendingIntent
            ActivityRecognition.getClient(this)
                .requestActivityUpdates(30_000L, pendingIntent)
        } catch (_: SecurityException) {
            // Permission state is stale or Play Services unavailable — no-op
        } catch (_: Exception) {
            // Play Services activity recognition unavailable on this device
        }
    }

    private fun startIdleCheckLoop() {
        lifecycleScope.launch {
            while (isActive) {
                delay(30_000L)
                val autoSaveEnabled = prefs.autoSaveEnabled.first()
                if (!autoSaveEnabled) continue
                if (speedCount <= 0f && _tripDistance.value <= 0.0) continue
                if (lastMovementTime <= 0L) continue

                val idleMinutes = prefs.autoSaveIdleMinutes.first()
                val idleMillis = idleMinutes * 60_000L
                val now = System.currentTimeMillis()
                if (now - lastMovementTime >= idleMillis) {
                    saveAndResetTrip(prefs, lifecycleScope, TripDatabase.getDatabase(this@SpeedTrackingService)) {
                        lastMovementTime = System.currentTimeMillis()
                        tripStartTime = System.currentTimeMillis()
                    }
                }
            }
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

                // Gate 1: reject low-accuracy fixes entirely (don't touch lastLocation)
                val MAX_ACCURACY_M = 15f
                if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return

                // Gate 2: speed-accuracy check (API 26+)
                var rawSpeed = if (location.hasSpeed()) location.speed else 0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    location.hasSpeedAccuracy() &&
                    location.speedAccuracyMetersPerSecond > 1.0f) {
                    rawSpeed = 0f
                }

                // Gate 3: noise floor
                var effectiveSpeed = if (rawSpeed < 0.8f) 0f else rawSpeed

                // Gate 4: ActivityRecognition override
                if (_isDeviceStill.value) effectiveSpeed = 0f

                val alt  = if (location.hasAltitude()) location.altitude else 0.0
                val bear = if (location.hasBearing()) location.bearing else 0f

                _speedMs.value  = effectiveSpeed
                _altitude.value = alt
                _bearing.value  = bear

                if (effectiveSpeed > _maxSpeed.value) _maxSpeed.value = effectiveSpeed
                if (alt < minAlt) minAlt = alt
                if (alt > maxAlt) maxAlt = alt

                if (currentSpeedLimitThreshold > 0) {
                    val speedKmh = effectiveSpeed * 3.6f
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

                // Gate 5: reject GPS teleports using implied speed between fixes
                lastLocation?.let { prev ->
                    val deltaMeters = prev.distanceTo(location)
                    val elapsedSec = ((location.time - prev.time).coerceAtLeast(1L)) / 1000.0
                    val impliedSpeedMs = deltaMeters / elapsedSec
                    val MAX_PLAUSIBLE_SPEED_MS = 70.0 // ~252 km/h

                    if (effectiveSpeed > 0f && impliedSpeedMs <= MAX_PLAUSIBLE_SPEED_MS) {
                        _tripDistance.value += deltaMeters / 1000.0
                    }
                }
                // always re-baseline, even on rejection, so a stray teleport
                // doesn't permanently block future updates
                lastLocation = location

                speedSum += effectiveSpeed
                speedCount++
                _avgSpeed.value = speedSum / speedCount

                if (speedHistory.size < 500) {
                    speedHistory.add(effectiveSpeed)
                    altitudeHistory.add(alt)
                }

                val now = System.currentTimeMillis()
                if (effectiveSpeed > 0.3f) {
                    lastMovementTime = now
                }
                if (tripStartTime == 0L) {
                    tripStartTime = now
                    lifecycleScope.launch {
                        prefs.saveTripStartTime(tripStartTime)
                    }
                }

                historyUpdateCount++
                if (historyUpdateCount >= HISTORY_WRITE_INTERVAL) {
                    historyUpdateCount = 0
                    lifecycleScope.launch {
                        val speedJson = JSONArray().apply { speedHistory.forEach { put(it) } }.toString()
                        val altJson = JSONArray().apply { altitudeHistory.forEach { put(it) } }.toString()
                        prefs.saveHistory(speedJson, altJson)
                        prefs.saveAltBounds(minAlt, maxAlt)
                        prefs.saveLastMovementTime(lastMovementTime)
                    }
                }

                lifecycleScope.launch {
                    prefs.saveTripData(
                        _tripDistance.value,
                        _maxSpeed.value,
                        speedSum,
                        speedCount
                    )
                }

                sendBroadcast(Intent(ACTION_SPEED_UPDATE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SPEED,    effectiveSpeed)
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
        activityRecognitionPendingIntent?.let { pi ->
            try {
                ActivityRecognition.getClient(this).removeActivityUpdates(pi)
            } catch (_: Exception) {}
        }
        _isDeviceStill.value = false
    }
}
