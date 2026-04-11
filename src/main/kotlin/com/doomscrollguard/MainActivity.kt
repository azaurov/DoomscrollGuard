package com.doomscrollguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
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

        // Button to request Overlay Permission
        binding.btnOverlayPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSliderLabels() {
        val threshold = binding.sliderThreshold.value.toInt()
        val window = binding.sliderWindow.value.toInt()
        val cooldown = binding.sliderCooldown.value.toInt()

        binding.labelThreshold.text = "Show Peter after $threshold scrolls"
        binding.labelWindow.text = "Count scrolls within $window seconds"
        binding.labelCooldown.text = "Wait ${cooldown}s before bugging me again"
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        binding.tvStatus.text = if (enabled) {
            "✅ Service active — Peter is watching you"
        } else {
            "⚠️ Accessibility permission needed"
        }
        binding.btnOpenAccessibility.text = if (enabled) {
            "Accessibility Settings (active)"
        } else {
            "Enable Accessibility Permission →"
        }

        val overlayEnabled = Settings.canDrawOverlays(this)
        binding.btnOverlayPermission.text = if (overlayEnabled) {
            "Overlay Permission (active)"
        } else {
            "Enable Overlay Permission →"
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
