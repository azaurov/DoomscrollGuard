# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

DoomscrollGuard — an Android app that uses an `AccessibilityService` to count scroll events in social media apps. When scroll count exceeds a threshold within a time window, it shows a sliding "Peter Griffin" overlay, plays a voice clip, and fires a notification. If the user has notification mirroring enabled in the Zepp app, the notification also vibrates their Amazfit GTR 3 watch. No content is ever read — only scroll event counts. Fully local/offline, no server.

See `README.md` for user-facing setup instructions (Zepp app notification mirroring, sensitivity settings).

## Build / run

Open in Android Studio and use Run, or from the CLI:

```
./gradlew assembleDebug      # build debug APK
./gradlew installDebug       # build + install to connected device
```

There are no unit/instrumentation tests in this repo currently (`androidx.test`/JUnit deps are present in `build.gradle` but no test sources exist).

## Source layout

The compiled source lives under the standard Gradle/Kotlin layout:

- `src/main/kotlin/com/doomscrollguard/` — `MainActivity.kt`, `ScrollTrackerService.kt`, `BootReceiver.kt` (real, compiled source)
- `src/main/AndroidManifest.xml` — real, compiled manifest
- `res/` — real resources (`build.gradle` sets `sourceSets.main.res.srcDirs = ['res']`, overriding the Gradle default of `src/main/res`)

## Architecture

Three components wired together via `src/main/AndroidManifest.xml`:

1. **`ScrollTrackerService`** (`AccessibilityService`) — the core of the app. Listens for `TYPE_VIEW_SCROLLED` events, but only for packages in `TRACKED_PACKAGES` (Instagram, TikTok, X, Reddit, YouTube, Facebook, Pinterest, Snapchat, Tumblr, LinkedIn). Maintains a rolling `ArrayDeque<Long>` of scroll timestamps per foreign-app session; clears it whenever `TYPE_WINDOW_STATE_CHANGED` reports a different foreground package. When the count within the configured time window hits the threshold (and cooldown has elapsed), it triggers the "Peter alert": a `SYSTEM_ALERT_WINDOW` overlay (`overlay_peter.xml`) animated across the screen via `WindowManager` + `ValueAnimator`, plus a `MediaPlayer` voice clip (`res/raw/peter_voice.mp3`) and a high-priority notification. Requires the user to grant the Accessibility permission and (for the overlay) `SYSTEM_ALERT_WINDOW`/"draw over other apps" permission.
2. **`MainActivity`** — settings UI (view binding + Material sliders/switch) for `scroll_threshold`, `window_seconds`, `cooldown_seconds`, and tracking on/off; also surfaces buttons to jump to Accessibility settings and overlay-permission settings, and shows live enabled/disabled status.
3. **`BootReceiver`** — `BOOT_COMPLETED` receiver (accessibility services normally re-enable automatically on reboot once granted; this exists as a fallback hook).

All three components read/write the same `SharedPreferences` file (`"doomscroll_prefs"`), which is the sole state-sharing mechanism between the UI and the background service — there is no other IPC.

Defaults live as `companion object` constants in `ScrollTrackerService` (`DEFAULT_SCROLL_THRESHOLD`, `DEFAULT_WINDOW_SECONDS`, `DEFAULT_COOLDOWN_SECONDS`) and are read through `prefs.getInt(...)` with those defaults as fallback — `MainActivity` reads the same constants to initialize its sliders, so keep them in sync.

To track an additional app, add its package name to `TRACKED_PACKAGES` in `ScrollTrackerService.kt`.

Note: `onAccessibilityEvent`'s `when` block exhaustively lists every `AccessibilityEvent` type; all branches other than `TYPE_WINDOW_STATE_CHANGED` and `TYPE_VIEW_SCROLLED` are `TODO()` stubs that will crash if ever hit (they aren't, in practice, because `accessibility_service_config.xml` restricts which event types are delivered).
