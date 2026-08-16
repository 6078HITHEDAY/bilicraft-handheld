package com.bilicraft.handheld.ui.chat

import com.bilicraft.handheld.protocol.RosterPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarIdentityTest {

    private fun player(name: String, uuid: String = "u-$name") =
        RosterPlayer(uuid = uuid, name = name, online = true)

    @Test
    fun `extract strips vip prefix`() {
        assertEquals("Steve", extractMcUsername("[VIP] Steve"))
        assertEquals("Notch", extractMcUsername("Admin | Notch"))
        assertEquals("Alice_01", extractMcUsername("Alice_01"))
        assertNull(extractMcUsername(""))
    }

    @Test
    fun `uuid wins and maps roster name`() {
        val roster = listOf(player("RealName", "1111-2222"))
        val id = resolveAvatarIdentity(
            label = "[VIP] Nick",
            senderUuid = "1111-2222",
            roster = roster
        )
        assertEquals("RealName", id.name)
        assertEquals("1111-2222", id.uuid)
    }

    @Test
    fun `preferred self name used even without roster`() {
        val id = resolveAvatarIdentity(
            label = "[VIP] NickName",
            preferredName = "RealMcName",
            preferredUuid = "abcd-efgh"
        )
        assertEquals("RealMcName", id.name)
        assertEquals("abcd-efgh", id.uuid)
    }

    @Test
    fun `preferred peer name used for dm`() {
        val id = resolveAvatarIdentity(
            label = "messages-you-fallback",
            preferredName = "Bob"
        )
        assertEquals("Bob", id.name)
    }

    @Test
    fun `roster endsWith match for prefixed label`() {
        val roster = listOf(player("CoolPlayer"))
        val id = resolveAvatarIdentity(
            label = "[MVP+] CoolPlayer",
            roster = roster
        )
        assertEquals("CoolPlayer", id.name)
        assertEquals("u-CoolPlayer", id.uuid)
    }

    @Test
    fun `preferred player from decorations used for avatar`() {
        val id = resolveAvatarIdentity(
            label = "HY",
            preferredName = "qingkuang33",
            roster = listOf(player("qingkuang33", "uuid-qk"))
        )
        assertEquals("qingkuang33", id.name)
        assertEquals("uuid-qk", id.uuid)
    }

    @Test
    fun `labelMatchesSelf handles prefix and uuid`() {
        assertTrue(labelMatchesSelf("[VIP] Alice", "Alice"))
        assertTrue(labelMatchesSelf("Alice", "Alice"))
        assertFalse(labelMatchesSelf("NotAlice", "Alice"))
        assertTrue(
            labelMatchesSelf(
                label = "whatever",
                selfName = "Alice",
                selfUuid = "aaaabbbbccccdddd",
                senderUuid = "aaaa-bbbb-cccc-dddd"
            )
        )
    }
}
