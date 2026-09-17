package com.mailsync.app.service

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceHelper {
    fun startForegroundService(context: Context) {
        // Disabled completely. 
        // We will now run exclusively via the system-bound NotificationListenerService
        // and rely on battery optimization exemptions to bypass Doze mode invisibly.
    }

    fun stopForegroundService(context: Context) {
        // No-op. We no longer use a foreground service.
    }
}
