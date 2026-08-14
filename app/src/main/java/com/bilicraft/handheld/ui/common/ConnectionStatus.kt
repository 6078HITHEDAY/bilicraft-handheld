package com.bilicraft.handheld.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.theme.StatusAmber
import com.bilicraft.handheld.ui.theme.StatusGray
import com.bilicraft.handheld.ui.theme.StatusGreen
import com.bilicraft.handheld.ui.theme.StatusRed

/** 连接状态色点：绿=已连接，黄=进行中，红=失败，灰=未连接。 */
@Composable
internal fun StatusDot(conn: ConnectionState) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(statusColor(conn))
    )
}

internal fun statusColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> StatusGreen
    is ConnectionState.Connecting,
    is ConnectionState.LoggingIn,
    is ConnectionState.Reconnecting -> StatusAmber
    is ConnectionState.Failed -> StatusRed
    is ConnectionState.Disconnected -> StatusGray
}

internal fun statusText(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "已连接"
    is ConnectionState.Connecting -> "连接中…"
    is ConnectionState.LoggingIn -> "登录中…"
    is ConnectionState.Reconnecting -> "重连中（第 ${state.attempt} 次）"
    is ConnectionState.Failed -> "失败：${state.reason}"
    is ConnectionState.Disconnected -> "未连接"
}
