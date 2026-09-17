package com.mailsync.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mailsync.app.data.SettingsManager

class BackgroundScanActivity : Activity() {
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = SettingsManager(this)

        if (!settingsManager.isConfigured()) {
            com.mailsync.app.utils.ToastManager.show(this, "⚠️ Please setup accounts first", android.widget.Toast.LENGTH_SHORT)
            finish()
            return
        }

        if (!settingsManager.isSyncEnabled()) {
            com.mailsync.app.utils.ToastManager.show(this, "⚠️ Sync is disabled. Enable it in MailSync.", android.widget.Toast.LENGTH_SHORT)
            finish()
            return
        }

        if (!isInternetAvailable()) {
            com.mailsync.app.utils.ToastManager.show(this, "No internet connection available.", android.widget.Toast.LENGTH_LONG)
            finish()
            return
        }

        // Refresh the active timestamp regardless — this wakes up the polling loop in NotificationListenerService
        com.mailsync.app.AppState.lastActiveTimeMs = System.currentTimeMillis()

        com.mailsync.app.utils.ToastManager.show(this, "✅ Scan started in background", android.widget.Toast.LENGTH_SHORT)

        finish()
    }
}

