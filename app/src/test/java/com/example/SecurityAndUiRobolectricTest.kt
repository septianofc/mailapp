package com.example

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.SecurityPreferencesRepository
import com.example.security.AndroidDeviceSecurityInspector
import com.example.security.DeviceIntegrityReport
import com.example.security.DeviceSecurityInspector
import com.example.ui.AppLockState
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.AppLockScreen
import com.example.ui.components.SecurityWarningBanner
import com.example.ui.components.SettingsSheet
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
@Config(sdk = [36])
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

  private class FakePreferencesRepository(initial: Boolean = true) : SecurityPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val antiScreenshotEnabled: Flow<Boolean> = state

    override suspend fun setAntiScreenshotEnabled(enabled: Boolean) {
      state.value = enabled
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
      val prefs = FakePreferencesRepository(initial = true)
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
  fun `settings sheet toggles anti screenshot switch`() {
    var toggledValue: Boolean? = null
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        SettingsSheet(
          antiScreenshotEnabled = true,
          deviceIntegrityReport = DeviceIntegrityReport(isDeveloperOptionsEnabled = true),
          onToggleAntiScreenshot = { toggledValue = it },
          onDismiss = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("anti_screenshot_switch").assertIsOn()
    composeTestRule.onNodeWithTag("anti_screenshot_switch").performClick()
    composeTestRule.waitForIdle()

    assertFalse(toggledValue ?: true)
  }

  @Test
  fun `security warning banner renders when risk detected`() {
    var expanded = false
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        SecurityWarningBanner(
          report =
            DeviceIntegrityReport(
              isRootOrMagiskSuspected = true,
              rootSignals = listOf("Binary/path su atau Magisk"),
              isUsbDebuggingEnabled = true,
            ),
          expanded = expanded,
          onToggleExpand = { expanded = !expanded },
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("security_warning_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("security_warning_banner").performClick()
    assertTrue(expanded)
  }

  @Test
  fun `applock screen shows unlock action when locked`() {
    var unlockClicked = false
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        AppLockScreen(
          appLockState = AppLockState.Locked,
          authErrorMessage = null,
          deviceIntegrityReport = DeviceIntegrityReport(),
          onUnlockClick = { unlockClicked = true },
          onOpenDeviceSecuritySettings = {},
          onRecheckCapability = {},
          onContinueWithoutCredential = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("app_lock_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("unlock_biometric_button").assertIsDisplayed().performClick()
    assertTrue(unlockClicked)
  }

  @Test
  fun `settings sheet shows off state when anti screenshot disabled`() {
    composeTestRule.setContent {
      MailSeptianSupportTheme {
        SettingsSheet(
          antiScreenshotEnabled = false,
          deviceIntegrityReport = DeviceIntegrityReport(),
          onToggleAntiScreenshot = {},
          onDismiss = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("anti_screenshot_switch").assertIsOff()
  }
}
