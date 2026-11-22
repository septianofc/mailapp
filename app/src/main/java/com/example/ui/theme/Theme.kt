package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SupportLightColorScheme =
  lightColorScheme(
    primary = BrandBluePrimary,
    onPrimary = PureWhite,
    primaryContainer = BrandBlueSoftContainer,
    onPrimaryContainer = BrandBlueOnContainer,
    secondary = BrandBlueAccent,
    onSecondary = PureWhite,
    secondaryContainer = BrandBlueSoftContainer,
    onSecondaryContainer = BrandBlueDark,
    tertiary = BrandBlueDark,
    onTertiary = PureWhite,
    background = SoftWhiteBackground,
    onBackground = SlateTextPrimary,
    surface = PureWhite,
    onSurface = SlateTextPrimary,
    surfaceVariant = SoftWhiteBackground,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateBorder,
    error = SecurityDangerRed,
    onError = PureWhite,
    errorContainer = SecurityDangerRedContainer,
    onErrorContainer = SecurityDangerRed,
  )

@Composable
fun MailSeptianSupportTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = SupportLightColorScheme,
    typography = Typography,
    content = content,
  )
}
