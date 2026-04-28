// ui/MainActivity.kt
package com.speedomate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            vm.startService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissionsAndStart()
        observeSpeed()

        binding.btnResetTrip.setOnClickListener { vm.resetTrip() }
        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
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
            vm.currentSpeed.collectLatest { speed ->
                binding.tvSpeed.text = "%.0f".format(speed)
            }
        }
        lifecycleScope.launch {
            vm.maxSpeed.collectLatest { binding.tvMaxSpeed.text = "Max: ${"%.0f".format(it)}" }
        }
        lifecycleScope.launch {
            vm.avgSpeed.collectLatest { binding.tvAvgSpeed.text = "Avg: ${"%.0f".format(it)}" }
        }
        lifecycleScope.launch {
            vm.tripDistance.collectLatest { binding.tvTrip.text = "Trip: ${"%.2f".format(it)}" }
        }
        lifecycleScope.launch {
            vm.isMetric.collectLatest { metric ->
                binding.tvUnit.text = if (metric) "km/h" else "mph"
                binding.tvTripUnit.text = if (metric) "km" else "mi"
            }
        }
    }
}