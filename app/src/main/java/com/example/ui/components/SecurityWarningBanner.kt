
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.DeviceIntegrityReport
import com.example.ui.theme.SecurityDangerRed
import com.example.ui.theme.SecurityDangerRedContainer
import com.example.ui.theme.SecurityWarningAmber
import com.example.ui.theme.SecurityWarningAmberContainer

@Composable
fun SecurityWarningBanner(
    report: DeviceIntegrityReport,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!report.hasAnyWarning) return

    val isHighSeverity = report.isRootOrMagiskSuspected

    val containerColor =
        if (isHighSeverity) {
            SecurityDangerRedContainer
        } else {
            SecurityWarningAmberContainer
        }

    val accentColor =
        if (isHighSeverity) {
            SecurityDangerRed
        } else {
            SecurityWarningAmber
        }

    val detectedLabels = buildList {
        if (report.isRootOrMagiskSuspected) {
            add(stringResource(id = R.string.flag_root_magisk))
        }

        if (report.isDeveloperOptionsEnabled) {
            add(stringResource(id = R.string.flag_dev_options))
        }

        if (report.isUsbDebuggingEnabled) {
            add(stringResource(id = R.string.flag_usb_debugging))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor.copy(alpha = 0.96f),
        shadowElevation = 4.dp,
        modifier = modifier
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onToggleExpand)
            .testTag("security_warning_banner"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.GppMaybe,
                    contentDescription = stringResource(
                        id = R.string.warning_banner_title
                    ),
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.warning_banner_title
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = detectedLabels.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.ExpandLess
                    } else {
                        Icons.Rounded.ExpandMore
                    },
                    contentDescription = stringResource(
                        id = R.string.details
                    ),
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.warning_banner_body,
                            detectedLabels.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                    )

                    if (report.rootSignals.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Indikator: ${
                                report.rootSignals.joinToString(", ")
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(
                            id = R.string.security_disclaimer
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                    )
                }
            }
        }
    }
}
