package com.example

import com.example.data.SecurityPreferencesRepository
import com.example.security.AppLockCapability
import com.example.security.DeviceIntegrityReport
import com.example.security.DeviceSecurityInspector
import com.example.ui.AppLockState
import com.example.ui.MainViewModel
import com.example.ui.WebErrorState
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

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

  private class FakeSecurityInspector(private var report: DeviceIntegrityReport) :
    DeviceSecurityInspector {
    override fun inspect(): DeviceIntegrityReport = report
  }

  @Test
  fun `splash finishes and reflects device integrity warnings`() =
    runTest(testDispatcher) {
      val inspector =
        FakeSecurityInspector(
          DeviceIntegrityReport(
            isRootOrMagiskSuspected = true,
            rootSignals = listOf("Build test-keys"),
            isDeveloperOptionsEnabled = true,
            isUsbDebuggingEnabled = false,
          )
        )
      val vm = MainViewModel(FakePreferencesRepository(true), inspector, testDispatcher)

      assertTrue(vm.uiState.value.showSplash)
      advanceTimeBy(MainViewModel.SPLASH_DURATION_MS + 50L)

      assertFalse(vm.uiState.value.showSplash)
      assertTrue(vm.uiState.value.deviceIntegrityReport.hasAnyWarning)
      assertTrue(vm.uiState.value.deviceIntegrityReport.isRootOrMagiskSuspected)
      assertTrue(vm.uiState.value.deviceIntegrityReport.isDeveloperOptionsEnabled)
    }

  @Test
  fun `app lock transitions and background relock work properly`() =
    runTest(testDispatcher) {
      val vm =
        MainViewModel(
          FakePreferencesRepository(true),
          FakeSecurityInspector(DeviceIntegrityReport()),
          testDispatcher,
        )

      vm.updateAppLockCapability(AppLockCapability.Available)
      assertEquals(AppLockState.Locked, vm.uiState.value.appLockState)

      vm.onUnlockSucceeded()
      assertEquals(AppLockState.Unlocked, vm.uiState.value.appLockState)

      vm.lockOnBackground()
      assertEquals(AppLockState.Locked, vm.uiState.value.appLockState)
    }

  @Test
  fun `anti screenshot preference persists and toggles cleanly`() =
    runTest(testDispatcher) {
      val prefs = FakePreferencesRepository(initial = true)
      val vm = MainViewModel(prefs, FakeSecurityInspector(DeviceIntegrityReport()), testDispatcher)
      testDispatcher.scheduler.advanceUntilIdle()
      assertTrue(vm.uiState.value.antiScreenshotEnabled)

      vm.setAntiScreenshotEnabled(false)
      testDispatcher.scheduler.advanceUntilIdle()
      assertFalse(vm.uiState.value.antiScreenshotEnabled)
    }

  @Test
  fun `ssl error and retry update web error state`() =
    runTest(testDispatcher) {
      val vm =
        MainViewModel(
          FakePreferencesRepository(true),
          FakeSecurityInspector(DeviceIntegrityReport()),
          testDispatcher,
        )

      vm.onWebSslError()
      assertEquals(WebErrorState.SslValidationError, vm.uiState.value.webErrorState)

      vm.retryWebLoad()
      assertEquals(WebErrorState.None, vm.uiState.value.webErrorState)
      assertEquals(1, vm.uiState.value.reloadTrigger)
    }
}
