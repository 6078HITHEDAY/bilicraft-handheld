package com.bilicraft.handheld.ui.vm

import com.bilicraft.handheld.config.NotificationTapBehavior
import com.bilicraft.handheld.config.PluginPanelLayout
import com.bilicraft.handheld.config.ThemeMode
import com.bilicraft.handheld.config.UiConfigRepository
import com.bilicraft.handheld.ui.common.UiConstants
import com.bilicraft.handheld.update.DownloadSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 设置/偏好域：纯偏好写入委托，UI 文案仍由 MainViewModel 门面决定。
 */
class SettingsStateHolder(
    private val scope: CoroutineScope,
    private val uiConfigRepo: UiConfigRepository
) {
    fun setChatFontScale(scale: Float) {
        scope.launch { uiConfigRepo.setChatFontScale(scale) }
    }

    fun setMaxUiLog(limit: Int) {
        scope.launch {
            uiConfigRepo.setMaxUiLog(UiConstants.clampMaxUiLog(limit))
        }
    }

    fun setNotificationTapBehavior(behavior: NotificationTapBehavior) {
        scope.launch { uiConfigRepo.setNotificationTapBehavior(behavior) }
    }

    fun setContactsGroupByServer(enabled: Boolean) {
        scope.launch { uiConfigRepo.setContactsGroupByServer(enabled) }
    }

    fun setQuickReplies(replies: List<String>) {
        scope.launch { uiConfigRepo.setQuickReplies(replies) }
    }

    fun setChatAutoScroll(enabled: Boolean) {
        scope.launch { uiConfigRepo.setChatAutoScroll(enabled) }
    }

    fun setCommandCompletionEnabled(enabled: Boolean) {
        scope.launch { uiConfigRepo.setCommandCompletionEnabled(enabled) }
    }

    fun setBackgroundLowPowerEnabled(enabled: Boolean) {
        scope.launch { uiConfigRepo.setBackgroundLowPowerEnabled(enabled) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        scope.launch { uiConfigRepo.setThemeMode(themeMode) }
    }

    fun setPluginPanelLayout(layout: PluginPanelLayout) {
        scope.launch { uiConfigRepo.setPluginPanelLayout(layout) }
    }

    fun setPrimaryContactServerId(serverId: String?) {
        scope.launch { uiConfigRepo.setPrimaryContactServerId(serverId) }
    }

    fun setDownloadSource(source: DownloadSource) {
        scope.launch { uiConfigRepo.setDownloadSource(source) }
    }
}
