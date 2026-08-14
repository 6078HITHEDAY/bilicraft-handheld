package com.bilicraft.handheld.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class ChatRuleKind {
    WhisperIn,
    WhisperOut,
    Channel,
    Mute
}

@Serializable
data class ChatRule(
    val id: String,
    val kind: ChatRuleKind,
    val pattern: String,
    val playerGroup: String = "player",
    val bodyGroup: String = "body",
    val channelGroup: String = "channel",
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val note: String = ""
) {
    @Transient
    val compiled: Regex? = runCatching { Regex(pattern) }.getOrNull()
}

@Serializable
data class ChatRuleSet(
    val version: Int = 1,
    val msgCommandTemplate: String = DEFAULT_MSG_TEMPLATE,
    val rules: List<ChatRule> = defaultRules()
) {
    fun formattedMsg(player: String, message: String): String =
        msgCommandTemplate
            .replace("{player}", player)
            .replace("{message}", message)
}

fun ChatRuleSet.mergedWithBuiltins(): ChatRuleSet {
    val existingIds = rules.map { it.id }.toSet()
    val missing = defaultRules().filter { it.id !in existingIds }
    return if (missing.isEmpty()) this else copy(rules = rules + missing)
}

fun defaultRules(): List<ChatRule> = listOf(
    ChatRule(
        id = "vanilla-in-arrow",
        kind = ChatRuleKind.WhisperIn,
        pattern = """^\[(?<player>\w{3,16}) *(?:->|→|>>) *(?:me|我|你)\] *(?<body>.*)$""",
        builtIn = true,
        note = "常见 [A -> me] 私聊入"
    ),
    ChatRule(
        id = "vanilla-in-whisper",
        kind = ChatRuleKind.WhisperIn,
        pattern = """^(?<player>\w{3,16}) *(?:悄悄地?对你说|whispers to you)[:：] *(?<body>.*)$""",
        builtIn = true,
        note = "vanilla /msg 入站中文与英文"
    ),
    ChatRule(
        id = "vanilla-out-arrow",
        kind = ChatRuleKind.WhisperOut,
        pattern = """^\[(?:me|我|你) *(?:->|→|>>) *(?<player>\w{3,16})\] *(?<body>.*)$""",
        builtIn = true,
        note = "常见 [me -> A] 私聊出"
    ),
    ChatRule(
        id = "vanilla-out-whisper",
        kind = ChatRuleKind.WhisperOut,
        pattern = """^你悄悄地?对 *(?<player>\w{3,16}) *说[:：] *(?<body>.*)$""",
        builtIn = true,
        note = "vanilla /msg 出站中文"
    ),
    ChatRule(
        id = "channel-common",
        kind = ChatRuleKind.Channel,
        pattern = """^\[(?<channel>全服|世界|本地|队伍|公会)\] *<?(?<player>\w{3,16})>? *[:：]? *(?<body>.*)$""",
        builtIn = true,
        note = "常见频道前缀"
    )
)

const val DEFAULT_MSG_TEMPLATE = "/msg {player} {message}"
