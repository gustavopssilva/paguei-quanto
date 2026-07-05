package com.ajudaqui.pagueiquanto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ClayBlue,
    onPrimary = Color.White,
    primaryContainer = ClayLightBlue,
    onPrimaryContainer = ClayBlue,
    
    secondary = ClayGreen,
    onSecondary = Color.White,
    
    tertiary = ClayOrange,
    onTertiary = Color.White,
    
    background = Slate50,
    onBackground = Slate900,
    
    surface = Color.White,
    onSurface = Slate900,
    
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    
    outline = Slate200,
    error = Red600
)

@Composable
fun PagueiQuantoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
