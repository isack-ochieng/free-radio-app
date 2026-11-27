package tech.iox.freeradio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

private val FreeRadioColors = darkColorScheme(
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
        colorScheme = FreeRadioColors,
        content = content
    )
}
