package com.mailsync.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otps")
data class OtpEntity(
    @PrimaryKey val id: String,
    val code: String,
    val sender: String,
    val subject: String,
    val account: String,
    val receivedAt: Long,
    val expiresAt: Long? = null,
    val isUsed: Boolean = false
)

