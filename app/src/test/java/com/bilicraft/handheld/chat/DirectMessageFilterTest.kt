package com.bilicraft.handheld.chat

import com.bilicraft.handheld.protocol.ChatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 私聊识别/分流的纯 JVM 单测。
 * 覆盖：resolveDmPeer 各入站格式、公屏过滤、私聊页过滤、去壳、进出方向判定。
 */
class DirectMessageFilterTest {

    // ---- resolveDmPeer：解析私聊对象 ----

    @Test
    fun `messages you 取 sender 兜底 fallback`() {
        assertEquals("Alice", resolveDmPeer("messages you: hello", "Alice", "Me"))
        assertEquals("Bob", resolveDmPeer("messages you: hello", null, "Me", "Bob"))
        assertNull(resolveDmPeer("messages you: hello", null, "Me"))
    }

    @Test
    fun `messages 你 中文变体`() {
        assertEquals("Alice", resolveDmPeer("messages 你: 你好", "Alice", "Me"))
    }

    @Test
    fun `messages 指名格式`() {
        assertEquals("Alice", resolveDmPeer("messages Alice: hi", null, "Me"))
        // 自己发的 messages you 不算私聊对象
        assertNull(resolveDmPeer("messages you: hi", null, "Me"))
    }

    @Test
    fun `命令回显 msg tell w whisper`() {
        assertEquals("Alice", resolveDmPeer("/msg Alice hello", null, "Me"))
        assertEquals("Alice", resolveDmPeer("/tell Alice hello", null, "Me"))
        assertEquals("Alice", resolveDmPeer("/w Alice hello", null, "Me"))
        assertEquals("Alice", resolveDmPeer("/whisper Alice hello", null, "Me"))
    }

    @Test
    fun `英文 whisper 进出站格式`() {
        assertEquals("Alice", resolveDmPeer("You whisper to Alice: hi", null, "Me"))
        assertEquals("Alice", resolveDmPeer("You whisperto Alice: hi", null, "Me"))
        assertEquals("Alice", resolveDmPeer("Alice whispers: hi", null, "Me"))
        assertEquals("Alice", resolveDmPeer("Alice whispers to you: hi", null, "Me"))
    }

    @Test
    fun `中文悄悄话格式`() {
        assertEquals("Alice", resolveDmPeer("你悄悄地对Alice说: 你好", null, "Me"))
        assertEquals("Alice", resolveDmPeer("你悄声对 Alice 说: 你好", null, "Me"))
        assertEquals("Alice", resolveDmPeer("Alice悄悄地对你说: 你好", null, "Me"))
        assertEquals("Alice", resolveDmPeer("Alice悄声对你说: 你好", null, "Me"))
        assertEquals("Alice", resolveDmPeer("Alice对你说: 你好", null, "Me"))
    }

    @Test
    fun `方括号与箭头格式按自己身份取对端`() {
        assertEquals("Bob", resolveDmPeer("[Me -> Bob]: hi", null, "Me"))
        assertEquals("Alice", resolveDmPeer("[Alice -> Me]: hi", null, "Me"))
        assertEquals("Bob", resolveDmPeer("Me -> Bob hi", null, "Me"))
        assertEquals("Alice", resolveDmPeer("Alice -> Me hi", null, "Me"))
    }

    @Test
    fun `普通群聊不是私聊`() {
        assertNull(resolveDmPeer("<Alice> hello everyone", "Alice", "Me"))
        assertNull(resolveDmPeer("Alice joined the game", null, "Me"))
        assertNull(resolveDmPeer("", null, "Me"))
    }

    @Test
    fun `whisper 标记但无名字时兜底 sender 或 fallback`() {
        assertEquals("Alice", resolveDmPeer("Alice 告诉你 悄悄话", "Alice", "Me"))
        assertEquals("Bob", resolveDmPeer("私聊消息无法识别名字", null, "Me", "Bob"))
        assertNull(resolveDmPeer("给你一条悄悄话", null, "Me"))
    }

    // ---- looksLikeDmPlain：公屏隐藏判定 ----

    @Test
    fun `私聊文案识别为隐藏`() {
        assertTrue(looksLikeDmPlain("messages you: hi"))
        assertTrue(looksLikeDmPlain("/msg Alice hi"))
        assertTrue(looksLikeDmPlain("You whisper to Alice: hi"))
        assertTrue(looksLikeDmPlain("Alice whispers: hi"))
        assertTrue(looksLikeDmPlain("你悄悄地对Alice说: 你好"))
        assertTrue(looksLikeDmPlain("[Me -> Bob]: hi"))
        assertTrue(looksLikeDmPlain("Alice -> Me hi"))
        assertFalse(looksLikeDmPlain("<Alice> hi"))
        assertFalse(looksLikeDmPlain("Alice joined the game"))
        assertFalse(looksLikeDmPlain(""))
    }

    // ---- filterPublicChat：公屏剔除私聊 ----

    @Test
    fun `公屏过滤保留群聊与系统消息`() {
        val log = listOf(
            ChatEvent("<Alice> hello", "", sender = "Alice"),
            ChatEvent("Alice joined the game", ""),
            ChatEvent("messages you: secret", "", sender = "Bob"),
            ChatEvent("/msg Alice hi", "", sender = "Me")
        )
        val kept = filterPublicChat(log, "Me")
        assertEquals(2, kept.size)
        assertEquals("<Alice> hello", kept[0].plainText)
        assertEquals("Alice joined the game", kept[1].plainText)
    }

    @Test
    fun `公屏过滤剔除已标记 dmPeer 的消息`() {
        val log = listOf(
            ChatEvent("messages you: hi", "", sender = "Bob", dmPeer = "Bob"),
            ChatEvent("<Alice> hello", "", sender = "Alice")
        )
        val kept = filterPublicChat(log, "Me")
        assertEquals(1, kept.size)
        assertEquals("<Alice> hello", kept[0].plainText)
    }

    // ---- filterDirectMessages：私聊页过滤 ----

    @Test
    fun `私聊页只留对应 peer`() {
        val log = listOf(
            ChatEvent("messages you: hi", "", sender = "Bob", dmPeer = "Bob"),
            ChatEvent("messages you: hey", "", sender = "Alice", dmPeer = "Alice"),
            ChatEvent("<Alice> 公屏", "", sender = "Alice"),
            ChatEvent("Alice joined the game", "")
        )
        val dm = filterDirectMessages(log, "Bob", "Me")
        assertEquals(1, dm.size)
        assertEquals("Bob", dm[0].dmPeer)
    }

    @Test
    fun `私聊页对未打标消息按 fallback 兜底并补标`() {
        val log = listOf(
            ChatEvent("messages you: hi", "", sender = "Bob"),
            ChatEvent("messages you: hey", "", sender = "Alice"),
            ChatEvent("Bob joined the game", "")
        )
        val dm = filterDirectMessages(log, "Bob", "Me")
        assertEquals(1, dm.size)
        assertEquals("Bob", dm[0].dmPeer)
        assertEquals("messages you: hi", dm[0].plainText)
    }

    @Test
    fun `私聊页忽略大小写匹配`() {
        val log = listOf(
            ChatEvent("messages you: hi", "", sender = "alice", dmPeer = "alice")
        )
        val dm = filterDirectMessages(log, "ALICE", "Me")
        assertEquals(1, dm.size)
    }

    @Test
    fun `空玩家名不匹配任何私聊`() {
        val log = listOf(ChatEvent("messages you: hi", "", sender = "Bob"))
        assertTrue(filterDirectMessages(log, "  ", "Me").isEmpty())
    }

    // ---- stripDmWrapper：去掉私聊前缀 ----

    @Test
    fun `去壳 messages 格式`() {
        assertEquals("hello", stripDmWrapper("messages you: hello", "Bob", "Me"))
        assertEquals("hi there", stripDmWrapper("messages Alice: hi there", "Alice", "Me"))
    }

    @Test
    fun `去壳命令与 whisper 格式`() {
        assertEquals("hello", stripDmWrapper("/msg Alice hello", "Alice", "Me"))
        assertEquals("hi", stripDmWrapper("You whisper to Alice: hi", "Alice", "Me"))
        assertEquals("hi", stripDmWrapper("Alice whispers to you: hi", "Alice", "Me"))
        assertEquals("你好", stripDmWrapper("你悄悄地对Alice说: 你好", "Alice", "Me"))
        assertEquals("你好", stripDmWrapper("Alice悄悄地对你说: 你好", "Alice", "Me"))
    }

    @Test
    fun `去壳方括号与箭头格式`() {
        assertEquals("hi", stripDmWrapper("[Me -> Bob]: hi", "Bob", "Me"))
        assertEquals("hi", stripDmWrapper("Me -> Bob hi", "Bob", "Me"))
    }

    @Test
    fun `无壳文本原样返回`() {
        assertEquals("hello", stripDmWrapper("hello", "Bob", "Me"))
    }

    // ---- isOutgoingDm：进出方向 ----

    @Test
    fun `messages you 判定为入站`() {
        assertFalse(isOutgoingDm(ChatEvent("messages you: hi", "", sender = "Bob"), "Me"))
    }

    @Test
    fun `命令与英文出站格式判定为出站`() {
        assertTrue(isOutgoingDm(ChatEvent("/msg Alice hi", "", sender = "Me"), "Me"))
        assertTrue(isOutgoingDm(ChatEvent("You whisper to Alice: hi", "", sender = "Me"), "Me"))
        assertTrue(isOutgoingDm(ChatEvent("你悄悄地对Alice说: 你好", "", sender = "Me"), "Me"))
    }

    @Test
    fun `sender 与自己对上判定为出站`() {
        assertTrue(isOutgoingDm(ChatEvent("messages Alice: hi", "", sender = "Me"), "Me"))
        assertFalse(isOutgoingDm(ChatEvent("messages Alice: hi", "", sender = "Alice"), "Me"))
    }

    @Test
    fun `方括号自己在前判定为出站`() {
        assertTrue(isOutgoingDm(ChatEvent("[Me -> Alice]: hi", "", sender = "Me"), "Me"))
        assertFalse(isOutgoingDm(ChatEvent("[Alice -> Me]: hi", "", sender = "Alice"), "Me"))
    }

    @Test
    fun `未登录或无名字不判定出站`() {
        assertFalse(isOutgoingDm(ChatEvent("/msg Alice hi", "", sender = "Me"), "未登录"))
        assertFalse(isOutgoingDm(ChatEvent("/msg Alice hi", "", sender = "Me"), ""))
    }
}
