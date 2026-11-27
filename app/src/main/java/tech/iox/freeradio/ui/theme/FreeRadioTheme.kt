package tech.iox.freeradio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val RadioBackground = Color(0xFF0C1118)
val RadioSurface = Color(0xFF151D27)
val RadioSurfaceRaised = Color(0xFF202B36)
val RadioSurfaceSoft = Color(0xFF293744)
val RadioAccent = Color(0xFFC7F36B)
val RadioAccentMuted = Color(0xFF8EA84E)
val RadioText = Color(0xFFF4F6F8)
val RadioMuted = Color(0xFF9AA7B3)
val RadioWarning = Color(0xFFFFC078)
val RadioDanger = Color(0xFFFF827A)

private val baseTypography = Typography()
private val RadioTypography = Typography(
    displayLarge = baseTypography.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = baseTypography.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = baseTypography.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = baseTypography.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = baseTypography.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = baseTypography.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = baseTypography.titleLarge.copy(fontFamily = FontFamily.Serif),
    titleMedium = baseTypography.titleMedium.copy(fontFamily = FontFamily.Serif),
    titleSmall = baseTypography.titleSmall.copy(fontFamily = FontFamily.Serif),
    bodyLarge = baseTypography.bodyLarge.copy(fontFamily = FontFamily.Serif),
    bodyMedium = baseTypography.bodyMedium.copy(fontFamily = FontFamily.Serif),
    bodySmall = baseTypography.bodySmall.copy(fontFamily = FontFamily.Serif),
    labelLarge = baseTypography.labelLarge.copy(fontFamily = FontFamily.Serif),
    labelMedium = baseTypography.labelMedium.copy(fontFamily = FontFamily.Serif),
    labelSmall = baseTypography.labelSmall.copy(fontFamily = FontFamily.Serif)
)

private val RadioColors = darkColorScheme(
    primary = RadioAccent,
    onPrimary = RadioBackground,
    secondary = RadioAccentMuted,
    onSecondary = RadioText,
    background = RadioBackground,
    onBackground = RadioText,
    surface = RadioSurface,
    onSurface = RadioText,
    surfaceVariant = RadioSurfaceRaised,
    onSurfaceVariant = RadioMuted,
    outline = RadioSurfaceSoft,
    error = RadioDanger
)

@Composable
fun FreeRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RadioColors,
        typography = RadioTypography,
        content = content
    )
}
