package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.DeviceIntegrityReport
import com.example.ui.theme.BrandBlueDark
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSoftContainer
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecurityDangerBorder
import com.example.ui.theme.SecurityDangerRed
import com.example.ui.theme.SecurityDangerRedContainer
import com.example.ui.theme.SoftWhiteBackground

@Composable
fun SecurityBlockedScreen(
  report: DeviceIntegrityReport,
  onOpenDeviceSettings: () -> Unit,
  onRecheckSecurity: () -> Unit,
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
            colors = listOf(SecurityDangerRedContainer.copy(alpha = 0.45f), SoftWhiteBackground, PureWhite)
          )
        )
        .safeDrawingPadding()
        .testTag("security_blocked_screen"),
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
      Surface(
        shape = CircleShape,
        color = SecurityDangerRedContainer,
        shadowElevation = 6.dp,
        modifier =
          Modifier.size(88.dp).border(2.5.dp, SecurityDangerRed, CircleShape),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Block,
            contentDescription = stringResource(id = R.string.blocked_title),
            tint = SecurityDangerRed,
            modifier = Modifier.size(44.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, SecurityDangerBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(50),
            color = SecurityDangerRed,
          ) {
            Text(
              text = stringResource(id = R.string.blocked_subtitle).uppercase(),
              style = MaterialTheme.typography.labelMedium,
              color = PureWhite,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = stringResource(id = R.string.blocked_title),
            style = MaterialTheme.typography.headlineSmall,
            color = SecurityDangerRed,
            textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = stringResource(id = R.string.blocked_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(16.dp))

          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            if (report.isRootOrMagiskSuspected) {
              BlockedReasonItem(
                icon = Icons.Rounded.GppBad,
                title = stringResource(id = R.string.flag_root_magisk),
                detail = report.rootSignals.joinToString(", ").ifBlank { null },
              )
            }
            if (report.isDeveloperOptionsEnabled) {
              BlockedReasonItem(
                icon = Icons.Rounded.DeveloperMode,
                title = stringResource(id = R.string.flag_dev_options),
                detail = null,
              )
            }
            if (report.isUsbDebuggingEnabled) {
              BlockedReasonItem(
                icon = Icons.Rounded.Usb,
                title = stringResource(id = R.string.flag_usb_debugging),
                detail = null,
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = BrandBlueSoftContainer.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = stringResource(id = R.string.blocked_action_hint),
              style = MaterialTheme.typography.bodySmall,
              color = BrandBlueDark,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(12.dp),
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          if (report.isDeveloperOptionsEnabled || report.isUsbDebuggingEnabled) {
            Button(
              onClick = onOpenDeviceSettings,
              colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag("blocked_open_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = stringResource(id = R.string.blocked_open_settings))
            }

            Spacer(modifier = Modifier.height(10.dp))
          }

          OutlinedButton(
            onClick = onRecheckSecurity,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BrandBluePrimary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBlueDark),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("blocked_recheck_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.blocked_recheck))
          }

          Spacer(modifier = Modifier.height(8.dp))

          TextButton(
            onClick = onExitApp,
            colors = ButtonDefaults.textButtonColors(contentColor = SecurityDangerRed),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("blocked_exit_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(id = R.string.blocked_exit_app))
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = stringResource(id = R.string.security_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
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

@Composable
private fun BlockedReasonItem(
  icon: ImageVector,
  title: String,
  detail: String?,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = SecurityDangerRedContainer.copy(alpha = 0.75f),
    border = BorderStroke(1.dp, SecurityDangerBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SecurityDangerRed,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = SecurityDangerRed,
        )
        if (!detail.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = SecurityDangerRed.copy(alpha = 0.85f),
          )
        }
      }
    }
  }
}
