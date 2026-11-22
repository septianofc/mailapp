package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.DeviceIntegrityReport
import com.example.ui.theme.BrandBlueDark
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSoftContainer
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecuritySafeGreen
import com.example.ui.theme.SecuritySafeGreenContainer
import com.example.ui.theme.SecurityWarningAmber
import com.example.ui.theme.SecurityWarningAmberContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SoftWhiteBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
  antiScreenshotEnabled: Boolean,
  deviceIntegrityReport: DeviceIntegrityReport,
  onToggleAntiScreenshot: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite,
    modifier = Modifier.testTag("settings_sheet"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
    ) {
      // Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = CircleShape,
          color = BrandBlueSoftContainer,
          modifier = Modifier.size(44.dp).border(1.5.dp, BrandBluePrimary, CircleShape),
        ) {
          Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = stringResource(id = R.string.cd_brand_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.padding(2.dp).clip(CircleShape),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = BrandBlueDark,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(id = R.string.settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp).testTag("close_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.close),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ONLY changeable user option: Anti Screenshot (FLAG_SECURE)
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = BrandBlueSoftContainer.copy(alpha = 0.5f),
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, BrandBluePrimary.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier.size(42.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.ScreenshotMonitor,
              contentDescription = null,
              tint = BrandBluePrimary,
              modifier = Modifier.padding(9.dp),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = stringResource(id = R.string.anti_screenshot_title),
                style = MaterialTheme.typography.titleMedium,
                color = BrandBlueDark,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                shape = RoundedCornerShape(50),
                color = if (antiScreenshotEnabled) BrandBluePrimary else SlateBorder,
              ) {
                Text(
                  text =
                    stringResource(
                      id = if (antiScreenshotEnabled) R.string.status_on else R.string.status_off
                    ),
                  style = MaterialTheme.typography.labelMedium,
                  color = PureWhite,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stringResource(id = R.string.anti_screenshot_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Switch(
            checked = antiScreenshotEnabled,
            onCheckedChange = onToggleAntiScreenshot,
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = BrandBluePrimary,
              ),
            modifier = Modifier.testTag("anti_screenshot_switch"),
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Read-only automatic device security status (no toggles to disable)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.Shield,
          contentDescription = null,
          tint = BrandBluePrimary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(id = R.string.security_status_section_title),
          style = MaterialTheme.typography.labelMedium,
          color = BrandBlueDark,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = SoftWhiteBackground,
        modifier = Modifier.fillMaxWidth().border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          SecurityCheckRow(
            label = stringResource(id = R.string.flag_root_magisk),
            detected = deviceIntegrityReport.isRootOrMagiskSuspected,
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SlateBorder)
          SecurityCheckRow(
            label = stringResource(id = R.string.flag_dev_options),
            detected = deviceIntegrityReport.isDeveloperOptionsEnabled,
          )
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SlateBorder)
          SecurityCheckRow(
            label = stringResource(id = R.string.flag_usb_debugging),
            detected = deviceIntegrityReport.isUsbDebuggingEnabled,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = stringResource(id = R.string.security_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Read-only app & APK protection info
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.Info,
          contentDescription = null,
          tint = BrandBluePrimary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(id = R.string.about_section_title),
          style = MaterialTheme.typography.labelMedium,
          color = BrandBlueDark,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = SoftWhiteBackground,
        modifier = Modifier.fillMaxWidth().border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = BrandBlueDark,
          )
          Text(
            text = stringResource(id = R.string.version_label),
            style = MaterialTheme.typography.bodySmall,
            color = BrandBluePrimary,
            fontWeight = FontWeight.Medium,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Allowlist: ${stringResource(id = R.string.about_host_value)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(id = R.string.about_apk_protection),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SecurityCheckRow(label: String, detected: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Surface(
      shape = RoundedCornerShape(50),
      color = if (detected) SecurityWarningAmberContainer else SecuritySafeGreenContainer,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Icon(
          imageVector = if (detected) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
          contentDescription = null,
          tint = if (detected) SecurityWarningAmber else SecuritySafeGreen,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text =
            stringResource(id = if (detected) R.string.status_detected else R.string.status_clean),
          style = MaterialTheme.typography.labelMedium,
          color = if (detected) SecurityWarningAmber else SecuritySafeGreen,
        )
      }
    }
  }
}
