# ⌚ DoomscrollGuard — Amazfit GTR 3 + Android

Vibrates your Amazfit GTR 3 when you've been doomscrolling on your phone.

---

## How it works

1. An **AccessibilityService** on your Android phone counts scroll events on social media apps
2. When you scroll past the threshold within the time window → app fires a notification
3. The **Zepp app** mirrors the notification to your GTR 3 → **watch vibrates**

No content is ever read — only scroll event *counts*.

---

## Build Instructions

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android phone running Android 8.0+ (API 26+)
- Amazfit GTR 3 paired via Zepp app

### Steps

1. **Open project in Android Studio**
   - File → Open → select the `DoomscrollGuard` folder

2. **Build & install**
   - Connect your Android phone via USB (enable developer mode + USB debugging)
   - Click ▶ Run (or `Shift+F10`)
   - Or: Build → Build Bundle(s)/APK(s) → Build APK(s) → sideload the APK

3. **Grant Accessibility permission** (required)
   - Open DoomscrollGuard
   - Tap **"Enable Accessibility Permission"**
   - Find **DoomscrollGuard** in the list and toggle ON
   - This is what lets the app count scrolls

4. **Grant Notification permission**
   - Android 13+ will prompt automatically on first launch

---

## Zepp App Configuration (Critical!)

For the watch to vibrate, Zepp must forward DoomscrollGuard notifications:

1. Open **Zepp** app on your phone
2. Go to: **Profile → GTR 3 → Notifications**
3. Enable **App Alerts**
4. Scroll down and make sure **DoomscrollGuard** is toggled ON

> The watch will vibrate and show the alert text whenever doomscrolling is detected.

---

## Settings

| Setting | Default | Description |
|---|---|---|
| Scroll trigger | 40 scrolls | Number of scroll events to count |
| Within | 60 seconds | Time window to count them in |
| Alert cooldown | 120 seconds | Minimum time between alerts |

**Sensitivity guide:**
- **Very sensitive**: 20 scrolls / 30 sec window
- **Default (recommended)**: 40 scrolls / 60 sec window
- **Relaxed**: 60 scrolls / 120 sec window

---

## Monitored Apps

Instagram, TikTok, X/Twitter, Reddit, YouTube, Facebook, Pinterest, Snapchat, Tumblr, LinkedIn

To add a custom app, edit `TRACKED_PACKAGES` in `ScrollTrackerService.kt` and add its package name.
You can find any app's package name at: `https://play.google.com/store/apps/details?id=<package_name>`

---

## Privacy

- The AccessibilityService only receives scroll event *counts* — **no text, images, or content**
- Nothing is sent to any server — fully local, offline app
- Source code is fully auditable above

---

## Troubleshooting

**Watch isn't vibrating:**
- Make sure Zepp notification mirroring is enabled for DoomscrollGuard (see setup step 4)
- Check that notifications are not silenced on your phone for this app
- Confirm the Accessibility service is still enabled (Settings → Accessibility)

**Too many / too few alerts:**
- Adjust sliders in the app — lower scroll count = more sensitive

**Service stops after phone restart:**
- Android AccessibilityServices survive reboots automatically once granted
- If it stops, simply re-toggle in Accessibility settings
