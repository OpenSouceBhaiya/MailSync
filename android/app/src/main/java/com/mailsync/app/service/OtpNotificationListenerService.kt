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
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class OtpNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        Log.d("OtpNotification", "Notification Listener Service Created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("OtpNotification", "Notification Listener Service Destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!settingsManager.isSyncEnabled()) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val fullText = when {
            bigText.isBlank() || bigText == text -> text
            text.isBlank() -> bigText
            bigText.startsWith(text) -> bigText
            else -> "$text $bigText".trim()
        }

        val systemPackages = setOf("android", "com.android.systemui", "com.android.settings",
            "com.android.packageinstaller", applicationContext.packageName)
        if (packageName in systemPackages) return
        if (fullText.isBlank() && title.isBlank()) return

        val emailTimeMs = sbn.notification.`when`
        val currentTime = System.currentTimeMillis()
        val actualTime = if (emailTimeMs > 0) emailTimeMs else sbn.postTime
        if (currentTime - actualTime > 300_000) return

        val senderName = title.takeIf { it.isNotBlank() } ?: packageName

        val extractedOtp = OtpExtractor.extractOtp(
            subject = senderName,
            bodyText = fullText,
            bodyHtml = null,
            receivedTimeMs = System.currentTimeMillis()
        )

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val appName = resolveSourceLabel(packageName)
        val finalAccountName = if (subText.isNotBlank()) {
            "$subText ($appName)"
        } else {
            appName
        }

        if (extractedOtp != null) {
            Log.d("OtpNotification", "Found OTP: ${extractedOtp.code} from $packageName")
            scope.launch {
                val db = AppDatabase.getDatabase(this@OtpNotificationListenerService)
                var isNewInsertion = false
                AppDatabase.insertMutex.withLock {
                    val existing = db.otpDao().getOtpByCodeRecent(extractedOtp.code, System.currentTimeMillis() - 6 * 60 * 1000L)
                    if (existing == null) {
                        isNewInsertion = true
                        db.otpDao().insertOtp(OtpEntity(
                            id = UUID.randomUUID().toString(),
                            code = extractedOtp.code,
                            sender = senderName,
                            subject = senderName,
                            account = finalAccountName,
                            receivedAt = System.currentTimeMillis(),
                            expiresAt = extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
                            sourcePackage = packageName
                        ))
                    }
                }

                if (isNewInsertion) {
                    kotlinx.coroutines.coroutineScope {
                        val firebaseManager = com.mailsync.app.data.FirebaseManager()
                        val keys = settingsManager.getAllLinkedDeviceKeys()
                        keys.forEach { (uuid, keyBase64) ->
                            launch {
                                val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                firebaseManager.broadcastOtp(
                                    extractedOtp.code, senderName,
                                    mapOf(uuid to keyBase64),
                                    extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
                                    pm.isInteractive
                                )
                            }
                        }
                    }
                    if (settingsManager.isClipboardCopyEnabled() && com.mailsync.app.utils.OtpCache.shouldCopy(extractedOtp.code)) {
                        try {
                            val intent = android.content.Intent(this@OtpNotificationListenerService, com.mailsync.app.ui.TransparentClipboardActivity::class.java).apply {
                                putExtra("EXTRA_OTP_CODE", extractedOtp.code)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            }
                            startActivity(intent)
                        } catch (e: Exception) { Log.e("OtpNotification", "Clipboard failed", e) }
                    }
                }
            }
        }
    }

    private fun resolveSourceLabel(pkg: String): String = when {
        pkg.contains("whatsapp") -> "WhatsApp"
        pkg.contains("telegram") -> "Telegram"
        pkg.contains("com.google.android.gm") -> "Gmail"
        pkg.contains("mms") || pkg.contains("messaging") || pkg.contains(".sms") -> "SMS"
        pkg.contains("jio") -> "Jio SMS"
        pkg.contains("paytm") -> "Paytm"
        pkg.contains("phonepe") -> "PhonePe"
        pkg.contains("gpay") || pkg.contains("tez") -> "Google Pay"
        pkg.contains("amazon") -> "Amazon"
        pkg.contains("hdfc") -> "HDFC Bank"
        pkg.contains("icici") -> "ICICI Bank"
        pkg.contains("sbi") -> "SBI"
        pkg.contains("axis") -> "Axis Bank"
        pkg.contains("kotak") -> "Kotak Bank"
        pkg.contains("airtel") -> "Airtel"
        pkg.contains("vi.") || pkg.contains("vodafone") -> "Vi"
        else -> try {
            val info = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) { pkg.substringAfterLast(".") }
    }
}

