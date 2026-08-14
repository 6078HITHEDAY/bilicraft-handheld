package com.bilicraft.handheld.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.appicon.AppIcon
import com.bilicraft.handheld.auth.AccountSummary
import com.bilicraft.handheld.cdk.CdkEntry
import com.bilicraft.handheld.cdk.CdkState
import com.bilicraft.handheld.config.ThemeMode
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.SectionTitle
import com.bilicraft.handheld.ui.common.SettingAction
import com.bilicraft.handheld.ui.common.SettingActions
import com.bilicraft.handheld.update.DownloadSource
import com.bilicraft.handheld.update.ReleaseInfo
import com.bilicraft.handheld.update.UpdateState
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsScreen(
    vm: MainViewModel,
    extraSections: @Composable () -> Unit = {}
) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val accountList by vm.accounts.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val cdkState by vm.cdkState.collectAsStateWithLifecycle()
    val ignoringBatteryOptimizations by vm.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    var removingAccountUuid by remember { mutableStateOf<String?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showChatRules by remember { mutableStateOf(false) }
    var showBackgroundLimitGuide by remember { mutableStateOf(false) }
    val currentAppIcon by vm.currentAppIcon.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshCdkActiveWindow()
            delay(CDK_ACTIVE_WINDOW_REFRESH_MS)
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refreshBatteryOptimizationState()
        onPauseOrDispose { }
    }

    if (showChatRules) {
        ChatRulesScreen(vm = vm, onBack = { showChatRules = false })
        return
    }

    if (showIconPicker) {
        AppIconPickerScreen(
            icons = vm.appIcons,
            current = currentAppIcon,
            onSelect = vm::selectAppIcon,
            onBack = { showIconPicker = false }
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("账号管理") }
        val accounts = accountList
        if (accounts.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("当前账号") },
                    supportingContent = { Text(vm.currentAccountName) },
                    leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                )
            }
        } else {
            items(accounts, key = { it.uuid }) { account ->
                AccountRow(
                    account = account,
                    onSwitch = { vm.switchAccount(account.uuid) },
                    onRemove = { removingAccountUuid = account.uuid }
                )
                HorizontalDivider()
            }
        }
        item {
            SettingActions(
                actions = listOf(
                    SettingAction("添加账号", Icons.Default.Add, vm::addAccount),
                    SettingAction("刷新 Token", Icons.Default.Refresh, vm::refreshToken),
                    SettingAction("退出全部", Icons.Default.Delete, vm::logout)
                )
            )
        }

        item { extraSections() }

        item { SectionTitle("聊天") }
        item {
            ListItem(
                headlineContent = { Text("聊天规则") },
                supportingContent = { Text("私聊/频道正则、/msg 模板，可贴真实聊天试跑。") },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showChatRules = true }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("私聊通知") },
                supportingContent = { Text("后台收到私聊时弹出 MessagingStyle 通知，可直接回复。") },
                trailingContent = {
                    Switch(
                        checked = preferences.notifyWhispers,
                        onCheckedChange = vm::setNotifyWhispers
                    )
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("被 @ 时通知") },
                supportingContent = { Text("公屏或频道里出现自己的名字时提醒。") },
                trailingContent = {
                    Switch(
                        checked = preferences.notifyMentions,
                        onCheckedChange = vm::setNotifyMentions
                    )
                }
            )
        }

        item { SectionTitle("聊天显示") }
        item {
            ListItem(
                headlineContent = { Text("自动滚动到最新聊天") },
                supportingContent = { Text("点击消息可复制；关闭后，新消息不会打断你查看历史聊天。") },
                trailingContent = {
                    Switch(
                        checked = preferences.chatAutoScroll,
                        onCheckedChange = vm::setChatAutoScroll
                    )
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("命令补全") },
                supportingContent = { Text("输入 / 命令时向服务器请求候选项。关闭后不发补全请求。") },
                trailingContent = {
                    Switch(
                        checked = preferences.commandCompletionEnabled,
                        onCheckedChange = vm::setCommandCompletionEnabled
                    )
                }
            )
        }

        item { SectionTitle("后台保活") }
        item {
            ListItem(
                headlineContent = { Text("忽略电池优化") },
                supportingContent = {
                    Text(
                        if (ignoringBatteryOptimizations) {
                            "已加入白名单，系统省电策略不会清理后台连接。"
                        } else {
                            "未加入白名单。息屏一段时间后，系统可能直接掐断后台连接。点击前往授权。"
                        }
                    )
                },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingContent = {
                    if (ignoringBatteryOptimizations) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable(enabled = !ignoringBatteryOptimizations) {
                    vm.requestIgnoreBatteryOptimizations()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("厂商后台限制") },
                supportingContent = {
                    Text("小米、华为、荣耀、OPPO、vivo 等系统还有自启动与后台锁定开关，未放开时仍可能被清理。查看各系统的设置位置。")
                },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showBackgroundLimitGuide = true }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("低能耗挂后台") },
                supportingContent = {
                    Text("退到后台或息屏时释放 CPU 唤醒锁并降低通知刷新频率，明显省电；代价是掉线概率略增，掉线后仍会自动重连。")
                },
                trailingContent = {
                    Switch(
                        checked = preferences.backgroundLowPowerEnabled,
                        onCheckedChange = vm::setBackgroundLowPowerEnabled
                    )
                }
            )
        }

        item { SectionTitle("外观") }
        item {
            ListItem(
                headlineContent = { Text("明暗主题") },
                supportingContent = { Text(preferences.themeMode.displayName) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showThemePicker = true }
            )
        }

        item { SectionTitle("个性化") }
        item {
            ListItem(
                headlineContent = { Text("替换启动图标") },
                supportingContent = { Text("当前：${currentAppIcon.displayName}") },
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showIconPicker = true }
            )
        }

        item { SectionTitle("CDK") }
        item {
            CdkModuleCard(
                state = cdkState,
                onRefresh = vm::refreshCdk
            )
        }

        item { SectionTitle("版本数据") }
        item {
            SettingActions(
                actions = listOf(
                    SettingAction("刷新版本列表", Icons.Default.Refresh) { vm.refreshVersions() },
                    SettingAction("清除缓存", Icons.Default.Delete, vm::clearVersionCache)
                )
            )
        }

        item { SectionTitle("关于") }
        item {
            ListItem(
                headlineContent = { Text("掌上碧玺") },
                supportingContent = { Text("版本 ${vm.versionNameText}\n包名 ${vm.packageNameText}") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("下载线路") },
                supportingContent = { Text(preferences.downloadSource.displayName) },
                leadingContent = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showSourcePicker = true }
            )
        }
        item {
            SettingActions(
                actions = listOf(
                    SettingAction("检查更新", Icons.Default.Refresh, vm::checkForUpdate)
                )
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showSourcePicker) {
        DownloadSourceDialog(
            current = preferences.downloadSource,
            onSelect = {
                vm.setDownloadSource(it)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showBackgroundLimitGuide) {
        BackgroundLimitGuideDialog(
            onOpenAppDetails = {
                vm.openAppDetailsSettings()
                showBackgroundLimitGuide = false
            },
            onDismiss = { showBackgroundLimitGuide = false }
        )
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("明暗主题") },
            text = {
                Column {
                    ThemeMode.entries.forEach { themeMode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setThemeMode(themeMode)
                                    showThemePicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.themeMode == themeMode,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(themeMode.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemePicker = false }) { Text("取消") }
            }
        )
    }

    UpdateDialog(
        state = updateState,
        onDownload = vm::downloadUpdate,
        onInstall = vm::installUpdate,
        onDismiss = vm::dismissUpdate
    )

    removingAccountUuid?.let { uuid ->
        val target = accountList.firstOrNull { it.uuid == uuid }
        AlertDialog(
            onDismissRequest = { removingAccountUuid = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("移除账号") },
            text = { Text("确定移除账号「${target?.username ?: uuid}」？该账号的登录凭据将从本机抹除，需要时可重新登录。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAccount(uuid)
                    removingAccountUuid = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { removingAccountUuid = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BackgroundLimitGuideDialog(
    onOpenAppDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text("厂商后台限制") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "「忽略电池优化」只解除 Android 原生限制。国内厂商系统另有一套后台管控，需要在系统设置里手动放开：",
                    style = MaterialTheme.typography.bodyMedium
                )
                BACKGROUND_LIMIT_STEPS.forEach { (vendor, path) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(vendor, style = MaterialTheme.typography.titleSmall)
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "另外建议在最近任务列表里给本应用加锁，避免一键清理时被关掉。各系统菜单名称可能随版本变化，找不到时按关键词搜索即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenAppDetails) { Text("打开应用详情") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

@Composable
private fun CdkModuleCard(
    state: CdkState,
    onRefresh: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CDK 兑换码", fontWeight = FontWeight.SemiBold)
                    Text(
                        "限时福利，记得及时兑换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (state.entries.isEmpty() && !state.loading) {
                Text(
                    "当前没有可兑换的 CDK，稍后再来看看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    CdkEntryItem(
                        entry = entry,
                        onCopy = { clipboardManager.setText(AnnotatedString(entry.code)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CdkEntryItem(
    entry: CdkEntry,
    onCopy: () -> Unit
) {
    var copied by remember(entry.id, entry.code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(CDK_COPY_FEEDBACK_MS)
        copied = false
    }

    ListItem(
        headlineContent = { Text(entry.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                listOfNotNull(
                    entry.description.takeIf { it.isNotBlank() },
                    "CDK：${entry.code}",
                    cdkWindowText(entry)
                ).joinToString("\n")
            )
        },
        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
        trailingContent = {
            TextButton(onClick = {
                onCopy()
                copied = true
            }) {
                Text(if (copied) "已复制" else "复制")
            }
        }
    )
}

private fun cdkWindowText(entry: CdkEntry): String? = when {
    !entry.startsAt.isNullOrBlank() && !entry.endsAt.isNullOrBlank() -> "显示时间：${entry.startsAt} ~ ${entry.endsAt}"
    !entry.startsAt.isNullOrBlank() -> "开始显示：${entry.startsAt}"
    !entry.endsAt.isNullOrBlank() -> "显示截止：${entry.endsAt}"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: AccountSummary,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = { Text(account.username, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(if (account.isActive) "当前使用中" else "点击切换到该账号") },
        leadingContent = {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = if (account.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row {
                if (!account.isActive) {
                    IconButton(onClick = onSwitch) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "切换到该账号")
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "移除该账号")
                }
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = { if (!account.isActive) onSwitch() },
            onLongClick = onRemove
        )
    )
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    onDownload: (ReleaseInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateState.Idle) return

    when (state) {
        is UpdateState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新") },
            text = { Text("正在查询最新版本…") },
            confirmButton = {}
        )

        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("已是最新版本") },
            text = { Text("当前已是最新版本，无需更新。") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } }
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("发现新版本 ${state.info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("更新内容", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Text(
                        "更新前请确认：应用内更新要求新旧包签名一致，否则系统会拒绝覆盖安装。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onDownload(state.info) }) { Text("下载") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载 ${state.info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(state.progress * 100).toInt()}%")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("下载中…")
                    }
                }
            },
            confirmButton = {}
        )

        is UpdateState.Downloaded -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("下载完成") },
            text = { Text("${state.info.versionName} 已下载完成，点击安装继续。若系统提示，请允许安装未知来源应用。") },
            confirmButton = { TextButton(onClick = { onInstall(state.apkFile) }) { Text("安装") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("更新失败") },
            text = { Text(state.reason) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } }
        )

        is UpdateState.Idle -> Unit
    }
}

@Composable
private fun DownloadSourceDialog(
    current: DownloadSource,
    onSelect: (DownloadSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
        title = { Text("下载线路") },
        text = {
            Column {
                DownloadSource.entries.forEach { source ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(source) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = source == current, onClick = { onSelect(source) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(source.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                source.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun AppIconPickerScreen(
    icons: List<AppIcon>,
    current: AppIcon,
    onSelect: (AppIcon) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "替换启动图标",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "切换后，桌面图标可能短暂消失再重现，部分系统会把应用从最近任务中清除，这是系统机制，属正常现象。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(icons, key = { it.id }) { icon ->
                AppIconOption(
                    icon = icon,
                    selected = icon.id == current.id,
                    onClick = { onSelect(icon) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppIconOption(
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(icon.previewResId),
                contentDescription = icon.displayName,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(icon.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    icon.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选用",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private const val CDK_ACTIVE_WINDOW_REFRESH_MS = 60_000L
private const val CDK_COPY_FEEDBACK_MS = 1_600L

private val BACKGROUND_LIMIT_STEPS = listOf(
    "小米 / Redmi（HyperOS、MIUI）" to
        "设置 → 应用设置 → 应用管理 → 掌上碧玺 → 开启「自启动」，并把「省电策略」改为「无限制」",
    "华为（HarmonyOS、EMUI）" to
        "设置 → 应用和服务 → 应用启动管理 → 掌上碧玺 → 关闭自动管理，允许「后台活动」",
    "荣耀（MagicOS）" to
        "设置 → 应用和服务 → 应用启动管理 → 掌上碧玺 → 关闭自动管理，允许「自启动」与「关联启动」",
    "OPPO / 一加 / realme（ColorOS）" to
        "设置 → 电池 → 应用耗电管理 → 掌上碧玺 → 允许「后台运行」与「自启动」",
    "vivo / iQOO（OriginOS、Funtouch）" to
        "设置 → 电池 → 后台耗电管理 → 掌上碧玺 → 允许「后台高耗电」，并在应用管理里开启自启动",
    "三星（One UI）" to
        "设置 → 电池 → 后台使用限制 → 把掌上碧玺加入「从不休眠的应用」"
)
