// service/SpeedAutoService.kt
package com.speedomate.service

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.speedomate.data.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class SpeedAutoService : CarAppService() {
    override fun createHostValidator() = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession() = SpeedSession()
}

class SpeedSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return SpeedScreen(carContext)
    }
}

class SpeedScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {

    private val prefs = PrefsManager(carContext)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var speedText = "0"
    private var maxText = "0"
    private var avgText = "0"
    private var tripText = "0.0"
    private var unit = "km/h"

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                scope.cancel()
            }
        })

        scope.launch {
            combine(
                SpeedTrackingService.speedMs,
                SpeedTrackingService.maxSpeed,
                SpeedTrackingService.avgSpeed,
                SpeedTrackingService.tripDistance,
                prefs.isMetric
            ) { values ->
                // combine with 5 flows uses array form
                val speed = values[0] as Float
                val max   = values[1] as Float
                val avg   = values[2] as Float
                val trip  = values[3] as Double
                val metric = values[4] as Boolean

                val factor = if (metric) 3.6f else 2.237f
                val distFactor = if (metric) 1.0 else 0.621371
                unit      = if (metric) "km/h" else "mph"
                speedText = "%.0f".format(speed * factor)
                maxText   = "%.0f".format(max * factor)
                avgText   = "%.0f".format(avg * factor)
                tripText  = "%.2f".format(trip * distFactor)
            }.collect {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val speedRow = Row.Builder()
            .setTitle("Current Speed")
            .addText("$speedText $unit")
            .build()

        val statsRow = Row.Builder()
            .setTitle("Trip Stats")
            .addText("Max: $maxText $unit   Avg: $avgText $unit")
            .addText("Distance: $tripText ${if (unit == "km/h") "km" else "mi"}")
            .build()

        val resetAction = Action.Builder()
            .setTitle("Reset Trip")
            .setOnClickListener {
                SpeedTrackingService.resetTrip()
                invalidate()
            }
            .build()

        return ListTemplate.Builder()
            .setTitle("SpeedoMate")
            .setHeaderAction(Action.APP_ICON)
            .addSectionedList(
                SectionedItemList.create(
                    ItemList.Builder()
                        .addItem(speedRow)
                        .addItem(statsRow)
                        .build(),
                    "Live Data"
                )
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(resetAction)
                    .build()
            )
            .build()
    }
}