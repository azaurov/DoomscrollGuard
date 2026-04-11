package com.doomscrollguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("DoomscrollGuard", "Boot completed - service will be available when user enables it")
            // Accessibility services don't need to be manually started after boot
            // but we can perform any necessary initialization here if needed.
        }
    }
}
