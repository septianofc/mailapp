package com.example.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

data class DeviceIntegrityReport(
  val isRootOrMagiskSuspected: Boolean = false,
  val rootSignals: List<String> = emptyList(),
  val isDeveloperOptionsEnabled: Boolean = false,
  val isUsbDebuggingEnabled: Boolean = false,
) {
  val hasAnyWarning: Boolean
    get() = isRootOrMagiskSuspected || isDeveloperOptionsEnabled || isUsbDebuggingEnabled
}

interface DeviceSecurityInspector {
  fun inspect(): DeviceIntegrityReport
}

/**
 * Automatic heuristic device integrity inspector.
 * Note: Client-side detection of root, Magisk, superuser, Developer Options, and USB Debugging
 * provides risk awareness and cannot guarantee 100% detection accuracy.
 */
class AndroidDeviceSecurityInspector(private val context: Context) : DeviceSecurityInspector {

  override fun inspect(): DeviceIntegrityReport {
    val signals = mutableListOf<String>()

    if (hasTestKeys()) {
      signals.add("Build test-keys")
    }
    if (hasKnownRootOrMagiskBinary()) {
      signals.add("Binary/path su atau Magisk")
    }
    if (hasKnownRootManagementPackage()) {
      signals.add("Aplikasi pengelola root/Magisk/hooking")
    }
    if (hasInsecureSystemProperties()) {
      signals.add("Properti sistem debuggable/non-secure")
    }

    val devOptionsEnabled = isGlobalSettingEnabled(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
    val usbDebuggingEnabled = isGlobalSettingEnabled(Settings.Global.ADB_ENABLED)

    return DeviceIntegrityReport(
      isRootOrMagiskSuspected = signals.isNotEmpty(),
      rootSignals = signals,
      isDeveloperOptionsEnabled = devOptionsEnabled,
      isUsbDebuggingEnabled = usbDebuggingEnabled,
    )
  }

  private fun hasTestKeys(): Boolean {
    return Build.TAGS?.contains("test-keys", ignoreCase = true) == true
  }

  private fun hasKnownRootOrMagiskBinary(): Boolean {
    return SUSPICIOUS_PATHS.any { path ->
      try {
        File(path).exists()
      } catch (_: SecurityException) {
        false
      }
    }
  }

  private fun hasKnownRootManagementPackage(): Boolean {
    val pm = context.packageManager
    return KNOWN_ROOT_PACKAGES.any { pkg -> isPackageInstalled(pm, pkg) }
  }

  private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    } catch (_: Exception) {
      false
    }
  }

  @SuppressLint("PrivateApi")
  private fun hasInsecureSystemProperties(): Boolean {
    return try {
      val sysProps = Class.forName("android.os.SystemProperties")
      val getMethod = sysProps.getMethod("get", String::class.java, String::class.java)
      val roDebuggable = getMethod.invoke(null, "ro.debuggable", "0") as? String
      val roSecure = getMethod.invoke(null, "ro.secure", "1") as? String
      val adbRoot = getMethod.invoke(null, "service.adb.root", "0") as? String
      roDebuggable == "1" || roSecure == "0" || adbRoot == "1"
    } catch (_: Exception) {
      false
    }
  }

  private fun isGlobalSettingEnabled(key: String): Boolean {
    return try {
      Settings.Global.getInt(context.contentResolver, key, 0) != 0
    } catch (_: Exception) {
      false
    }
  }

  companion object {
    val SUSPICIOUS_PATHS =
      listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/daemonsu",
        "/system/xbin/busybox",
        "/sbin/.magisk",
        "/debug_ramdisk/.magisk",
        "/data/adb/magisk",
        "/data/adb/magisk.db",
        "/data/adb/ksu",
        "/data/adb/ap",
      )

    val KNOWN_ROOT_PACKAGES =
      listOf(
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "me.weishu.kernelsu",
        "me.bmax.apatch",
        "org.lsposed.manager",
        "de.robv.android.xposed.installer",
      )
  }
}
