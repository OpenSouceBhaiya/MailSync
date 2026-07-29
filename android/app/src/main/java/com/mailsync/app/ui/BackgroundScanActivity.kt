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
            Toast.makeText(this, "⚠️ Please setup accounts first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!settingsManager.isSyncEnabled()) {
            Toast.makeText(this, "⚠️ Sync is disabled. Enable it in MailSync.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!isInternetAvailable()) {
            Toast.makeText(this, "No internet connection available.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Refresh the active timestamp regardless — this wakes up the polling loop in NotificationListenerService
        com.mailsync.app.AppState.lastActiveTimeMs = System.currentTimeMillis()

        Toast.makeText(this, "✅ Scan started in background", Toast.LENGTH_SHORT).show()

        finish()
    }
}
