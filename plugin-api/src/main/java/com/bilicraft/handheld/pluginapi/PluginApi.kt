package com.bilicraft.handheld.pluginapi

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

const val BH_PLUGIN_API_VERSION = 1

interface BhPlugin {
    val descriptor: BhPluginDescriptor

    fun entrypoints(host: BhPluginHost): List<BhPluginEntrypoint> = listOf(
        BhPluginEntrypoint(
            id = "main",
            title = descriptor.name,
            description = descriptor.description
        )
    )

    fun createPanel(host: BhPluginHost): BhPluginPanel

    fun createPanel(host: BhPluginHost, entrypointId: String): BhPluginPanel = createPanel(host)

    fun onLoad(host: BhPluginHost) = Unit

    fun onUnload(host: BhPluginHost) = Unit
}

data class BhPluginEntrypoint(
    val id: String,
    val title: String,
    val description: String = "",
    val order: Int = 0
)

data class BhPluginDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val minApiVersion: Int = BH_PLUGIN_API_VERSION
)

interface BhPluginPanel {
    @Composable
    fun Content(host: BhPluginHost, onClose: () -> Unit)
}

interface BhPluginHost {
    val appContext: Context
    val pluginDataDir: File
    val connectionState: StateFlow<BhConnectionState>
    val chatEvents: Flow<BhChatEvent>

    /** 当前登录玩家（名字 + UUID）；未登录时为 null */
    val currentPlayer: BhPlayer? get() = null

    fun sendChat(text: String): Boolean
    fun log(message: String)
    suspend fun httpGet(url: String): String
}

data class BhChatEvent(
    val plainText: String,
    val rawJson: String,
    val sender: String?,
    val timestamp: Long,
    /** 频道规则解析出的正版玩家名；未解析时为 null。 */
    val player: String? = null,
    /** 称号（如「建筑大师」）；未解析时为 null。 */
    val title: String? = null,
    /** 阵营标记；未解析时为 null。 */
    val faction: String? = null,
    /** 多服网络子服名（如 HY）；未解析时为 null。 */
    val server: String? = null
)

/** 当前登录玩家信息。uuid 为无符号字符串（如 069a79f4-44e9-4726-a5be-fca90e38aaf5）。 */
data class BhPlayer(
    val name: String,   // 玩家名字
    val uuid: String    // 玩家 UUID
)

enum class BhConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Failed
}