package com.mailsync.app.utils

object OtpCache {
    var lastCopiedCode: String? = null
    var lastCopiedTime: Long = 0

    @Synchronized
    fun shouldCopy(code: String): Boolean {
        val sanitizedCode = code.replace("\\s".toRegex(), "")
        val now = System.currentTimeMillis()
        if (sanitizedCode == lastCopiedCode && (now - lastCopiedTime) < 60000) {
            return false // Skip if exact same code was copied in last 60 seconds
        }
        lastCopiedCode = sanitizedCode
        lastCopiedTime = now
        return true
    }
}
