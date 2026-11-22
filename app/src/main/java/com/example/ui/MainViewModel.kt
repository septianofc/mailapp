package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SecurityPreferencesRepository
import com.example.security.AppLockCapability
import com.example.security.DeviceIntegrityReport
import com.example.security.DeviceSecurityInspector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppLockState {
  data object Locked : AppLockState

  data object Unlocked : AppLockState

  data object NoDeviceCredential : AppLockState

  data class HardwareUnavailable(val reason: String) : AppLockState
}

sealed interface WebErrorState {
  data object None : WebErrorState

  data class NetworkError(val description: String) : WebErrorState

  data object SslValidationError : WebErrorState
}

data class MainUiState(
  val showSplash: Boolean = true,
  val appLockState: AppLockState = AppLockState.Locked,
  val appLockPreference: Boolean? = null,
  val systemSupportsAppLock: Boolean = false,
  val isAuthenticating: Boolean = false,
  val authErrorMessage: String? = null,
  val antiScreenshotEnabled: Boolean = true,
  val deviceIntegrityReport: DeviceIntegrityReport = DeviceIntegrityReport(),
  val isSettingsOpen: Boolean = false,
  val isWebLoading: Boolean = true,
  val webErrorState: WebErrorState = WebErrorState.None,
  val blockedNavigationCount: Int = 0,
  val lastBlockedNotice: String? = null,
  val reloadTrigger: Int = 0,
  val unlockedWithoutDeviceLock: Boolean = false,
) {
  /** Effective App Lock state: manual user preference if set, otherwise follows system capability. */
  val isAppLockEnabled: Boolean
    get() = appLockPreference ?: systemSupportsAppLock

  val isAppLockFollowingSystem: Boolean
    get() = appLockPreference == null
}

class MainViewModel(
  private val preferencesRepository: SecurityPreferencesRepository,
  private val securityInspector: DeviceSecurityInspector,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  private var lastCapability: AppLockCapability = AppLockCapability.NoCredentialEnrolled

  init {
    viewModelScope.launch {
      preferencesRepository.antiScreenshotEnabled.collect { enabled ->
        _uiState.update { it.copy(antiScreenshotEnabled = enabled) }
      }
    }

    viewModelScope.launch {
      preferencesRepository.appLockPreference.collect { pref ->
        _uiState.update { state ->
          val updated = state.copy(appLockPreference = pref)
          resolveLockState(updated, lastCapability)
        }
      }
    }

    refreshDeviceIntegrity()

    viewModelScope.launch {
      delay(SPLASH_DURATION_MS)
      _uiState.update { it.copy(showSplash = false) }
    }
  }

  fun refreshDeviceIntegrity() {
    viewModelScope.launch {
      val report = withContext(ioDispatcher) { securityInspector.inspect() }
      _uiState.update { it.copy(deviceIntegrityReport = report) }
    }
  }

  fun updateAppLockCapability(capability: AppLockCapability) {
    lastCapability = capability
    _uiState.update { state ->
      val updated =
        state.copy(systemSupportsAppLock = capability is AppLockCapability.Available)
      resolveLockState(updated, capability)
    }
  }

  private fun resolveLockState(
    state: MainUiState,
    capability: AppLockCapability,
  ): MainUiState {
    if (!state.isAppLockEnabled) {
      return state.copy(
        appLockState = AppLockState.Unlocked,
        authErrorMessage = null,
      )
    }
    if (state.appLockState is AppLockState.Unlocked) {
      return state
    }
    val nextLockState =
      when (capability) {
        AppLockCapability.Available -> AppLockState.Locked
        AppLockCapability.NoCredentialEnrolled -> AppLockState.NoDeviceCredential
        is AppLockCapability.Unavailable -> AppLockState.HardwareUnavailable(capability.reason)
      }
    return state.copy(appLockState = nextLockState)
  }

  fun onAuthenticationStarted() {
    _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null) }
  }

  fun onAuthenticationFinished() {
    _uiState.update { it.copy(isAuthenticating = false) }
  }

  fun onUnlockSucceeded() {
    _uiState.update {
      it.copy(
        appLockState = AppLockState.Unlocked,
        isAuthenticating = false,
        authErrorMessage = null,
        unlockedWithoutDeviceLock = false,
      )
    }
  }

  fun onUnlockFailed(message: String) {
    _uiState.update { it.copy(authErrorMessage = message, isAuthenticating = false) }
  }

  fun onContinueWithoutDeviceCredential() {
    _uiState.update {
      it.copy(
        appLockState = AppLockState.Unlocked,
        unlockedWithoutDeviceLock = true,
        authErrorMessage = null,
      )
    }
  }

  fun lockOnBackground() {
    _uiState.update { state ->
      if (state.isAuthenticating || !state.isAppLockEnabled) {
        state
      } else {
        state.copy(appLockState = AppLockState.Locked, authErrorMessage = null)
      }
    }
  }

  fun setAntiScreenshotEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setAntiScreenshotEnabled(enabled) }
  }

  fun setAppLockEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setAppLockEnabled(enabled) }
    _uiState.update { state ->
      if (!enabled) {
        state.copy(appLockPreference = false, appLockState = AppLockState.Unlocked)
      } else {
        state.copy(appLockPreference = true)
      }
    }
  }

  fun setSettingsOpen(open: Boolean) {
    _uiState.update { it.copy(isSettingsOpen = open) }
  }

  fun onWebPageStarted() {
    _uiState.update { it.copy(isWebLoading = true, webErrorState = WebErrorState.None) }
  }

  fun onWebPageFinished() {
    _uiState.update { it.copy(isWebLoading = false) }
  }

  fun onWebNetworkError(description: String) {
    _uiState.update {
      it.copy(
        isWebLoading = false,
        webErrorState = WebErrorState.NetworkError(description),
      )
    }
  }

  fun onWebSslError() {
    _uiState.update {
      it.copy(isWebLoading = false, webErrorState = WebErrorState.SslValidationError)
    }
  }

  fun onNavigationBlocked(notice: String) {
    _uiState.update {
      it.copy(
        blockedNavigationCount = it.blockedNavigationCount + 1,
        lastBlockedNotice = notice,
      )
    }
  }

  fun clearBlockedNotice() {
    _uiState.update { it.copy(lastBlockedNotice = null) }
  }

  fun retryWebLoad() {
    _uiState.update {
      it.copy(
        webErrorState = WebErrorState.None,
        isWebLoading = true,
        reloadTrigger = it.reloadTrigger + 1,
      )
    }
  }

  companion object {
    const val SPLASH_DURATION_MS = 1100L
  }
}

class MainViewModelFactory(
  private val preferencesRepository: SecurityPreferencesRepository,
  private val securityInspector: DeviceSecurityInspector,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
      return MainViewModel(preferencesRepository, securityInspector) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
