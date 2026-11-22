package com.example

import com.example.data.SecurityPreferencesRepository
import com.example.security.AppLockCapability
import com.example.security.DeviceIntegrityReport
import com.example.security.DeviceSecurityInspector
import com.example.security.NetworkStatus
import com.example.security.NetworkStatusMonitor
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

  private class FakeSecurityInspector(private var report: DeviceIntegrityReport) :
    DeviceSecurityInspector {
    override fun inspect(): DeviceIntegrityReport = report
  }

  private class FakeNetworkMonitor(initialStatus: NetworkStatus = NetworkStatus.Online) :
    NetworkStatusMonitor {
    val state = MutableStateFlow(initialStatus)

    override fun currentStatus(): NetworkStatus = state.value

    override fun observeStatus(): Flow<NetworkStatus> = state
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
      val vm = MainViewModel(FakePreferencesRepository(), inspector, testDispatcher)

      assertTrue(vm.uiState.value.showSplash)
      advanceTimeBy(MainViewModel.SPLASH_DURATION_MS + 50L)

      assertFalse(vm.uiState.value.showSplash)
      assertTrue(vm.uiState.value.deviceIntegrityReport.hasAnyWarning)
      assertTrue(vm.uiState.value.deviceIntegrityReport.isRootOrMagiskSuspected)
      assertTrue(vm.uiState.value.deviceIntegrityReport.isDeveloperOptionsEnabled)
    }

  @Test
  fun `app lock default follows system capability when preference is unset`() =
    runTest(testDispatcher) {
      val vm =
        MainViewModel(
          FakePreferencesRepository(initialAppLockPref = null),
          FakeSecurityInspector(DeviceIntegrityReport()),
          testDispatcher,
        )

      vm.updateAppLockCapability(AppLockCapability.NoCredentialEnrolled)
      assertTrue(vm.uiState.value.isAppLockFollowingSystem)
      assertFalse(vm.uiState.value.isAppLockEnabled)
      assertEquals(AppLockState.Unlocked, vm.uiState.value.appLockState)

      vm.updateAppLockCapability(AppLockCapability.Available)
      assertTrue(vm.uiState.value.isAppLockEnabled)
      vm.lockOnBackground()
      assertEquals(AppLockState.Locked, vm.uiState.value.appLockState)
    }

  @Test
  fun `app lock transitions and manual toggle off disable background relock`() =
    runTest(testDispatcher) {
      val vm =
        MainViewModel(
          FakePreferencesRepository(),
          FakeSecurityInspector(DeviceIntegrityReport()),
          testDispatcher,
        )

      vm.updateAppLockCapability(AppLockCapability.Available)
      assertEquals(AppLockState.Locked, vm.uiState.value.appLockState)

      vm.onUnlockSucceeded()
      assertEquals(AppLockState.Unlocked, vm.uiState.value.appLockState)

      vm.lockOnBackground()
      assertEquals(AppLockState.Locked, vm.uiState.value.appLockState)

      vm.setAppLockEnabled(false)
      testDispatcher.scheduler.advanceUntilIdle()
      assertFalse(vm.uiState.value.isAppLockFollowingSystem)
      assertFalse(vm.uiState.value.isAppLockEnabled)
      assertEquals(AppLockState.Unlocked, vm.uiState.value.appLockState)

      vm.lockOnBackground()
      assertEquals(AppLockState.Unlocked, vm.uiState.value.appLockState)

      vm.setAppLockEnabled(true)
      testDispatcher.scheduler.advanceUntilIdle()
      assertTrue(vm.uiState.value.isAppLockEnabled)
      vm.lockOnBackground()
      assertEquals(AppLockState.Locked, vm.uiState.value.appLockState)
    }

  @Test
  fun `anti screenshot preference persists and toggles cleanly`() =
    runTest(testDispatcher) {
      val prefs = FakePreferencesRepository(initialAntiScreenshot = true)
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
          FakePreferencesRepository(),
          FakeSecurityInspector(DeviceIntegrityReport()),
          testDispatcher,
        )

      vm.onWebSslError()
      assertEquals(WebErrorState.SslValidationError, vm.uiState.value.webErrorState)

      vm.retryWebLoad()
      assertEquals(WebErrorState.None, vm.uiState.value.webErrorState)
      assertEquals(1, vm.uiState.value.reloadTrigger)

      vm.onWebNetworkError("Gangguan server")
      assertTrue(vm.uiState.value.webErrorState is WebErrorState.NetworkError)
      vm.goHomeWebLoad()
      assertEquals(WebErrorState.None, vm.uiState.value.webErrorState)
      assertEquals(1, vm.uiState.value.goHomeTrigger)
    }

  @Test
  fun `network indicator stays visible while offline and auto-hides after 5s when online or unstable`() =
    runTest(testDispatcher) {
      val monitor = FakeNetworkMonitor(NetworkStatus.Online)
      val vm =
        MainViewModel(
          FakePreferencesRepository(),
          FakeSecurityInspector(DeviceIntegrityReport()),
          testDispatcher,
          monitor,
        )

      advanceTimeBy(MainViewModel.SPLASH_DURATION_MS + 10L)
      vm.onPortalVisibilityChanged(true)

      // Initial Online shows for 5 seconds then hides
      assertEquals(NetworkStatus.Online, vm.uiState.value.networkStatus)
      assertTrue(vm.uiState.value.isNetworkBannerVisible)
      assertFalse(vm.uiState.value.isNetworkRecovered)

      advanceTimeBy(MainViewModel.NETWORK_BANNER_DURATION_MS + 50L)
      assertFalse(vm.uiState.value.isNetworkBannerVisible)

      // Unstable shows for 5 seconds then hides
      monitor.state.value = NetworkStatus.Unstable
      advanceTimeBy(50L)
      assertEquals(NetworkStatus.Unstable, vm.uiState.value.networkStatus)
      assertTrue(vm.uiState.value.isNetworkBannerVisible)

      advanceTimeBy(MainViewModel.NETWORK_BANNER_DURATION_MS + 50L)
      assertFalse(vm.uiState.value.isNetworkBannerVisible)

      // Offline remains continuously visible even well past 5 seconds
      monitor.state.value = NetworkStatus.Offline
      advanceTimeBy(50L)
      assertEquals(NetworkStatus.Offline, vm.uiState.value.networkStatus)
      assertTrue(vm.uiState.value.isNetworkBannerVisible)

      advanceTimeBy(MainViewModel.NETWORK_BANNER_DURATION_MS * 3)
      assertTrue(vm.uiState.value.isNetworkBannerVisible)

      // Returning Online shows recovered message for 5 seconds then hides
      monitor.state.value = NetworkStatus.Online
      advanceTimeBy(50L)
      assertEquals(NetworkStatus.Online, vm.uiState.value.networkStatus)
      assertTrue(vm.uiState.value.isNetworkBannerVisible)
      assertTrue(vm.uiState.value.isNetworkRecovered)

      advanceTimeBy(MainViewModel.NETWORK_BANNER_DURATION_MS + 50L)
      assertFalse(vm.uiState.value.isNetworkBannerVisible)
    }
}
