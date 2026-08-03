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
        
        saveErrorToPrefs(context, "${errorCode.code}: ${e.message}")
        copyToClipboard(context, "${errorCode.code}: ${e.message}")
        Toast.makeText(context, "Error copied. Please report this bug.", Toast.LENGTH_LONG).show()
    }

    fun reportApiException(context: Context, statusCode: Int, tag: String = "MailSyncError") {
        // Handle known user-recoverable codes with friendly messages — no error copy needed
        when (statusCode) {
            12501 -> {
                Toast.makeText(context, "Sign-in cancelled. Please select a Google Account to continue.", Toast.LENGTH_LONG).show()
                return
            }
            7 -> {
                // NETWORK_ERROR: Google Play Services lost connection mid-handshake
                Toast.makeText(context, "Connection lost during sign-in. Please check your internet and try again.", Toast.LENGTH_LONG).show()
                Log.e(tag, "Google Sign-In NETWORK_ERROR (status 7)")
                return
            }
            12500 -> {
                Toast.makeText(context, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                Log.e(tag, "Google Sign-In SIGN_IN_FAILED (status 12500)")
                return
            }
        }
        
        val errorCode = when (statusCode) {
            10 -> ErrorCode.AUTH_INVALID
            else -> ErrorCode.UNKNOWN
        }
        val fullMessage = "Error: ${errorCode.code} - ${errorCode.message} (Code $statusCode)"
        Log.e(tag, fullMessage)
        
        saveErrorToPrefs(context, "${errorCode.code}: API Status $statusCode")
        copyToClipboard(context, "${errorCode.code}: API Status $statusCode")
        Toast.makeText(context, "Error copied. Please report this bug.", Toast.LENGTH_LONG).show()
    }

    private fun saveErrorToPrefs(context: Context, errorMessage: String) {
        val prefs = context.getSharedPreferences("otp_sync_prefs", Context.MODE_PRIVATE)
        val errorsJson = prefs.getString("system_errors", "[]") ?: "[]"
        try {
            val errorsList = org.json.JSONArray(errorsJson)
            val newErrorsList = org.json.JSONArray()
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            newErrorsList.put("[$timestamp] $errorMessage")
            
            val maxErrors = 5
            for (i in 0 until Math.min(errorsList.length(), maxErrors - 1)) {
                newErrorsList.put(errorsList.getString(i))
            }
            prefs.edit().putString("system_errors", newErrorsList.toString()).apply()
            // Push error to Chrome Extension via Firebase
            val settingsManager = com.mailsync.app.data.SettingsManager(context)
            val keys = settingsManager.getAllLinkedDeviceKeys()
            if (keys.isNotEmpty()) {
                val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("devices")
                for (entry in keys) {
                    dbRef.child(entry.key).child("app_error").setValue("[$timestamp] $errorMessage")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearErrors(context: Context) {
        val prefs = context.getSharedPreferences("otp_sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("system_errors").apply()
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
