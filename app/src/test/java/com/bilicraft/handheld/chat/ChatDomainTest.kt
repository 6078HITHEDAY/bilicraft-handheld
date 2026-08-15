package com.bilicraft.handheld.chat

import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.ChatSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSpansTest {
    @Test
    fun `empty range yields empty`() {
        val spans = listOf(ChatSpan("hello", color = 0xFF0000))
        assertTrue(spans.sliceByPlainRange(2, 2).isEmpty())
    }

    @Test
    fun `slice inside one span keeps style`() {
        val spans = listOf(ChatSpan("abcdef", color = 0x00FF00, bold = true))
        val sliced = spans.sliceByPlainRange(1, 4)
        assertEquals(1, sliced.size)
        assertEquals("bcd", sliced[0].text)
        assertEquals(0x00FF00, sliced[0].color)
        assertTrue(sliced[0].bold)
    }

    @Test
    fun `slice across span boundary splits`() {
        val spans = listOf(
            ChatSpan("ab", color = 1),
            ChatSpan("cd", color = 2),
            ChatSpan("ef", color = 3)
        )
        val sliced = spans.sliceByPlainRange(1, 5)
        assertEquals(listOf("b", "cd", "e"), sliced.map { it.text })
        assertEquals(listOf(1, 2, 3), sliced.map { it.color })
    }
}

class ChatRouterTest {
    private val rules = ChatRuleSet()
    private val router = ChatRouter(ruleSet = { rules }, selfName = { "Me" })

    @Test
    fun `vanilla incoming translate key routes to whisper`() {
        val routed = router.route(
            ChatEvent(
                plainText = "Steve 悄悄对你说：hello there",
                rawJson = "",
                translateKey = "commands.message.display.incoming"
            )
        )
        assertEquals(ConversationKind.Whisper, routed.kind)
        assertEquals("Steve", routed.peerName)
        assertEquals("hello there", routed.bodyPlain)
        assertEquals(MessageDirection.In, routed.direction)
    }

    @Test
    fun `arrow whisper outgoing`() {
        val routed = router.route(ChatEvent(plainText = "[me -> Alex] secret", rawJson = ""))
        assertEquals(ConversationKind.Whisper, routed.kind)
        assertEquals("Alex", routed.peerName)
        assertEquals("secret", routed.bodyPlain)
        assertEquals(MessageDirection.Out, routed.direction)
    }

    @Test
    fun `player chat without target goes public`() {
        val routed = router.route(
            ChatEvent(plainText = "<Steve> hi", rawJson = "", sender = "Steve")
        )
        assertEquals(ConversationIds.PUBLIC, routed.conversationId)
        assertEquals(ConversationKind.Public, routed.kind)
        // body must be content-only so optimistic send echo can match
        assertEquals("hi", routed.bodyPlain)
    }

    @Test
    fun `public self echo body matches pending send`() {
        val matcher = ChatEchoMatcher()
        matcher.track(
            PendingSend("m1", ConversationIds.PUBLIC, ChatEchoMatcher.normalize("hi"), System.currentTimeMillis())
        )
        val routed = router.route(
            ChatEvent(plainText = "<Me> hi", rawJson = "", sender = "Me")
        )
        assertEquals("m1", matcher.match(routed.conversationId, routed.bodyPlain))
    }

    @Test
    fun `system message without sender`() {
        val routed = router.route(ChatEvent(plainText = "已连接到服务器", rawJson = ""))
        assertEquals(ConversationIds.SYSTEM, routed.conversationId)
    }

    @Test
    fun `channel rule`() {
        val routed = router.route(ChatEvent(plainText = "[全服] Steve: 大家好", rawJson = ""))
        assertEquals(ConversationKind.Channel, routed.kind)
        assertEquals("全服", routed.channelName)
        assertEquals("大家好", routed.bodyPlain)
    }

    @Test
    fun `player chat target routes to whisper`() {
        val routed = router.route(
            ChatEvent(plainText = "<Steve> psst", rawJson = "", sender = "Steve", target = "Me")
        )
        assertEquals(ConversationKind.Whisper, routed.kind)
        assertEquals("Steve", routed.peerName)
        assertEquals("psst", routed.bodyPlain)
    }
}

class ChatEchoMatcherTest {
    @Test
    fun `matches normalized body in window`() {
        var now = 1_000L
        val matcher = ChatEchoMatcher(windowMs = 8_000L, now = { now })
        matcher.track(PendingSend("m1", ConversationIds.whisper("steve"), ChatEchoMatcher.normalize("hello  world"), now))
        val matched = matcher.match(ConversationIds.whisper("steve"), "hello world")
        assertEquals("m1", matched)
        assertEquals(null, matcher.match(ConversationIds.whisper("steve"), "hello world"))
    }

    @Test
    fun `expire after window`() {
        var now = 1_000L
        val matcher = ChatEchoMatcher(windowMs = 8_000L, now = { now })
        matcher.track(PendingSend("m1", ConversationIds.PUBLIC, "hi", now))
        now = 10_000L
        val expired = matcher.expire()
        assertEquals(1, expired.size)
        assertEquals("m1", expired[0].messageId)
    }
}

class ChatStoreTest {
    @Test
    fun `jsonl round trip and unread`() {
        val dir = kotlin.io.path.createTempDirectory("chat-store").toFile()
        val store = ChatStore(dir)
        store.ingest(
            RoutedChat(
                conversationId = ConversationIds.whisper("steve"),
                kind = ConversationKind.Whisper,
                peerName = "Steve",
                channelName = null,
                bodyPlain = "hi",
                bodySpans = listOf(ChatSpan("hi")),
                direction = MessageDirection.In
            ),
            eventTimestamp = 123L,
            sender = "Steve"
        )
        assertEquals(1, store.conversation(ConversationIds.whisper("steve"))?.unreadCount)
        val reloaded = ChatStore(dir)
        assertEquals("hi", reloaded.conversation(ConversationIds.whisper("steve"))?.lastPreview)
        assertEquals("hi", reloaded.messagesFor(ConversationIds.whisper("steve")).single().bodyPlain)
    }

    @Test
    fun `stale Sending becomes Unconfirmed on reload`() {
        val dir = kotlin.io.path.createTempDirectory("chat-store-sending").toFile()
        val store = ChatStore(dir)
        store.appendOutgoing(
            conversationId = ConversationIds.PUBLIC,
            kind = ConversationKind.Public,
            title = "公屏聊天",
            peerName = null,
            text = "pending",
            isCommand = false
        )
        assertEquals(DeliveryStatus.Sending, store.messagesFor(ConversationIds.PUBLIC).single().delivery)
        val reloaded = ChatStore(dir)
        assertEquals(DeliveryStatus.Unconfirmed, reloaded.messagesFor(ConversationIds.PUBLIC).single().delivery)
    }

    @Test
    fun `markDelivery persists across reload`() {
        val dir = kotlin.io.path.createTempDirectory("chat-store-delivery").toFile()
        val store = ChatStore(dir)
        val outgoing = store.appendOutgoing(
            conversationId = ConversationIds.PUBLIC,
            kind = ConversationKind.Public,
            title = "公屏聊天",
            peerName = null,
            text = "ok",
            isCommand = false
        )
        store.markDelivery(outgoing.id, DeliveryStatus.Sent)
        val reloaded = ChatStore(dir)
        assertEquals(DeliveryStatus.Sent, reloaded.messagesFor(ConversationIds.PUBLIC).single().delivery)
    }
}

class ChatRuleSetTest {
    @Test
    fun `msg template substitutes player and message`() {
        val set = ChatRuleSet(msgCommandTemplate = "/m {player} {message}")
        assertEquals("/m Alex hello", set.formattedMsg("Alex", "hello"))
    }

    @Test
    fun `merged builtins keep user edited ids`() {
        val custom = ChatRule(id = "vanilla-in-arrow", kind = ChatRuleKind.WhisperIn, pattern = "x", note = "mine")
        val merged = ChatRuleSet(rules = listOf(custom)).mergedWithBuiltins()
        assertEquals("x", merged.rules.first { it.id == "vanilla-in-arrow" }.pattern)
        assertTrue(merged.rules.any { it.id == "channel-common" })
    }
}
