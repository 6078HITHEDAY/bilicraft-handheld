package com.bilicraft.handheld.chat

import com.bilicraft.handheld.protocol.ChatEvent

/**
 * 私聊识别与分流：公屏隐藏私聊，私聊页只显示对应 peer 的消息。
 *
 * Bilicraft 入站常见格式：`messages you: 正文`（不含对方名字，需用 sender / 最近私聊对象兜底）。
 */

/** @param fallbackPeer 最近一次私聊对象（用于 `messages you:` 这类不带名字的回显） */
internal fun resolveDmPeer(
    plain: String,
    sender: String?,
    selfName: String,
    fallbackPeer: String? = null
): String? {
    val text = plain.trim()
    if (text.isEmpty()) return null
    val self = selfName.trim().takeIf { it.isNotEmpty() && it != "未登录" }
    val hint = fallbackPeer?.trim()?.takeIf { it.isNotEmpty() }

    // Bilicraft：messages you: ... / messages 你: ...
    if (MESSAGES_YOU.containsMatchIn(text)) {
        val fromSender = sender?.trim()?.takeIf { it.isNotEmpty() && (self == null || !it.equals(self, true)) }
        return fromSender ?: hint
    }
    // messages PlayerName: ...（非 you）
    MESSAGES_NAMED.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
        if (!name.equals("you", true) && name != "你") return name
    }

    // 客户端发出的命令回显：/msg|/tell|/w Name ...
    CMD_MSG.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    EN_OUT.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    EN_IN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    ZH_OUT.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    ZH_IN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    BRACKET.find(text)?.let { m ->
        val a = m.groupValues[1].trim()
        val b = m.groupValues[2].trim()
        val selfish = setOf("you", "me", "我", "你")
        return when {
            self != null && a.equals(self, true) -> b
            self != null && b.equals(self, true) -> a
            a.lowercase() in selfish -> b
            b.lowercase() in selfish -> a
            else -> null
        }
    }

    ARROW.find(text)?.let { m ->
        val a = m.groupValues[1].trim()
        val b = m.groupValues[2].trim()
        val selfish = setOf("you", "me", "我", "你")
        return when {
            self != null && a.equals(self, true) -> b
            self != null && b.equals(self, true) -> a
            a.lowercase() in selfish -> b
            b.lowercase() in selfish -> a
            else -> null
        }
    }

    if (WHISPER_MARKERS.any { text.contains(it, ignoreCase = true) }) {
        ZH_TO.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (sender != null && self != null && !sender.equals(self, true)) return sender.trim()
        if (hint != null) return hint
    }

    return null
}

/** 是否为可识别的私聊文案（即使还解析不出 peer，公屏也应先藏起来）。 */
internal fun looksLikeDmPlain(plain: String): Boolean {
    val text = plain.trim()
    if (text.isEmpty()) return false
    if (MESSAGES_YOU.containsMatchIn(text)) return true
    if (MESSAGES_NAMED.containsMatchIn(text)) return true
    if (CMD_MSG.containsMatchIn(text)) return true
    if (EN_OUT.containsMatchIn(text) || EN_IN.containsMatchIn(text)) return true
    if (ZH_OUT.containsMatchIn(text) || ZH_IN.containsMatchIn(text)) return true
    if (BRACKET.containsMatchIn(text) || ARROW.containsMatchIn(text)) return true
    return WHISPER_MARKERS.any { text.contains(it, ignoreCase = true) }
}

internal fun filterPublicChat(
    log: List<ChatEvent>,
    selfName: String,
    fallbackPeer: String? = null
): List<ChatEvent> =
    log.filter { ev ->
        if (ev.dmPeer != null) return@filter false
        if (looksLikeDmPlain(ev.plainText)) return@filter false
        resolveDmPeer(ev.plainText, ev.sender, selfName, fallbackPeer) == null
    }

internal fun filterDirectMessages(
    log: List<ChatEvent>,
    playerName: String,
    selfName: String,
    fallbackPeer: String? = null
): List<ChatEvent> {
    val target = playerName.trim()
    if (target.isEmpty()) return emptyList()
    return log.mapNotNull { ev ->
        val peer = ev.dmPeer?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveDmPeer(ev.plainText, ev.sender, selfName, fallbackPeer ?: target)
            ?: return@mapNotNull null
        if (!peer.equals(target, ignoreCase = true)) return@mapNotNull null
        if (ev.dmPeer == null) {
            ev.copy(
                dmPeer = peer,
                sender = ev.sender ?: peer
            )
        } else {
            ev
        }
    }
}

internal fun stripDmWrapper(plain: String, peer: String, selfName: String): String {
    val text = plain.trim()
    MESSAGES_YOU.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    MESSAGES_NAMED.find(text)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    CMD_MSG.find(text)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    listOf(EN_OUT, EN_IN, ZH_OUT, ZH_IN).forEach { re ->
        re.find(text)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    BRACKET.find(text)?.let { m ->
        val rest = text.substring(m.range.last + 1).trim().removePrefix(":").trim()
        if (rest.isNotEmpty()) return rest
    }
    ARROW.find(text)?.let { m ->
        val rest = text.substring(m.range.last + 1).trim().removePrefix(":").trim()
        if (rest.isNotEmpty()) return rest
    }
    return text
}

internal fun isOutgoingDm(ev: ChatEvent, selfName: String): Boolean {
    val self = selfName.trim()
    val t = ev.plainText.trim()
    // 入站：messages you: … 一定是对方发来的
    if (MESSAGES_YOU.containsMatchIn(t)) return false
    if (self.isEmpty() || self == "未登录") return false
    if (ev.sender?.equals(self, ignoreCase = true) == true) return true
    return EN_OUT.containsMatchIn(t) || ZH_OUT.containsMatchIn(t) ||
        CMD_MSG.containsMatchIn(t) ||
        (BRACKET.find(t)?.groupValues?.getOrNull(1)?.equals(self, true) == true) ||
        (ARROW.find(t)?.groupValues?.getOrNull(1)?.equals(self, true) == true)
}

/** `messages you: 正文` / `messages 你: 正文` */
private val MESSAGES_YOU = Regex(
    """^messages\s+(?:you|你)\s*[:：]\s*(.*)$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

/** `messages PlayerName: 正文` */
private val MESSAGES_NAMED = Regex(
    """^messages\s+(\S+)\s*[:：]\s*(.*)$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val CMD_MSG = Regex(
    """^/(?:msg|tell|w|whisper)\s+(\S+)\s+(.+)$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val EN_OUT = Regex(
    """^(?:You whisper to|You whisperto)\s+(\S+)\s*:\s*(.*)$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val EN_IN = Regex(
    """^(\S+)\s+whispers(?:\s+to\s+you)?\s*:\s*(.*)$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val ZH_OUT = Regex(
    """^你(?:悄悄地|悄声)?对\s*(\S+)\s*说\s*[:：]\s*(.*)$""",
    RegexOption.DOT_MATCHES_ALL
)
private val ZH_IN = Regex(
    """^(\S+?)\s*(?:悄悄地|悄声)?对你说\s*[:：]\s*(.*)$""",
    RegexOption.DOT_MATCHES_ALL
)
private val ZH_TO = Regex("""对\s*(\S+)\s*说""")
private val BRACKET = Regex("""^\[\s*([^\]\->]+?)\s*(?:->|→)\s*([^\]]+?)\s*]""")
private val ARROW = Regex("""^(\S+)\s*(?:->|→)\s*(\S+)\s*""")

private val WHISPER_MARKERS = listOf(
    "whispers",
    "whisper",
    "悄悄地",
    "悄声",
    "私聊",
    "对你说",
    "告诉你"
)
