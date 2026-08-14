package com.bilicraft.handheld.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.bilicraft.handheld.config.ThemeMode

/**
 * 应用主题入口。
 *
 * 品牌配色原先散在 MainActivity 里，这里收成唯一来源：Activity 只传明暗偏好，不再持有色值。
 */
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
        colorScheme = if (dark) BilicraftDarkColors else BilicraftLightColors
    ) {
        CompositionLocalProvider(LocalChatColors provides if (dark) DarkChatColors else LightChatColors) {
            content()
        }
    }
}

private val BilicraftLightColors = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    secondary = Color(0xFF006B5F),
    tertiary = Color(0xFF7A5C00),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE7EFFD)
)

private val BilicraftDarkColors = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    secondary = Color(0xFF72D8C8),
    tertiary = Color(0xFFE8C45C),
    surface = Color(0xFF101318),
    surfaceVariant = Color(0xFF263142)
)

/**
 * 聊天记录区配色：MC 聊天惯用深底浅字，因此不随明暗主题变化。
 */
internal val ChatSurfaceColor = Color(0xFF1E1E1E)
internal val ChatDefaultTextColor = Color(0xFFE0E0E0)

data class ChatColors(
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val systemCapsule: Color,
    val incomingText: Color,
    val outgoingText: Color
)

internal val LocalChatColors = staticCompositionLocalOf { LightChatColors }

private val LightChatColors = ChatColors(
    incomingBubble = Color(0xFFE7EFFD),
    outgoingBubble = Color(0xFF1B6EF3),
    systemCapsule = Color(0xFFE8E0D0),
    incomingText = Color(0xFF1A1C1E),
    outgoingText = Color(0xFFFFFFFF)
)

private val DarkChatColors = ChatColors(
    incomingBubble = Color(0xFF263142),
    outgoingBubble = Color(0xFF1B6EF3),
    systemCapsule = Color(0xFF3A3428),
    incomingText = Color(0xFFE8EAED),
    outgoingText = Color(0xFFFFFFFF)
)

/** 连接状态色：绿=已连接，黄=进行中，红=失败，灰=未连接。 */
internal val StatusGreen = Color(0xFF2E7D32)
internal val StatusAmber = Color(0xFFF9A825)
internal val StatusRed = Color(0xFFC62828)
internal val StatusGray = Color(0xFF9E9E9E)
