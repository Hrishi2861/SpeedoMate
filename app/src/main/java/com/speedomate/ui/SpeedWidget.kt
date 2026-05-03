package com.speedomate.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.speedomate.R
import com.speedomate.data.PrefsManager
import com.speedomate.service.SpeedTrackingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SpeedWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, isLarge = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            SpeedTrackingService.ACTION_SPEED_UPDATE -> {
                val speedMs   = intent.getFloatExtra(SpeedTrackingService.EXTRA_SPEED, 0f)
                val maxMs     = intent.getFloatExtra(SpeedTrackingService.EXTRA_MAX, 0f)
                val avgMs     = intent.getFloatExtra(SpeedTrackingService.EXTRA_AVG, 0f)
                val tripKm    = intent.getFloatExtra(SpeedTrackingService.EXTRA_TRIP, 0f)

                val isMetric = runBlocking {
                    PrefsManager(context).isMetric.first()
                }
                val factor     = if (isMetric) 3.6f else 2.237f
                val distFactor = if (isMetric) 1f else 0.621371f
                val unit       = if (isMetric) "km/h" else "mph"
                val distUnit   = if (isMetric) "km" else "mi"

                val speed = "%.0f".format(speedMs * factor)
                val max   = "%.0f".format(maxMs * factor)
                val avg   = "%.0f".format(avgMs * factor)
                val trip  = "%.2f".format(tripKm * distFactor)

                val awm = AppWidgetManager.getInstance(context)

                // Update small widgets
                val smallIds = awm.getAppWidgetIds(
                    ComponentName(context, SpeedWidget::class.java)
                )
                smallIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_speed)
                    views.setTextViewText(R.id.widget_speed, speed)
                    views.setTextViewText(R.id.widget_unit, unit)
                    views.setTextViewText(R.id.widget_trip, "Trip: $trip $distUnit")
                    views.setTextViewText(R.id.widget_status, "● Live")
                    views.setOnClickPendingIntent(
                        R.id.widget_speed, getLaunchIntent(context)
                    )
                    awm.updateAppWidget(id, views)
                }

                // Update large widgets
                val largeIds = awm.getAppWidgetIds(
                    ComponentName(context, SpeedWidgetLarge::class.java)
                )
                largeIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_speed_large)
                    views.setTextViewText(R.id.widget_speed, speed)
                    views.setTextViewText(R.id.widget_unit, unit)
                    views.setTextViewText(R.id.widget_max, max)
                    views.setTextViewText(R.id.widget_max_unit, unit)
                    views.setTextViewText(R.id.widget_avg, avg)
                    views.setTextViewText(R.id.widget_avg_unit, unit)
                    views.setTextViewText(R.id.widget_trip, trip)
                    views.setTextViewText(R.id.widget_trip_unit, distUnit)
                    views.setTextViewText(R.id.widget_status, "● Live")
                    views.setOnClickPendingIntent(
                        R.id.widget_speed, getLaunchIntent(context)
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_save, getSaveResetIntent(context)
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_discard, getDiscardIntent(context)
                    )
                    awm.updateAppWidget(id, views)
                }
            }

            ACTION_WIDGET_SAVE_RESET -> {
                val serviceIntent = Intent(context, SpeedTrackingService::class.java)
                context.startForegroundService(serviceIntent)
                val prefs = PrefsManager(context)
                val db = com.speedomate.data.TripDatabase.getDatabase(context)
                val scope = kotlinx.coroutines.MainScope()
                SpeedTrackingService.saveAndResetTrip(prefs, scope, db)
            }

            ACTION_WIDGET_DISCARD -> {
                val prefs = PrefsManager(context)
                val scope = kotlinx.coroutines.MainScope()
                SpeedTrackingService.discardAndResetTrip(prefs, scope)
            }
        }
    }

    private fun getLaunchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getSaveResetIntent(context: Context): PendingIntent {
        val intent = Intent(context, SpeedWidget::class.java).apply {
            action = ACTION_WIDGET_SAVE_RESET
        }
        return PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getDiscardIntent(context: Context): PendingIntent {
        val intent = Intent(context, SpeedWidget::class.java).apply {
            action = ACTION_WIDGET_DISCARD
        }
        return PendingIntent.getBroadcast(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_WIDGET_SAVE_RESET = "com.speedomate.WIDGET_SAVE_RESET"
        const val ACTION_WIDGET_DISCARD    = "com.speedomate.WIDGET_DISCARD"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isLarge: Boolean
        ) {
            val layout = if (isLarge) R.layout.widget_speed_large else R.layout.widget_speed
            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.widget_speed, "0")
            views.setTextViewText(R.id.widget_status, "Tap to start")
            views.setOnClickPendingIntent(
                R.id.widget_speed,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

class SpeedWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach {
            SpeedWidget.updateWidget(context, appWidgetManager, it, isLarge = true)
        }
    }
}