package com.bilicraft.handheld.ui.chat

import com.bilicraft.handheld.config.ChatParseConfig
import com.bilicraft.handheld.config.ChatParsePreset
import com.bilicraft.handheld.protocol.ChatDecorations

/**
 * 频道公屏解析结果：装饰字段 + 剥离后的正文。
 * message 单独返回，不塞进 [ChatDecorations]（装饰给头像/称号展示，正文给气泡）。
 */
internal data class ChatParseResult(
    val decorations: ChatDecorations,
    val message: String?
)

/**
 * 碧玺正文预设：`आ[建筑大师] │ Quartz_Crystal: 消息`（阵营可空）。
 * 子服名（HY 等）通常在协议 sender，由 [enrichServerFromProtocolSender] 补上；
 * Custom 规则也可用 `(?<server>…)` 直接从正文捕获。
 */
internal val BILICRAFT_CHAT_PATTERN =
    """^(?<faction>[^\s\[]+)?\s*\[(?<title>[^\]]+)\]\s*│\s*(?<player>[A-Za-z0-9_]{2,16})\s*:\s*(?<message>.*)$"""

private val KNOWN_GROUPS = setOf("player", "message", "title", "faction", "server")

internal fun ChatParseConfig.resolvePattern(): Regex? {
    val source = when (preset) {
        ChatParsePreset.Vanilla -> return null
        ChatParsePreset.Bilicraft -> BILICRAFT_CHAT_PATTERN
        ChatParsePreset.Custom -> pattern.trim().takeIf { it.isNotEmpty() } ?: return null
    }
    return runCatching { Regex(source, setOf(RegexOption.DOT_MATCHES_ALL)) }.getOrNull()
}

/**
 * 按频道配置从公屏纯文本抽出装饰字段。
 * Vanilla / 编译失败 / 未匹配 → null。
 */
internal fun parseChatDecorations(text: String, config: ChatParseConfig): ChatParseResult? {
    val regex = config.resolvePattern() ?: return null
    val m = regex.find(text.trim()) ?: return null
    val names = m.groups as? MatchNamedGroupCollection ?: return null

    fun named(key: String): String? =
        runCatching { names[key]?.value?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

    val player = named("player")
    val message = named("message")
    val title = named("title")
    val faction = named("faction")
    val server = named("server")

    val extras = linkedMapOf<String, String>()
    for (key in EXTRA_GROUP_HINTS) {
        if (key in KNOWN_GROUPS) continue
        named(key)?.let { extras[key] = it }
    }

    if (player == null && title == null && faction == null && server == null &&
        message == null && extras.isEmpty()
    ) {
        return null
    }

    return ChatParseResult(
        decorations = ChatDecorations(
            player = player,
            title = title,
            faction = faction,
            server = server,
            extras = extras
        ),
        message = message
    )
}

/**
 * 多服网络：协议 sender 常是子服名（如 HY），真玩家名在正文规则里。
 * 若规则未写出 `(?<server>)`，且 sender ≠ player，则把 sender 记为 server。
 */
internal fun ChatDecorations.enrichServerFromProtocolSender(protocolSender: String?): ChatDecorations {
    if (!server.isNullOrBlank()) return this
    val tag = protocolSender?.trim()?.takeIf { it.isNotEmpty() } ?: return this
    val playerName = player?.trim().orEmpty()
    if (playerName.isNotEmpty() && tag.equals(playerName, ignoreCase = true)) return this
    // 已有玩家名时：sender 几乎一定是子服标签，而非玩家
    if (playerName.isNotEmpty()) return copy(server = tag)
    return this
}

/** 预留扩展 named group；Custom 规则可选用。 */
private val EXTRA_GROUP_HINTS = listOf(
    "channel", "rank", "prefix", "suffix", "role", "tag", "world"
)
