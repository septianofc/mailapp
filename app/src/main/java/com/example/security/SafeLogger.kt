package com.example.security

import android.util.Log
import com.example.BuildConfig

/**
 * Debug-only logger wrapper.
 * Release builds set BuildConfig.ENABLE_LOGS = false and strip calls via R8 ProGuard rules.
 */
object SafeLogger {
  private const val TAG = "MailSeptianSec"

  @JvmStatic
  fun d(message: String) {
    if (BuildConfig.ENABLE_LOGS && BuildConfig.DEBUG) {
      Log.d(TAG, message)
    }
  }

  @JvmStatic
  fun w(message: String) {
    if (BuildConfig.ENABLE_LOGS && BuildConfig.DEBUG) {
      Log.w(TAG, message)
    }
  }

  @JvmStatic
  fun i(message: String) {
    if (BuildConfig.ENABLE_LOGS && BuildConfig.DEBUG) {
      Log.i(TAG, message)
    }
  }
}
