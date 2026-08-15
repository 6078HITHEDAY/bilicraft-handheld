@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bilicraft.handheld.appicon.AppIcon
import com.bilicraft.handheld.cdk.CdkEntry
import com.bilicraft.handheld.cdk.CdkState
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.chat.PlayerAvatar
import com.bilicraft.handheld.ui.common.SectionTitle
import com.bilicraft.handheld.ui.common.SettingAction
import com.bilicraft.handheld.ui.common.SettingActions
import com.bilicraft.handheld.ui.plugins.PluginCenterScreen
import com.bilicraft.handheld.update.DownloadSource
import com.bilicraft.handheld.update.ReleaseInfo
import com.bilicraft.handheld.update.UpdateState
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsScreen(
    vm: MainViewModel,
    onOpenPluginCenter: () -> Unit,
    onOpenAppIconPicker: () -> Unit
) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val accountList by vm.accounts.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val cdkState by vm.cdkState.collectAsStateWithLifecycle()
    val ignoringBatteryOptimizations by vm.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    var removingAccountUuid by remember { mutableStateOf<String?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showBackgroundLimitGuide by remember { mutableStateOf(false) }
    var showFontScale by remember { mutableStateOf(false) }
    var showLogLimit by remember { mutableStateOf(false) }
    var showNotifBehavior by remember { mutableStateOf(false) }
    var showQuickReplies by remember { mutableStateOf(false) }
    val currentAppIcon by vm.currentAppIcon.collectAsStateWithLifecycle()
    val officialMarket by vm.officialMarket.collectAsStateWithLifecycle()
    val pluginUpdateCount = officialMarket.entries.count { it.updateAvailable }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshCdkActiveWindow()
            delay(com.bilicraft.handheld.ui.common.UiConstants.CDK_ACTIVE_WINDOW_REFRESH_MS)
        }
    }

    // 系统电池优化授权没有回调，只能在回到本页时重新查询。
    LifecycleResumeEffect(Unit) {
        vm.refreshBatteryOptimizationState()
        onPauseOrDispose { }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { AccountSection(vm, accountList) { removingAccountUuid = it } }

        item { SectionTitle("插件") }
        item {
            ListItem(
                headlineContent = { Text("插件管理") },
                supportingContent = { Text("官方插件市场与外部插件包") },
                leadingContent = {
                    if (pluginUpdateCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(if (pluginUpdateCount > 9) "9+" else pluginUpdateCount.toString())
                                }
                            }
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                        }
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null)
                    }
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenPluginCenter)
            )
        }

        item {
            ChatSection(
                vm = vm,
                preferences = preferences,
                onFontScale = { showFontScale = true },
                onLogLimit = { showLogLimit = true },
                onQuickReplies = { showQuickReplies = true }
            )
        }
        item { BackgroundSection(vm, preferences, ignoringBatteryOptimizations, onGuide = { showBackgroundLimitGuide = true }) }
        item {
            AppearanceSection(
                preferences = preferences,
                currentAppIcon = currentAppIcon,
                onTheme = { showThemePicker = true },
                onIcon = onOpenAppIconPicker,
                onNotif = { showNotifBehavior = true }
            )
        }
        item { CdkSection(cdkState, vm) }
        item { AboutSection(vm, preferences, onSource = { showSourcePicker = true }) }
    }

    removingAccountUuid?.let { uuid ->
        val name = accountList.firstOrNull { it.uuid == uuid }?.username ?: "该账号"
        AlertDialog(
            onDismissRequest = { removingAccountUuid = null },
            title = { Text("移除账号") },
            text = { Text("确定移除 $name？本地登录凭证将被删除。") },
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

    if (showThemePicker) {
        ThemeModeDialog(
            current = preferences.themeMode,
            onSelect = vm::setThemeMode,
            onDismiss = { showThemePicker = false }
        )
    }

    if (showFontScale) {
        ChatFontScaleDialog(
            current = preferences.chatFontScale,
            onSelect = vm::setChatFontScale,
            onDismiss = { showFontScale = false }
        )
    }

    if (showLogLimit) {
        MaxUiLogDialog(
            current = preferences.maxUiLog,
            onSelect = vm::setMaxUiLog,
            onDismiss = { showLogLimit = false }
        )
    }

    if (showQuickReplies) {
        var draft by remember(preferences.quickReplies) {
            mutableStateOf(preferences.quickReplies.joinToString("\n"))
        }
        AlertDialog(
            onDismissRequest = { showQuickReplies = false },
            title = { Text("快捷回复") },
            text = {
                Column {
                    Text(
                        "每行一条短语，输入栏「短语」可快速发送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val replies = draft.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(12)
                    vm.setQuickReplies(replies)
                    showQuickReplies = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickReplies = false }) { Text("取消") }
            }
        )
    }

    if (showNotifBehavior) {
        NotificationTapDialog(
            current = preferences.notificationTapBehavior,
            onSelect = vm::setNotificationTapBehavior,
            onDismiss = { showNotifBehavior = false }
        )
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
            onOpenAppDetails = vm::openAppDetailsSettings,
            onDismiss = { showBackgroundLimitGuide = false }
        )
    }

    UpdateDialog(
        state = updateState,
        onDownload = vm::downloadUpdate,
        onInstall = vm::installUpdate,
        onDismiss = vm::dismissUpdate
    )
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
internal fun CdkModuleCard(
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
internal fun CdkEntryItem(
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
@Composable
internal fun AccountRow(
    account: com.bilicraft.handheld.auth.AccountSummary,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = { Text(account.username, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(if (account.isActive) "当前使用中" else "点击切换到该账号") },
        leadingContent = {
            PlayerAvatar(name = account.username, online = account.isActive, size = 40.dp)
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

/**
 * 更新下载源选择：单选列表，选中即回传并落盘。
 * 顺序即 DownloadSource 声明顺序（镜像在前、直连在后），呼应「优先国内镜像」。
 */
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

/**
 * 启动图标选择页（全屏子页面）。
 * 卡片列表，每张卡预览图标 + 名称 + 描述，选中项右侧打勾。
 * 图标切换的平台副作用（桌面图标短暂消失、可能被移出最近任务）在页顶提示，避免用户误以为出错。
 */
@Composable
internal fun AppIconPickerScreen(
    icons: List<AppIcon>,
    current: AppIcon,
    onSelect: (AppIcon) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        com.bilicraft.handheld.ui.common.DetailHeader(title = "替换启动图标", onBack = onBack)

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


private const val CDK_COPY_FEEDBACK_MS = 1_600L
