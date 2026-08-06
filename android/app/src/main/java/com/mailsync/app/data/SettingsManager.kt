package com.mailsync.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages secure storage of the connected Google Account email.
 */
class SettingsManager(context: Context) {

    private val sharedPreferences: android.content.SharedPreferences

    init {
        sharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            try {
                EncryptedSharedPreferences.create(
                    context,
                    "secure_otp_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // Samsung devices and general Keystore corruption can cause this to crash.
                // Clear the corrupt preferences file and retry.
                context.getSharedPreferences("secure_otp_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                EncryptedSharedPreferences.create(
                    context,
                    "secure_otp_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        } catch (e: Exception) {
            // Catastrophic Keystore failure on this specific device. Fallback to standard SharedPreferences.
            context.getSharedPreferences("secure_otp_prefs_fallback", Context.MODE_PRIVATE)
        }
    }
    
    fun clearConfig() {
        sharedPreferences.edit()
            .remove(KEY_CONNECTED_ACCOUNTS)
            .remove("connected_accounts_list")
            .apply()
    }
    
    fun incrementAppOpenCount() {
        val current = sharedPreferences.getInt("app_open_count", 0)
        sharedPreferences.edit().putInt("app_open_count", current + 1).apply()
    }
    
    fun getAppOpenCount(): Int {
        return sharedPreferences.getInt("app_open_count", 0)
    }

    fun addAccountEmail(email: String) {
        val accounts = getConnectedAccounts().toMutableList()
        if (!accounts.contains(email)) {
            accounts.add(email)
            sharedPreferences.edit()
                .putString("connected_accounts_list", accounts.joinToString(","))
                .putLong("account_enabled_time_$email", System.currentTimeMillis())
                .apply()
        }
    }

    fun removeAccountEmail(email: String) {
        val accounts = getConnectedAccounts().toMutableList()
        accounts.remove(email)
        sharedPreferences.edit()
            .putString("connected_accounts_list", accounts.joinToString(","))
            .remove("account_name_$email")
            .apply()
    }

    fun getConnectedAccounts(): List<String> {
        val listString = sharedPreferences.getString("connected_accounts_list", null)
        if (listString != null) {
            return if (listString.isEmpty()) emptyList() else listString.split(",")
        }
        
        // Migration from old StringSet
        val oldSet = sharedPreferences.getStringSet(KEY_CONNECTED_ACCOUNTS, null)
        if (oldSet != null) {
            val list = oldSet.toList()
            sharedPreferences.edit()
                .putString("connected_accounts_list", list.joinToString(","))
                .remove(KEY_CONNECTED_ACCOUNTS)
                .apply()
            return list
        }
        
        return emptyList()
    }
    
    fun setAccountName(email: String, name: String?) {
        if (name != null) {
            sharedPreferences.edit().putString("account_name_$email", name).apply()
        }
    }
    
    fun getAccountName(email: String): String? {
        return sharedPreferences.getString("account_name_$email", null)
    }
    
    fun setRefreshToken(email: String, token: String?) {
        if (token != null) {
            sharedPreferences.edit().putString("refresh_token_$email", token).apply()
        } else {
            sharedPreferences.edit().remove("refresh_token_$email").apply()
        }
    }
    
    fun getRefreshToken(email: String): String? {
        return sharedPreferences.getString("refresh_token_$email", null)
    }

    fun setAccessToken(email: String, token: String?, expiresInSeconds: Long = 3600L) {
        val editor = sharedPreferences.edit()
        if (token != null) {
            editor.putString("access_token_$email", token)
            // Expire 5 minutes early as a buffer
            val bufferSeconds = if (expiresInSeconds > 300) 300L else 0L
            val expiryMs = System.currentTimeMillis() + ((expiresInSeconds - bufferSeconds) * 1000L)
            editor.putLong("access_token_expiry_$email", expiryMs)
        } else {
            editor.remove("access_token_$email")
            editor.remove("access_token_expiry_$email")
        }
        editor.apply()
    }

    fun getAccessToken(email: String): String? {
        val token = sharedPreferences.getString("access_token_$email", null) ?: return null
        val expiry = sharedPreferences.getLong("access_token_expiry_$email", 0L)
        if (System.currentTimeMillis() >= expiry) {
            return null // Token expired
        }
        return token
    }
    
    fun getDisabledSyncAccounts(): Set<String> {
        return sharedPreferences.getStringSet(KEY_DISABLED_SYNC_ACCOUNTS, emptySet()) ?: emptySet()
    }
    
    fun setAccountSyncEnabled(email: String, enabled: Boolean) {
        val disabled = getDisabledSyncAccounts().toMutableSet()
        val editor = sharedPreferences.edit()
        if (enabled) {
            disabled.remove(email)
            // Reset the timestamp so we don't fetch OTPs from when it was disabled
            editor.putLong("account_enabled_time_$email", System.currentTimeMillis())
        } else {
            disabled.add(email)
        }
        editor.putStringSet(KEY_DISABLED_SYNC_ACCOUNTS, disabled).apply()
    }

    fun getAccountEnabledTime(email: String): Long {
        // Return the recorded time, or 0 if not found (legacy fallback)
        return sharedPreferences.getLong("account_enabled_time_$email", 0L)
    }

    fun isConfigured(): Boolean {
        return getConnectedAccounts().isNotEmpty()
    }

    fun isBiometricLockEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_LOCK, false)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    fun isSyncEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SYNC_ENABLED, false)
    }

    fun setSyncEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    fun hasSeenOnboarding(): Boolean {
        return sharedPreferences.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, hasSeen).apply()
    }
    
    fun getAutoStopDelayMs(): Long {
        return sharedPreferences.getLong(KEY_AUTO_STOP_DELAY, 15 * 60 * 1000L) // Default 15 minutes
    }
    
    fun setAutoStopDelayMs(delayMs: Long) {
        sharedPreferences.edit().putLong(KEY_AUTO_STOP_DELAY, delayMs).apply()
    }

    fun isInstantSyncEnabled(): Boolean {
        return sharedPreferences.getBoolean("instant_sync_enabled", true)
    }

    fun setInstantSyncEnabled(enabled: Boolean) {
        val editor = sharedPreferences.edit().putBoolean("instant_sync_enabled", enabled)
        if (enabled) {
            editor.putLong("instant_sync_enabled_time", System.currentTimeMillis())
        }
        editor.apply()
    }
    
    fun getInstantSyncEnabledTime(): Long {
        return sharedPreferences.getLong("instant_sync_enabled_time", 0L)
    }
    
    fun isClipboardCopyEnabled(): Boolean {
        return sharedPreferences.getBoolean("clipboard_copy_enabled", true)
    }

    fun setClipboardCopyEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("clipboard_copy_enabled", enabled).apply()
    }
    
    fun isAlwaysOnSyncEnabled(): Boolean {
        return sharedPreferences.getBoolean("always_on_sync_enabled", true)
    }

    fun setAlwaysOnSyncEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("always_on_sync_enabled", enabled).apply()
    }
    
    // ── Linked Devices (PCs) ───────────────────────────────────────────────

    fun addLinkedDeviceKey(uuid: String, keyBase64: String, name: String, browser: String, dateLinked: String) {
        val uuids = getLinkedDeviceUuids().toMutableSet()
        uuids.add(uuid)
        sharedPreferences.edit()
            .putStringSet(KEY_LINKED_DEVICE_UUIDS, uuids)
            .putString("device_key_$uuid", keyBase64)
            .putString("device_name_$uuid", name)
            .putString("device_browser_$uuid", browser)
            .putString("device_date_$uuid", dateLinked)
            .putString("device_status_$uuid", "active")
            .apply()
    }

    fun removeLinkedDeviceKey(uuid: String) {
        val uuids = getLinkedDeviceUuids().toMutableSet()
        uuids.remove(uuid)
        val editor = sharedPreferences.edit()
        editor.remove(KEY_LINKED_DEVICE_UUIDS)
        editor.apply()
        
        sharedPreferences.edit()
            .putStringSet(KEY_LINKED_DEVICE_UUIDS, uuids)
            .remove("device_key_$uuid")
            .remove("device_name_$uuid")
            .remove("device_browser_$uuid")
            .remove("device_date_$uuid")
            .remove("device_status_$uuid")
            .remove("device_last_otp_time_$uuid")
            .apply()
    }

    fun updateLinkedDeviceStatus(uuid: String, status: String) {
        sharedPreferences.edit().putString("device_status_$uuid", status).apply()
    }
    
    fun updateLinkedDeviceName(uuid: String, name: String) {
        sharedPreferences.edit().putString("device_name_$uuid", name).apply()
    }
    
    fun updateLinkedDeviceLastOtpTime(uuid: String, time: String) {
        sharedPreferences.edit().putString("device_last_otp_time_$uuid", time).apply()
    }

    private fun getLinkedDeviceUuids(): Set<String> {
        return sharedPreferences.getStringSet(KEY_LINKED_DEVICE_UUIDS, emptySet()) ?: emptySet()
    }

    fun getAllLinkedDeviceKeys(): Map<String, String> {
        val uuids = getLinkedDeviceUuids()
        val keys = mutableMapOf<String, String>()
        for (uuid in uuids) {
            // Only return keys for active devices
            val status = sharedPreferences.getString("device_status_$uuid", "active")
            if (status != "terminated") {
                val key = sharedPreferences.getString("device_key_$uuid", null)
                if (key != null) {
                    keys[uuid] = key
                }
            }
        }
        return keys
    }

    fun getLinkedDevicesMetadata(): List<com.mailsync.app.ui.LinkedDevice> {
        val uuids = getLinkedDeviceUuids()
        val devices = mutableListOf<com.mailsync.app.ui.LinkedDevice>()
        for (uuid in uuids) {
            val name = sharedPreferences.getString("device_name_$uuid", "Unknown PC") ?: "Unknown PC"
            val browser = sharedPreferences.getString("device_browser_$uuid", "Chrome") ?: "Chrome"
            val date = sharedPreferences.getString("device_date_$uuid", "") ?: ""
            val status = sharedPreferences.getString("device_status_$uuid", "active") ?: "active"
            val lastOtp = sharedPreferences.getString("device_last_otp_time_$uuid", null)
            devices.add(com.mailsync.app.ui.LinkedDevice(uuid, name, browser, date, status, lastOtp))
        }
        return devices
    }

    companion object {
        private const val KEY_CONNECTED_ACCOUNTS = "connected_accounts"
        private const val KEY_DISABLED_SYNC_ACCOUNTS = "disabled_sync_accounts"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_AUTO_STOP_DELAY = "auto_stop_delay"
        private const val KEY_LINKED_DEVICE_UUIDS = "linked_device_uuids"
    }
}
