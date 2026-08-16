package com.bilicraft.handheld.ui.vm

import com.bilicraft.handheld.config.ServerContact
import com.bilicraft.handheld.config.scopedContactId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 跨服同玩家 UUID 不得互相覆盖：scoped id 后两服各留一条。 */
class ContactIdOverwriteTest {

    @Test
    fun samePlayerUuidAcrossServersKeepsBothRows() {
        val contacts = mutableListOf<ServerContact>()
        fun upsert(contact: ServerContact) {
            val idx = contacts.indexOfFirst {
                it.id == contact.id ||
                    (it.serverId == contact.serverId &&
                        it.playerName.equals(contact.playerName, ignoreCase = true))
            }
            if (idx >= 0) {
                val prev = contacts[idx]
                contacts[idx] = contact.copy(id = prev.id, note = contact.note.ifBlank { prev.note })
            } else {
                contacts += contact
            }
        }

        val uuid = "11111111-2222-3333-4444-555555555555"
        upsert(
            ServerContact(
                id = scopedContactId("server-a", uuid),
                serverId = "server-a",
                playerName = "Steve",
                note = "from-A"
            )
        )
        upsert(
            ServerContact(
                id = scopedContactId("server-b", uuid),
                serverId = "server-b",
                playerName = "Steve",
                note = "from-B"
            )
        )

        assertEquals(2, contacts.size)
        assertTrue(contacts.any { it.serverId == "server-a" && it.note == "from-A" })
        assertTrue(contacts.any { it.serverId == "server-b" && it.note == "from-B" })
    }
}
