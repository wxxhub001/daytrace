package com.daytrace.ui.theme

import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 白底琥珀黄（番茄 Todo 风）
 *
 * 原则：**白色是底，黄色是填充**。
 * 琥珀黄 (#FFC300) 的亮度很高，直接当正文或小字会看不清，
 * 所以：
 *  - 黄色只出现在按钮底色、进度条、图标底、选中态这些"块状"位置；
 *  - 白底上的强调文字一律用深琥珀 (#4A3600)，保证对比度；
 *  - 绿色只用于"达标 / 已完成"的正向反馈。
 */
private val Amber = Color(0xFFFFC300)      // 琥珀黄 · 主色（只做填充）
private val Pumpkin = Color(0xFFFF9F1C)    // 南瓜橙 · 渐变终点
private val Leaf = Color(0xFF3FAE6A)       // 叶绿 · 达标

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF3A2B00),
    primaryContainer = Color(0xFFFFF6DA),
    onPrimaryContainer = Color(0xFF4A3600),
    secondary = Pumpkin,
    onSecondary = Color(0xFF3D2400),
    secondaryContainer = Color(0xFFFFEBD1),
    onSecondaryContainer = Color(0xFF4A2A00),
    tertiary = Leaf,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F4E8),
    onTertiaryContainer = Color(0xFF14532D),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191919),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191919),
    surfaceVariant = Color(0xFFF6F6F6),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outline = Color(0xFFE6E6E6),
    outlineVariant = Color(0xFFF0F0F0),
    error = Color(0xFFFF5C5C),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEB),
    onErrorContainer = Color(0xFF7A1F1F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFD24D),
    onPrimary = Color(0xFF3A2B00),
    primaryContainer = Color(0xFF3D3000),
    onPrimaryContainer = Color(0xFFFFE9A8),
    secondary = Color(0xFFFFB25E),
    onSecondary = Color(0xFF3D2400),
    secondaryContainer = Color(0xFF3E2A12),
    onSecondaryContainer = Color(0xFFFFDFC0),
    tertiary = Color(0xFF6FD08C),
    onTertiary = Color(0xFF06240F),
    tertiaryContainer = Color(0xFF1E3A27),
    onTertiaryContainer = Color(0xFFC9F2D5),
    background = Color(0xFF111111),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFF9A9A9A),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0F09),
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val baseTypography = Typography()

private val AppTypography = Typography(
    // 番茄 Todo 的数字很有存在感：大、粗、字距收紧
    displaySmall = baseTypography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = baseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun DayTraceTheme(
    // 这套设计是白底黄辅，不跟随系统深色模式：默认恒为浅色，避免整屏变黑
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}