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
            vm.isMetric.collectLatest { metric ->
                val speedUnit = if (metric) "km/h" else "mph"
                val distUnit  = if (metric) "km" else "mi"
                binding.speedometerView.unit = speedUnit
                binding.tvMaxUnit.text  = speedUnit
                binding.tvAvgUnit.text  = speedUnit
                binding.tvTripUnit.text = distUnit
                // Adjust dial max based on unit
                binding.speedometerView.setMaxDisplaySpeed(if (metric) 180f else 120f)
            }
        }
        lifecycleScope.launch {
            vm.currentSpeed.collectLatest { speed ->
                binding.speedometerView.setSpeed(speed)
            }
        }
        lifecycleScope.launch {
            vm.maxSpeed.collectLatest { speed ->
                binding.tvMaxSpeed.text = "%.0f".format(speed)
            }
        }
        lifecycleScope.launch {
            vm.avgSpeed.collectLatest { speed ->
                binding.tvAvgSpeed.text = "%.0f".format(speed)
            }
        }
        lifecycleScope.launch {
            vm.tripDistance.collectLatest { dist ->
                binding.tvTrip.text = "%.2f".format(dist)
            }
        }
    }
}