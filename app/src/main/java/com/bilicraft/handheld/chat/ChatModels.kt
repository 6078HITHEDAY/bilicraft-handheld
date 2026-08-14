package com.bilicraft.handheld.chat

import com.bilicraft.handheld.protocol.ChatSpan
import kotlinx.serialization.Serializable

enum class ConversationKind {
    Public,
    Whisper,
    Channel,
    System
}

enum class MessageDirection {
    In,
    Out
}

enum class DeliveryStatus {
    Received,
    Sending,
    Sent,
    Unconfirmed
}

object ConversationIds {
    const val PUBLIC = "public"
    const val SYSTEM = "system"

    fun whisper(player: String): String = "whisper:${player.lowercase()}"
    fun channel(name: String): String = "channel:${name.lowercase()}"
}

@Serializable
data class StoredMessage(
    val id: String,
    val conversationId: String,
    val timestamp: Long,
    val sender: String? = null,
    val plainText: String,
    val bodyPlain: String,
    val spans: List<ChatSpan> = emptyList(),
    val direction: MessageDirection = MessageDirection.In,
    val kind: ConversationKind = ConversationKind.Public,
    val delivery: DeliveryStatus = DeliveryStatus.Received,
    val isCommand: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val peerName: String? = null,
    val channelName: String? = null,
    val lastPreview: String = "",
    val lastTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val lastReadAt: Long = 0L,
    val pinned: Boolean = false,
    val muted: Boolean = false
)

data class RoutedChat(
    val conversationId: String,
    val kind: ConversationKind,
    val peerName: String?,
    val channelName: String?,
    val bodyPlain: String,
    val bodySpans: List<ChatSpan>,
    val direction: MessageDirection,
    val muted: Boolean = false
)
