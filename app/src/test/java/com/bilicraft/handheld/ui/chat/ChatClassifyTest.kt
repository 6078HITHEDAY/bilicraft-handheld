package com.bilicraft.handheld.ui.chat

import com.bilicraft.handheld.protocol.ChatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 气泡分类（自己/他人/系统）的纯 JVM 单测。
 * 覆盖：私聊进出方向、群聊角括号解析、进出服/死亡/成就等系统句识别、无发送者兜底。
 */
class ChatClassifyTest {

    private fun chat(plain: String, sender: String? = null, dmPeer: String? = null) =
        ChatEvent(plain, "", sender = sender, dmPeer = dmPeer)

    // ---- 私聊：出站 / 入站 ----

    @Test
    fun `出站私聊归为自己气泡`() {
        val c = classifyChat(chat("/msg Bob hi", sender = "Me", dmPeer = "Bob"), "Me")
        assertEquals(BubbleKind.Self, c.kind)
        assertEquals("Me", c.senderLabel)
        assertEquals("hi", c.bodyPlain)
    }

    @Test
    fun `入站私聊归为他人气泡`() {
        val c = classifyChat(chat("messages you: hi", sender = "Bob", dmPeer = "Bob"), "Me")
        assertEquals(BubbleKind.Other, c.kind)
        assertEquals("Bob", c.senderLabel)
        assertEquals("hi", c.bodyPlain)
    }

    // ---- 群聊：角括号解析 ----

    @Test
    fun `群聊自己发言归为自己气泡`() {
        val c = classifyChat(chat("<Me> hello", sender = "Me"), "Me")
        assertEquals(BubbleKind.Self, c.kind)
        assertEquals("Me", c.senderLabel)
        assertEquals("hello", c.bodyPlain)
    }

    @Test
    fun `群聊他人发言归为他人气泡并去角括号`() {
        val c = classifyChat(chat("<Alice> hello", sender = "Alice"), "Me")
        assertEquals(BubbleKind.Other, c.kind)
        assertEquals("Alice", c.senderLabel)
        assertEquals("hello", c.bodyPlain)
    }

    @Test
    fun `无 sender 字段时从角括号推断发送者`() {
        val c = classifyChat(chat("<Alice> hi there"), "Me")
        assertEquals(BubbleKind.Other, c.kind)
        assertEquals("Alice", c.senderLabel)
        assertEquals("hi there", c.bodyPlain)
    }

    @Test
    fun `正文含大于号不被误切`() {
        val c = classifyChat(chat("<Alice> a > b", sender = "Alice"), "Me")
        assertEquals(BubbleKind.Other, c.kind)
        assertEquals("a > b", c.bodyPlain)
    }

    // ---- 系统句 ----

    @Test
    fun `进出服归为系统消息`() {
        assertEquals(BubbleKind.System, classifyChat(chat("Alice joined the game"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Alice left the game"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Alice 加入了游戏"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Alice 离开了游戏"), "Me").kind)
    }

    @Test
    fun `死亡信息归为系统消息`() {
        assertEquals(BubbleKind.System, classifyChat(chat("Steve was slain by Zombie"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve was killed by Zombie"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve burned to death"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve fell from a high place"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve drowned"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve was blown up by Creeper"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve suffocated in a wall"), "Me").kind)
        assertEquals(BubbleKind.System, classifyChat(chat("Steve was shot by Skeleton"), "Me").kind)
    }

    @Test
    fun `成就归为系统消息`() {
        assertEquals(
            BubbleKind.System,
            classifyChat(chat("Steve has made the advancement [Getting Wood]"), "Me").kind
        )
        assertEquals(
            BubbleKind.System,
            classifyChat(chat("Steve has completed the challenge [Bullseye]"), "Me").kind
        )
    }

    @Test
    fun `无发送者无角括号的公告归为系统消息`() {
        val c = classifyChat(chat("欢迎来到 Bilicraft 服务器"), "Me")
        assertEquals(BubbleKind.System, c.kind)
        assertNull(c.senderLabel)
        assertEquals("欢迎来到 Bilicraft 服务器", c.bodyPlain)
    }
}
