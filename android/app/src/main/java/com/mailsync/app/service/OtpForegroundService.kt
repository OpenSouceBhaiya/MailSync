package com.mailsync.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mailsync.app.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * OtpForegroundService
 *
 * This service exists ONLY to satisfy Android's requirement that
 * startForegroundService() is followed by startForeground() within 5 seconds.
 * The actual OTP polling is handled by OtpNotificationListenerService.
 *
 * It starts, shows a silent foreground notification, updates the lastActiveTimeMs
 * timestamp (which controls the polling window in OtpNotificationListenerService),
 * and then stops itself after 1 second.
 */
class OtpForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val CHANNEL_ID = "otp_sync_channel"
        private const val NOTIFICATION_ID = 1
        @Volatile var lastActiveTimeMs: Long = System.currentTimeMillis()
        @Volatile var isRunning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // MUST call startForeground() within 5 seconds of startForegroundService()
        // Failure to do so causes ForegroundServiceDidNotStartInTimeException on Android 14+
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d("OtpForegroundService", "Service started with foreground notification")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Update the active timestamp
        lastActiveTimeMs = System.currentTimeMillis()
        Log.d("OtpForegroundService", "Timestamp refreshed: $lastActiveTimeMs")

        // We DO NOT call stopSelf() here anymore. We must remain alive as a true 
        // Foreground Service so that Android OS Doze Mode does not freeze the 
        // network polling coroutines in OtpNotificationListenerService after 15 minutes.

        return START_STICKY // Keep service running in background until explicitly stopped
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MailSync")
            .setContentText("OTP sync is active in background")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OTP Sync Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent background sync indicator"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        Log.d("OtpForegroundService", "Service stopped")
    }
}
