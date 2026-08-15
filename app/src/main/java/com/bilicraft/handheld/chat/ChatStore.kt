package com.bilicraft.handheld.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class ChatStore(
    private val dir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val conversationsFile = File(dir, "conversations.json")
    private val messagesFile = File(dir, "messages.jsonl")

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<StoredMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<StoredMessage>>> = _messages.asStateFlow()

    init {
        dir.mkdirs()
        load()
    }

    fun ingest(routed: RoutedChat, eventTimestamp: Long, sender: String?): StoredMessage? {
        if (routed.muted) return null
        val message = StoredMessage(
            id = UUID.randomUUID().toString(),
            conversationId = routed.conversationId,
            timestamp = eventTimestamp,
            sender = sender ?: routed.peerName,
            plainText = routed.bodyPlain,
            bodyPlain = routed.bodyPlain,
            spans = routed.bodySpans,
            direction = routed.direction,
            kind = routed.kind,
            delivery = if (routed.direction == MessageDirection.Out) DeliveryStatus.Sent else DeliveryStatus.Received
        )
        appendMessage(message, preview = routed.bodyPlain, title = titleFor(routed), kind = routed.kind, peer = routed.peerName, channel = routed.channelName)
        return message
    }

    fun appendOutgoing(
        conversationId: String,
        kind: ConversationKind,
        title: String,
        peerName: String?,
        text: String,
        isCommand: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): StoredMessage {
        val message = StoredMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            timestamp = timestamp,
            sender = null,
            plainText = text,
            bodyPlain = text,
            spans = emptyList(),
            direction = MessageDirection.Out,
            kind = kind,
            delivery = DeliveryStatus.Sending,
            isCommand = isCommand
        )
        appendMessage(message, preview = text, title = title, kind = kind, peer = peerName, channel = null, incrementUnread = false)
        return message
    }

    fun markDelivery(messageId: String, status: DeliveryStatus) {
        var changed = false
        _messages.update { current ->
            current.mapValues { (_, list) ->
                list.map { msg ->
                    if (msg.id == messageId && msg.delivery != status) {
                        changed = true
                        msg.copy(delivery = status)
                    } else msg
                }
            }
        }
        if (changed) rewriteJsonl()
    }

    fun markRead(conversationId: String) {
        updateConversation(conversationId) { it.copy(unreadCount = 0, lastReadAt = System.currentTimeMillis()) }
    }

    fun togglePinned(conversationId: String) {
        updateConversation(conversationId) { it.copy(pinned = !it.pinned) }
    }

    fun toggleMuted(conversationId: String) {
        updateConversation(conversationId) { it.copy(muted = !it.muted) }
    }

    fun clearConversation(conversationId: String) {
        _messages.update { it + (conversationId to emptyList()) }
        updateConversation(conversationId) { it.copy(lastPreview = "", lastTimestamp = 0L, unreadCount = 0) }
        rewriteJsonl()
    }

    fun deleteConversation(conversationId: String) {
        if (conversationId == ConversationIds.PUBLIC) return
        _messages.update { it - conversationId }
        _conversations.update { list -> list.filterNot { it.id == conversationId } }
        persistConversations()
        rewriteJsonl()
    }

    fun ensurePublicAndSystem() {
        upsertSeed(ConversationIds.PUBLIC, ConversationKind.Public, "公屏聊天")
        upsertSeed(ConversationIds.SYSTEM, ConversationKind.System, "系统消息")
    }

    fun ensureConversation(
        id: String,
        kind: ConversationKind,
        title: String,
        peerName: String? = null,
        channelName: String? = null
    ): Conversation {
        conversation(id)?.let { return it }
        val created = Conversation(id = id, kind = kind, title = title, peerName = peerName, channelName = channelName)
        _conversations.update { list ->
            val public = list.firstOrNull { it.id == ConversationIds.PUBLIC }
            val rest = list.filter { it.id != ConversationIds.PUBLIC && it.id != id }
            if (public != null) listOf(public) + rest + created else rest + created
        }
        persistConversations()
        return created
    }

    fun conversation(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    fun messagesFor(id: String): List<StoredMessage> = _messages.value[id].orEmpty()

    private fun appendMessage(
        message: StoredMessage,
        preview: String,
        title: String,
        kind: ConversationKind,
        peer: String?,
        channel: String?,
        incrementUnread: Boolean = message.direction == MessageDirection.In
    ) {
        _messages.update { current ->
            val existing = current[message.conversationId].orEmpty()
            current + (message.conversationId to (existing + message).takeLast(KEEP_IN_MEMORY))
        }
        upsertConversation(message.conversationId, title, kind, peer, channel, preview, message.timestamp, incrementUnread)
        messagesFile.appendText(json.encodeToString(message) + "\n")
        maybeCompact()
    }

    private fun upsertSeed(id: String, kind: ConversationKind, title: String) {
        if (_conversations.value.any { it.id == id }) return
        _conversations.update { list ->
            val seeded = Conversation(id = id, kind = kind, title = title)
            if (id == ConversationIds.PUBLIC) listOf(seeded) + list else list + seeded
        }
        persistConversations()
    }

    private fun upsertConversation(
        id: String,
        title: String,
        kind: ConversationKind,
        peer: String?,
        channel: String?,
        preview: String,
        timestamp: Long,
        incrementUnread: Boolean
    ) {
        _conversations.update { list ->
            val existing = list.firstOrNull { it.id == id }
            val next = (existing ?: Conversation(id = id, kind = kind, title = title, peerName = peer, channelName = channel)).copy(
                title = existing?.title ?: title,
                peerName = existing?.peerName ?: peer,
                channelName = existing?.channelName ?: channel,
                lastPreview = preview,
                lastTimestamp = timestamp,
                unreadCount = if (incrementUnread && existing?.muted != true) (existing?.unreadCount ?: 0) + 1 else existing?.unreadCount ?: 0
            )
            val without = list.filterNot { it.id == id }
            when (id) {
                ConversationIds.PUBLIC -> listOf(next) + without.filter { it.id != ConversationIds.PUBLIC }
                else -> {
                    val public = without.firstOrNull { it.id == ConversationIds.PUBLIC }
                    val rest = without.filter { it.id != ConversationIds.PUBLIC } + next
                    if (public != null) listOf(public) + rest else rest
                }
            }
        }
        persistConversations()
    }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        _conversations.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persistConversations()
    }

    private fun titleFor(routed: RoutedChat): String = when (routed.kind) {
        ConversationKind.Public -> "公屏聊天"
        ConversationKind.System -> "系统消息"
        ConversationKind.Whisper -> routed.peerName ?: "私聊"
        ConversationKind.Channel -> routed.channelName ?: "频道"
    }

    private fun load() {
        val loaded = runCatching {
            if (conversationsFile.exists()) json.decodeFromString<List<Conversation>>(conversationsFile.readText()) else emptyList()
        }.getOrDefault(emptyList())
        _conversations.value = loaded
        ensurePublicAndSystem()
        val recent = readTailMessages()
        _messages.value = recent
            .map { msg ->
                if (msg.delivery == DeliveryStatus.Sending) msg.copy(delivery = DeliveryStatus.Unconfirmed) else msg
            }
            .groupBy { it.conversationId }
            .mapValues { (_, list) -> list.takeLast(KEEP_IN_MEMORY) }
    }

    private fun persistConversations() {
        conversationsFile.writeText(json.encodeToString(_conversations.value))
    }

    private fun readTailMessages(): List<StoredMessage> {
        if (!messagesFile.exists()) return emptyList()
        val lines = tailLines(messagesFile, KEEP_IN_MEMORY * 4)
        return lines.mapNotNull { line ->
            runCatching { json.decodeFromString<StoredMessage>(line) }.getOrNull()
        }
    }

    private fun maybeCompact() {
        val lineCount = runCatching {
            messagesFile.useLines { it.count() }
        }.getOrDefault(0)
        if (lineCount <= COMPACT_AFTER_LINES) return
        rewriteJsonl()
    }

    private fun rewriteJsonl() {
        val keep = _messages.value.values.flatten().sortedBy { it.timestamp }.takeLast(KEEP_ON_DISK)
        messagesFile.writeText(keep.joinToString("") { json.encodeToString(it) + "\n" })
    }

    private fun tailLines(file: File, maxLines: Int): List<String> {
        if (file.length() < 256 * 1024) {
            return file.readLines().takeLast(maxLines)
        }
        RandomAccessFile(file, "r").use { raf ->
            val chunk = minOf(file.length(), 256 * 1024L)
            raf.seek(file.length() - chunk)
            val bytes = ByteArray(chunk.toInt())
            raf.readFully(bytes)
            val text = String(bytes, Charsets.UTF_8)
            val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            return lines.takeLast(maxLines)
        }
    }

    private companion object {
        const val KEEP_IN_MEMORY = 200
        const val KEEP_ON_DISK = 500
        const val COMPACT_AFTER_LINES = 20_000
    }
}
