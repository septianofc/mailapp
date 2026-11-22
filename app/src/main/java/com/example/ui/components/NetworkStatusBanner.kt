package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.NetworkStatus
import com.example.ui.theme.SecurityDangerBorder
import com.example.ui.theme.SecurityDangerRed
import com.example.ui.theme.SecurityDangerRedContainer
import com.example.ui.theme.SecuritySafeGreen
import com.example.ui.theme.SecuritySafeGreenContainer
import com.example.ui.theme.SecurityWarningAmber
import com.example.ui.theme.SecurityWarningAmberContainer

@Composable
fun NetworkStatusBanner(
  status: NetworkStatus,
  isRecovered: Boolean,
  modifier: Modifier = Modifier,
) {
  val containerColor =
    when (status) {
      NetworkStatus.Offline -> SecurityDangerRedContainer
      NetworkStatus.Unstable -> SecurityWarningAmberContainer
      NetworkStatus.Online -> SecuritySafeGreenContainer
    }
  val borderColor =
    when (status) {
      NetworkStatus.Offline -> SecurityDangerBorder
      NetworkStatus.Unstable -> SecurityWarningAmber.copy(alpha = 0.45f)
      NetworkStatus.Online -> SecuritySafeGreen.copy(alpha = 0.45f)
    }
  val contentColor =
    when (status) {
      NetworkStatus.Offline -> SecurityDangerRed
      NetworkStatus.Unstable -> SecurityWarningAmber
      NetworkStatus.Online -> SecuritySafeGreen
    }
  val icon =
    when (status) {
      NetworkStatus.Offline -> Icons.Rounded.WifiOff
      NetworkStatus.Unstable -> Icons.Rounded.NetworkCheck
      NetworkStatus.Online -> Icons.Rounded.CheckCircle
    }
  val message =
    when (status) {
      NetworkStatus.Offline -> stringResource(id = R.string.network_status_offline)
      NetworkStatus.Unstable -> stringResource(id = R.string.network_status_unstable)
      NetworkStatus.Online ->
        stringResource(
          id =
            if (isRecovered) {
              R.string.network_status_online_recovered
            } else {
              R.string.network_status_online
            }
        )
    }

  Surface(
    shape = RoundedCornerShape(50),
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    shadowElevation = 4.dp,
    modifier = modifier.widthIn(max = 360.dp).testTag("network_status_indicator"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
