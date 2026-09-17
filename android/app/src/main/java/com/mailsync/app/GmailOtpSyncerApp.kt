package com.mailsync.app

import android.app.Application
import android.util.Log

/**
 * Global application class. Runs once when the app process starts.
 * We'll use this later to initialize WorkManager for background polling.
 */
class GmailOtpSyncerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Example of guarded logging: we don't log sensitive info, 
        // but it's safe to log app lifecycle events.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Application started")
        }
    }
    
    companion object {
        private const val TAG = "GmailOtpSyncerApp"
    }
}
