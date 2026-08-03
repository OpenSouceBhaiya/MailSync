package com.mailsync.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mailsync.app.data.OtpRepository
import com.mailsync.app.data.SettingsManager
import com.mailsync.app.service.ServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import com.mailsync.app.data.FirebaseManager

class SettingsViewModel(
    private val context: android.content.Context,
    private val repository: OtpRepository,
    val settingsManager: SettingsManager,
    val firebaseManager: FirebaseManager
) : ViewModel() {
    private val otpPrefs = context.getSharedPreferences("otp_sync_prefs", android.content.Context.MODE_PRIVATE)

    private val _revokedAccounts = MutableStateFlow(otpPrefs.getStringSet("revoked_accounts", emptySet())?.toSet() ?: emptySet())
    val revokedAccounts: StateFlow<Set<String>> = _revokedAccounts.asStateFlow()

    private val _isConfigured = MutableStateFlow(settingsManager.isConfigured())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _accounts = MutableStateFlow<List<String>>(settingsManager.getConnectedAccounts())
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _linkedDevices = MutableStateFlow<List<com.mailsync.app.ui.LinkedDevice>>(settingsManager.getLinkedDevicesMetadata())
    val linkedDevices: StateFlow<List<com.mailsync.app.ui.LinkedDevice>> = _linkedDevices.asStateFlow()

    private val _disabledSyncAccounts = MutableStateFlow(settingsManager.getDisabledSyncAccounts())
    val disabledSyncAccounts: StateFlow<Set<String>> = _disabledSyncAccounts.asStateFlow()

    private val _isSyncEnabled = MutableStateFlow(settingsManager.isSyncEnabled())
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()
    
    private val _systemErrors = MutableStateFlow<List<String>>(
        parseSystemErrors(otpPrefs.getString("system_errors", "[]"))
    )
    val systemErrors: StateFlow<List<String>> = _systemErrors.asStateFlow()

    private fun parseSystemErrors(jsonStr: String?): List<String> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            return list
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "revoked_accounts") {
            _revokedAccounts.value = sharedPreferences.getStringSet("revoked_accounts", emptySet())?.toSet() ?: emptySet()
        } else if (key == "system_errors") {
            _systemErrors.value = parseSystemErrors(sharedPreferences.getString("system_errors", "[]"))
        }
    }

    init {
        otpPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        checkForegroundServiceStatePublic()
        updateFirebaseAccountName()

        // Listen to individual devices to detect if extension unlinks itself or renames
        val uuids = settingsManager.getLinkedDevicesMetadata().map { it.id }
        for (uuid in uuids) {
            firebaseManager.devicesRef.child(uuid).addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        // Device deleted by extension!
                        settingsManager.removeLinkedDeviceKey(uuid)
                        refreshLinkedDevices()
                        checkForegroundServiceStatePublic()
                    } else {
                        val status = snapshot.child("status").getValue(String::class.java)
                        val name = snapshot.child("name").getValue(String::class.java)
                        
                        var changed = false
                        if (status != null) {
                            settingsManager.updateLinkedDeviceStatus(uuid, status)
                            changed = true
                        }
                        if (name != null) {
                            settingsManager.updateLinkedDeviceName(uuid, name)
                            changed = true
                        }
                        if (changed) {
                            refreshLinkedDevices()
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
    }

    private val deviceListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()

    fun refreshLinkedDevices() {
        val allDevices = settingsManager.getLinkedDevicesMetadata()
        val terminatedDevices = allDevices.filter { it.status == "terminated" }
        
        if (terminatedDevices.isNotEmpty()) {
            val names = terminatedDevices.map { it.name }.joinToString(", ")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "$names session terminated. Please link again.", android.widget.Toast.LENGTH_LONG).show()
            }
            terminatedDevices.forEach { 
                settingsManager.removeLinkedDeviceKey(it.id)
                deviceListeners[it.id]?.let { listener ->
                    firebaseManager.devicesRef.child(it.id).removeEventListener(listener)
                    deviceListeners.remove(it.id)
                }
                firebaseManager.devicesRef.child(it.id).removeValue()
                firebaseManager.otpsRef.child(it.id).removeValue()
            }
            checkForegroundServiceStatePublic()
        }
        
        val newDevices = allDevices.filter { it.status != "terminated" }
        _linkedDevices.value = newDevices
        
        val uuids = newDevices.map { it.id }
        for (uuid in uuids) {
            if (!deviceListeners.containsKey(uuid)) {
                val listener = object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (!snapshot.exists()) {
                            settingsManager.removeLinkedDeviceKey(uuid)
                            refreshLinkedDevices()
                            checkForegroundServiceStatePublic()
                        } else {
                            val status = snapshot.child("status").getValue(String::class.java)
                            if (status == "terminated") {
                                settingsManager.updateLinkedDeviceStatus(uuid, "terminated")
                                refreshLinkedDevices()
                                return
                            }
                            val name = snapshot.child("name").getValue(String::class.java)
                            
                            var changed = false
                            if (status != null) {
                                settingsManager.updateLinkedDeviceStatus(uuid, status)
                                changed = true
                            }
                            if (name != null) {
                                settingsManager.updateLinkedDeviceName(uuid, name)
                                changed = true
                            }
                            if (changed) {
                                val currentDevices = settingsManager.getLinkedDevicesMetadata()
                                if (_linkedDevices.value != currentDevices) {
                                    _linkedDevices.value = currentDevices
                                }
                            }
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                }
                firebaseManager.devicesRef.child(uuid).addValueEventListener(listener)
                deviceListeners[uuid] = listener
            }
        }
    }

    /**
     * Link a new PC device from a scanned QR code.
     * Uses viewModelScope so the Firebase write is guaranteed to complete
     * even when the QR scanner composable navigates away immediately after scanning.
     */
    fun linkDevice(uuid: String, keyBase64: String, pcName: String, browser: String, dateLinked: String) {
        // Save key locally FIRST (this is synchronous and safe)
        settingsManager.addLinkedDeviceKey(uuid, keyBase64, pcName, browser, dateLinked)
        refreshLinkedDevices()

        // Write dateLinked to Firebase using viewModelScope — lifecycle-safe, won't be GC'd on navigation
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Clean up old stale device nodes from Firebase before linking new one
                val existingDevices = settingsManager.getLinkedDevicesMetadata()
                for (oldDevice in existingDevices) {
                    if (oldDevice.id != uuid) {
                        try {
                            firebaseManager.devicesRef.child(oldDevice.id).removeValue().await()
                            firebaseManager.otpsRef.child(oldDevice.id).removeValue().await()
                        } catch (e: Exception) {
                            android.util.Log.w("SettingsViewModel", "Failed to remove old device ${oldDevice.id}", e)
                        }
                        settingsManager.removeLinkedDeviceKey(oldDevice.id)
                    }
                }

                val firstAccount = accounts.value.firstOrNull()
                val accountName = firstAccount?.let { getAccountName(it) }
                // This writes dateLinked to Firebase — the extension polls for this field
                firebaseManager.linkDeviceMetadata(uuid, pcName, browser, accountName)
                checkForegroundServiceStatePublic()
                android.util.Log.d("SettingsViewModel", "linkDevice: Firebase write complete for uuid=$uuid")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "linkDevice: Firebase write FAILED for uuid=$uuid", e)
            }
        }
    }

    fun unlinkLinkedDevice(uuid: String) {
        // Fully remove from local storage so list stays clean immediately
        settingsManager.removeLinkedDeviceKey(uuid)
        deviceListeners[uuid]?.let { listener ->
            firebaseManager.devicesRef.child(uuid).removeEventListener(listener)
            deviceListeners.remove(uuid)
        }
        refreshLinkedDevices()
        checkForegroundServiceStatePublic()

        viewModelScope.launch {
            try {
                // Mark as terminated in Firebase so the extension knows immediately
                val updates = mapOf<String, Any>(
                    "status" to "terminated",
                    "syncEnabled" to false
                )
                firebaseManager.devicesRef.child(uuid).updateChildren(updates).await()
                // Give Firebase a moment, then delete the node entirely
                kotlinx.coroutines.delay(1500)
                firebaseManager.devicesRef.child(uuid).removeValue().await()
                firebaseManager.otpsRef.child(uuid).removeValue().await()
            } catch (e: Exception) {
                // Ignore if it fails due to network
            }
        }
    }
    
    fun renameLinkedDevice(uuid: String, newName: String) {
        settingsManager.updateLinkedDeviceName(uuid, newName)
        // Broadcast custom name to Firebase so extension can save it
        firebaseManager.devicesRef.child(uuid).child("name").setValue(newName)
        refreshLinkedDevices()
    }

    private val _isBiometricEnabled = MutableStateFlow(settingsManager.isBiometricLockEnabled())
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _highlightBugReport = MutableStateFlow(false)
    val highlightBugReport: StateFlow<Boolean> = _highlightBugReport.asStateFlow()

    fun triggerHighlightBugReport() {
        _highlightBugReport.value = true
    }

    fun clearHighlightBugReport() {
        _highlightBugReport.value = false
    }

    fun addAccountEmail(email: String, displayName: String?, serverAuthCode: String?, context: android.content.Context) {
        viewModelScope.launch {
            if (serverAuthCode != null) {
                val authHelper = com.mailsync.app.data.GoogleAuthHelper(context)
                val tokens = authHelper.exchangeAuthCodeForTokens(serverAuthCode)
                val existingRefreshToken = settingsManager.getRefreshToken(email)
                
                if (tokens?.accessToken != null) {
                    // Save the fresh access token
                    settingsManager.setAccessToken(email, tokens.accessToken, tokens.expiresInSeconds)
                    if (tokens.refreshToken != null) {
                        settingsManager.setRefreshToken(email, tokens.refreshToken)
                    }
                } else if (existingRefreshToken == null) {
                    // Token exchange failed completely and no refresh token exists
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Google rejected authentication. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    com.mailsync.app.utils.ErrorReporter.reportError(context, Exception("Auth Code Exchange Failed. No access token returned for $email."), "SettingsViewModel")
                    return@launch
                }
            } else {
                val existingRefreshToken = settingsManager.getRefreshToken(email)
                val existingAccessToken = settingsManager.getAccessToken(email)
                if (existingRefreshToken == null && existingAccessToken == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "No server auth code provided by Google Play Services.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
            }
            
            settingsManager.addAccountEmail(email)
            settingsManager.setAccountName(email, displayName)
            _accounts.value = settingsManager.getConnectedAccounts()
            _isConfigured.value = true
            
            // Remove from revoked list if re-added
            val revoked = otpPrefs.getStringSet("revoked_accounts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (revoked.remove(email)) {
                otpPrefs.edit().putStringSet("revoked_accounts", revoked).apply()
            }
            
            // Show success toast NOW — after exchange confirmed, account saved, revoked list cleaned
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Account added successfully!", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            setSyncEnabled(true)
            setAccountSyncEnabled(email, true)
            updateFirebaseAccountName()
        }
    }
    
    fun getAccountName(email: String): String? {
        return settingsManager.getAccountName(email)
    }
    
    fun removeAccountEmail(email: String) {
        settingsManager.removeAccountEmail(email)
        // Also remove from disabled list if it was there
        settingsManager.setAccountSyncEnabled(email, true)

        // Clear from revoked list
        val revoked = otpPrefs.getStringSet("revoked_accounts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (revoked.remove(email)) {
            otpPrefs.edit().putStringSet("revoked_accounts", revoked).apply()
        }
        _revokedAccounts.value = revoked.toSet()

        // Explicitly revoke Google Play Services access so the next sign-in forces a prompt
        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener {
                client.revokeAccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _accounts.value = settingsManager.getConnectedAccounts()
        _disabledSyncAccounts.value = settingsManager.getDisabledSyncAccounts()
        _isConfigured.value = settingsManager.isConfigured()
        checkForegroundServiceStatePublic()
        updateFirebaseAccountName()
    }

    private fun updateFirebaseAccountName() {
        val firstAccount = _accounts.value.firstOrNull()
        val accountName = firstAccount?.let { getAccountName(it) }
        val uuids = settingsManager.getLinkedDevicesMetadata().map { it.id }
        if (uuids.isNotEmpty()) {
            viewModelScope.launch {
                firebaseManager.updateAccountName(uuids, accountName)
            }
        }
    }

    fun setAccountSyncEnabled(email: String, enabled: Boolean) {
        settingsManager.setAccountSyncEnabled(email, enabled)
        _disabledSyncAccounts.value = settingsManager.getDisabledSyncAccounts()
        checkForegroundServiceStatePublic()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        settingsManager.setBiometricLockEnabled(enabled)
        _isBiometricEnabled.value = enabled
    }


    fun setSyncEnabled(enabled: Boolean) {
        settingsManager.setSyncEnabled(enabled)
        _isSyncEnabled.value = enabled
        checkForegroundServiceStatePublic()
    }
    
    private val _isInstantSyncEnabled = MutableStateFlow(settingsManager.isInstantSyncEnabled())
    val isInstantSyncEnabled: StateFlow<Boolean> = _isInstantSyncEnabled.asStateFlow()
    
    fun setInstantSyncEnabled(enabled: Boolean) {
        settingsManager.setInstantSyncEnabled(enabled)
        _isInstantSyncEnabled.value = enabled
    }
    
    private val _isClipboardCopyEnabled = MutableStateFlow(settingsManager.isClipboardCopyEnabled())
    val isClipboardCopyEnabled: StateFlow<Boolean> = _isClipboardCopyEnabled.asStateFlow()
    
    fun setClipboardCopyEnabled(enabled: Boolean) {
        settingsManager.setClipboardCopyEnabled(enabled)
        _isClipboardCopyEnabled.value = enabled
    }
    
    private val _isAlwaysOnSyncEnabled = MutableStateFlow(settingsManager.isAlwaysOnSyncEnabled())
    val isAlwaysOnSyncEnabled: StateFlow<Boolean> = _isAlwaysOnSyncEnabled.asStateFlow()

    fun setAlwaysOnSyncEnabled(enabled: Boolean) {
        settingsManager.setAlwaysOnSyncEnabled(enabled)
        _isAlwaysOnSyncEnabled.value = enabled
        // Service start/stop removed since polling is now fully integrated into NotificationListenerService
    }
    
    fun checkForegroundServiceStatePublic() {
        val enabled = _isSyncEnabled.value
        val connected = _accounts.value
        val disabled = _disabledSyncAccounts.value
        val allDisabled = connected.isEmpty() || connected.all { it in disabled }
        
        val status = if (!settingsManager.isConfigured() || connected.isEmpty()) {
            "error_no_accounts"
        } else if (!enabled || allDisabled) {
            "paused"
        } else {
            "active"
        }
        
        viewModelScope.launch {
            val uuids = _linkedDevices.value.map { it.id }
            if (uuids.isNotEmpty()) {
                firebaseManager.updateSyncState(uuids, status == "active", status)
            }
        }

        if (status == "active") {
            com.mailsync.app.service.ServiceHelper.startForegroundService(context)
        } else {
            com.mailsync.app.service.ServiceHelper.stopForegroundService(context)
        }
    }
    
    private val _hasSeenOnboarding = MutableStateFlow(settingsManager.hasSeenOnboarding())
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()
    
    val appOpenCount: Int get() = settingsManager.getAppOpenCount()

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        settingsManager.setHasSeenOnboarding(hasSeen)
        _hasSeenOnboarding.value = hasSeen
    }
    
    private val _autoStopDelayMs = MutableStateFlow(settingsManager.getAutoStopDelayMs())
    val autoStopDelayMs: StateFlow<Long> = _autoStopDelayMs.asStateFlow()

    fun setAutoStopDelayMs(delayMs: Long) {
        settingsManager.setAutoStopDelayMs(delayMs)
        _autoStopDelayMs.value = delayMs
    }

    fun clearConfig() {
        settingsManager.clearConfig()
        _isConfigured.value = false
        _accounts.value = emptyList()
    }

    class Factory(
        private val context: android.content.Context,
        private val repository: OtpRepository,
        private val settingsManager: SettingsManager,
        private val firebaseManager: FirebaseManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(context, repository, settingsManager, firebaseManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
