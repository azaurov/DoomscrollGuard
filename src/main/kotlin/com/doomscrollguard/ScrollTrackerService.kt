package com.doomscrollguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ScrollTrackerService : AccessibilityService() {

    companion object {
        const val TAG = "DoomscrollGuard"
        const val CHANNEL_ID = "doomscroll_alert"
        const val NOTIF_ID = 1001

        const val DEFAULT_SCROLL_THRESHOLD = 1000
        const val DEFAULT_WINDOW_SECONDS = 3600
        const val DEFAULT_COOLDOWN_SECONDS = 120

        val TRACKED_PACKAGES = setOf(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.twitter.android", "com.reddit.frontpage", "com.google.android.youtube",
            "com.facebook.katana", "com.facebook.lite", "com.pinterest",
            "com.snapchat.android", "com.tumblr", "com.linkedin.android"
        )
    }

    private lateinit var prefs: SharedPreferences
    private val scrollTimestamps = ArrayDeque<Long>()
    private var currentPackage: String = ""
    private var lastAlertTime: Long = 0L
    private var isTracking: Boolean = false

    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private var mediaPlayer: MediaPlayer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        isTracking = prefs.getBoolean("tracking_enabled", true)
        Log.d(TAG, "ScrollTrackerService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isTracking || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentPackage) {
                    currentPackage = pkg
                    scrollTimestamps.clear()
                    removeOverlay()
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg !in TRACKED_PACKAGES) return

                val now = System.currentTimeMillis()
                val windowMs = getScrollWindowSeconds() * 1000L
                val threshold = getScrollThreshold()
                val cooldownMs = getCooldownSeconds() * 1000L

                scrollTimestamps.addLast(now)
                while (scrollTimestamps.isNotEmpty() && now - scrollTimestamps.first() > windowMs) {
                    scrollTimestamps.removeFirst()
                }

                if (scrollTimestamps.size >= threshold) {
                    if (now - lastAlertTime > cooldownMs) {
                        lastAlertTime = now
                        scrollTimestamps.clear()
                        triggerPeterAlert(pkg)
                    }
                }
            }
        }
    }

    private fun triggerPeterAlert(packageName: String) {
        val appName = getFriendlyName(packageName)
        sendNotification(appName)
        
        if (Settings.canDrawOverlays(this)) {
            showPeterOverlay()
            playPeterVoice()
        }
    }

    private fun playPeterVoice() {
        try {
            // Stop any existing playback
            mediaPlayer?.release()
            
            // Note: This expects a file at res/raw/peter_voice.mp3 (or .ogg)
            mediaPlayer = MediaPlayer.create(this, R.raw.peter_voice)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play Peter's voice. Did you add res/raw/peter_voice.mp3?", e)
        }
    }

    private fun showPeterOverlay() {
        Handler(Looper.getMainLooper()).post {
            removeOverlay()

            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_peter, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(overlayView, params)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    removeOverlay()
                }, 4000) // Slightly longer to match the voice line
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay", e)
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.windowToken != null) {
                windowManager.removeView(it)
            }
            overlayView = null
        }
    }

    private fun sendNotification(appName: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Peter Griffin says:")
            .setContentText("Stop scrolling already! Jeez!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "No notification permission")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Doomscroll Alerts")
            .setDescription("Peter Griffin alerts")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun getScrollThreshold() = prefs.getInt("scroll_threshold", DEFAULT_SCROLL_THRESHOLD)
    private fun getScrollWindowSeconds() = prefs.getInt("window_seconds", DEFAULT_WINDOW_SECONDS)
    private fun getCooldownSeconds() = prefs.getInt("cooldown_seconds", DEFAULT_COOLDOWN_SECONDS)

    private fun getFriendlyName(pkg: String): String = when (pkg) {
        "com.instagram.android" -> "Instagram"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
        "com.twitter.android" -> "X"
        "com.reddit.frontpage" -> "Reddit"
        "com.google.android.youtube" -> "YouTube"
        else -> "this app"
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
