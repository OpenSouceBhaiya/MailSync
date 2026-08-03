package com.mailsync.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.math.max

object AppSession {
    val startTime: Long = System.currentTimeMillis()
}

class OtpRepository(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val firebaseManager: FirebaseManager
) {
    private val db = AppDatabase.getDatabase(context)
    private val otpDao = db.otpDao()
    
    private val gmailApiClient = GmailApiClient(context)

    // Removed logEvent
    suspend fun syncWithBackend(): List<OtpEntity> = withContext(Dispatchers.IO) {
        val allAccounts = settingsManager.getConnectedAccounts()
        val disabledAccounts = settingsManager.getDisabledSyncAccounts()
        val emails = allAccounts.filter { it !in disabledAccounts }
        
        if (allAccounts.isEmpty()) throw IllegalStateException("No Google Accounts connected")

        val freshlyFetchedOtps = mutableListOf<OtpEntity>()

        try {
            // Fetch OTPs from the last 5 minutes. We rely on the database to ignore duplicates.
            val globalSinceMs = maxOf(
                System.currentTimeMillis() - (5 * 60 * 1000),
                settingsManager.getInstantSyncEnabledTime()
            )

            val deferreds = emails.mapIndexed { index, email ->
                async {
                    if (emails.size >= 4) {
                        kotlinx.coroutines.delay(index * 200L)
                    }
                    val localFetchedOtps = mutableListOf<OtpEntity>()
                    try {
                        val enabledTime = settingsManager.getAccountEnabledTime(email)
                        val accountSinceMs = maxOf(globalSinceMs, enabledTime)
                        
                        val authHelper = com.mailsync.app.data.GoogleAuthHelper(context)
                        
                        // 1. Check for valid cached Access Token first (prevents spamming Google token endpoint every 2s)
                        var accessToken = settingsManager.getAccessToken(email)
                        
                        // 2. If Access Token is expired or missing, try Refresh Token
                        if (accessToken == null) {
                            val refreshToken = settingsManager.getRefreshToken(email)
                            if (refreshToken != null) {
                                try {
                                    val tokens = authHelper.refreshAccessToken(refreshToken)
                                    if (tokens?.accessToken != null) {
                                        accessToken = tokens.accessToken
                                        settingsManager.setAccessToken(email, tokens.accessToken, tokens.expiresInSeconds)
                                        if (tokens.refreshToken != null && tokens.refreshToken != refreshToken) {
                                            settingsManager.setRefreshToken(email, tokens.refreshToken)
                                        }
                                    } else {
                                        Log.w("OtpRepository", "Token refresh returned null for $email — will retry next cycle")
                                    }
                                } catch (e: Exception) {
                                    Log.e("OtpRepository", "Token refresh exception for $email", e)
                                    val msg = e.message ?: ""
                                    if (msg.contains("401") || msg.contains("403") || msg.contains("invalid_grant")) {
                                        val prefs = context.getSharedPreferences("otp_sync_prefs", Context.MODE_PRIVATE)
                                        val revoked = prefs.getStringSet("revoked_accounts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                                        revoked.add(email)
                                        prefs.edit().putStringSet("revoked_accounts", revoked).apply()
                                    }
                                    return@async localFetchedOtps
                                }
                            }
                        }
                        
                        var newOtps = emptyList<OtpEntity>()
                        if (accessToken != null) {
                            newOtps = gmailApiClient.fetchLatestOtps(email, accessToken, accountSinceMs)
                        } else {
                            Log.w("OtpRepository", "No valid access or refresh token for $email — skipping sync")
                            return@async localFetchedOtps
                        }

                        if (newOtps.isNotEmpty()) {
                            // Collect OTPs to broadcast AFTER releasing the mutex (network calls must NOT hold the DB lock)
                            val otpsToBroadcast = mutableListOf<OtpEntity>()
                            val otpsToClipboard = mutableListOf<OtpEntity>()

                            AppDatabase.insertMutex.withLock {
                                newOtps.forEach { entity ->
                                    // Use a 2-minute window to catch duplicates between notification and Gmail API paths
                                    val existingByCode = otpDao.getOtpByCodeRecent(entity.code, System.currentTimeMillis() - 2 * 60 * 1000)
                                    val existingById = otpDao.getOtpById(entity.id)
                                    
                                    if (existingByCode != null) {
                                        // It was already captured by Notification!
                                        // OVERRIDE the notification's potentially bad sender/account name with the backend's accurate details.
                                        val updatedEntity = existingByCode.copy(
                                            sender = entity.sender,
                                            account = entity.account
                                        )
                                        otpDao.updateOtp(updatedEntity)
                                        Log.d("OtpRepository", "Gmail API: OTP ${entity.code} updated with backend metadata (${entity.account})")
                                    } else if (existingById == null) {
                                        // Safe to insert new
                                        otpDao.insertOtp(entity)
                                        localFetchedOtps.add(entity)

                                        // Only broadcast/copy if the OTP is actually new (received in the last 5 minutes)
                                        val isRecent = (System.currentTimeMillis() - entity.receivedAt) < 300000
                                        if (isRecent) {
                                            otpsToBroadcast.add(entity)
                                            otpsToClipboard.add(entity)
                                        }
                                    }
                                }
                            }
                            // Mutex released — now safe to make network calls and start activities

                            for (entity in otpsToBroadcast) {
                                val keys = settingsManager.getAllLinkedDeviceKeys()
                                if (keys.isNotEmpty()) {
                                    firebaseManager.broadcastOtp(entity.code, entity.sender, keys)
                                }
                            }

                            for (entity in otpsToClipboard) {
                                if (settingsManager.isClipboardCopyEnabled() && com.mailsync.app.utils.OtpCache.shouldCopy(entity.code)) {
                                    try {
                                        // Launch TransparentClipboardActivity to copy to clipboard (bypasses Android 10+ background clipboard limits)
                                        val intent = android.content.Intent(context, com.mailsync.app.ui.TransparentClipboardActivity::class.java).apply {
                                            putExtra("EXTRA_OTP_CODE", entity.code)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("OtpRepository", "Failed to launch TransparentClipboardActivity", e)
                                    }

                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(100)
                                    }
                                }
                            }
                        } // closes if (newOtps.isNotEmpty())
                    
                        // Mark initial sync as done for this email unconditionally after processing it
                        val prefs = context.getSharedPreferences("otp_sync_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("initial_sync_$email", true).apply()
                    } catch (e: Exception) {
                        Log.e("OtpRepository", "Failed to sync account: $email", e)
                        val prefs = context.getSharedPreferences("otp_sync_prefs", Context.MODE_PRIVATE)
                        // ONLY mark as revoked when Google's server explicitly rejected our token with 401/403.
                        // Network timeouts, null responses, and other transient errors must NOT trigger revocation.
                        val msg = e.message ?: ""
                        val isConfirmedAuthError = (msg.contains("401") || msg.contains("403"))
                            && (msg.contains("invalid_grant") || msg.contains("unauthorized_client") 
                                || msg.contains("access_denied") || msg.contains("Token has been expired"))
                        if (isConfirmedAuthError) {
                            val revoked = prefs.getStringSet("revoked_accounts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                            revoked.add(email)
                            prefs.edit().putStringSet("revoked_accounts", revoked).apply()
                        }
                    }
                    localFetchedOtps
                }
            }
            freshlyFetchedOtps.addAll(deferreds.awaitAll().flatten())
            
            // Removed logging

            return@withContext freshlyFetchedOtps
        } catch (e: java.net.UnknownHostException) {
            Log.e("OtpRepository", "Sync failed: No Internet", e)
            throw Exception("No internet connection. Please check your network and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("OtpRepository", "Sync failed: Timeout", e)
            throw Exception("Connection timed out. Please try again.")
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            Log.e("OtpRepository", "Sync failed: Auth", e)
            throw Exception("Authentication error. Please re-connect your Google Account.")
        } catch (e: Exception) {
            Log.e("OtpRepository", "Sync failed", e)
            val msg = e.message ?: ""
            if (msg.contains("rateLimitExceeded") || msg.contains("429")) {
                throw Exception("Google API rate limit exceeded. Please wait a moment.")
            } else if (msg.contains("401") || msg.contains("403")) {
                throw Exception("Access denied. Please re-connect your Google Account.")
            }
            throw e
        }
    }

    fun getAllOtps(): Flow<List<OtpEntity>> = otpDao.getAllOtps()
    fun getUnreadOtps(): Flow<List<OtpEntity>> = otpDao.getUnreadOtps()
    fun getUsedOtps(): Flow<List<OtpEntity>> = otpDao.getUsedOtps()
    
    fun getLatestOtpFlow(): Flow<OtpEntity?> = otpDao.getLatestOtpFlow()
    
    suspend fun fetchLatestOtp(): OtpEntity? = withContext(Dispatchers.IO) {
        otpDao.getLatestOtpSync()
    }

    suspend fun markAsUsed(id: String) = withContext(Dispatchers.IO) {
        val otp = otpDao.getOtpById(id)
        if (otp != null) {
            otpDao.updateOtp(otp.copy(isUsed = true))
        }
    }

    fun getTopSenders(): Flow<List<SenderCount>> = otpDao.getTopSenders()
    fun getTotalOtpsCount(): Flow<Int> = otpDao.getTotalOtpsCount()
    fun getOtpsSince(timestamp: Long): Flow<List<OtpEntity>> = otpDao.getOtpsSince(timestamp)
    
    suspend fun getSuccessRate(): Int = withContext(Dispatchers.IO) {
        val total = otpDao.getLatestOtpSync() // just a quick check if there's any otp
        if (total != null) 100 else 0
    }
}
