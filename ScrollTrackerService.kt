package com.doomscrollguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class ScrollTrackerService : AccessibilityService() {

    companion object {
        const val TAG = "DoomscrollGuard"
        const val CHANNEL_ID = "doomscroll_alert"
        const val NOTIF_ID = 1001

        // Default thresholds (stored as seconds internally, displayed as min/hr in UI)
        const val DEFAULT_SCROLL_THRESHOLD = 40       // scroll events to trigger alert
        const val DEFAULT_WINDOW_SECONDS = 60         // 1 minute window
        const val DEFAULT_COOLDOWN_SECONDS = 120      // 2 minute cooldown

        // Apps considered "doomscroll" candidates
        val TRACKED_PACKAGES = setOf(
            "com.instagram.android",          // Instagram
            "com.zhiliaoapp.musically",       // TikTok
            "com.ss.android.ugc.trill",       // TikTok (alt)
            "com.twitter.android",            // X / Twitter
            "com.reddit.frontpage",           // Reddit
            "com.google.android.youtube",     // YouTube
            "com.facebook.katana",            // Facebook
            "com.facebook.lite",              // Facebook Lite
            "com.pinterest",                  // Pinterest
            "com.snapchat.android",           // Snapchat
            "com.tumblr",                     // Tumblr
            "com.android.chrome",             // Chrome
            "org.mozilla.firefox"             // Firefox (bonus)
        )
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // Scroll tracking state
    private val scrollTimestamps = ArrayDeque<Long>()  // timestamps of recent scroll events
    private var currentPackage: String = ""
    private var lastAlertTime: Long = 0L
    private var isTracking: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        isTracking = prefs.getBoolean("tracking_enabled", true)
        Log.d(TAG, "ScrollTrackerService connected. Tracking: $isTracking")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isTracking || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Track which app is in the foreground
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentPackage) {
                    currentPackage = pkg
                    // Reset scroll buffer when switching apps
                    scrollTimestamps.clear()
                    Log.d(TAG, "Switched to package: $pkg")
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg !in TRACKED_PACKAGES) return

                val now = System.currentTimeMillis()
                val windowMs = getScrollWindowSeconds() * 1000L
                val threshold = getScrollThreshold()
                val cooldownMs = getCooldownSeconds() * 1000L

                // Add current scroll event
                scrollTimestamps.addLast(now)

                // Remove events outside the time window
                while (scrollTimestamps.isNotEmpty() &&
                    now - scrollTimestamps.first() > windowMs) {
                    scrollTimestamps.removeFirst()
                }

                Log.d(TAG, "Scroll event on $pkg — ${scrollTimestamps.size}/$threshold in window")

                // Check if we've hit the threshold
                if (scrollTimestamps.size >= threshold) {
                    if (now - lastAlertTime > cooldownMs) {
                        lastAlertTime = now
                        scrollTimestamps.clear()
                        sendWatchAlert(pkg)
                    }
                }
            }
        }
    }

    private fun sendWatchAlert(packageName: String) {
        val appName = getFriendlyName(packageName)
        Log.d(TAG, "Doomscroll detected on $appName — firing notification")

        // Build a notification that the Zepp app will mirror to the Amazfit GTR 3
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⛔ Doomscrolling Alert")
            .setContentText("You've been scrolling $appName for a while. Take a break!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // Phone stays mostly silent; watch will still vibrate via Zepp mirroring
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVibrate(longArrayOf(0))  // minimal phone vibration
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Doomscroll Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vibrates your watch when you're doomscrolling"
                enableVibration(false)  // Suppress phone vibration; watch handles it
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun setTracking(enabled: Boolean) {
        isTracking = enabled
        prefs.edit().putBoolean("tracking_enabled", enabled).apply()
        if (!enabled) scrollTimestamps.clear()
    }

    private fun getScrollThreshold() =
        prefs.getInt("scroll_threshold", DEFAULT_SCROLL_THRESHOLD)

    private fun getScrollWindowSeconds() =
        prefs.getInt("window_seconds", DEFAULT_WINDOW_SECONDS)

    private fun getCooldownSeconds() =
        prefs.getInt("cooldown_seconds", DEFAULT_COOLDOWN_SECONDS)

    private fun getFriendlyName(pkg: String): String = when (pkg) {
        "com.instagram.android" -> "Instagram"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
        "com.twitter.android" -> "X / Twitter"
        "com.reddit.frontpage" -> "Reddit"
        "com.google.android.youtube" -> "YouTube"
        "com.facebook.katana", "com.facebook.lite" -> "Facebook"
        "com.pinterest" -> "Pinterest"
        "com.snapchat.android" -> "Snapchat"
        "com.tumblr" -> "Tumblr"
        "com.android.chrome" -> "Chrome"
        "org.mozilla.firefox" -> "Firefox"
        else -> pkg
    }

    override fun onInterrupt() {
        Log.d(TAG, "ScrollTrackerService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ScrollTrackerService destroyed")
    }
}
