package com.mailsync.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpDao {
    @Query("SELECT * FROM otps ORDER BY receivedAt DESC")
    fun getAllOtps(): Flow<List<OtpEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOtp(otp: OtpEntity): Long

    @Query("SELECT * FROM otps WHERE isUsed = 0 ORDER BY receivedAt DESC")
    fun getUnreadOtps(): Flow<List<OtpEntity>>

    @Query("SELECT * FROM otps WHERE isUsed = 1 ORDER BY receivedAt DESC")
    fun getUsedOtps(): Flow<List<OtpEntity>>

    @Query("SELECT * FROM otps ORDER BY receivedAt DESC LIMIT 1")
    fun getLatestOtpFlow(): Flow<OtpEntity?>

    @Query("SELECT * FROM otps ORDER BY receivedAt DESC LIMIT 1")
    fun getLatestOtpSync(): OtpEntity?

    @Query("SELECT * FROM otps WHERE id = :id")
    suspend fun getOtpById(id: String): OtpEntity?

    @Query("SELECT * FROM otps WHERE code = :code AND receivedAt >= :sinceTimestamp ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getOtpByCodeRecent(code: String, sinceTimestamp: Long): OtpEntity?

    @Update
    suspend fun updateOtp(otp: OtpEntity)

    @Query("UPDATE otps SET isUsed = 1 WHERE id = :id")
    suspend fun markAsUsed(id: String)
    
    @Query("SELECT COUNT(*) FROM otps")
    fun getTotalOtpsCount(): Flow<Int>

    @Query("SELECT sender, COUNT(*) as count FROM otps GROUP BY sender ORDER BY count DESC LIMIT 5")
    fun getTopSenders(): Flow<List<SenderCount>>

    @Query("SELECT * FROM otps WHERE receivedAt >= :sinceTimestamp")
    fun getOtpsSince(sinceTimestamp: Long): Flow<List<OtpEntity>>
}

data class SenderCount(
    val sender: String,
    val count: Int
)

