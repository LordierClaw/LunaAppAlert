package dev.lordierclaw.lunaappalert.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
private val AppColors = lightColorScheme(
    primary = AlertBlue, onPrimary = Color.White,
    primaryContainer = AlertBlueSoft, onPrimaryContainer = AlertBlue,
    secondary = AlertSecondary, onSecondary = Color.White,
    secondaryContainer = AlertSurface, onSecondaryContainer = AlertInk,
    background = Color.White, onBackground = AlertInk,
    surface = Color.White, onSurface = AlertInk,
    surfaceVariant = AlertSurface, onSurfaceVariant = AlertSecondary,
    outline = AlertBorder, outlineVariant = AlertBorder,
    error = AlertDanger, onError = Color.White,
    surfaceContainer = AlertSurface, surfaceContainerLow = AlertSurface,
)
@Composable
fun LunaAppAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
