// ui/SettingsActivity.kt
package com.speedomate.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.speedomate.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val vm: SpeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            vm.isMetric.collectLatest { isMetric ->
                binding.toggleUnit.isChecked = isMetric
                binding.toggleUnit.text = if (isMetric) "km/h" else "mph"
            }
        }

        binding.toggleUnit.setOnCheckedChangeListener { _, isChecked ->
            vm.setMetric(isChecked)
        }
    }
}