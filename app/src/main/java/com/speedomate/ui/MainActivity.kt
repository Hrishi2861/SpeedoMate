package com.speedomate.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.speedomate.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: SpeedViewModel by viewModels()

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

        checkPermissionsAndStart()
        observeSpeed()

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
        // Re-check permission when user returns from Settings
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED) {
            vm.startService()
        }
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
}