package com.bilicraft.handheld.ui.vm

import com.bilicraft.handheld.config.ServerContact
import com.bilicraft.handheld.config.UiConfigRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 联系人域：手动添加、备注、以及 DM/名单触发的自动建联。
 */
class ContactsStateHolder(
    private val scope: CoroutineScope,
    private val uiConfigRepo: UiConfigRepository
) {
    private val ensureMutex = Mutex()

    /** 同服同名则更新备注，避免重复条目。 */
    fun create(serverId: String, playerName: String, note: String = "") {
        val name = playerName.trim()
        if (name.isBlank()) return
        scope.launch {
            ensureMutex.withLock {
                val existing = uiConfigRepo.contacts.value.firstOrNull {
                    it.serverId == serverId && it.playerName.equals(name, ignoreCase = true)
                }
                if (existing != null) {
                    val mergedNote = note.trim().ifBlank { existing.note }
                    uiConfigRepo.upsertContact(existing.copy(note = mergedNote))
                } else {
                    uiConfigRepo.upsertContact(uiConfigRepo.newContact(serverId, name, note))
                }
            }
        }
    }

    fun save(contact: ServerContact) {
        if (contact.playerName.isBlank()) return
        scope.launch {
            uiConfigRepo.upsertContact(
                contact.copy(playerName = contact.playerName.trim(), note = contact.note.trim())
            )
        }
    }

    fun delete(id: String) {
        scope.launch { uiConfigRepo.deleteContact(id) }
    }

    /**
     * 按服+玩家名查找或创建联系人。
     * 名单点进私聊时可用真实 uuid 作为 id，便于后续匹配在线状态。
     */
    suspend fun ensureContact(
        serverId: String,
        playerName: String,
        uuid: String? = null
    ): ServerContact? = ensureMutex.withLock {
        val name = playerName.trim()
        if (name.isEmpty()) return null
        val existing = uiConfigRepo.contacts.value.firstOrNull {
            it.serverId == serverId && it.playerName.equals(name, ignoreCase = true)
        }
        if (existing != null) {
            return existing
        }
        val created = ServerContact(
            id = if (!uuid.isNullOrBlank()) {
                com.bilicraft.handheld.config.scopedContactId(serverId, uuid)
            } else {
                UUID.randomUUID().toString()
            },
            serverId = serverId,
            playerName = name,
            note = ""
        )
        val sameIdOtherServer = uiConfigRepo.contacts.value.firstOrNull {
            it.id == created.id && it.serverId != serverId
        }
        uiConfigRepo.upsertContact(created)
        return created
    }

    fun ensureContactAsync(serverId: String, playerName: String, uuid: String? = null) {
        scope.launch { ensureContact(serverId, playerName, uuid) }
    }

    fun openAutoContact(
        serverId: String,
        playerName: String,
        uuid: String? = null,
        onReady: (ServerContact) -> Unit
    ) {
        scope.launch {
            ensureContact(serverId, playerName, uuid)?.let(onReady)
        }
    }
}
