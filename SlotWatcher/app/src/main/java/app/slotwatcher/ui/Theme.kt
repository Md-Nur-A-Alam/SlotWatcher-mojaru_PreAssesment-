package app.slotwatcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slotwatcher.R

/*
 * Mojaru brand palette, taken from mojaru.com's stylesheet:
 *   deep purple #532D80 · violet #6F39B7 · yellow #FFCE39 · coral #FF6A3D · navy text #1A2238
 * Typography: Inter (the font mojaru.com uses), bundled as a variable font.
 */
object Brand {
    val Purple = Color(0xFF532D80)
    val Violet = Color(0xFF6F39B7)
    val Yellow = Color(0xFFFFCE39)
    val Coral = Color(0xFFFF6A3D)
    val Navy = Color(0xFF1A2238)
    val DeepPurple = Color(0xFF3F2260)

    /** Header gradient used on Home, Developer and the alarm screen. */
    val HeroGradient = Brush.linearGradient(listOf(Color(0xFF3F2260), Purple, Violet))
}

/** Status colours that aren't part of Material's scheme. Always paired with an icon + text. */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val alert: Color,
    val alertContainer: Color,
    val onAlertContainer: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF1E7B4F),
    successContainer = Color(0xFFD7F3E3),
    onSuccessContainer = Color(0xFF0B3B24),
    alert = Color(0xFFC2410C),
    alertContainer = Color(0xFFFFE4D9),
    onAlertContainer = Color(0xFF5A1A04),
)

private val DarkStatus = StatusColors(
    success = Color(0xFF7FD8A6),
    successContainer = Color(0xFF15402B),
    onSuccessContainer = Color(0xFFC9F2DA),
    alert = Color(0xFFFF9A76),
    alertContainer = Color(0xFF5A2410),
    onAlertContainer = Color(0xFFFFDCCD),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val LightColors = lightColorScheme(
    primary = Brand.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE3FA),
    onPrimaryContainer = Color(0xFF2A1442),
    secondary = Brand.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DAF8),
    onSecondaryContainer = Color(0xFF2A1442),
    tertiary = Brand.Yellow,
    onTertiary = Brand.Navy,
    tertiaryContainer = Color(0xFFFFF1C2),
    onTertiaryContainer = Color(0xFF3D2E00),
    background = Color(0xFFF7F5FB),
    onBackground = Brand.Navy,
    surface = Color(0xFFF7F5FB),
    onSurface = Brand.Navy,
    surfaceVariant = Color(0xFFEDE8F5),
    onSurfaceVariant = Color(0xFF4F5570),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1EDF8),
    surfaceContainerHigh = Color(0xFFEBE6F3),
    surfaceContainerHighest = Color(0xFFE5DFEF),
    outline = Color(0xFF8B8299),
    outlineVariant = Color(0xFFD9D2E4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E2),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDB4F5),
    onPrimary = Color(0xFF2A1442),
    primaryContainer = Brand.DeepPurple,
    onPrimaryContainer = Color(0xFFEDE3FA),
    secondary = Color(0xFFB99AEE),
    onSecondary = Color(0xFF2A1442),
    secondaryContainer = Color(0xFF3A2558),
    onSecondaryContainer = Color(0xFFE6DAF8),
    tertiary = Brand.Yellow,
    onTertiary = Brand.Navy,
    tertiaryContainer = Color(0xFF4A3A00),
    onTertiaryContainer = Color(0xFFFFE9A6),
    background = Color(0xFF120C1B),
    onBackground = Color(0xFFEDE8F5),
    surface = Color(0xFF120C1B),
    onSurface = Color(0xFFEDE8F5),
    surfaceVariant = Color(0xFF2A2138),
    onSurfaceVariant = Color(0xFFC9C0D8),
    surfaceContainerLowest = Color(0xFF0D0814),
    surfaceContainerLow = Color(0xFF1B1427),
    surfaceContainer = Color(0xFF1F172C),
    surfaceContainerHigh = Color(0xFF2A2138),
    surfaceContainerHighest = Color(0xFF342A44),
    outline = Color(0xFF968CA6),
    outlineVariant = Color(0xFF443A54),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val AppTypography = Typography(
    displaySmall = style(34, 40, 800, -0.5),
    headlineLarge = style(30, 36, 700, -0.4),
    headlineMedium = style(26, 32, 700, -0.3),
    headlineSmall = style(22, 28, 700, -0.2),
    titleLarge = style(20, 26, 700, -0.1),
    titleMedium = style(16, 22, 600),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 400, 0.1),
    labelLarge = style(14, 20, 600, 0.1),
    labelMedium = style(12, 16, 600, 0.3),
    labelSmall = style(11, 14, 600, 0.4),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 4/8dp spacing rhythm. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 20.dp
}

@Composable
fun SlotWatcherTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
