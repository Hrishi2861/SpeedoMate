package com.speedomate.service

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.speedomate.data.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SpeedoMateApplication : Application(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wasProjection = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        tryAutoStartOnAACount()
    }

    private fun tryAutoStartOnAACount() {
        val prefs = PrefsManager(this)

        try {
            val carConnectionClass = Class.forName("androidx.car.app.connection.CarConnection")
            val getInstanceMethod = carConnectionClass.getMethod("getInstance", android.content.Context::class.java)
            val carConnection = getInstanceMethod.invoke(null, this)

            val typeField = carConnectionClass.getField("type")
            val typeLiveData = typeField.get(carConnection) as? androidx.lifecycle.LiveData<Int>
                ?: return

            typeLiveData.observe(this) { type ->
                val isProjection = type == 2

                if (isProjection && !wasProjection) {
                    wasProjection = true
                    scope.launch {
                        val enabled = prefs.autoStartAndroidAuto.first()
                        if (enabled) {
                            Log.d("SpeedoMateApp", "Android Auto connected, auto-starting tracking service")
                            val intent = Intent(this@SpeedoMateApplication, SpeedTrackingService::class.java)
                            startForegroundService(intent)
                        }
                    }
                } else if (!isProjection) {
                    wasProjection = false
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.w("SpeedoMateApp", "CarConnection class not available, Android Auto auto-start disabled")
        } catch (e: Exception) {
            Log.w("SpeedoMateApp", "Failed to set up AA auto-start: ${e.message}")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
    }
}
