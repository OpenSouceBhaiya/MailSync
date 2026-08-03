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
import kotlinx.coroutines.flow.collectLatest

sealed class HistoryUiState {
    object SetupRequired : HistoryUiState()
    object Loading : HistoryUiState()
    object Empty : HistoryUiState()
    data class Success(val otps: List<OtpEntity>) : HistoryUiState()
}

class OtpHistoryViewModel(
    private val repository: OtpRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        startObserving()
    }

    private fun startObserving() {
        viewModelScope.launch {
            repository.getAllOtps().collectLatest { otps ->
                if (otps.isNotEmpty()) {
                    _uiState.value = HistoryUiState.Success(otps)
                } else {
                    _uiState.value = HistoryUiState.Empty
                }
            }
        }
    }

    fun markAsUsed(id: String) {
        viewModelScope.launch {
            repository.markAsUsed(id)
        }
    }

    class Factory(
        private val repository: OtpRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OtpHistoryViewModel::class.java)) {
                return OtpHistoryViewModel(repository, settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
