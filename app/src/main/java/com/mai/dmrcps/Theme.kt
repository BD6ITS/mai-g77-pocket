package com.mai.dmrcps

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字阶照 Pixel 系统应用的观感微调：标题加粗、字距略收、正文行高放宽。
 * 和「安服」那版同一套参数——两边看着才是一家的东西。
 */
private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    letter: Double = 0.0,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letter.sp,
)

val MaiTypography = Typography(
    displaySmall = style(FontWeight.Normal, 36, 44),
    headlineLarge = style(FontWeight.Normal, 32, 40),
    headlineMedium = style(FontWeight.Normal, 28, 36),
    headlineSmall = style(FontWeight.SemiBold, 24, 32),
    titleLarge = style(FontWeight.SemiBold, 22, 28),
    titleMedium = style(FontWeight.SemiBold, 16, 24, 0.1),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.5),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.4),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.5),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.5),
)

/** 五档对齐官方 ShapeTokens：对话框(extraLarge 28) > 卡片(large 16) > 按钮(medium 12)。 */
val MaiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Android 11 及以下没有动态取色时的兜底配色：钢蓝（专业工具的味道），
 * 不用 Material 默认那套紫。完整给出 surfaceContainer 五档，
 * 卡片/弹窗/导航栏才有层次。
 */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF2A5B8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF4F6178),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0B1C2C),
    tertiary = Color(0xFF2F6B5F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB5F0DF),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2E8),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFF1F4F8),
    surfaceContainerLow = Color(0xFFEBEEF3),
    surfaceContainer = Color(0xFFE5E8EE),
    surfaceContainerHigh = Color(0xFFDFE2E8),
    surfaceContainerHighest = Color(0xFFD9DCE2),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFA2C9F0),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF124A76),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFB9C8DC),
    onSecondary = Color(0xFF233142),
    secondaryContainer = Color(0xFF39485A),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFF99D3C2),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF155044),
    onTertiaryContainer = Color(0xFFB5F0DF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2F),
    surfaceContainerHighest = Color(0xFF31353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

@Composable
fun MaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Android 12+ 跟壁纸取色；11 及以下用上面那套钢蓝兜底。 */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaiTypography,
        shapes = MaiShapes,
        content = content,
    )
}
