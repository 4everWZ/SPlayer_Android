package top.imsyy.splayer.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NativeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF04D5D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5B1B24),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF2A1800),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFF4F6FB),
    surface = Color(0xFF12151D),
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF1B1F29),
    onSurfaceVariant = Color(0xFFAEB7CC),
    outline = Color(0xFF3A4254),
    error = Color(0xFFFF7A7A),
)

private val NativeLightColorScheme = lightColorScheme(
    primary = Color(0xFFD8475A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = Color(0xFF41000A),
    secondary = Color(0xFF8B5A00),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF12151D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151D),
    surfaceVariant = Color(0xFFE7EAF3),
    onSurfaceVariant = Color(0xFF596276),
    outline = Color(0xFFC4CAD8),
    error = Color(0xFFBA1A1A),
)

@Composable
fun SPlayerNativeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NativeDarkColorScheme else NativeLightColorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
