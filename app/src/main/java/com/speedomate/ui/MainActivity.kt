package com.speedomate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.startService()
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
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        binding.btnTripHistory.setOnClickListener {
            startActivity(android.content.Intent(this, TripHistoryActivity::class.java))
        }
    }

    private fun checkPermissionsAndStart() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED) {
            vm.startService()
        } else {
            permLauncher.launch(arrayOf(fine, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
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