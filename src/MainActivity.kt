package com.doomscrollguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.doomscrollguard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)

        setupUI()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupUI() {
        // Restore saved settings
        binding.switchEnabled.isChecked = prefs.getBoolean("tracking_enabled", true)
        binding.sliderThreshold.value = prefs.getInt(
            "scroll_threshold", ScrollTrackerService.DEFAULT_SCROLL_THRESHOLD
        ).toFloat()
        binding.sliderWindow.value = prefs.getInt(
            "window_seconds", ScrollTrackerService.DEFAULT_WINDOW_SECONDS
        ).toFloat()
        binding.sliderCooldown.value = prefs.getInt(
            "cooldown_seconds", ScrollTrackerService.DEFAULT_COOLDOWN_SECONDS
        ).toFloat()

        updateSliderLabels()

        // Enable / disable tracking
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tracking_enabled", isChecked).apply()
        }

        // Scroll threshold (how many scrolls to trigger)
        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("scroll_threshold", value.toInt()).apply()
            updateSliderLabels()
        }

        // Time window for counting scrolls
        binding.sliderWindow.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("window_seconds", value.toInt()).apply()
            updateSliderLabels()
        }

        // Cooldown between alerts
        binding.sliderCooldown.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("cooldown_seconds", value.toInt()).apply()
            updateSliderLabels()
        }

        // Button to open Accessibility Settings
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun updateSliderLabels() {
        val threshold = binding.sliderThreshold.value.toInt()
        val window = binding.sliderWindow.value.toInt()
        val cooldown = binding.sliderCooldown.value.toInt()

        binding.labelThreshold.text = "Scroll trigger: $threshold scrolls"
        binding.labelWindow.text = "Within: $window seconds"
        binding.labelCooldown.text = "Alert cooldown: ${cooldown}s (${cooldown / 60} min)"
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        binding.tvStatus.text = if (enabled) {
            "✅ Service active — watch alerts enabled"
        } else {
            "⚠️ Accessibility permission needed"
        }
        binding.btnOpenAccessibility.text = if (enabled) {
            "Accessibility Settings (active)"
        } else {
            "Enable Accessibility Permission →"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
    }
}
