package com.pw.parent.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pw.parent.data.ParentRepository
import com.pw.parent.data.NotificationItem
import com.pw.parent.data.OfflineDraft
import com.pw.parent.utils.ParentNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ParentViewModel(
    application: Application,
    private val repository: ParentRepository
) : AndroidViewModel(application) {

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Target web resource loaded dynamically from environment configuration
    val targetUrl = com.pw.parent.BuildConfig.TARGET_URL

    // Only this host (plus its subdomains) may use camera, microphone and geolocation.
    val trustedHost: String = runCatching {
        java.net.URI(targetUrl).host.orEmpty()
    }.getOrDefault("")

    // Backing flows for states
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _loadProgress = MutableStateFlow(0)
    val loadProgress: StateFlow<Int> = _loadProgress.asStateFlow()

    private val _isWebViewError = MutableStateFlow(false)
    val isWebViewError: StateFlow<Boolean> = _isWebViewError.asStateFlow()

    private val _isDarkThemeOverride = MutableStateFlow<Boolean?>(null)
    val isDarkThemeOverride: StateFlow<Boolean?> = _isDarkThemeOverride.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showNotificationsTray = MutableStateFlow(false)
    val showNotificationsTray: StateFlow<Boolean> = _showNotificationsTray.asStateFlow()

    // Persistent items from database
    val offlineDrafts: StateFlow<List<OfflineDraft>> = repository.allOfflineDrafts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val notificationLogs: StateFlow<List<NotificationItem>> = repository.allNotifications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Setup network callback for real-time monitoring
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }
    }

    init {
        // Safe check for initial status
        _isOnline.value = checkInitialNetworkStatus()
        observeNetworkChanges()
    }

    private fun checkInitialNetworkStatus(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeNetworkChanges() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // UI actions
    fun setLoadProgress(progress: Int) {
        _loadProgress.value = progress
        if (progress == 100) {
            _isWebViewError.value = false
        }
    }

    fun setWebViewError(hasError: Boolean) {
        _isWebViewError.value = hasError
    }

    /**
     * True when [origin] belongs to the app's own web app. Used to gate WebView-level
     * permission grants so a third-party page can never silently get the camera or mic.
     */
    fun isTrustedOrigin(origin: String): Boolean {
        if (origin.isBlank() || trustedHost.isBlank()) return false
        val host = runCatching {
            val uri = if (origin.contains("://")) java.net.URI(origin) else java.net.URI("https://$origin")
            uri.host.orEmpty()
        }.getOrNull() ?: return false

        return host.equals(trustedHost, ignoreCase = true) ||
            host.endsWith(".$trustedHost", ignoreCase = true)
    }

    fun setDarkThemeOverride(isDark: Boolean?) {
        _isDarkThemeOverride.value = isDark
    }

    fun setShowSettings(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun setShowNotificationsTray(show: Boolean) {
        _showNotificationsTray.value = show
    }

    // DB Operations
    fun saveDraft(content: String, recipient: String = "General") {
        viewModelScope.launch {
            repository.saveOfflineDraft(content, recipient)
            // Trigger local informative notification for beautiful UX
            triggerLocalNotification(
                "Offline Draft Cached",
                "Saved \"$content\" to sync box."
            )
        }
    }

    fun deleteDraft(draft: OfflineDraft) {
        viewModelScope.launch {
            repository.deleteOfflineDraft(draft)
        }
    }

    fun clearAllDrafts() {
        viewModelScope.launch {
            repository.clearAllDrafts()
        }
    }

    fun triggerLocalNotification(title: String, message: String) {
        viewModelScope.launch {
            repository.saveNotification(title, message)
            ParentNotificationHelper.showNotification(getApplication(), title, message)
        }
    }

    fun deleteNotificationLog(id: Int) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }
}

// Custom ViewModel Factory
class ParentViewModelFactory(
    private val application: Application,
    private val repository: ParentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParentViewModel::class.java)) {
            return ParentViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
