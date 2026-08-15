package com.bilicraft.handheld.ui.common

/** UI 硬编码收口：日志上限、布局比例、缓存等。 */
object UiConstants {
    const val MAX_UI_LOG_DEFAULT = 500
    const val MAX_UI_LOG_MIN = 100
    const val MAX_UI_LOG_MAX = 5000
    val MAX_UI_LOG_PRESETS: List<Int> = listOf(200, 500, 1000, 2000)
    const val BUBBLE_WIDTH_FRACTION = 0.78f
    const val PLUGIN_PANEL_WIDTH_FRACTION = 0.82f
    const val PLUGIN_PANEL_MAX_WIDTH_DP = 420
    const val GROUP_MEMBERS_STRIP_LIMIT = 24
    const val AVATAR_CACHE_MAX = 80
    const val CDK_ACTIVE_WINDOW_REFRESH_MS = 60_000L
    const val WIDE_LAYOUT_MIN_WIDTH_DP = 600
    const val MIN_TOUCH_TARGET_DP = 48
    const val NAV_TRANSITION_MS = 220
    const val CHANNEL_PING_TTL_MS = 60_000L
    const val CHANNEL_PING_TTL_LOW_POWER_MS = 5 * 60_000L

    fun clampMaxUiLog(limit: Int): Int = limit.coerceIn(MAX_UI_LOG_MIN, MAX_UI_LOG_MAX)
}
