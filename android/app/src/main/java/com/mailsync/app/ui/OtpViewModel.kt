package com.mailsync.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mailsync.app.data.OtpRepository
import com.mailsync.app.data.OtpEntity
import com.mailsync.app.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast

// Represents the different states the UI can be in
sealed class OtpUiState {
    object SetupRequired : OtpUiState()
    object Loading : OtpUiState()
    object Empty : OtpUiState()
    data class Success(val otp: OtpEntity) : OtpUiState()
    data class Error(val message: String) : OtpUiState()
}

class OtpViewModel(
    private val context: Context,
    private val repository: OtpRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<OtpUiState>(OtpUiState.Loading)
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private val _lastScanTime = MutableStateFlow<Long>(0)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        checkSetupAndLoad()
        observeLatestOtp()
    }
    
    private fun observeLatestOtp() {
        viewModelScope.launch {
            repository.getLatestOtpFlow().collect { otp ->
                if (otp != null) {
                    _uiState.value = OtpUiState.Success(otp)
                } else {
                    _uiState.value = OtpUiState.Empty
                }
            }
        }
    }
    
    fun checkSetupAndLoad() {
        if (!settingsManager.isConfigured()) {
            _uiState.value = OtpUiState.SetupRequired
        } else {
            loadInitialData()
        }
    }

    private fun loadInitialData() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            if (settingsManager.isSyncEnabled()) {
                fetchOtpsSilent()
            }
        }
    }

    fun fetchOtps() {
        if (_isSyncing.value) return
        _uiState.value = OtpUiState.Loading
        viewModelScope.launch {
            if (settingsManager.isSyncEnabled()) {
                fetchOtpsSilent()
            } else {
                _uiState.value = OtpUiState.Error("Sync is currently paused.")
            }
        }
    }

    private suspend fun fetchOtpsSilent() {
        try {
            _isSyncing.value = true
            
            // Guaranteed visual delay so the UI reload animation has time to play
            val syncStartTime = System.currentTimeMillis()
            val newOtps = repository.syncWithBackend()
            val elapsed = System.currentTimeMillis() - syncStartTime
            if (elapsed < 800) {
                kotlinx.coroutines.delay(800 - elapsed)
            }
            
            // Auto-copy newly fetched OTP to clipboard
            if (newOtps.isNotEmpty()) {
                val latestNew = newOtps.maxByOrNull { it.receivedAt }
                if (latestNew != null) {
                    val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
                    if (latestNew.receivedAt > fiveMinutesAgo) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OTP", latestNew.code)
                        clipboard.setPrimaryClip(clip)
                    }
                    // We shouldn't show Toast in background often, but for manual fetch it's okay.
                }
            }
        } catch (e: Exception) {
            _lastScanTime.value = System.currentTimeMillis()
            if (_uiState.value !is OtpUiState.Success) {
                _uiState.value = OtpUiState.Error(e.message ?: "Network error occurred")
            }
        } finally {
            _isSyncing.value = false
            if (_uiState.value is OtpUiState.Loading) {
                val latest = repository.fetchLatestOtp()
                if (latest != null) {
                    _uiState.value = OtpUiState.Success(latest)
                } else {
                    _uiState.value = OtpUiState.Empty
                }
            }
        }
    }

    /**
     * Factory needed to inject the repository and settings manager into the ViewModel.
     */
    class Factory(
        private val context: Context,
        private val repository: OtpRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OtpViewModel::class.java)) {
                return OtpViewModel(context, repository, settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
