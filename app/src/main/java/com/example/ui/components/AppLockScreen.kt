package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.AppLockState
import com.example.ui.theme.BrandBlueBorder
import com.example.ui.theme.BrandBlueDark
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSoftContainer
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecurityDangerRed
import com.example.ui.theme.SecurityDangerRedContainer

@Composable
fun AppLockScreen(
  appLockState: AppLockState,
  authErrorMessage: String?,
  onUnlockClick: () -> Unit,
  onOpenDeviceSecuritySettings: () -> Unit,
  onRecheckCapability: () -> Unit,
  onContinueWithoutCredential: () -> Unit,
  onExitApp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onExitApp)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(BrandBlueSoftContainer.copy(alpha = 0.65f), PureWhite, PureWhite)
          )
        )
        .safeDrawingPadding()
        .testTag("app_lock_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier =
        Modifier.widthIn(max = 460.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
    ) {
      Box(contentAlignment = Alignment.BottomEnd) {
        Surface(
          shape = CircleShape,
          shadowElevation = 6.dp,
          color = PureWhite,
          modifier =
            Modifier.size(88.dp).border(2.5.dp, BrandBluePrimary, CircleShape),
        ) {
          Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = stringResource(id = R.string.cd_brand_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape),
          )
        }
        Surface(
          shape = CircleShape,
          color = BrandBluePrimary,
          shadowElevation = 4.dp,
          modifier = Modifier.size(30.dp).border(2.dp, PureWhite, CircleShape),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = PureWhite,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, BrandBlueBorder.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp),
        ) {
          val isNoCredential = appLockState is AppLockState.NoDeviceCredential
          val hardwareUnavailable = appLockState as? AppLockState.HardwareUnavailable

          Text(
            text =
              when {
                isNoCredential -> stringResource(id = R.string.applock_no_credential_title)
                hardwareUnavailable != null ->
                  stringResource(id = R.string.applock_no_credential_title)
                else -> stringResource(id = R.string.applock_title)
              },
            style = MaterialTheme.typography.headlineSmall,
            color = BrandBlueDark,
            textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text =
              when {
                isNoCredential -> stringResource(id = R.string.applock_no_credential_desc)
                hardwareUnavailable != null -> hardwareUnavailable.reason
                else -> stringResource(id = R.string.applock_subtitle)
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          if (!authErrorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = SecurityDangerRedContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = stringResource(id = R.string.applock_error_prefix, authErrorMessage),
                style = MaterialTheme.typography.bodySmall,
                color = SecurityDangerRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(22.dp))

          if (!isNoCredential && hardwareUnavailable == null) {
            Button(
              onClick = onUnlockClick,
              colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.fillMaxWidth().height(50.dp).testTag("unlock_biometric_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = stringResource(id = R.string.applock_button_unlock))
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
              onClick = onExitApp,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("applock_exit_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(id = R.string.applock_button_exit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Button(
              onClick = onOpenDeviceSecuritySettings,
              colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.fillMaxWidth().height(50.dp).testTag("open_security_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = stringResource(id = R.string.applock_open_device_settings))
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
              onClick = onRecheckCapability,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("recheck_lock_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = stringResource(id = R.string.applock_recheck))
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
              onClick = onContinueWithoutCredential,
              modifier = Modifier.fillMaxWidth().testTag("continue_unprotected_button"),
            ) {
              Text(
                text = stringResource(id = R.string.applock_continue_unprotected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(id = R.string.version_label),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
