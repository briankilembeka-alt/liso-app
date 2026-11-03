package ug.liso.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Agreed brand: blue on white; red is reserved for LIVE. */
object LisoColors {
    val Blue = Color(0xFF1F5BFF)
    val BlueDark = Color(0xFF1746C7)
    val LiveRed = Color(0xFFD23A1E)
    val Ink = Color(0xFF111827)
    val Muted = Color(0xFF6B7280)
    val Surface = Color(0xFFF5F7FB)
}

private val scheme = lightColorScheme(
    primary = LisoColors.Blue,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = LisoColors.Ink,
    surface = Color.White,
    surfaceVariant = LisoColors.Surface,
    onSurface = LisoColors.Ink,
    onSurfaceVariant = LisoColors.Muted,
    error = LisoColors.LiveRed,
)

// Plus Jakarta Sans is the agreed typeface; add it as a downloadable font so it costs no APK size.
@Composable
fun LisoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
