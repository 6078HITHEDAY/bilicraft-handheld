package com.bilicraft.handheld.ui.vm

import com.bilicraft.handheld.config.UiConfigRepository
import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.ui.ServerRuntimeUiState
import com.bilicraft.handheld.ui.common.UiConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聊天域状态操作：从 MainViewModel 拆出的可委托逻辑。
 * MainViewModel 仍作门面，避免一次性大爆炸重构。
 */
class ChatStateHolder(
    private val scope: CoroutineScope,
    private val uiConfigRepo: UiConfigRepository,
    private val serverRuntime: MutableStateFlow<ServerRuntimeUiState>
) {
    fun clearChannelChat(serverId: String) {
        serverRuntime.update { current ->
            current.copy(chatLogs = current.chatLogs + (serverId to emptyList()))
        }
        scope.launch { uiConfigRepo.markChannelRead(serverId, System.currentTimeMillis()) }
    }

    fun removeLocalMessage(serverId: String, timestamp: Long, plainText: String) {
        serverRuntime.update { current ->
            val log = current.chatLogs[serverId].orEmpty().filterNot {
                it.timestamp == timestamp && it.plainText == plainText
            }
            current.copy(chatLogs = current.chatLogs + (serverId to log))
        }
    }

    fun unreadCount(serverId: String, preferencesLastRead: Map<String, Long>): Int {
        val lastRead = preferencesLastRead[serverId] ?: 0L
        return serverRuntime.value.chatLogs[serverId].orEmpty().count { it.timestamp > lastRead }
    }

    fun trimLog(serverId: String, event: ChatEvent, maxUiLog: Int): List<ChatEvent> {
        val current = serverRuntime.value.chatLogs[serverId].orEmpty()
        return (current + event).takeLast(UiConstants.clampMaxUiLog(maxUiLog))
    }

    fun markChannelRead(serverId: String, timestamp: Long) {
        scope.launch { uiConfigRepo.markChannelRead(serverId, timestamp) }
    }

    fun togglePinnedChannel(serverId: String) {
        scope.launch { uiConfigRepo.togglePinnedChannel(serverId) }
    }

    fun toggleArchivedChannel(serverId: String) {
        scope.launch { uiConfigRepo.toggleArchivedChannel(serverId) }
    }
}
