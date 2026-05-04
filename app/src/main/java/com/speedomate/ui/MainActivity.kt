package com.speedomate.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.lifecycle.lifecycleScope
import com.speedomate.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: SpeedViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private lateinit var vibrator: Vibrator
    private var rotationSensor: Sensor? = null
    private val alertHandler = Handler(Looper.getMainLooper())

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.speedomate.SPEED_LIMIT_ALERT") {
                triggerAlert()
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val orientationValues = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                azimuth = ((azimuth % 360f) + 360f) % 360f
                binding.speedometerView.setHeading(azimuth)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Track if user has denied before
    private val prefs by lazy {
        getSharedPreferences("speedomate_perm", MODE_PRIVATE)
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        when {
            perms[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Permission granted
                prefs.edit().putBoolean("perm_denied_once", false).apply()
                vm.startService()
            }
            else -> {
                // Permission denied
                prefs.edit().putBoolean("perm_denied_once", true).apply()
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = getSystemService(Vibrator::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        registerReceiver(alertReceiver, IntentFilter("com.speedomate.SPEED_LIMIT_ALERT"), RECEIVER_NOT_EXPORTED)

        checkPermissionsAndStart()
        observeSpeed()
        observeSpeedLimitAlert()

        binding.btnResetTrip.setOnClickListener {
            vm.saveAndResetTrip {
                runOnUiThread {
                    Toast.makeText(this, "✅ Trip saved & reset!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDiscardTrip.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Discard Trip?")
                .setMessage("This will reset without saving. Are you sure?")
                .setPositiveButton("Discard") { _, _ ->
                    vm.discardAndResetTrip {
                        runOnUiThread {
                            Toast.makeText(this, "🗑️ Trip discarded", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnTripHistory.setOnClickListener {
            startActivity(Intent(this, TripHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED) {
            vm.startService()
        }
        rotationSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }

    private fun checkPermissionsAndStart() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION

        when {
            // Already granted
            ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED -> {
                vm.startService()
            }

            // Should show rationale (denied once before)
            shouldShowRequestPermissionRationale(fine) -> {
                showPermissionRationaleDialog()
            }

            // First time or permanently denied
            else -> {
                val deniedBefore = prefs.getBoolean("perm_denied_once", false)
                if (deniedBefore) {
                    // Permanently denied — go to settings
                    showGoToSettingsDialog()
                } else {
                    // First time — just request
                    permLauncher.launch(
                        arrayOf(fine, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            }
        }
    }

    // First time denial dialog
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("📍 Location Permission Required")
            .setMessage(
                "SpeedoMate needs location access to measure your speed using GPS.\n\n" +
                        "This is only used while the app is open — we never track you in the background."
            )
            .setCancelable(false)
            .setPositiveButton("Grant Permission") { _, _ ->
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Not Now") { _, _ ->
                showPermissionDeniedDialog()
            }
            .show()
    }

    // After denial dialog
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Permission Denied")
            .setMessage(
                "Without location permission, SpeedoMate cannot measure your speed.\n\n" +
                        "Please grant location permission to use the app."
            )
            .setCancelable(false)
            .setPositiveButton("Try Again") { _, _ ->
                // Check if permanently denied
                val fine = Manifest.permission.ACCESS_FINE_LOCATION
                if (shouldShowRequestPermissionRationale(fine)) {
                    permLauncher.launch(
                        arrayOf(fine, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                } else {
                    showGoToSettingsDialog()
                }
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .show()
    }

    // Permanently denied — redirect to App Settings
    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔒 Permission Permanently Denied")
            .setMessage(
                "Location permission was permanently denied.\n\n" +
                        "Please go to App Settings → Permissions → Location → " +
                        "select 'While using the app' to enable it."
            )
            .setCancelable(false)
            .setPositiveButton("Open App Settings") { _, _ ->
                // Opens SpeedoMate's page in system App Info
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .show()
    }

    private fun observeSpeed() {
        lifecycleScope.launch {
            vm.isMetric.collectLatest { metric ->
                val speedUnit = if (metric) "km/h" else "mph"
                val distUnit  = if (metric) "km" else "mi"
                binding.speedometerView.unit = speedUnit
                binding.tvMaxUnit.text  = speedUnit
                binding.tvAvgUnit.text  = speedUnit
                binding.tvTripUnit.text = distUnit
                binding.speedometerView.setMaxDisplaySpeed(if (metric) 180f else 120f)
            }
        }
        lifecycleScope.launch {
            vm.speedLimitThreshold.collectLatest { threshold ->
                val metric = vm.isMetric.value
                binding.speedometerView.speedLimitThreshold = threshold.toFloat()
            }
        }
        lifecycleScope.launch {
            vm.currentSpeed.collectLatest { binding.speedometerView.setSpeed(it) }
        }
        lifecycleScope.launch {
            vm.maxSpeed.collectLatest { binding.tvMaxSpeed.text = "%.0f".format(it) }
        }
        lifecycleScope.launch {
            vm.avgSpeed.collectLatest { binding.tvAvgSpeed.text = "%.0f".format(it) }
        }
        lifecycleScope.launch {
            vm.tripDistance.collectLatest { binding.tvTrip.text = "%.2f".format(it) }
        }
    }

    private fun observeSpeedLimitAlert() {
        lifecycleScope.launch {
            vm.speedLimitAlert.collectLatest { isAlerting ->
                binding.speedometerView.speedLimitExceeded = isAlerting
            }
        }
    }

    private fun triggerAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
        playBeep()
        alertHandler.postDelayed({ playBeep() }, 1000)
    }

    private fun playBeep() {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 200)
        alertHandler.postDelayed({ toneGen.release() }, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(alertReceiver)
    }
}