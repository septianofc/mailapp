package com.example

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.SecurityPreferencesRepository
import com.example.security.AndroidDeviceSecurityInspector
import com.example.security.DeviceIntegrityReport
import com.example.security.DeviceSecurityInspector
import com.example.security.NetworkStatus
import com.example.ui.AppLockState
import com.example.ui.MainViewModel
import com.example.ui.components.AppLockScreen
import com.example.ui.components.NetworkStatusBanner
import com.example.ui.components.SecurityBlockedScreen
import com.example.ui.components.SettingsSheetContent
import com.example.ui.theme.MailSeptianSupportTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityAndUiRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class FakePreferencesRepository(
    initialAntiScreenshot: Boolean = true,
    initialAppLockPref: Boolean? = null,
  ) : SecurityPreferencesRepository {
    private val antiScreenshotState = MutableStateFlow(initialAntiScreenshot)
    private val appLockState = MutableStateFlow(initialAppLockPref)

    override val antiScreenshotEnabled: Flow<Boolean> = antiScreenshotState
    override val appLockPreference: Flow<Boolean?> = appLockState

    override suspend fun setAntiScreenshotEnabled(enabled: Boolean) {
      antiScreenshotState.value = enabled
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
      appLockState.value = enabled
    }
  }

  private class FakeSecurityInspector(private val report: DeviceIntegrityReport) :
    DeviceSecurityInspector {
    override fun inspect(): DeviceIntegrityReport = report
  }

  @Test
  fun `android inspector detects developer options and usb debugging`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
    Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)

    val report = AndroidDeviceSecurityInspector(context).inspect()
    assertTrue(report.isDeveloperOptionsEnabled)
    assertTrue(report.isUsbDebuggingEnabled)
    assertTrue(report.hasAnyWarning)
  }

  @Test
  fun `viewmodel transitions splash lock and anti screenshot preference`() =
    runTest(testDispatcher) {
      val prefs = FakePreferencesRepository(initialAntiScreenshot = true)
      val inspector =
        FakeSecurityInspector(
          DeviceIntegrityReport(isUsbDebuggingEnabled = true)
        )
      val vm = MainViewModel(prefs, inspector, testDispatcher)

      assertTrue(vm.uiState.value.showSplash)
      assertTrue(vm.uiState.value.antiScreenshotEnabled)

      advanceTimeBy(MainViewModel.SPLASH_DURATION_MS + 50L)
      assertFalse(vm.uiState.value.showSplash)
      assertTrue(vm.uiState.value.deviceIntegrityReport.isUsbDebuggingEnabled)

      vm.setAntiScreenshotEnabled(false)
      testDispatcher.scheduler.advanceUntilIdle()
      assertFalse(vm.uiState.value.antiScreenshotEnabled)

      vm.onUnlockSucceeded()
      assertTrue(vm.uiState.value.appLockState is AppLockState.Unlocked)
    }

  @Test
  fun `settings sheet toggles anti screenshot and app lock switches`() {
    var toggledAntiScreenshot: Boolean? = null
    var toggledAppLock: Boolean? = null
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        SettingsSheetContent(
          antiScreenshotEnabled = true,
          appLockEnabled = true,
          appLockFollowingSystem = true,
          deviceIntegrityReport = DeviceIntegrityReport(isDeveloperOptionsEnabled = true),
          onToggleAntiScreenshot = { toggledAntiScreenshot = it },
          onToggleAppLock = { toggledAppLock = it },
          onDismiss = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_sheet_content").assertIsDisplayed()
    composeTestRule.onNodeWithTag("anti_screenshot_switch").performScrollTo().assertIsOn()
    composeTestRule.onNodeWithTag("anti_screenshot_switch").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("app_lock_switch").performScrollTo().assertIsOn()
    composeTestRule.onNodeWithTag("app_lock_switch").performClick()
    composeTestRule.waitForIdle()

    assertFalse(toggledAntiScreenshot ?: true)
    assertFalse(toggledAppLock ?: true)
  }

  @Test
  fun `security blocked screen renders and supports exit and recheck when risk detected`() {
    var recheckClicked = false
    var exitClicked = false
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        SecurityBlockedScreen(
          report =
            DeviceIntegrityReport(
              isRootOrMagiskSuspected = true,
              rootSignals = listOf("Binary/path su atau Magisk"),
              isDeveloperOptionsEnabled = true,
              isUsbDebuggingEnabled = true,
            ),
          onOpenDeviceSettings = {},
          onRecheckSecurity = { recheckClicked = true },
          onExitApp = { exitClicked = true },
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("security_blocked_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("blocked_recheck_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("blocked_exit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(recheckClicked)
    assertTrue(exitClicked)
  }

  @Test
  fun `applock screen shows unlock and exit actions when locked`() {
    var unlockClicked = false
    var exitClicked = false
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        AppLockScreen(
          appLockState = AppLockState.Locked,
          authErrorMessage = null,
          onUnlockClick = { unlockClicked = true },
          onOpenDeviceSecuritySettings = {},
          onRecheckCapability = {},
          onContinueWithoutCredential = {},
          onExitApp = { exitClicked = true },
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("app_lock_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("unlock_biometric_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("applock_exit_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(unlockClicked)
    assertTrue(exitClicked)
  }

  @Test
  fun `settings sheet shows off states when switches disabled`() {
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        SettingsSheetContent(
          antiScreenshotEnabled = false,
          appLockEnabled = false,
          appLockFollowingSystem = false,
          deviceIntegrityReport = DeviceIntegrityReport(),
          onToggleAntiScreenshot = {},
          onToggleAppLock = {},
          onDismiss = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("anti_screenshot_switch").performScrollTo().assertIsOff()
    composeTestRule.onNodeWithTag("app_lock_switch").performScrollTo().assertIsOff()
  }

  @Test
  fun `network status banner renders offline unstable and recovered online messages`() {
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        NetworkStatusBanner(status = NetworkStatus.Offline, isRecovered = false)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("network_status_indicator")
      .assertIsDisplayed()
      .assertTextContains("Offline", substring = true)
  }

  @Test
  fun `network status banner renders unstable state`() {
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        NetworkStatusBanner(status = NetworkStatus.Unstable, isRecovered = false)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("network_status_indicator")
      .assertIsDisplayed()
      .assertTextContains("kurang stabil", substring = true)
  }

  @Test
  fun `network status banner renders recovered online state`() {
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        NetworkStatusBanner(status = NetworkStatus.Online, isRecovered = true)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("network_status_indicator")
      .assertIsDisplayed()
      .assertTextContains("Kembali online", substring = true)
  }
}
