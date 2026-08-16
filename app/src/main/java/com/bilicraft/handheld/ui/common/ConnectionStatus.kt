package com.bilicraft.handheld.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.theme.BilicraftSemanticColors

/** 连接状态色点：绿=已连接，黄=进行中，红=失败，灰=未连接。 */
@Composable
internal fun StatusDot(conn: ConnectionState) {
    val pulsing = conn is ConnectionState.Connecting ||
        conn is ConnectionState.LoggingIn ||
        conn is ConnectionState.Reconnecting
    val animatePulse = pulsing && motionEnabled()
    // 始终调用 remember*，避免条件 composable 打乱 slot（H3）
    val pulseMs = motionDurationMs(700).coerceAtLeast(1)
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val animated by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val scale = if (animatePulse) animated else 1f
    Box(
        Modifier
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(statusColor(conn))
            .semantics { contentDescription = statusText(conn) }
    )
}

internal fun statusColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> BilicraftSemanticColors.success
    is ConnectionState.Connecting,
    is ConnectionState.LoggingIn,
    is ConnectionState.Reconnecting -> BilicraftSemanticColors.warning
    is ConnectionState.Failed -> BilicraftSemanticColors.danger
    is ConnectionState.Disconnected -> BilicraftSemanticColors.neutral
}

internal fun statusText(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "已连接"
    is ConnectionState.Connecting -> "连接中…"
    is ConnectionState.LoggingIn -> "登录中…"
    is ConnectionState.Reconnecting -> "重连中（第 ${state.attempt} 次）"
    is ConnectionState.Failed -> "失败：${state.reason}"
    is ConnectionState.Disconnected -> "未连接"
}
