package com.bilicraft.handheld.chat

import com.bilicraft.handheld.config.UiPreferences
import com.bilicraft.handheld.notify.ChatNotificationManager
import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.protocol.OnlinePlayer
import com.bilicraft.handheld.protocol.ServerPinger
import com.bilicraft.handheld.server.ServerIconRepository
import com.bilicraft.handheld.session.SessionController
import com.bilicraft.handheld.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class ChatCoordinator(
    private val filesDir: File,
    private val session: SessionController,
    private val rules: ChatRuleRepository,
    private val icons: ServerIconRepository,
    private val notifications: ChatNotificationManager,
    private val preferences: () -> UiPreferences,
    private val selfName: () -> String?,
    private val activeServerId: () -> String?,
    private val serverHost: (String) -> String,
    private val isForeground: () -> Boolean,
    private val scope: CoroutineScope
) {
    private val stores = mutableMapOf<String, ChatStore>()
    private val echoByServer = mutableMapOf<String, ChatEchoMatcher>()
    private val _activeStore = MutableStateFlow<ChatStore?>(null)
    val activeStore: StateFlow<ChatStore?> = _activeStore.asStateFlow()

    private val _lastPreview = MutableStateFlow<String?>(null)
    val lastPreview: StateFlow<String?> = _lastPreview.asStateFlow()

    private val router = ChatRouter(
        ruleSet = { rules.ruleSet.value },
        selfName = selfName
    )

    init {
        scope.launch {
            session.events.collect { event ->
                when (event) {
                    is SessionEvent.Chat -> onChat(event.serverId, event.event)
                    is SessionEvent.Ping -> event.serverId?.let { id ->
                        icons.ingest(id, serverHost(id), event.status)
                    }
                    is SessionEvent.State -> Unit
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(2_000)
                echoByServer.forEach { (serverId, matcher) ->
                    matcher.expire().forEach { pending ->
                        store(serverId).markDelivery(pending.messageId, DeliveryStatus.Unconfirmed)
                    }
                }
            }
        }
    }

    fun store(serverId: String): ChatStore = synchronized(stores) {
        stores.getOrPut(serverId) { ChatStore(File(filesDir, "chat/$serverId")) }
    }

    fun activate(serverId: String?) {
        _activeStore.value = if (serverId != null) store(serverId) else null
    }

    fun dropStore(serverId: String) {
        val removed = synchronized(stores) { stores.remove(serverId) }
        synchronized(echoByServer) { echoByServer.remove(serverId) }
        File(filesDir, "chat/$serverId").deleteRecursively()
        if (removed != null && _activeStore.value === removed) {
            _activeStore.value = null
        }
    }

    fun send(serverId: String, conversationId: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (session.connState.value !is ConnectionState.Connected) return false
        if (activeServerId() != serverId) return false
        val store = store(serverId)
        val conversation = resolveConversation(store, conversationId) ?: return false
        val isCommand = trimmed.startsWith("/")
        val wire = when {
            isCommand -> trimmed
            conversation.kind == ConversationKind.Whisper -> {
                val peer = conversation.peerName ?: return false
                rules.ruleSet.value.formattedMsg(peer, trimmed)
            }
            else -> trimmed
        }
        val outgoing = store.appendOutgoing(
            conversationId = conversation.id,
            kind = conversation.kind,
            title = conversation.title,
            peerName = conversation.peerName,
            text = trimmed,
            isCommand = isCommand
        )
        echoFor(serverId).track(
            PendingSend(outgoing.id, conversation.id, ChatEchoMatcher.normalize(trimmed), System.currentTimeMillis())
        )
        session.sendChat(wire)
        return true
    }

    private fun resolveConversation(store: ChatStore, conversationId: String): Conversation? {
        store.conversation(conversationId)?.let { return it }
        return when {
            conversationId == ConversationIds.PUBLIC || conversationId == ConversationIds.SYSTEM -> {
                store.ensurePublicAndSystem()
                store.conversation(conversationId)
            }
            conversationId.startsWith("whisper:") -> {
                val peer = conversationId.removePrefix("whisper:")
                if (peer.isBlank()) null
                else store.ensureConversation(conversationId, ConversationKind.Whisper, peer, peerName = peer)
            }
            conversationId.startsWith("channel:") -> {
                val channel = conversationId.removePrefix("channel:")
                if (channel.isBlank()) null
                else store.ensureConversation(conversationId, ConversationKind.Channel, channel, channelName = channel)
            }
            else -> null
        }
    }

    fun replyFromNotification(serverId: String, conversationId: String, text: String) {
        scope.launch {
            if (!send(serverId, conversationId, text)) {
                notifications.notifySendFailed(serverId, conversationId, "未能发送，请确认已连接该服务器")
            }
        }
    }

    fun ingestPing(serverId: String, host: String, status: ServerPinger.Status) {
        icons.ingest(serverId, host, status)
    }

    fun playerUuid(name: String): String? =
        session.onlinePlayers.value.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.uuid

    fun onlinePlayers(): Map<String, OnlinePlayer> = session.onlinePlayers.value

    private fun onChat(serverId: String?, event: ChatEvent) {
        val id = serverId ?: return
        val routed = router.route(event)
        val echoId = echoFor(id).match(routed.conversationId, routed.bodyPlain)
        if (echoId != null) {
            store(id).markDelivery(echoId, DeliveryStatus.Sent)
            return
        }
        val stored = store(id).ingest(routed, event.timestamp, event.sender) ?: return
        _lastPreview.value = stored.bodyPlain.take(80)
        val prefs = preferences()
        val conversation = store(id).conversation(routed.conversationId)
        if (conversation?.muted == true) return
        if (isForeground()) return
        notifications.notifyMessage(
            serverId = id,
            conversationId = routed.conversationId,
            title = conversation?.title ?: routed.peerName ?: "聊天",
            kind = routed.kind,
            message = stored,
            iconFile = if (routed.kind == ConversationKind.Public || routed.kind == ConversationKind.System) {
                icons.icons.value[id]?.file
            } else null,
            notifyWhispers = prefs.notifyWhispers,
            notifyMentions = prefs.notifyMentions,
            selfName = selfName()
        )
    }

    private fun echoFor(serverId: String): ChatEchoMatcher = synchronized(echoByServer) {
        echoByServer.getOrPut(serverId) { ChatEchoMatcher() }
    }

    fun openWhisper(serverId: String, player: String): String {
        val id = ConversationIds.whisper(player)
        store(serverId).ensureConversation(id, ConversationKind.Whisper, player, peerName = player)
        return id
    }

    fun markRead(serverId: String, conversationId: String) {
        store(serverId).markRead(conversationId)
        notifications.cancel(serverId, conversationId)
    }

    fun togglePinned(serverId: String, conversationId: String) {
        store(serverId).togglePinned(conversationId)
    }

    fun toggleMuted(serverId: String, conversationId: String) {
        store(serverId).toggleMuted(conversationId)
    }

    fun clearConversation(serverId: String, conversationId: String) {
        store(serverId).clearConversation(conversationId)
        notifications.cancel(serverId, conversationId)
    }

    fun deleteConversation(serverId: String, conversationId: String) {
        store(serverId).deleteConversation(conversationId)
        notifications.cancel(serverId, conversationId)
    }
}
