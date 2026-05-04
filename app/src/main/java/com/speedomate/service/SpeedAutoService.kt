package com.speedomate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.speedomate.data.PrefsManager
import com.speedomate.data.TripDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

class SpeedAutoService : CarAppService() {
    override fun createHostValidator() = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession() = SpeedSession()
}

class SpeedSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val serviceIntent = Intent(carContext, SpeedTrackingService::class.java)
        carContext.startForegroundService(serviceIntent)
        return SpeedScreen(carContext)
    }

    override fun onNewIntent(intent: Intent) {}
}

class SpeedScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {

    private val prefs = PrefsManager(carContext)
    private val database = TripDatabase.getDatabase(carContext)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isMetric = true
    private var speedLimitThreshold = 0
    private val alertHandler = Handler(Looper.getMainLooper())

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.speedomate.SPEED_LIMIT_ALERT") {
                triggerAABeep()
            }
        }
    }

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                carContext.registerReceiver(alertReceiver, IntentFilter("com.speedomate.SPEED_LIMIT_ALERT"), Context.RECEIVER_NOT_EXPORTED)
            }
            if (event == Lifecycle.Event.ON_DESTROY) {
                scope.cancel()
                try { carContext.unregisterReceiver(alertReceiver) } catch (_: Exception) {}
            }
        })

        scope.launch { prefs.isMetric.collectLatest { isMetric = it; invalidate() } }
        scope.launch { prefs.speedLimitThreshold.collectLatest { speedLimitThreshold = it; invalidate() } }
        scope.launch { SpeedTrackingService.speedMs.collect { invalidate() } }
        scope.launch { SpeedTrackingService.maxSpeed.collect { invalidate() } }
        scope.launch { SpeedTrackingService.avgSpeed.collect { invalidate() } }
        scope.launch { SpeedTrackingService.tripDistance.collect { invalidate() } }
        scope.launch { SpeedTrackingService.bearing.collect { invalidate() } }
        scope.launch { SpeedTrackingService.speedLimitAlert.collect { invalidate() } }
    }

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

    override fun onGetTemplate(): Template {
        val speed = SpeedTrackingService.speedMs.value
        val max = SpeedTrackingService.maxSpeed.value
        val avg = SpeedTrackingService.avgSpeed.value
        val trip = SpeedTrackingService.tripDistance.value
        val bearing = SpeedTrackingService.bearing.value
        val alert = SpeedTrackingService.speedLimitAlert.value

        val factor = if (isMetric) 3.6f else 2.237f
        val distFactor = if (isMetric) 1.0 else 0.621371
        val unit = if (isMetric) "km/h" else "mph"

        val speedText = "%.0f".format(speed * factor)
        val maxText = "%.0f".format(max * factor)
        val avgText = "%.0f".format(avg * factor)
        val tripText = "%.2f".format(trip * distFactor)
        val headingText = if (bearing > 0f) "${bearing.toInt()}° ${bearingToCardinal(bearing)}" else "—° —"

        val speedRow = Row.Builder()
            .setTitle("Current Speed")
            .addText("$speedText $unit")
            .build()

        val statsRow = Row.Builder()
            .setTitle("Trip Stats")
            .addText("Max: $maxText $unit   Avg: $avgText $unit")
            .addText("Distance: $tripText ${if (isMetric) "km" else "mi"}")
            .build()

        val headingRow = Row.Builder()
            .setTitle("Heading")
            .addText(headingText)
            .build()

        val unitToggleRow = Row.Builder()
            .setTitle("Speed Unit")
            .addText("Current: $unit (tap to toggle)")
            .setOnClickListener {
                isMetric = !isMetric
                scope.launch { prefs.setMetric(isMetric) }
                invalidate()
            }
            .build()

        val sectionItems = ItemList.Builder()
            .addItem(speedRow)
            .addItem(statsRow)
            .addItem(headingRow)
            .addItem(unitToggleRow)

        val showAlert = alert && speedLimitThreshold > 0
        if (showAlert) {
            val alertRow = Row.Builder()
                .setTitle("⚠️ Speed Limit Exceeded!")
                .addText("Slow down!")
                .build()
            sectionItems.addItem(alertRow)
        }

        val saveResetAction = Action.Builder()
            .setTitle("Save+Reset")
            .setOnClickListener {
                SpeedTrackingService.saveAndResetTrip(prefs, scope, database) {
                    invalidate()
                }
            }
            .build()

        val discardAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        android.R.drawable.ic_menu_delete
                    )
                ).build()
            )
            .setOnClickListener {
                SpeedTrackingService.discardAndResetTrip(prefs, scope) {
                    invalidate()
                }
            }
            .build()

        return ListTemplate.Builder()
            .setTitle("SpeedoMate")
            .setHeaderAction(Action.APP_ICON)
            .addSectionedList(
                SectionedItemList.create(
                    sectionItems.build(),
                    "Live Data"
                )
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(saveResetAction)
                    .addAction(discardAction)
                    .build()
            )
            .build()
    }

    private fun triggerAABeep() {
        playAABeep()
        alertHandler.postDelayed({ playAABeep() }, 1000)
    }

    private fun playAABeep() {
        val audioManager = carContext.getSystemService(AudioManager::class.java)
        val focusResult = audioManager.requestAudioFocus(
            AudioManager.OnAudioFocusChangeListener { },
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 200)
            alertHandler.postDelayed({
                toneGen.release()
                audioManager.abandonAudioFocus(null)
            }, 300)
        }
    }
}
