package com.bilicraft.handheld.ui.chat

import com.bilicraft.handheld.chat.isOutgoingDm
import com.bilicraft.handheld.chat.stripDmWrapper
import com.bilicraft.handheld.protocol.ChatEvent

internal enum class BubbleKind { Self, Other, System }

/**
 * 把协议层 ChatEvent 分成自己 / 他人 / 系统，并抽出气泡正文（去掉 &lt;名字&gt; 前缀）。
 * System Chat 里常见的玩家发言也会被识别成他人消息。
 */
internal data class ClassifiedChat(
    val kind: BubbleKind,
    val senderLabel: String?,
    val bodyPlain: String,
    val event: ChatEvent
)

internal fun classifyChat(ev: ChatEvent, selfName: String): ClassifiedChat {
    val self = selfName.trim()
    val peer = ev.dmPeer?.trim()?.takeIf { it.isNotEmpty() }
    if (peer != null) {
        val outgoing = isOutgoingDm(ev, self)
        val body = stripDmWrapper(ev.plainText, peer, self).ifBlank { ev.plainText }
        return ClassifiedChat(
            kind = if (outgoing) BubbleKind.Self else BubbleKind.Other,
            senderLabel = if (outgoing) self.takeIf { it.isNotEmpty() && it != "未登录" } ?: peer else peer,
            bodyPlain = body,
            event = ev
        )
    }

    val plain = ev.plainText
    if (isBareSystemNotice(plain)) {
        return ClassifiedChat(BubbleKind.System, null, plain, ev)
    }

    val explicitSender = ev.sender?.trim()?.takeIf { it.isNotEmpty() }
    val angle = ANGLE_SENDER.find(plain)
    val inferredSender = explicitSender ?: angle?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    val kind = when {
        inferredSender != null && self.isNotEmpty() && inferredSender.equals(self, ignoreCase = true) ->
            BubbleKind.Self
        inferredSender != null -> BubbleKind.Other
        else -> BubbleKind.System
    }

    val body = when (kind) {
        BubbleKind.System -> plain
        else -> {
            val stripped = angle?.groupValues?.getOrNull(2)
            when {
                !stripped.isNullOrBlank() -> stripped
                explicitSender != null && plain.startsWith("<") ->
                    plain.substringAfter("> ", missingDelimiterValue = plain)
                else -> plain
            }
        }
    }

    return ClassifiedChat(
        kind = kind,
        senderLabel = inferredSender,
        bodyPlain = body.trim(),
        event = ev
    )
}

/** 无 &lt;name&gt; 前缀的进出服/死亡等系统句。 */
private fun isBareSystemNotice(plain: String): Boolean {
    val lower = plain.lowercase()
    return SYSTEM_PATTERNS.any { it.containsMatchIn(lower) }
}

private val ANGLE_SENDER = Regex("""^<([^>\n]{1,32})>\s?(.*)$""", RegexOption.DOT_MATCHES_ALL)

private val SYSTEM_PATTERNS = listOf(
    Regex("""\bjoined the game\b"""),
    Regex("""\bleft the game\b"""),
    Regex("""加入了游戏"""),
    Regex("""离开了游戏"""),
    Regex("""\bhas made the advancement\b"""),
    Regex("""\bwas slain\b"""),
    Regex("""\bfell from\b"""),
    Regex("""\bdrowned\b"""),
)
