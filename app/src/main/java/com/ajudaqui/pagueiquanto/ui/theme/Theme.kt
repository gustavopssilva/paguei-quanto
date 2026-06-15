package com.ajudaqui.pagueiquanto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald600.copy(alpha = 0.1f),
    onPrimaryContainer = Emerald700,
    
    secondary = Slate200,
    onSecondary = Slate800,
    
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
