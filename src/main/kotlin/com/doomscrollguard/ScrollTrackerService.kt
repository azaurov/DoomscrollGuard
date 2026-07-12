package com.doomscrollguard

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

@SuppressLint("AccessibilityPolicy")
class ScrollTrackerService : AccessibilityService() {

    companion object {
        const val TAG = "DoomscrollGuard"
        const val CHANNEL_ID = "doomscroll_alert"
        const val NOTIF_ID = 1001
        const val STATUS_CHANNEL_ID = "doomscroll_status"
        const val STATUS_NOTIF_ID = 1002

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
    private val overlayHandler = Handler(Looper.getMainLooper())
    private var overlayTimeoutRunnable: Runnable? = null
    private var isOverlayShowing: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("doomscroll_prefs", MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        createStatusNotificationChannel()
        startAsForegroundService()
        isTracking = prefs.getBoolean("tracking_enabled", true)
        Log.d(TAG, "ScrollTrackerService connected.")
    }

    // Running as a foreground service (with this persistent low-priority notification)
    // gives the process much stronger protection from OS/OEM background-process freezing
    // (e.g. Samsung's Freecess) than a bare bound AccessibilityService gets on its own.
    // Without it, the process can be frozen mid-overlay-animation, leaving the Peter
    // overlay stuck on screen forever since its cleanup callbacks never get to run.
    private fun startAsForegroundService() {
        val notification = NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DoomscrollGuard is watching")
            .setContentText("Tracking scroll activity in the background")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                STATUS_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(STATUS_NOTIF_ID, notification)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isTracking || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                // The Peter overlay window itself can generate a WINDOW_STATE_CHANGED
                // event attributed to our own package. Without this guard, showing the
                // overlay while some other app is currentPackage immediately triggers
                // "app switched" cleanup and tears the overlay down mid-animation.
                if (pkg == packageName) return
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
                        triggerPeterAlert()
                    }
                }
            }

            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                TODO()
            }

            AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> {
                TODO()
            }

            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                TODO()
            }

            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                TODO()
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_SPEECH_STATE_CHANGE -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TARGETED_BY_SCROLL -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> {
                TODO()
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                TODO()
            }
        }
    }

    private fun triggerPeterAlert() {
        if (isOverlayShowing) return
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

    @SuppressLint("InflateParams")
    private fun showPeterOverlay() {
        isOverlayShowing = true
        overlayHandler.post {
            detachOverlayView()

            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_peter, null)
            val peterImage = overlayView?.findViewById<ImageView>(R.id.peter_image)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            // Get screen dimensions
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Position Peter off-screen to the right
            params.gravity = Gravity.TOP or Gravity.START
            params.x = screenWidth // Start off-screen to the right
            params.y = (screenHeight * 0.3f).toInt() // 30% from top

            windowManager.addView(overlayView, params)

            // Safety net: if the animator callback never fires (e.g. this process gets
            // frozen mid-animation by OS/OEM background-process management), the overlay
            // window would otherwise stay attached forever since it's owned by the system
            // compositor, not this process. Force it down after a generous timeout.
            overlayTimeoutRunnable?.let { overlayHandler.removeCallbacks(it) }
            val timeoutRunnable = Runnable {
                removeOverlay()
                NotificationManagerCompat.from(this@ScrollTrackerService).cancel(NOTIF_ID)
            }
            overlayTimeoutRunnable = timeoutRunnable
            overlayHandler.postDelayed(timeoutRunnable, 6000L)

            // Defer animator setup until after layout so peterImage.width is measured.
            overlayView?.post {
                val animator = ValueAnimator.ofFloat(
                    screenWidth.toFloat(),
                    -(peterImage?.width?.takeIf { it > 0 }?.toFloat() ?: 300f)
                )
                animator.duration = 3000L // 3 seconds for smooth glide
                animator.addUpdateListener { animation ->
                    val x = animation.animatedValue as Float
                    params.x = x.toInt()
                    if (overlayView != null && overlayView?.windowToken != null) {
                        try {
                            windowManager.updateViewLayout(overlayView, params)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "Overlay view no longer attached", e)
                        }
                    }
                }

                animator.start()

                // Remove overlay after animation completes
                animator.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        removeOverlay()
                        NotificationManagerCompat.from(this@ScrollTrackerService).cancel(NOTIF_ID)
                    }
                    override fun onAnimationCancel(animation: Animator) {}
                })
            }
        }
    }

    /** Fully dismisses the overlay: tears down the view and clears the "showing" flag. */
    private fun removeOverlay() {
        detachOverlayView()
        isOverlayShowing = false
    }

    /** Tears down whatever overlay view is currently attached, without clearing
     *  isOverlayShowing -- used when swapping in a fresh view for a new alert. */
    private fun detachOverlayView() {
        overlayTimeoutRunnable?.let { overlayHandler.removeCallbacks(it) }
        overlayTimeoutRunnable = null
        overlayView?.let {
            if (it.windowToken != null) {
                try {
                    windowManager.removeView(it)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Overlay view already detached", e)
                }
            }
            overlayView = null
        }
    }

    private fun sendNotification() {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Peter Griffin says:")
            .setContentText("Stop scrolling already! Jeez!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
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

    private fun createStatusNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(STATUS_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName("DoomscrollGuard Status")
            .setDescription("Persistent notification that keeps scroll tracking running reliably")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun getScrollThreshold() = prefs.getInt("scroll_threshold", DEFAULT_SCROLL_THRESHOLD)
    private fun getScrollWindowSeconds() = prefs.getInt("window_seconds", DEFAULT_WINDOW_SECONDS)
    private fun getCooldownSeconds() = prefs.getInt("cooldown_seconds", DEFAULT_COOLDOWN_SECONDS)

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        removeOverlay()
        mediaPlayer?.release()
    }
}
