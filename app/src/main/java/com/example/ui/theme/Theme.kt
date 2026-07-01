package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = DrivePrimaryBlue,
    secondary = DrivePrimaryContainer,
    background = DriveDarkBackground,
    surface = DriveDarkSurface,
    onPrimary = DriveOnBackground,
    onSecondary = DriveOnPrimaryContainer,
    onBackground = DriveDarkOnSurface,
    onSurface = DriveDarkOnSurface,
    surfaceVariant = DriveDarkSurfaceVariant,
    onSurfaceVariant = DriveDarkOnSurface
  )

private val LightColorScheme =
  lightColorScheme(
    primary = DrivePrimaryBlue,
    primaryContainer = DrivePrimaryContainer,
    onPrimaryContainer = DriveOnPrimaryContainer,
    secondary = DrivePrimaryBlue,
    background = DriveBackground,
    surface = DriveSurface,
    onPrimary = DriveSurface,
    onSecondary = DriveSurface,
    onBackground = DriveOnBackground,
    onSurface = DriveOnBackground,
    surfaceVariant = DriveSurfaceVariant,
    onSurfaceVariant = DriveOnSurfaceVariant,
    outline = DriveOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force high-density custom design theme as requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
