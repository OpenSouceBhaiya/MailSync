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
    
    private fun listenForActiveLogins() {
        val firebaseManager = com.mailsync.app.data.FirebaseManager()
        loginStateListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                var active = false
                val trackedUuids = settingsManager.getLinkedDevicesMetadata().map { it.id }
                for (uuid in trackedUuids) {
                    val deviceSnap = snapshot.child(uuid)
                    if (deviceSnap.child("pcLoginActive").exists()) {
                        active = true
                        break
                    }
                }
                isAnyPcLoginActive = active
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        firebaseManager.devicesRef.addValueEventListener(loginStateListener!!)
    }
    
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
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OtpNotification", "Error in backend polling loop", e)
                }
                
                // Dynamic Ultra-Low Latency:
                // 1.5s if PC is actively on a login page (fastest mode)
                // 2.5s if Always On is enabled
                // 4s idle mode — still fast enough for real-time OTP delivery
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
        val fullText = "$text $bigText".trim()
        
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
                AppDatabase.insertMutex.withLock {
                    val existingByCode = db.otpDao().getOtpByCodeRecent(extractedOtp.code, System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
                    
                    if (existingByCode != null) {
                        // Backend already captured it! Leave the sender name alone because the backend name is more accurate.
                        // We do not overwrite it. We just skip inserting so we don't duplicate.
                    } else {
                        isNewInsertion = true
                        val entityId = UUID.randomUUID().toString()
                        val finalAccount = if (receivingEmail.isNotBlank()) receivingEmail else {
                            val firstEnabled = connectedAccounts.firstOrNull { acc -> !disabledAccounts.any { it.equals(acc, ignoreCase = true) } }
                            if (firstEnabled != null) firstEnabled else "Notification"
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
                    if (isNewInsertion) {
                        // Broadcast to linked PCs via Firebase
                        val firebaseManager = com.mailsync.app.data.FirebaseManager()
                        val keys = settingsManager.getAllLinkedDeviceKeys()
                        if (keys.isNotEmpty()) {
                            firebaseManager.broadcastOtp(extractedOtp.code, senderName, keys)
                        }
                    }
                }
                
                // Use TransparentClipboardActivity to bypass Android 10+ background clipboard restrictions
                // ONLY copy if this was a brand new insertion. If it already existed, the backend already copied it.
                if (isNewInsertion && com.mailsync.app.utils.OtpCache.shouldCopy(extractedOtp.code)) {
                    try {
                        val intent = android.content.Intent(this@OtpNotificationListenerService, com.mailsync.app.ui.TransparentClipboardActivity::class.java).apply {
                            putExtra("EXTRA_OTP_CODE", extractedOtp.code)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("OtpNotification", "Failed to launch TransparentClipboardActivity", e)
                    }
                } else {
                    Log.d("OtpNotification", "Skipped duplicate clipboard copy for ${extractedOtp.code}")
                }
            }
        }
    }
}
