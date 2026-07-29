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

    init {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "revoked_accounts") {
                _revokedAccounts.value = sharedPreferences.getStringSet("revoked_accounts", emptySet())?.toSet() ?: emptySet()
            }
        }
        otpPrefs.registerOnSharedPreferenceChangeListener(listener)
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
        val newDevices = settingsManager.getLinkedDevicesMetadata()
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

    fun unlinkLinkedDevice(uuid: String) {
        // Fully remove from local storage so list stays clean immediately
        settingsManager.removeLinkedDeviceKey(uuid)
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

    fun addAccountEmail(email: String, displayName: String?) {
        settingsManager.addAccountEmail(email)
        settingsManager.setAccountName(email, displayName)
        _accounts.value = settingsManager.getConnectedAccounts()
        _isConfigured.value = true
        
        // Remove from revoked list if re-added
        val revoked = otpPrefs.getStringSet("revoked_accounts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (revoked.remove(email)) {
            otpPrefs.edit().putStringSet("revoked_accounts", revoked).apply()
        }
        
        setSyncEnabled(true)
        setAccountSyncEnabled(email, true)
        updateFirebaseAccountName()
    }
    
    fun getAccountName(email: String): String? {
        return settingsManager.getAccountName(email)
    }
    
    fun removeAccountEmail(email: String) {
        settingsManager.removeAccountEmail(email)
        // Also remove from disabled list if it was there
        settingsManager.setAccountSyncEnabled(email, true)
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
        if (enabled) {
            try {
                val intent = android.content.Intent(context, com.mailsync.app.service.FirebaseSyncService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
            }
        } else {
            val intent = android.content.Intent(context, com.mailsync.app.service.FirebaseSyncService::class.java)
            context.stopService(intent)
        }
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
