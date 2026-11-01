package com.sdm.videoplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sdm.videoplayer.data.prefs.AccentChoice
import com.sdm.videoplayer.data.prefs.ThemeMode

/** ألوان هوية SDM VP: خلفية كحلية داكنة + تدرجات أزرق/بنفسجي وإضاءة نيونية. */
object SdmColors {
    val Night = Color(0xFF0A0A12)
    val NightSurface = Color(0xFF12121D)
    val NightElevated = Color(0xFF1A1A28)
    val Silver = Color(0xFFE9EAF2)
    val Violet = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF6D28D9)
    val ElectricBlue = Color(0xFF3B82F6)
    val NeonCyan = Color(0xFF22D3EE)
    val Magenta = Color(0xFFEC4899)
    val Amber = Color(0xFFF59E0B)
    val TextMuted = Color(0xFF9AA0B4)
}

fun accentColor(accent: AccentChoice): Color = when (accent) {
    AccentChoice.PURPLE -> SdmColors.Violet
    AccentChoice.BLUE -> SdmColors.ElectricBlue
    AccentChoice.CYAN -> SdmColors.NeonCyan
    AccentChoice.MAGENTA -> SdmColors.Magenta
    AccentChoice.AMBER -> SdmColors.Amber
}

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.22f),
    onPrimaryContainer = SdmColors.Silver,
    secondary = SdmColors.NeonCyan,
    onSecondary = SdmColors.Night,
    tertiary = SdmColors.Magenta,
    background = SdmColors.Night,
    onBackground = SdmColors.Silver,
    surface = SdmColors.NightSurface,
    onSurface = SdmColors.Silver,
    surfaceVariant = SdmColors.NightElevated,
    onSurfaceVariant = SdmColors.TextMuted,
    outline = Color(0xFF2C2C3D),
    error = Color(0xFFEF4444)
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.15f),
    secondary = SdmColors.ElectricBlue,
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF14141C),
    surface = Color.White,
    onSurface = Color(0xFF14141C),
    surfaceVariant = Color(0xFFEDEDF5),
    onSurfaceVariant = Color(0xFF5A5F70),
    outline = Color(0xFFD3D4E0),
    error = Color(0xFFDC2626)
)

private val SdmTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
)

@Composable
fun SdmTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: AccentChoice = AccentChoice.PURPLE,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) darkScheme(accentColor(accent)) else lightScheme(accentColor(accent)),
        typography = SdmTypography,
        content = content
    )
}
