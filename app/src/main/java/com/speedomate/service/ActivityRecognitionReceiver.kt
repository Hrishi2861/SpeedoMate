package com.speedomate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val i = intent ?: return
        val result = ActivityRecognitionResult.extractResult(i) ?: return
        val activity = result.mostProbableActivity
        SpeedTrackingService.setDeviceStill(
            activity.type == DetectedActivity.STILL && activity.confidence >= 75
        )
    }
}
