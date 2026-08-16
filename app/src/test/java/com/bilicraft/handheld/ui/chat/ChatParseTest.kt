package com.bilicraft.handheld.ui.chat

import com.bilicraft.handheld.config.ChatParseConfig
import com.bilicraft.handheld.config.ChatParsePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatParseTest {

    private val bilicraft = ChatParseConfig(preset = ChatParsePreset.Bilicraft)

    @Test
    fun `bilicraft extracts faction title player message`() {
        val r = parseChatDecorations("आ[建筑大师] │ Quartz_Crystal: 这玩意儿咋搞", bilicraft)!!
        assertEquals("आ", r.decorations.faction)
        assertEquals("建筑大师", r.decorations.title)
        assertEquals("Quartz_Crystal", r.decorations.player)
        assertEquals("这玩意儿咋搞", r.message)
    }

    @Test
    fun `protocol sender HY becomes server when body has player`() {
        val r = parseChatDecorations("आ[建筑大师] │ Quartz_Crystal: hi", bilicraft)!!
        val deco = r.decorations.enrichServerFromProtocolSender("HY")
        assertEquals("HY", deco.server)
        assertEquals("Quartz_Crystal", deco.player)
        // sender 与玩家同名时不误标为子服
        val same = r.decorations.enrichServerFromProtocolSender("Quartz_Crystal")
        assertNull(same.server)
    }

    @Test
    fun `custom can capture server named group`() {
        val r = parseChatDecorations(
            "[HY] Alice: hello",
            ChatParseConfig(
                preset = ChatParsePreset.Custom,
                pattern = """^\[(?<server>[^\]]+)\]\s*(?<player>[A-Za-z0-9_]{2,16})\s*:\s*(?<message>.*)$"""
            )
        )!!
        assertEquals("HY", r.decorations.server)
        assertEquals("Alice", r.decorations.player)
        assertEquals("hello", r.message)
    }

    @Test
    fun `bilicraft works without faction`() {
        val r = parseChatDecorations("[探险家] │ myflycat: hello", bilicraft)!!
        assertNull(r.decorations.faction)
        assertEquals("探险家", r.decorations.title)
        assertEquals("myflycat", r.decorations.player)
        assertEquals("hello", r.message)
    }

    @Test
    fun `vanilla returns null`() {
        assertNull(
            parseChatDecorations(
                "आ[建筑大师] │ Quartz_Crystal: hi",
                ChatParseConfig(preset = ChatParsePreset.Vanilla)
            )
        )
    }

    @Test
    fun `custom invalid pattern falls back`() {
        assertNull(
            parseChatDecorations(
                "Name: hi",
                ChatParseConfig(preset = ChatParsePreset.Custom, pattern = "(?unclosed")
            )
        )
    }

    @Test
    fun `custom named groups`() {
        val r = parseChatDecorations(
            "TAG|Alice> hello world",
            ChatParseConfig(
                preset = ChatParsePreset.Custom,
                pattern = """^(?<faction>[^|]+)\|(?<player>[A-Za-z0-9_]{2,16})>\s*(?<message>.*)$"""
            )
        )!!
        assertEquals("TAG", r.decorations.faction)
        assertEquals("Alice", r.decorations.player)
        assertEquals("hello world", r.message)
        assertNull(r.decorations.title)
    }
}
