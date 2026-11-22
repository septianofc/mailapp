package com.example.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface AppLockCapability {
  data object Available : AppLockCapability

  data object NoCredentialEnrolled : AppLockCapability

  data class Unavailable(val reason: String) : AppLockCapability
}

/**
 * Coordinates AndroidX BiometricPrompt with Biometrics and DEVICE_CREDENTIAL (PIN/Pattern/Password)
 * according to the device's Android OS level. Never stores or requests any plaintext PIN.
 */
object AppLockManager {

  fun allowedAuthenticators(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
      BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
  }

  fun checkCapability(activity: FragmentActivity): AppLockCapability {
    val manager = BiometricManager.from(activity)
    return when (manager.canAuthenticate(allowedAuthenticators())) {
      BiometricManager.BIOMETRIC_SUCCESS -> AppLockCapability.Available
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AppLockCapability.NoCredentialEnrolled
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        AppLockCapability.Unavailable("Perangkat tidak memiliki modul autentikasi yang didukung.")
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
        AppLockCapability.Unavailable("Modul biometrik sedang tidak tersedia.")
      BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
        AppLockCapability.Unavailable("Pembaruan keamanan sistem diperlukan untuk autentikasi.")
      else -> AppLockCapability.Unavailable("Autentikasi perangkat tidak tersedia.")
    }
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onCancelExit: () -> Unit,
    onError: (String) -> Unit,
    onFinished: () -> Unit,
  ) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback =
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          onFinished()
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          onFinished()
          when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
              // User canceled system PIN/Fingerprint prompt -> exit the APK immediately
              onCancelExit()
            }
            else -> onError(errString.toString())
          }
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          // Keep prompt open so user can retry biometric or switch to DEVICE_CREDENTIAL
        }
      }

    val prompt = BiometricPrompt(activity, executor, callback)
    // Note: DEVICE_CREDENTIAL cannot be combined with setNegativeButtonText().
    val promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(allowedAuthenticators())
        .build()

    prompt.authenticate(promptInfo)
  }
}
