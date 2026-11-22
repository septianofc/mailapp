package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DataStoreSecurityPreferencesRepository
import com.example.security.AndroidDeviceSecurityInspector
import com.example.security.AppLockCapability
import com.example.security.AppLockManager
import com.example.ui.AppLockState
import com.example.ui.MailSeptianApp
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.MailSeptianSupportTheme

class MainActivity : FragmentActivity() {

  private val viewModel: MainViewModel by viewModels {
    MainViewModelFactory(
      preferencesRepository = DataStoreSecurityPreferencesRepository(applicationContext),
      securityInspector = AndroidDeviceSecurityInspector(applicationContext),
    )
  }

  private var autoPromptIssuedForCurrentLock = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Protect initial splash frame immediately until persisted preference is loaded
    applyFlagSecure(true)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
      navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
    )

    lifecycle.addObserver(
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_START -> {
            viewModel.refreshDeviceIntegrity()
            refreshLockCapability()
          }
          Lifecycle.Event.ON_STOP -> {
            if (!isChangingConfigurations && !viewModel.uiState.value.isAuthenticating) {
              autoPromptIssuedForCurrentLock = false
              viewModel.lockOnBackground()
            }
          }
          else -> Unit
        }
      }
    )

    refreshLockCapability()

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      LaunchedEffect(uiState.antiScreenshotEnabled) {
        applyFlagSecure(uiState.antiScreenshotEnabled)
      }

      LaunchedEffect(
        uiState.showSplash,
        uiState.isAppLockEnabled,
        uiState.appLockState,
        uiState.deviceIntegrityReport.hasAnyWarning,
      ) {
        if (
          !uiState.showSplash &&
            !uiState.deviceIntegrityReport.hasAnyWarning &&
            uiState.isAppLockEnabled &&
            uiState.appLockState is AppLockState.Locked &&
            !autoPromptIssuedForCurrentLock
        ) {
          autoPromptIssuedForCurrentLock = true
          launchBiometricPrompt()
        }
      }

      MailSeptianSupportTheme {
        MailSeptianApp(
          uiState = uiState,
          onTriggerBiometricUnlock = { launchBiometricPrompt() },
          onOpenDeviceSecuritySettings = { openDeviceSecuritySettings() },
          onOpenDeviceDeveloperSettings = { openDeveloperOrSystemSettings() },
          onRecheckLockCapability = {
            refreshLockCapability()
            if (
              !viewModel.uiState.value.deviceIntegrityReport.hasAnyWarning &&
                viewModel.uiState.value.isAppLockEnabled &&
                viewModel.uiState.value.appLockState is AppLockState.Locked
            ) {
              launchBiometricPrompt()
            }
          },
          onRecheckDeviceIntegrity = { viewModel.refreshDeviceIntegrity() },
          onContinueWithoutCredential = { viewModel.onContinueWithoutDeviceCredential() },
          onExitApp = { exitApplication() },
          onToggleAntiScreenshot = { enabled -> viewModel.setAntiScreenshotEnabled(enabled) },
          onToggleAppLock = { enabled -> viewModel.setAppLockEnabled(enabled) },
          onOpenSettings = { viewModel.setSettingsOpen(true) },
          onCloseSettings = { viewModel.setSettingsOpen(false) },
          onWebPageStarted = { viewModel.onWebPageStarted() },
          onWebPageFinished = { viewModel.onWebPageFinished() },
          onWebNetworkError = { desc -> viewModel.onWebNetworkError(desc) },
          onWebSslError = { viewModel.onWebSslError() },
          onNavigationBlocked = { notice -> viewModel.onNavigationBlocked(notice) },
          onClearBlockedNotice = { viewModel.clearBlockedNotice() },
          onRetryWebLoad = { viewModel.retryWebLoad() },
        )
      }
    }
  }

  private fun applyFlagSecure(enabled: Boolean) {
    if (enabled) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  private fun refreshLockCapability() {
    val capability = AppLockManager.checkCapability(this)
    viewModel.updateAppLockCapability(capability)
  }

  private fun launchBiometricPrompt() {
    if (viewModel.uiState.value.deviceIntegrityReport.hasAnyWarning) return
    if (!viewModel.uiState.value.isAppLockEnabled) return
    val capability = AppLockManager.checkCapability(this)
    if (capability != AppLockCapability.Available) {
      viewModel.updateAppLockCapability(capability)
      return
    }
    if (viewModel.uiState.value.isAuthenticating) return

    viewModel.onAuthenticationStarted()
    AppLockManager.authenticate(
      activity = this,
      title = getString(R.string.applock_prompt_title),
      subtitle = getString(R.string.applock_prompt_subtitle),
      onSuccess = { viewModel.onUnlockSucceeded() },
      onCancelExit = { exitApplication() },
      onError = { message -> viewModel.onUnlockFailed(message) },
      onFinished = { viewModel.onAuthenticationFinished() },
    )
  }

  private fun exitApplication() {
    finishAndRemoveTask()
  }

  private fun openDeviceSecuritySettings() {
    val intent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
          putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            AppLockManager.allowedAuthenticators(),
          )
        }
      } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
      }
    runCatching { startActivity(intent) }
      .onFailure {
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
      }
  }

  private fun openDeveloperOrSystemSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
      .onFailure {
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
      }
  }
}
