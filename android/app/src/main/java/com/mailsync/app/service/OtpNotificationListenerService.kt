package com.mailsync.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.mailsync.app.data.OtpExtractor
import com.mailsync.app.data.SettingsManager
import com.mailsync.app.data.AppDatabase
import com.mailsync.app.data.OtpEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

class OtpNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager
    
    // Polling job to replace ForegroundService
    private var pollingJob: kotlinx.coroutines.Job? = null
    @Volatile private var isAnyPcLoginActive = false
    private var loginStateListener: com.google.firebase.database.ValueEventListener? = null
    
    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        Log.d("OtpNotification", "Service Created")
        listenForActiveLogins()
        startPollingLoop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        loginStateListener?.let { com.mailsync.app.data.FirebaseManager().devicesRef.removeEventListener(it) }
    }
    
    private val deviceListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()

    private fun listenForActiveLogins() {
        val firebaseManager = com.mailsync.app.data.FirebaseManager()
        val trackedUuids = settingsManager.getLinkedDevicesMetadata().map { it.id }

        // Remove listeners for devices no longer tracked
        val toRemove = deviceListeners.keys.filter { it !in trackedUuids }
        for (uuid in toRemove) {
            val listener = deviceListeners.remove(uuid)
            if (listener != null) {
                firebaseManager.devicesRef.child(uuid).child("pcLoginActive").removeEventListener(listener)
            }
        }

        for (uuid in trackedUuids) {
            if (deviceListeners.containsKey(uuid)) continue

            val listener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    checkActiveLoginsStatus()
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e("OtpNotification", "Firebase listener cancelled for $uuid", error.toException())
                }
            }
            firebaseManager.devicesRef.child(uuid).child("pcLoginActive").addValueEventListener(listener)
            deviceListeners[uuid] = listener
        }
    }

    private fun checkActiveLoginsStatus() {
        val firebaseManager = com.mailsync.app.data.FirebaseManager()
        val trackedUuids = settingsManager.getLinkedDevicesMetadata().map { it.id }
        
        // Check state of all tracked devices
        var active = false
        // We can't synchronously read all devices easily without a callback, 
        // but since we know this is called when a device changes, we can just do a single read of each.
        // Actually, we can use get() to read the latest state asynchronously.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            for (uuid in trackedUuids) {
                try {
                    val snapshot = firebaseManager.devicesRef.child(uuid).child("pcLoginActive").child("active").get().await()
                    if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                        active = true
                        break
                    }
                } catch (e: Exception) { }
            }
            isAnyPcLoginActive = active
            if (active) {
                Log.d("OtpNotification", "PC Login active! Accelerating polling.")
            }
        }
    }
    
    private var lastDeepIdleSyncMs = 0L

    private fun startPollingLoop() {
        pollingJob = scope.launch {
            val firebaseManager = com.mailsync.app.data.FirebaseManager()
            val repository = com.mailsync.app.data.OtpRepository(this@OtpNotificationListenerService, settingsManager, firebaseManager)
            while (isActive) {
                try {
                    // Smart polling: Only pull hard if a PC is actively on a login page OR Always On is enabled
                    val isAlwaysOn = settingsManager.isAlwaysOnSyncEnabled()
                    val autoStopDelayMs = settingsManager.getAutoStopDelayMs()
                    val idleTimeMs = System.currentTimeMillis() - com.mailsync.app.AppState.lastActiveTimeMs
                    
                    if (settingsManager.isSyncEnabled() && settingsManager.isConfigured()) {
                        if (isAnyPcLoginActive || isAlwaysOn || idleTimeMs <= autoStopDelayMs) {
                            repository.syncWithBackend()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastDeepIdleSyncMs > 15000) {
                                repository.syncWithBackend()
                                lastDeepIdleSyncMs = now
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OtpNotification", "Error in backend polling loop", e)
                }
                
                // Dynamic Ultra-Low Latency:
                // 800ms if PC is actively on a login page (fastest mode)
                // 1500ms if Always On is enabled
                // 2000ms idle mode — still fast enough for real-time OTP delivery
                val delayTime = if (isAnyPcLoginActive) {
                    800L
                } else if (settingsManager.isAlwaysOnSyncEnabled()) {
                    1500L
                } else {
                    2000L // Never more than 2s — ensures backend OTPs arrive within 2-3s
                }
                kotlinx.coroutines.delay(delayTime)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "" // Usually Sender Name
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "" // Usually Subject/Body
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: "" // Usually receiving email address
        val fullText = if (bigText.isBlank() || bigText == text) text else if (text.isBlank()) bigText else if (bigText.startsWith(text)) bigText else "$text $bigText".trim()
        
        // Ensure sync is enabled globally
        if (!settingsManager.isSyncEnabled()) return
        
        // Ignore old notifications (older than 5 minutes)
        // Use sbn.notification.`when` because Gmail sets this to the true email receipt time.
        // If a phone is offline and reconnects, sbn.postTime is NOW, but `when` is the actual old time.
        val emailTimeMs = sbn.notification.`when`
        val currentTime = System.currentTimeMillis()
        // If `when` is 0 (some apps don't set it), fallback to postTime
        val actualTime = if (emailTimeMs > 0) emailTimeMs else sbn.postTime
        
        if (currentTime - actualTime > 300_000) {
            Log.d("OtpNotification", "Ignored old notification from ${actualTime}")
            return
        }
        
        // Extract receiving email address (e.g. from user@gmail.com)
        val receivingEmail = subText.trim()
        val connectedAccounts = settingsManager.getConnectedAccounts()
        val disabledAccounts = settingsManager.getDisabledSyncAccounts()
        
        // If an email is provided in the notification, ensure it's one we are tracking and it's not disabled.
        if (receivingEmail.isNotEmpty() && receivingEmail.contains("@")) {
            val isTracked = connectedAccounts.any { it.equals(receivingEmail, ignoreCase = true) }
            val isDisabled = disabledAccounts.any { it.equals(receivingEmail, ignoreCase = true) }
            if (!isTracked || isDisabled) {
                Log.d("OtpNotification", "Ignored OTP notification for un-tracked or disabled email: $receivingEmail")
                return
            }
        } else {
            // Fallback: If Gmail doesn't provide the subText email, don't drop it.
            // Just check if there are ANY enabled accounts. If all are disabled, ignore.
            val anyEnabled = connectedAccounts.any { acc -> !disabledAccounts.any { it.equals(acc, ignoreCase = true) } }
            if (!anyEnabled) {
                Log.d("OtpNotification", "Ignored OTP notification because all tracked accounts are disabled.")
                return
            }
        }
        
        // Try to clean up the sender name. Sometimes title is "email@domain.com", we'll just use title.
        val senderName = title.takeIf { it.isNotBlank() } ?: "Notification"

        val extractedOtp = OtpExtractor.extractOtp(
            subject = senderName,
            bodyText = fullText,
            bodyHtml = null,
            receivedTimeMs = System.currentTimeMillis()
        )
        
        if (extractedOtp != null) {
            Log.d("OtpNotification", "Found OTP via Notification: ${extractedOtp.code} from $packageName")
            scope.launch {
                val db = AppDatabase.getDatabase(this@OtpNotificationListenerService)
                
                var isNewInsertion = false
                var shouldBroadcast = false
                
                // Use mutex only for the DB operations — release it BEFORE making network calls
                AppDatabase.insertMutex.withLock {
                    // Use a narrow window (2 minutes) to catch the same OTP from both
                    // notification and Gmail API paths, but not block genuinely new same-code OTPs
                    val existingByCode = db.otpDao().getOtpByCodeRecent(
                        extractedOtp.code,
                        System.currentTimeMillis() - 2 * 60 * 1000L // 2-minute dedup window
                    )
                    
                    if (existingByCode != null) {
                        // Already in DB (either from another notification or from Gmail API polling).
                        // Do NOT re-broadcast; the first path already did it.
                        Log.d("OtpNotification", "OTP ${extractedOtp.code} already in DB, skipping duplicate")
                    } else {
                        isNewInsertion = true
                        shouldBroadcast = true
                        val entityId = UUID.randomUUID().toString()
                        val finalAccount = if (receivingEmail.isNotBlank()) receivingEmail else {
                            val firstEnabled = connectedAccounts.firstOrNull { acc -> !disabledAccounts.any { it.equals(acc, ignoreCase = true) } }
                            firstEnabled ?: "Notification"
                        }
                        db.otpDao().insertOtp(
                            OtpEntity(
                                id = entityId,
                                code = extractedOtp.code,
                                sender = senderName,
                                subject = senderName,
                                account = finalAccount,
                                receivedAt = System.currentTimeMillis(),
                                expiresAt = extractedOtp.expiresAt
                            )
                        )
                    }
                }
                // Mutex released — now safe to do network operations
                
                if (shouldBroadcast) {
                    // Broadcast to linked PCs via Firebase (outside mutex to avoid blocking DB writes)
                    val firebaseManager = com.mailsync.app.data.FirebaseManager()
                    val keys = settingsManager.getAllLinkedDeviceKeys()
                    if (keys.isNotEmpty()) {
                        firebaseManager.broadcastOtp(extractedOtp.code, senderName, keys)
                    }
                }
                
                // Use TransparentClipboardActivity to bypass Android 10+ background clipboard restrictions
                // ONLY copy if this was a brand new insertion. If it already existed, the backend already copied it.
                if (isNewInsertion && settingsManager.isClipboardCopyEnabled() && com.mailsync.app.utils.OtpCache.shouldCopy(extractedOtp.code)) {
                    try {
                        val intent = android.content.Intent(this@OtpNotificationListenerService, com.mailsync.app.ui.TransparentClipboardActivity::class.java).apply {
                            putExtra("EXTRA_OTP_CODE", extractedOtp.code)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("OtpNotification", "Failed to launch TransparentClipboardActivity", e)
                    }
                } else if (!isNewInsertion) {
                    Log.d("OtpNotification", "Skipped duplicate clipboard copy for ${extractedOtp.code}")
                }
            }
        }
    }
}
