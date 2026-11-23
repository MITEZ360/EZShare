package com.ezshare.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandTeal = Color(0xFF0D9488)
private val BrandBlue = Color(0xFF2563EB)
private val BrandCyan = Color(0xFF0891B2)

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5EF),
    onPrimaryContainer = Color(0xFF064E45),
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEBFF),
    onSecondaryContainer = Color(0xFF173D80),
    tertiary = BrandCyan,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F1FA),
    onTertiaryContainer = Color(0xFF0A3C4D),
    background = Color(0xFFEEF1F5),
    onBackground = Color(0xFF12232B),
    surface = Color.White,
    onSurface = Color(0xFF12232B),
    surfaceVariant = Color(0xFFE2E8EE),
    onSurfaceVariant = Color(0xFF4B5F6B),
    surfaceContainerLowest = Color(0xFFF7F9FB),
    surfaceContainerLow = Color(0xFFF3F6F9),
    surfaceContainer = Color(0xFFEAEFF4),
    surfaceContainerHigh = Color(0xFFE1E7EE),
    surfaceContainerHighest = Color(0xFFD8E0E8),
    outline = Color(0xFFB4C2CF),
    outlineVariant = Color(0xFFD5DEE7),
    error = Color(0xFFD92D3B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF8A1420)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003A33),
    primaryContainer = Color(0xFF0F6B60),
    onPrimaryContainer = Color(0xFFC7FFF3),
    secondary = Color(0xFF8FB6FF),
    onSecondary = Color(0xFF102D5C),
    secondaryContainer = Color(0xFF1D3E75),
    onSecondaryContainer = Color(0xFFDCE8FF),
    tertiary = Color(0xFF7DD3EF),
    onTertiary = Color(0xFF063342),
    tertiaryContainer = Color(0xFF0E4A5B),
    onTertiaryContainer = Color(0xFFD6F4FD),
    background = Color(0xFF0A1218),
    onBackground = Color(0xFFE4EFF1),
    surface = Color(0xFF1A2A33),
    onSurface = Color(0xFFE4EFF1),
    surfaceVariant = Color(0xFF1D353C),
    onSurfaceVariant = Color(0xFFA9C2C8),
    surfaceContainerLowest = Color(0xFF081417),
    surfaceContainerLow = Color(0xFF102126),
    surfaceContainer = Color(0xFF152A31),
    surfaceContainerHigh = Color(0xFF1B343B),
    surfaceContainerHighest = Color(0xFF223F46),
    outline = Color(0xFF3E5B63),
    outlineVariant = Color(0xFF2C464D),
    error = Color(0xFFFF9B9B),
    onError = Color(0xFF4A0D14),
    errorContainer = Color(0xFF6E1A23),
    onErrorContainer = Color(0xFFFFD5D8)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EZShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
