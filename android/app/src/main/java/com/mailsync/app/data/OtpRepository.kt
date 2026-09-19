package com.mailsync.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.max

class OtpRepository(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val firebaseManager: FirebaseManager
) {
    private val db = AppDatabase.getDatabase(context)
    private val otpDao = db.otpDao()

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
