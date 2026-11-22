package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.NetworkStatus
import com.example.ui.components.AppLockScreen
import com.example.ui.components.NetworkStatusBanner
import com.example.ui.components.SecureWebViewContainer
import com.example.ui.components.SecurityBlockedScreen
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SplashOverlay
import com.example.ui.theme.BrandBlueBorder
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.PureWhite

@Composable
fun MailSeptianApp(
  uiState: MainUiState,
  onPortalVisibilityChanged: (Boolean) -> Unit = {},
  onTriggerBiometricUnlock: () -> Unit,
  onOpenDeviceSecuritySettings: () -> Unit,
  onOpenDeviceDeveloperSettings: () -> Unit,
  onRecheckLockCapability: () -> Unit,
  onRecheckDeviceIntegrity: () -> Unit,
  onContinueWithoutCredential: () -> Unit,
  onExitApp: () -> Unit,
  onToggleAntiScreenshot: (Boolean) -> Unit,
  onToggleAppLock: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  onCloseSettings: () -> Unit,
  onWebPageStarted: () -> Unit,
  onWebPageFinished: () -> Unit,
  onWebNetworkError: (String) -> Unit,
  onWebSslError: () -> Unit,
  onNavigationBlocked: (String) -> Unit,
  onClearBlockedNotice: () -> Unit,
  onRetryWebLoad: () -> Unit,
  onGoHomeWebLoad: () -> Unit = {},
) {
  val isPortalActive =
    !uiState.showSplash &&
      !uiState.deviceIntegrityReport.hasAnyWarning &&
      (!uiState.isAppLockEnabled || uiState.appLockState is AppLockState.Unlocked)

  LaunchedEffect(isPortalActive) {
    onPortalVisibilityChanged(isPortalActive)
  }

  Box(modifier = Modifier.fillMaxSize().background(PureWhite)) {
    when {
      uiState.showSplash -> {
        // Rendered via AnimatedVisibility below
      }
      // 1. Strict full-screen block whenever Root, Magisk, Superuser, Developer Options,
      //    or USB Debugging is detected (no toggle to bypass).
      uiState.deviceIntegrityReport.hasAnyWarning -> {
        SecurityBlockedScreen(
          report = uiState.deviceIntegrityReport,
          onOpenDeviceSettings = onOpenDeviceDeveloperSettings,
          onRecheckSecurity = onRecheckDeviceIntegrity,
          onExitApp = onExitApp,
        )
      }
      // 2. App Lock gate (active when enabled by user or system default)
      uiState.isAppLockEnabled && uiState.appLockState !is AppLockState.Unlocked -> {
        AppLockScreen(
          appLockState = uiState.appLockState,
          authErrorMessage = uiState.authErrorMessage,
          onUnlockClick = onTriggerBiometricUnlock,
          onOpenDeviceSecuritySettings = onOpenDeviceSecuritySettings,
          onRecheckCapability = onRecheckLockCapability,
          onContinueWithoutCredential = onContinueWithoutCredential,
          onExitApp = onExitApp,
        )
      }
      // 3. Unlocked Full-Screen Native-Looking Portal + Bottom Network Indicator + Bottom-Right Settings
      else -> {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .imePadding()
        ) {
          SecureWebViewContainer(
            webErrorState = uiState.webErrorState,
            isNetworkOnline = uiState.networkStatus != NetworkStatus.Offline,
            reloadTrigger = uiState.reloadTrigger,
            goHomeTrigger = uiState.goHomeTrigger,
            blockedNotice = uiState.lastBlockedNotice,
            onPageStarted = onWebPageStarted,
            onPageFinished = onWebPageFinished,
            onNetworkError = onWebNetworkError,
            onSslError = onWebSslError,
            onNavigationBlocked = onNavigationBlocked,
            onClearBlockedNotice = onClearBlockedNotice,
            onRetry = onRetryWebLoad,
            onGoHome = onGoHomeWebLoad,
            modifier = Modifier.fillMaxSize(),
          )

          // Bottom Network Indicator (Offline stays visible; Online/Unstable auto-hide after 5s)
          AnimatedVisibility(
            visible = uiState.isNetworkBannerVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier =
              Modifier.align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 78.dp, bottom = 19.dp),
          ) {
            NetworkStatusBanner(
              status = uiState.networkStatus,
              isRecovered = uiState.isNetworkRecovered,
            )
          }

          // Bottom-Right floating Settings button
          Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = PureWhite.copy(alpha = 0.96f),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, BrandBlueBorder),
            modifier =
              Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(50.dp)
                .testTag("settings_button"),
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
        }
      }
    }

    // Brief Branded Splash Screen
    AnimatedVisibility(
      visible = uiState.showSplash,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      SplashOverlay()
    }

    // Settings Sheet
    if (uiState.isSettingsOpen && !uiState.deviceIntegrityReport.hasAnyWarning) {
      SettingsSheet(
        antiScreenshotEnabled = uiState.antiScreenshotEnabled,
        appLockEnabled = uiState.isAppLockEnabled,
        appLockFollowingSystem = uiState.isAppLockFollowingSystem,
        deviceIntegrityReport = uiState.deviceIntegrityReport,
        onToggleAntiScreenshot = onToggleAntiScreenshot,
        onToggleAppLock = onToggleAppLock,
        onDismiss = onCloseSettings,
      )
    }
  }
}
