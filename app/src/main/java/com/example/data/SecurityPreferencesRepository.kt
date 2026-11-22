package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "mail_septian_security_prefs")

interface SecurityPreferencesRepository {
  val antiScreenshotEnabled: Flow<Boolean>

  /** Null means no manual override has been set, so App Lock follows system lock availability. */
  val appLockPreference: Flow<Boolean?>

  suspend fun setAntiScreenshotEnabled(enabled: Boolean)

  suspend fun setAppLockEnabled(enabled: Boolean)
}

class DataStoreSecurityPreferencesRepository(private val context: Context) :
  SecurityPreferencesRepository {

  override val antiScreenshotEnabled: Flow<Boolean> =
    context.securityDataStore.data.map { prefs -> prefs[KEY_ANTI_SCREENSHOT] ?: true }

  override val appLockPreference: Flow<Boolean?> =
    context.securityDataStore.data.map { prefs -> prefs[KEY_APP_LOCK_ENABLED] }

  override suspend fun setAntiScreenshotEnabled(enabled: Boolean) {
    context.securityDataStore.edit { prefs -> prefs[KEY_ANTI_SCREENSHOT] = enabled }
  }

  override suspend fun setAppLockEnabled(enabled: Boolean) {
    context.securityDataStore.edit { prefs -> prefs[KEY_APP_LOCK_ENABLED] = enabled }
  }

  private companion object {
    val KEY_ANTI_SCREENSHOT = booleanPreferencesKey("anti_screenshot_enabled")
    val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
  }
}
