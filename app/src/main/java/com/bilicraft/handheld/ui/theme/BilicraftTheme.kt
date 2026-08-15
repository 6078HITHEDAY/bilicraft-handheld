package com.bilicraft.handheld.ui.theme

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilicraft.handheld.config.ThemeMode

/** 间距常量：替换散落的 8/12/16.dp。 */
object BilicraftSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val cardRadius = 14.dp
    val bubbleRadius = 16.dp
}

/** 连接/状态语义色。 */
object BilicraftSemanticColors {
    val success = Color(0xFF2E7D32)
    val warning = Color(0xFFF9A825)
    val danger = Color(0xFFC62828)
    val info = Color(0xFF1565C0)
    val neutral = Color(0xFF9E9E9E)
}

@Deprecated("Use BilicraftSemanticColors.success", ReplaceWith("BilicraftSemanticColors.success"))
internal val StatusGreen = BilicraftSemanticColors.success
@Deprecated("Use BilicraftSemanticColors.warning", ReplaceWith("BilicraftSemanticColors.warning"))
internal val StatusAmber = BilicraftSemanticColors.warning
@Deprecated("Use BilicraftSemanticColors.danger", ReplaceWith("BilicraftSemanticColors.danger"))
internal val StatusRed = BilicraftSemanticColors.danger
@Deprecated("Use BilicraftSemanticColors.neutral", ReplaceWith("BilicraftSemanticColors.neutral"))
internal val StatusGray = BilicraftSemanticColors.neutral

private val BrandBlue = Color(0xFF1B6EF3)
private val BrandBlueDark = Color(0xFF9CC2FF)

private val BilicraftLightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlue,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF006B5F),
    tertiary = Color(0xFF7A5C00),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE7EFFD),
    background = Color(0xFFF5F7FB)
)

private val BilicraftDarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1E4B8C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF72D8C8),
    tertiary = Color(0xFFE8C45C),
    surface = Color(0xFF101318),
    surfaceVariant = Color(0xFF263142),
    background = Color(0xFF0B0D10)
)

private val BilicraftTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)

private val BilicraftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(BilicraftSpacing.cardRadius),
    large = RoundedCornerShape(BilicraftSpacing.bubbleRadius),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BilicraftTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) BilicraftDarkColors else BilicraftLightColors,
        typography = BilicraftTypography,
        shapes = BilicraftShapes,
        content = content
    )
}

@Composable
internal fun chatSurfaceColor(): Color = MaterialTheme.colorScheme.background

@Composable
internal fun chatDefaultTextColor(): Color = MaterialTheme.colorScheme.onSurface

/** 自己气泡：品牌蓝底，配合 onPrimaryContainer 白字。 */
@Composable
internal fun bubbleSelfColor(): Color = MaterialTheme.colorScheme.primaryContainer

@Composable
internal fun bubbleOtherColor(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
internal fun bubbleSystemColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

@Composable
internal fun bubbleTimestampColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
