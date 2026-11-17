package com.earthx.explorer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object EX {
    val Space = Color(0xFF02040A)
    val Deep = Color(0xFF070B16)
    val Glass = Color(0xCC0B1222)
    val GlassStrong = Color(0xEE0B1222)
    val Stroke = Color(0x335CE1FF)
    val StrokeSoft = Color(0x1FFFFFFF)
    val Accent = Color(0xFF5CE1FF)
    val Accent2 = Color(0xFF7C9CFF)
    val Amber = Color(0xFFFFB347)
    val Danger = Color(0xFFFF5A6E)
    val Good = Color(0xFF5BE49B)
    val Text = Color(0xFFE8EEF7)
    val TextDim = Color(0xFF9AA8BD)
    val TextFaint = Color(0xFF66748A)
}

private val scheme = darkColorScheme(
    primary = EX.Accent, onPrimary = Color(0xFF00222C),
    secondary = EX.Amber, onSecondary = Color(0xFF2A1700),
    tertiary = EX.Accent2,
    background = EX.Space, onBackground = EX.Text,
    surface = EX.Deep, onSurface = EX.Text,
    surfaceVariant = Color(0xFF121A2C), onSurfaceVariant = EX.TextDim,
    surfaceContainer = Color(0xFF0D1424), surfaceContainerHigh = Color(0xFF131C30), surfaceContainerHighest = Color(0xFF18233A),
    surfaceContainerLow = Color(0xFF0A101E), surfaceContainerLowest = EX.Space,
    outline = Color(0xFF2A3A55), outlineVariant = Color(0xFF1C2840),
    error = EX.Danger,
)

private val base = FontFamily.SansSerif
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.2.sp),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp),
    bodyLarge = TextStyle(fontFamily = base, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = base, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = base, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.8.sp),
)

@Composable
fun EarthXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
