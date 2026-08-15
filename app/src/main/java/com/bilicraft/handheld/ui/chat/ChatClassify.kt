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

/** 无 &lt;name&gt; 前缀的进出服/死亡/成就等系统句。 */
private fun isBareSystemNotice(plain: String): Boolean {
    val lower = plain.lowercase()
    return SYSTEM_PATTERNS.any { it.containsMatchIn(lower) }
}

private val ANGLE_SENDER = Regex("""^<([^>\n]{1,32})>\s?(.*)$""", RegexOption.DOT_MATCHES_ALL)

private val SYSTEM_PATTERNS = listOf(
    // 进出服
    Regex("""\bjoined the game\b"""),
    Regex("""\bleft the game\b"""),
    Regex("""加入了游戏"""),
    Regex("""离开了游戏"""),
    // 成就/进度
    Regex("""\bhas (?:made|completed|reached) the (?:advancement|challenge|goal)\b"""),
    Regex("""(?:获得了成就|完成了进度|达成进度|达成成就)"""),
    // 死亡（英文常见变体）
    Regex("""\bwas slain\b"""),
    Regex("""\bwas killed by\b"""),
    Regex("""\bburned to death\b"""),
    Regex("""\bwent up in flames\b"""),
    Regex("""\bsuffocated\b"""),
    Regex("""\bwas blown up\b"""),
    Regex("""\bwas shot by\b"""),
    Regex("""\bwas squashed\b"""),
    Regex("""\bwas pricked\b"""),
    Regex("""\bwas struck by lightning\b"""),
    Regex("""\bwas pummeled\b"""),
    Regex("""\bwas knocked off\b"""),
    Regex("""\bwas doomed to fall\b"""),
    Regex("""\bfell from\b"""),
    Regex("""\bfell out of the world\b"""),
    Regex("""\bwithered away\b"""),
    Regex("""\bwas smacked\b"""),
    Regex("""\btried to swim in lava\b"""),
    Regex("""\bwas impaled\b"""),
    Regex("""\bwas stung\b"""),
    Regex("""\bdrowned\b"""),
    // 死亡（中文常见变体）
    Regex("""被(?:[^，。！？\s]{1,12})?杀死了"""),
    Regex("""被击杀"""),
    Regex("""被射杀"""),
    Regex("""被炸死"""),
    Regex("""被烧死"""),
    Regex("""被淹死"""),
    Regex("""被闪电击中"""),
    Regex("""溺亡"""),
    Regex("""掉出了世界"""),
    Regex("""摔死"""),
    Regex("""窒息而死"""),
)
