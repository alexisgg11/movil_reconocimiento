package com.example.reconocimiento.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val colors = lightColorScheme(
    primary = Color(0xFF1474D4), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDF5FF), onPrimaryContainer = Color(0xFF1474D4),
    secondary = Color(0xFF666666), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F4), onSecondaryContainer = Color(0xFF202020),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF202020),
    surface = Color.White, onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFF4F4F4), onSurfaceVariant = Color(0xFF666666),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF4F4F4),
    outline = Color(0xFF8795A6), outlineVariant = Color(0xFFDDE4ED), error = Color(0xFFB3261E)
)
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp), small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(14.dp)
)

@Composable
fun ReconocimientoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography, shapes = shapes, content = content)
}
