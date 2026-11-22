package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AppLockScreen
import com.example.ui.components.SecureWebViewContainer
import com.example.ui.components.SecurityWarningBanner
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SplashOverlay
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSoftContainer
import com.example.ui.theme.PureWhite

@Composable
fun MailSeptianApp(
  uiState: MainUiState,
  onTriggerBiometricUnlock: () -> Unit,
  onOpenDeviceSecuritySettings: () -> Unit,
  onRecheckLockCapability: () -> Unit,
  onContinueWithoutCredential: () -> Unit,
  onToggleAntiScreenshot: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  onCloseSettings: () -> Unit,
  onToggleWarningExpanded: () -> Unit,
  onWebProgressChanged: (Int) -> Unit,
  onWebPageStarted: () -> Unit,
  onWebPageFinished: () -> Unit,
  onWebNetworkError: (String) -> Unit,
  onWebSslError: () -> Unit,
  onNavigationBlocked: (String) -> Unit,
  onClearBlockedNotice: () -> Unit,
  onRetryWebLoad: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize().background(PureWhite)) {
    // 1. Unlocked Full-Screen WebView Workspace
    if (uiState.appLockState is AppLockState.Unlocked) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
      ) {
        SecureWebViewContainer(
          webProgress = uiState.webProgress,
          isWebLoading = uiState.isWebLoading,
          webErrorState = uiState.webErrorState,
          reloadTrigger = uiState.reloadTrigger,
          blockedNotice = uiState.lastBlockedNotice,
          onProgressChanged = onWebProgressChanged,
          onPageStarted = onWebPageStarted,
          onPageFinished = onWebPageFinished,
          onNetworkError = onWebNetworkError,
          onSslError = onWebSslError,
          onNavigationBlocked = onNavigationBlocked,
          onClearBlockedNotice = onClearBlockedNotice,
          onRetry = onRetryWebLoad,
          modifier = Modifier.fillMaxSize(),
        )

        // Top overlay: Minimal top-left settings button + automatic security warning banner
        Row(
          verticalAlignment = Alignment.Top,
          modifier =
            Modifier.fillMaxWidth()
              .align(Alignment.TopStart)
              .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = PureWhite.copy(alpha = 0.95f),
            shadowElevation = 5.dp,
            border = BorderStroke(1.dp, BrandBlueSoftContainer),
            modifier = Modifier.size(48.dp).testTag("settings_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(id = R.string.cd_open_settings),
                tint = BrandBluePrimary,
                modifier = Modifier.size(24.dp),
              )
            }
          }

          if (uiState.deviceIntegrityReport.hasAnyWarning) {
            Spacer(modifier = Modifier.width(10.dp))
            SecurityWarningBanner(
              report = uiState.deviceIntegrityReport,
              expanded = uiState.isWarningExpanded,
              onToggleExpand = onToggleWarningExpanded,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    } else if (!uiState.showSplash) {
      // 2. App Lock Gate
      AppLockScreen(
        appLockState = uiState.appLockState,
        authErrorMessage = uiState.authErrorMessage,
        deviceIntegrityReport = uiState.deviceIntegrityReport,
        onUnlockClick = onTriggerBiometricUnlock,
        onOpenDeviceSecuritySettings = onOpenDeviceSecuritySettings,
        onRecheckCapability = onRecheckLockCapability,
        onContinueWithoutCredential = onContinueWithoutCredential,
      )
    }

    // 3. Brief Branded Splash Screen
    AnimatedVisibility(
      visible = uiState.showSplash,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      SplashOverlay()
    }

    // 4. Settings Sheet (only Anti Screenshot ON/OFF is editable)
    if (uiState.isSettingsOpen) {
      SettingsSheet(
        antiScreenshotEnabled = uiState.antiScreenshotEnabled,
        deviceIntegrityReport = uiState.deviceIntegrityReport,
        onToggleAntiScreenshot = onToggleAntiScreenshot,
        onDismiss = onCloseSettings,
      )
    }
  }
}
