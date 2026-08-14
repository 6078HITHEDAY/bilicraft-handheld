package com.bilicraft.handheld.chat

import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.ChatSpan

class ChatRouter(
    private val ruleSet: () -> ChatRuleSet,
    private val selfName: () -> String?
) {
    fun route(event: ChatEvent): RoutedChat {
        val rules = ruleSet().rules.filter { it.enabled && it.compiled != null }
        val muted = rules.any { it.kind == ChatRuleKind.Mute && it.compiled!!.containsMatchIn(event.plainText) }
        translateWhisper(event)?.let { return it.copy(muted = muted) }
        targetWhisper(event)?.let { return it.copy(muted = muted) }
        regexRoute(event, rules)?.let { return it.copy(muted = muted) }
        if (!event.sender.isNullOrBlank()) {
            return RoutedChat(
                conversationId = ConversationIds.PUBLIC,
                kind = ConversationKind.Public,
                peerName = null,
                channelName = null,
                bodyPlain = event.plainText,
                bodySpans = event.spans.ifEmpty { listOf(ChatSpan(event.plainText)) },
                direction = if (event.sender.equals(selfName(), ignoreCase = true)) {
                    MessageDirection.Out
                } else {
                    MessageDirection.In
                },
                muted = muted
            )
        }
        return RoutedChat(
            conversationId = ConversationIds.SYSTEM,
            kind = ConversationKind.System,
            peerName = null,
            channelName = null,
            bodyPlain = event.plainText,
            bodySpans = event.spans.ifEmpty { listOf(ChatSpan(event.plainText)) },
            direction = MessageDirection.In,
            muted = muted
        )
    }

    fun tryMatch(plainText: String): RoutedChat? {
        val dummy = ChatEvent(plainText = plainText, rawJson = plainText)
        val routed = route(dummy)
        return routed.takeIf { it.kind != ConversationKind.Public && it.kind != ConversationKind.System }
            ?: routed.takeIf { dummy.sender != null }
    }

    private fun translateWhisper(event: ChatEvent): RoutedChat? {
        val key = event.translateKey ?: return null
        val incoming = key == "commands.message.display.incoming"
        val outgoing = key == "commands.message.display.outgoing"
        if (!incoming && !outgoing) return null
        val kind = if (incoming) ChatRuleKind.WhisperIn else ChatRuleKind.WhisperOut
        val matched = matchRules(event, kind) ?: fallbackWhisperFromPlain(event, incoming)
        return matched
    }

    private fun targetWhisper(event: ChatEvent): RoutedChat? {
        val target = event.target?.takeIf { it.isNotBlank() } ?: return null
        val self = selfName()
        val outgoing = !event.sender.isNullOrBlank() && event.sender.equals(self, ignoreCase = true)
        val peer = if (outgoing) target else (event.sender ?: target)
        val body = stripChatDecor(event)
        return RoutedChat(
            conversationId = ConversationIds.whisper(peer),
            kind = ConversationKind.Whisper,
            peerName = peer,
            channelName = null,
            bodyPlain = body.first,
            bodySpans = body.second,
            direction = if (outgoing) MessageDirection.Out else MessageDirection.In
        )
    }

    private fun regexRoute(event: ChatEvent, rules: List<ChatRule>): RoutedChat? {
        for (kind in listOf(ChatRuleKind.WhisperIn, ChatRuleKind.WhisperOut, ChatRuleKind.Channel)) {
            matchRules(event, kind, rules)?.let { return it }
        }
        return null
    }

    private fun matchRules(
        event: ChatEvent,
        kind: ChatRuleKind,
        rules: List<ChatRule> = ruleSet().rules.filter { it.enabled && it.compiled != null }
    ): RoutedChat? {
        for (rule in rules.filter { it.kind == kind }) {
            val match = rule.compiled?.find(event.plainText) ?: continue
            val player = match.named(rule.playerGroup)
            val body = match.named(rule.bodyGroup) ?: event.plainText
            val channel = match.named(rule.channelGroup)
            val bodyRange = match.groupRange(rule.bodyGroup) ?: (0 to event.plainText.length)
            val bodySpans = event.spans.sliceByPlainRange(bodyRange.first, bodyRange.second)
                .ifEmpty { listOf(ChatSpan(body)) }
            return when (kind) {
                ChatRuleKind.WhisperIn, ChatRuleKind.WhisperOut -> {
                    val peer = player ?: return null
                    RoutedChat(
                        conversationId = ConversationIds.whisper(peer),
                        kind = ConversationKind.Whisper,
                        peerName = peer,
                        channelName = null,
                        bodyPlain = body,
                        bodySpans = bodySpans,
                        direction = if (kind == ChatRuleKind.WhisperOut) MessageDirection.Out else MessageDirection.In
                    )
                }
                ChatRuleKind.Channel -> {
                    val channelName = channel ?: return null
                    RoutedChat(
                        conversationId = ConversationIds.channel(channelName),
                        kind = ConversationKind.Channel,
                        peerName = player,
                        channelName = channelName,
                        bodyPlain = body,
                        bodySpans = bodySpans,
                        direction = if (player.equals(selfName(), ignoreCase = true)) {
                            MessageDirection.Out
                        } else {
                            MessageDirection.In
                        }
                    )
                }
                ChatRuleKind.Mute -> null
            }
        }
        return null
    }

    private fun fallbackWhisperFromPlain(event: ChatEvent, incoming: Boolean): RoutedChat? {
        val kind = if (incoming) ChatRuleKind.WhisperIn else ChatRuleKind.WhisperOut
        return matchRules(event, kind) ?: run {
            val tokens = event.plainText.split(Regex("[:：]"), limit = 2)
            val player = Regex("""\b([A-Za-z0-9_]{3,16})\b""").find(tokens.firstOrNull().orEmpty())?.groupValues?.get(1)
                ?: return null
            val body = tokens.getOrNull(1)?.trim()?.ifBlank { event.plainText } ?: event.plainText
            RoutedChat(
                conversationId = ConversationIds.whisper(player),
                kind = ConversationKind.Whisper,
                peerName = player,
                channelName = null,
                bodyPlain = body,
                bodySpans = listOf(ChatSpan(body)),
                direction = if (incoming) MessageDirection.In else MessageDirection.Out
            )
        }
    }

    private fun stripChatDecor(event: ChatEvent): Pair<String, List<ChatSpan>> {
        val prefix = event.sender?.let { "<$it> " }
        if (prefix != null && event.plainText.startsWith(prefix)) {
            val body = event.plainText.removePrefix(prefix)
            return body to event.spans.sliceByPlainRange(prefix.length, event.plainText.length).ifEmpty { listOf(ChatSpan(body)) }
        }
        return event.plainText to event.spans.ifEmpty { listOf(ChatSpan(event.plainText)) }
    }
}

private fun MatchResult.named(name: String): String? =
    runCatching { groups[name]?.value }.getOrNull()?.takeIf { it.isNotBlank() }

private fun MatchResult.groupRange(name: String): Pair<Int, Int>? {
    val group = runCatching { groups[name] }.getOrNull() ?: return null
    return group.range.first to (group.range.last + 1)
}
