package top.imsyy.splayer.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

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

private val NativeTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun SPlayerNativeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val cappedDensity = Density(
        density = density.density,
        fontScale = density.fontScale.coerceAtMost(1.0f),
    )
    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        MaterialTheme(
            colorScheme = if (darkTheme) NativeDarkColorScheme else NativeLightColorScheme,
            typography = NativeTypography,
            shapes = Shapes(),
            content = content,
        )
    }
}
