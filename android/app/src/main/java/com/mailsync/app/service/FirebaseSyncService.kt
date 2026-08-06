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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mailsync.app.data.SettingsManager
import com.mailsync.app.data.OtpRepository
import com.mailsync.app.data.FirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
class FirebaseSyncService : Service() {

    private lateinit var settingsManager: SettingsManager
    private val pcLoginListeners = mutableMapOf<String, ValueEventListener>()
    private var databaseRef = FirebaseDatabase.getInstance("https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app").reference
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val CHANNEL_ID = "firebase_sync_channel"
        private const val NOTIFICATION_ID = 2
        var isRunning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settingsManager = SettingsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningToFirebase()
        return START_STICKY
    }

    private fun startListeningToFirebase() {
        val uuids = settingsManager.getLinkedDevicesMetadata().map { it.id }
        if (uuids.isEmpty()) {
            stopSelf()
            return
        }

        for (uuid in uuids) {
            if (pcLoginListeners.containsKey(uuid)) continue

            val ref = databaseRef.child("devices").child(uuid).child("pcLoginActive")
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val active = snapshot.child("active").getValue(Boolean::class.java) ?: false
                        if (active) {
                            Log.d("FirebaseSyncService", "PC Login Detected! Waking up app...")
                            triggerManualScan()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseSyncService", "Firebase listener cancelled", error.toException())
                }
            }
            
            ref.addValueEventListener(listener)
            pcLoginListeners[uuid] = listener
        }
    }

    private fun triggerManualScan() {
        serviceScope.launch {
            try {
                val firebaseManager = FirebaseManager()
                val repository = OtpRepository(this@FirebaseSyncService, settingsManager, firebaseManager)
                repository.syncWithBackend()
            } catch (e: Exception) {
                Log.e("FirebaseSyncService", "Manual scan failed", e)
            }
        }
    }


    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MailSync Always-On")
            .setContentText("Listening for PC login requests")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Always-On Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Maintains connection to PC"
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
        for ((uuid, listener) in pcLoginListeners) {
            databaseRef.child("devices").child(uuid).child("pcLoginActive").removeEventListener(listener)
        }
        pcLoginListeners.clear()
    }
}
