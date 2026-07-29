package com.mailsync.app.utils

import android.content.Context
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log

enum class ErrorCode(val code: String, val message: String) {
    AUTH_FAILED("AUTH-100", "Google Sign-In failed or was cancelled."),
    AUTH_INVALID("AUTH-101", "Invalid credentials or Developer Error. Share this code with support."),
    SYNC_NETWORK("SYNC-200", "Network connection failed during sync."),
    SYNC_QUOTA("SYNC-201", "API quota exceeded."),
    DB_ERROR("DB-300", "Database read/write error."),
    UNKNOWN("SYS-500", "An unexpected error occurred.")
}

object ErrorReporter {
    fun reportError(context: Context, e: Exception, tag: String = "MailSyncError") {
        val errorCode = mapExceptionToCode(e)
        val fullMessage = "Error: ${errorCode.code} - ${errorCode.message}"
        Log.e(tag, fullMessage, e)
        
        copyToClipboard(context, "${errorCode.code}: ${e.message}")
        Toast.makeText(context, "Error copied. Please report this bug.", Toast.LENGTH_LONG).show()
    }

    fun reportApiException(context: Context, statusCode: Int, tag: String = "MailSyncError") {
        if (statusCode == 12501) {
            Toast.makeText(context, "Sign-in cancelled. Please select a Google Account to continue.", Toast.LENGTH_LONG).show()
            return
        }
        
        val errorCode = when (statusCode) {
            10 -> ErrorCode.AUTH_INVALID
            else -> ErrorCode.UNKNOWN
        }
        val fullMessage = "Error: ${errorCode.code} - ${errorCode.message} (Code $statusCode)"
        Log.e(tag, fullMessage)
        
        copyToClipboard(context, "${errorCode.code}: API Status $statusCode")
        Toast.makeText(context, "Error copied. Please report this bug.", Toast.LENGTH_LONG).show()
    }

    private fun mapExceptionToCode(e: Exception): ErrorCode {
        return when {
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.io.IOException -> ErrorCode.SYNC_NETWORK
            e.message?.contains("quota", ignoreCase = true) == true -> ErrorCode.SYNC_QUOTA
            e is android.database.sqlite.SQLiteException -> ErrorCode.DB_ERROR
            else -> ErrorCode.UNKNOWN
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MailSync Error", text)
        clipboard.setPrimaryClip(clip)
    }
}
