package com.bilicraft.handheld.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.appicon.AppIcon
import com.bilicraft.handheld.auth.AccountSummary
import com.bilicraft.handheld.cdk.CdkState
import com.bilicraft.handheld.config.NotificationTapBehavior
import com.bilicraft.handheld.config.UiPreferences
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.chat.PlayerAvatar
import com.bilicraft.handheld.ui.common.PreferenceRow
import com.bilicraft.handheld.ui.common.SectionTitle
import com.bilicraft.handheld.ui.common.SettingAction
import com.bilicraft.handheld.ui.common.SettingActions
import com.bilicraft.handheld.ui.common.SettingSection

@Composable
internal fun AccountSection(
    vm: MainViewModel,
    accounts: List<AccountSummary>,
    onRequestRemove: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("账号管理")
        if (accounts.isEmpty()) {
            ListItem(
                headlineContent = { Text("当前账号") },
                supportingContent = { Text(vm.currentAccountName) },
                leadingContent = {
                    val name = vm.currentAccountName
                    if (name.isNotBlank() && name != "未登录") {
                        PlayerAvatar(name = name, online = true, size = 40.dp)
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                    }
                }
            )
        } else {
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    onSwitch = { vm.switchAccount(account.uuid) },
                    onRemove = { onRequestRemove(account.uuid) }
                )
                HorizontalDivider()
            }
        }
        SettingActions(
            actions = listOf(
                SettingAction("添加账号", Icons.Default.Add, vm::addAccount),
                SettingAction("刷新 Token", Icons.Default.Refresh, vm::refreshToken),
                SettingAction("退出全部", Icons.Default.Delete, vm::logout)
            )
        )
    }
}

@Composable
internal fun ChatSection(
    vm: MainViewModel,
    preferences: UiPreferences,
    onFontScale: () -> Unit,
    onLogLimit: () -> Unit
) {
    SettingSection(title = "聊天显示") {
        PreferenceRow(
            title = "自动滚动到最新聊天",
            subtitle = "关闭后，新消息不会打断你查看历史。",
            checked = preferences.chatAutoScroll,
            onCheckedChange = vm::setChatAutoScroll
        )
        PreferenceRow(
            title = "命令补全",
            subtitle = "输入 / 命令时向服务器请求候选项。",
            checked = preferences.commandCompletionEnabled,
            onCheckedChange = vm::setCommandCompletionEnabled
        )
        PreferenceRow(
            title = "聊天字号",
            subtitle = "当前 ${(preferences.chatFontScale * 100).toInt()}%",
            onClick = onFontScale,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
        PreferenceRow(
            title = "聊天记录上限",
            subtitle = "每频道保留 ${preferences.maxUiLog} 条",
            onClick = onLogLimit,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
    }
}

@Composable
internal fun BackgroundSection(
    vm: MainViewModel,
    preferences: UiPreferences,
    ignoringBatteryOptimizations: Boolean,
    onGuide: () -> Unit
) {
    SettingSection(title = "后台保活") {
        PreferenceRow(
            title = "低能耗挂后台",
            subtitle = "退到后台或息屏后降低连接功率。",
            checked = preferences.backgroundLowPowerEnabled,
            onCheckedChange = vm::setBackgroundLowPowerEnabled
        )
        PreferenceRow(
            title = "忽略电池优化",
            subtitle = if (ignoringBatteryOptimizations) "已授权" else "未授权，点击申请",
            onClick = vm::requestIgnoreBatteryOptimizations,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
        PreferenceRow(
            title = "厂商后台限制指引",
            subtitle = "小米 / 华为 / OPPO 等设置路径",
            onClick = onGuide,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
    }
}

@Composable
internal fun AppearanceSection(
    preferences: UiPreferences,
    currentAppIcon: AppIcon,
    onTheme: () -> Unit,
    onIcon: () -> Unit,
    onNotif: () -> Unit
) {
    SettingSection(title = "外观") {
        PreferenceRow(
            title = "主题",
            subtitle = preferences.themeMode.displayName,
            icon = Icons.Default.Settings,
            onClick = onTheme,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
        PreferenceRow(
            title = "启动图标",
            subtitle = currentAppIcon.displayName,
            icon = Icons.Default.Image,
            onClick = onIcon,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
        PreferenceRow(
            title = "通知点击",
            subtitle = when (preferences.notificationTapBehavior) {
                NotificationTapBehavior.OpenHome -> "打开主页"
                NotificationTapBehavior.OpenChannel -> "打开对应频道"
            },
            onClick = onNotif,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
    }
}

@Composable
internal fun CdkSection(cdkState: CdkState, vm: MainViewModel) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("CDK")
        CdkModuleCard(state = cdkState, onRefresh = { vm.refreshCdk() })
    }
}

@Composable
internal fun AboutSection(
    vm: MainViewModel,
    preferences: UiPreferences,
    onSource: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        SettingSection(title = "版本数据") {
            PreferenceRow(
                title = "刷新版本列表",
                subtitle = "重新拉取 Minecraft 版本元数据",
                onClick = { vm.refreshVersions(silent = false) },
                trailing = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
            PreferenceRow(
                title = "清除版本缓存",
                onClick = vm::clearVersionCache,
                trailing = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
        SettingSection(title = "关于") {
            PreferenceRow(
                title = "检查更新",
                subtitle = "v${vm.versionNameText}",
                icon = Icons.Default.Info,
                onClick = { vm.checkForUpdate() },
                trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            )
            PreferenceRow(
                title = "下载线路",
                subtitle = preferences.downloadSource.displayName,
                onClick = onSource,
                trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            )
            PreferenceRow(
                title = "包名",
                subtitle = vm.packageNameText
            )
            PreferenceRow(
                title = "插件目录",
                subtitle = vm.pluginDropDirText
            )
        }
    }
}
