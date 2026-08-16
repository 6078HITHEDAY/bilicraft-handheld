package com.bilicraft.handheld.ui.chat

import com.bilicraft.handheld.protocol.RosterPlayer

/**
 * 气泡头像查询键：皮肤 CDN 认正版用户名 / UUID，不认聊天昵称或权限前缀。
 */
internal data class AvatarIdentity(
    val name: String,
    val uuid: String? = null
)

private val MC_USERNAME = Regex("""^[A-Za-z0-9_]{2,16}$""")
private val MC_USERNAME_TOKEN = Regex("""[A-Za-z0-9_]{2,16}""")

internal fun isLikelyMcUsername(value: String): Boolean = MC_USERNAME.matches(value.trim())

internal fun normalizePlayerUuid(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }?.replace("-", "")?.lowercase()

/** 从「[VIP] Steve」这类显示名里抽出更像正版 ID 的 token（偏右侧）。 */
internal fun extractMcUsername(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (isLikelyMcUsername(trimmed)) return trimmed
    return MC_USERNAME_TOKEN.findAll(trimmed).map { it.value }.lastOrNull()
}

/**
 * 判断聊天显示名 / UUID 是否指向当前登录账号（处理前缀昵称）。
 */
internal fun labelMatchesSelf(
    label: String?,
    selfName: String,
    selfUuid: String? = null,
    senderUuid: String? = null
): Boolean {
    val selfId = normalizePlayerUuid(selfUuid)
    val eventId = normalizePlayerUuid(senderUuid)
    if (selfId != null && eventId != null && selfId == eventId) return true

    val self = selfName.trim()
    if (self.isEmpty() || self == "未登录") return false
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return false
    if (raw.equals(self, ignoreCase = true)) return true
    if (extractMcUsername(raw)?.equals(self, ignoreCase = true) == true) return true
    if (raw.endsWith(self, ignoreCase = true) && raw.length >= self.length) {
        val before = raw.dropLast(self.length)
        if (before.isEmpty()) return true
        val edge = before.last()
        if (edge.isWhitespace() || edge in "[]|()<>·•:：_-/│") return true
    }
    return false
}

/**
 * @param label 气泡上的发送者显示名
 * @param senderUuid Player Chat 包 UUID（优先）
 * @param roster 当前服在线名单
 * @param preferredName 已知正版名（自己账号、私聊对方、或 decorations.player）
 * @param preferredUuid 已知 UUID
 * @param bodyHint 气泡正文兜底扫名字（无频道解析时）
 */
internal fun resolveAvatarIdentity(
    label: String?,
    senderUuid: String? = null,
    roster: List<RosterPlayer> = emptyList(),
    preferredName: String? = null,
    preferredUuid: String? = null,
    bodyHint: String? = null
): AvatarIdentity {
    val preferred = preferredName?.trim()?.takeIf { it.isNotEmpty() && it != "未登录" }
    val uuid = normalizePlayerUuid(senderUuid) ?: normalizePlayerUuid(preferredUuid)
    val dashedUuid = (senderUuid ?: preferredUuid)?.trim()?.takeIf { it.isNotEmpty() }

    if (uuid != null) {
        val fromRoster = roster.firstOrNull { normalizePlayerUuid(it.uuid) == uuid }
        if (fromRoster != null && fromRoster.name.isNotBlank()) {
            return AvatarIdentity(fromRoster.name, fromRoster.uuid)
        }
        if (preferred != null) {
            return AvatarIdentity(preferred, dashedUuid ?: preferredUuid)
        }
    }

    if (preferred != null) {
        val hit = roster.firstOrNull { it.name.equals(preferred, ignoreCase = true) }
        return AvatarIdentity(preferred, hit?.uuid ?: dashedUuid ?: preferredUuid)
    }

    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return AvatarIdentity("", dashedUuid)

    roster.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let {
        return AvatarIdentity(it.name, it.uuid)
    }

    val extracted = extractMcUsername(raw)
    if (extracted != null) {
        roster.firstOrNull { it.name.equals(extracted, ignoreCase = true) }?.let {
            return AvatarIdentity(it.name, it.uuid)
        }
        roster.firstOrNull {
            it.name.isNotBlank() && raw.endsWith(it.name, ignoreCase = true)
        }?.let {
            return AvatarIdentity(it.name, it.uuid)
        }
        return AvatarIdentity(extracted, dashedUuid)
    }

    if (!bodyHint.isNullOrBlank()) {
        roster.firstOrNull {
            it.name.isNotBlank() && bodyHint.contains(it.name, ignoreCase = true)
        }?.let {
            return AvatarIdentity(it.name, it.uuid)
        }
    }

    roster.firstOrNull {
        it.name.isNotBlank() && raw.contains(it.name, ignoreCase = true)
    }?.let {
        return AvatarIdentity(it.name, it.uuid)
    }

    return AvatarIdentity(raw, dashedUuid)
}
