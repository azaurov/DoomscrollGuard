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

    private val scrollOptions = listOf(10, 20, 30, 40, 50, 60, 75, 100)
    private val windowOptions = listOf(
        30 to "30 seconds",
        60 to "1 minute",
        90 to "1 min 30 sec",
        120 to "2 minutes",
        180 to "3 minutes",
        300 to "5 minutes",
        600 to "10 minutes"
    )
    private val cooldownOptions = listOf(
        60 to "1 minute",
        120 to "2 minutes",
        300 to "5 minutes",
        600 to "10 minutes",
        900 to "15 minutes",
        1800 to "30 minutes",
        3600 to "1 hour"
    )

    private var scrollIdx = 3
    private var windowIdx = 1
    private var cooldownIdx = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        loadSavedIndices()
        setupUI()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun loadSavedIndices() {
        val savedScroll = prefs.getInt("scroll_threshold", scrollOptions[scrollIdx])
        val savedWindow = prefs.getInt("window_seconds", windowOptions[windowIdx].first)
        val savedCooldown = prefs.getInt("cooldown_seconds", cooldownOptions[cooldownIdx].first)
        scrollIdx = scrollOptions.indexOfFirst { it == savedScroll }.takeIf { it >= 0 } ?: 3
        windowIdx = windowOptions.indexOfFirst { it.first == savedWindow }.takeIf { it >= 0 } ?: 1
        cooldownIdx = cooldownOptions.indexOfFirst { it.first == savedCooldown }.takeIf { it >= 0 } ?: 1
    }

    private fun setupUI() {
        binding.switchEnabled.isChecked = prefs.getBoolean("tracking_enabled", true)
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tracking_enabled", isChecked).apply()
            updateSummary()
        }

        updateScrollLabel()
        binding.btnScrollDown.setOnClickListener {
            if (scrollIdx > 0) { scrollIdx--; saveScrollPref(); updateScrollLabel() }
        }
        binding.btnScrollUp.setOnClickListener {
            if (scrollIdx < scrollOptions.lastIndex) { scrollIdx++; saveScrollPref(); updateScrollLabel() }
        }

        updateWindowLabel()
        binding.btnWindowDown.setOnClickListener {
            if (windowIdx > 0) { windowIdx--; saveWindowPref(); updateWindowLabel() }
        }
        binding.btnWindowUp.setOnClickListener {
            if (windowIdx < windowOptions.lastIndex) { windowIdx++; saveWindowPref(); updateWindowLabel() }
        }

        updateCooldownLabel()
        binding.btnCooldownDown.setOnClickListener {
            if (cooldownIdx > 0) { cooldownIdx--; saveCooldownPref(); updateCooldownLabel() }
        }
        binding.btnCooldownUp.setOnClickListener {
            if (cooldownIdx < cooldownOptions.lastIndex) { cooldownIdx++; saveCooldownPref(); updateCooldownLabel() }
        }

        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        updateSummary()
    }

    private fun updateScrollLabel() {
        binding.tvScrollValue.text = "${scrollOptions[scrollIdx]} scrolls"
        binding.btnScrollDown.isEnabled = scrollIdx > 0
        binding.btnScrollUp.isEnabled = scrollIdx < scrollOptions.lastIndex
        updateSummary()
    }

    private fun updateWindowLabel() {
        binding.tvWindowValue.text = windowOptions[windowIdx].second
        binding.btnWindowDown.isEnabled = windowIdx > 0
        binding.btnWindowUp.isEnabled = windowIdx < windowOptions.lastIndex
        updateSummary()
    }

    private fun updateCooldownLabel() {
        binding.tvCooldownValue.text = cooldownOptions[cooldownIdx].second
        binding.btnCooldownDown.isEnabled = cooldownIdx > 0
        binding.btnCooldownUp.isEnabled = cooldownIdx < cooldownOptions.lastIndex
        updateSummary()
    }

    private fun updateSummary() {
        val enabled = binding.switchEnabled.isChecked
        if (!enabled) { binding.tvSummary.text = "Detection is paused."; return }
        val s = scrollOptions[scrollIdx]
        val w = windowOptions[windowIdx].second
        val c = cooldownOptions[cooldownIdx].second
        binding.tvSummary.text = "Alert after $s scrolls within $w.\nWon't repeat for $c."
    }

    private fun saveScrollPref() = prefs.edit().putInt("scroll_threshold", scrollOptions[scrollIdx]).apply()
    private fun saveWindowPref() = prefs.edit().putInt("window_seconds", windowOptions[windowIdx].first).apply()
    private fun saveCooldownPref() = prefs.edit().putInt("cooldown_seconds", cooldownOptions[cooldownIdx].first).apply()

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        binding.tvStatus.text = if (enabled) "✅ Service active — watch alerts enabled" else "⚠️ Accessibility permission needed"
        binding.btnOpenAccessibility.text = if (enabled) "Accessibility Settings (active)" else "Enable Accessibility Permission →"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
