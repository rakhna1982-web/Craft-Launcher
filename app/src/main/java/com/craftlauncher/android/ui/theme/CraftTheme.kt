package com.craftlauncher.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftlauncher.android.data.model.ThemeMode
import com.example.R

val EmeraldAccent = Color(0xFF34D399)
val EmeraldAction = Color(0xFF22C55E)
val EmeraldDeep = Color(0xFF0D3B26)
val EmeraldTint = Color(0xFFA7F3D0)

val DiamondCyan = Color(0xFF38BDF8)
val AmberSnapshot = Color(0xFFF59E0B)
val RedstoneError = Color(0xFFEF4444)

private val ObsidianDarkScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF052014),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldTint,
    secondary = DiamondCyan,
    onSecondary = Color(0xFF041D29),
    secondaryContainer = Color(0xFF0E3347),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberSnapshot,
    onTertiary = Color(0xFF2B1900),
    tertiaryContainer = Color(0xFF422905),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF141920),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1C232E),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF07090B),
    surfaceContainerLow = Color(0xFF11161C),
    surfaceContainer = Color(0xFF171D26),
    surfaceContainerHigh = Color(0xFF1E2632),
    surfaceContainerHighest = Color(0xFF273140),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = RedstoneError,
    onError = Color.White,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA)
)

private val AmoledBlackScheme = ObsidianDarkScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF07090C),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF090C10),
    surfaceContainer = Color(0xFF0F1318),
    surfaceContainerHigh = Color(0xFF151B22)
)

private val EmeraldLightScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF3),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val InterFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val CraftTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 14.sp
    )
)

val CraftShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CraftLauncherTheme(
    themeMode: ThemeMode = ThemeMode.OBSIDIAN_DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val scheme = when (themeMode) {
        ThemeMode.OBSIDIAN_DARK -> ObsidianDarkScheme
        ThemeMode.AMOLED_BLACK -> AmoledBlackScheme
        ThemeMode.EMERALD_LIGHT -> EmeraldLightScheme
        ThemeMode.SYSTEM -> if (systemDark) ObsidianDarkScheme else EmeraldLightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = CraftTypography,
        shapes = CraftShapes,
        content = content
    )
}
