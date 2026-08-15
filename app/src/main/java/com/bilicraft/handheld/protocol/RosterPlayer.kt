package com.bilicraft.handheld.protocol

/**
 * Tab 列表里的一名玩家（Player Info 归一化结果）。
 * online=false 表示已从列表移除但仍保留在 UI 通讯录里展示为离线。
 */
data class RosterPlayer(
    val uuid: String,
    val name: String,
    val online: Boolean,
    val latencyMs: Int = -1
)

sealed interface RosterEvent {
    data class Upsert(val players: List<RosterPlayer>) : RosterEvent
    data class Remove(val uuids: List<String>) : RosterEvent
    data object Clear : RosterEvent
}
