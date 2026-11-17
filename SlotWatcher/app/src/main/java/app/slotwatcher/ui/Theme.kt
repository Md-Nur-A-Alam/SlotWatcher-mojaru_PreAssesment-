package app.slotwatcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF002022),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFFB2EBF2),
)

@Composable
fun SlotWatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
