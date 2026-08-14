package com.bilicraft.handheld.ui.server

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.CommandSuggestion
import com.bilicraft.handheld.protocol.CommandSuggestionState
import com.bilicraft.handheld.protocol.CommandSuggestions
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.VersionDropdown
import com.bilicraft.handheld.ui.common.EmptyState
import com.bilicraft.handheld.ui.common.StatusDot
import com.bilicraft.handheld.ui.common.statusColor
import com.bilicraft.handheld.ui.common.statusText
import com.bilicraft.handheld.ui.common.toAnnotated
import com.bilicraft.handheld.ui.plugins.PluginEntrypointEdgeToggle
import com.bilicraft.handheld.ui.plugins.PluginEntrypointSidePanel
import com.bilicraft.handheld.ui.theme.ChatDefaultTextColor
import com.bilicraft.handheld.ui.theme.ChatSurfaceColor
import com.bilicraft.handheld.version.McVersion
import com.bilicraft.handheld.version.VersionRepository
import kotlin.system.exitProcess
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ServerSessionsScreen(vm: MainViewModel) {
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedVersion by vm.selectedVersion.collectAsStateWithLifecycle()
    val forceSigning by vm.forceSigning.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val commandSuggestions by vm.commandSuggestions.collectAsStateWithLifecycle()
    val pluginEntrypoints by vm.externalPluginEntrypoints.collectAsStateWithLifecycle()
    val serverSessionStateHolder = rememberSaveableStateHolder()

    val context = LocalContext.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var editingServer by remember { mutableStateOf<ServerConfig?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var menuServer by remember { mutableStateOf<ServerConfig?>(null) }
    var pluginMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(servers.size) {
        selectedIndex = selectedIndex.coerceIn(0, (servers.size - 1).coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (servers.isEmpty()) {
                EmptyState(
                    title = "还没有服务器配置",
                    message = "可新增服务器。默认配置来自 UI 配置仓库，用户删除后不会强制恢复。",
                    actionText = "新增服务器",
                    onAction = { showCreateDialog = true }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        servers.forEachIndexed { index, server ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                text = {
                                    Text(
                                        server.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.combinedClickable(
                                            onClick = { selectedIndex = index },
                                            onLongClick = { menuServer = server }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    val currentServerId = servers.getOrNull(selectedIndex)?.id
                    val currentConnected = currentServerId != null &&
                        runtime.connectionStates[currentServerId] is ConnectionState.Connected
                    IconButton(
                        onClick = { vm.respawn() },
                        enabled = currentConnected
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "复活")
                    }
                    IconButton(onClick = { showExitConfirm = true }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "退出应用")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增服务器")
                    }
                }

                val selectedServer = servers.getOrNull(selectedIndex)
                if (selectedServer != null) {
                    val selectedConn = runtime.connectionStates[selectedServer.id] ?: ConnectionState.Disconnected
                    val selectedLog = runtime.chatLogs[selectedServer.id].orEmpty()
                    val isActiveServer = runtime.activeServerId == selectedServer.id
                    serverSessionStateHolder.SaveableStateProvider(selectedServer.id) {
                        ServerSessionPage(
                            server = selectedServer,
                            conn = selectedConn,
                            log = selectedLog,
                            isActiveServer = isActiveServer,
                            chatAutoScroll = preferences.chatAutoScroll,
                            commandCompletionEnabled = preferences.commandCompletionEnabled,
                            commandSuggestions = commandSuggestions,
                            onConnect = { vm.connect(selectedServer) },
                            onStop = vm::stopConnection,
                            onSend = { vm.sendChat(selectedServer.id, it) },
                            onRequestCommandSuggestions = { vm.requestCommandSuggestions(selectedServer.id, it) },
                            onEdit = { editingServer = selectedServer }
                        )
                    }
                }
            }
        }

        PluginEntrypointSidePanel(
            expanded = pluginMenuExpanded,
            entrypoints = pluginEntrypoints,
            layout = preferences.pluginPanelLayout,
            onLayoutChange = vm::setPluginPanelLayout,
            onOpen = { entry ->
                pluginMenuExpanded = false
                vm.openExternalPluginEntrypoint(entry.pluginId, entry.entrypointId)
            },
            onDismiss = { pluginMenuExpanded = false }
        )

        PluginEntrypointEdgeToggle(
            expanded = pluginMenuExpanded,
            onToggle = { pluginMenuExpanded = !pluginMenuExpanded },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }

    if (showCreateDialog) {
        ServerEditorDialog(
            title = "新增服务器",
            initial = null,
            versions = versions,
            selectedVersion = selectedVersion,
            forceSigning = forceSigning,
            onSelectVersion = vm::selectVersion,
            onForceSigning = vm::setForceSigning,
            onDismiss = { showCreateDialog = false },
            onSave = { name, host, port, version, signing ->
                vm.createServer(name, host, port, version, signing)
                showCreateDialog = false
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出应用") },
            text = { Text("将断开连接并完全退出，不会保留后台。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        vm.prepareFullExit()
                        (context as? Activity)?.finishAndRemoveTask()
                        exitProcess(0)
                    }
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    editingServer?.let { server ->
        ServerEditorDialog(
            title = "编辑服务器",
            initial = server,
            versions = versions,
            selectedVersion = server.toMcVersion(),
            forceSigning = server.signingRequired,
            onSelectVersion = {},
            onForceSigning = {},
            onDismiss = { editingServer = null },
            onSave = { name, host, port, version, signing ->
                vm.saveServer(
                    server.copy(
                        name = name.ifBlank { host },
                        host = host.trim(),
                        port = port.takeIf { it in 1..65535 } ?: 25565,
                        versionId = version.id,
                        protocolNumber = version.protocolNumber,
                        signingRequired = signing
                    )
                )
                editingServer = null
            }
        )
    }

    menuServer?.let { server ->
        AlertDialog(
            onDismissRequest = { menuServer = null },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            title = { Text(server.name) },
            text = { Text("编辑或删除该服务器配置。删除只影响 UI 配置文件，不影响连接核心。") },
            confirmButton = {
                TextButton(onClick = { editingServer = server; menuServer = null }) { Text("编辑") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteServer(server.id); menuServer = null }) { Text("删除") }
                    TextButton(onClick = { menuServer = null }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun ServerSessionPage(
    server: ServerConfig,
    conn: ConnectionState,
    log: List<ChatEvent>,
    isActiveServer: Boolean,
    chatAutoScroll: Boolean,
    commandCompletionEnabled: Boolean,
    commandSuggestions: CommandSuggestionState,
    onConnect: () -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onRequestCommandSuggestions: (String) -> Unit,
    onEdit: () -> Unit
) {
    val connected = isActiveServer && conn is ConnectionState.Connected
    val connecting = isActiveServer && conn !is ConnectionState.Disconnected && conn !is ConnectionState.Failed
    var input by remember(server.id) { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(input.text, connected, commandCompletionEnabled) {
        if (!connected || !commandCompletionEnabled || !input.text.startsWith("/")) {
            onRequestCommandSuggestions("")
            return@LaunchedEffect
        }
        delay(COMMAND_COMPLETION_DEBOUNCE_MS)
        onRequestCommandSuggestions(input.text)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        ServerInfoBar(
            server = server,
            conn = conn,
            connected = connected,
            connecting = connecting,
            isActiveServer = isActiveServer,
            onConnect = onConnect,
            onStop = onStop,
            onEdit = onEdit
        )

        Spacer(Modifier.height(12.dp))
        ChatLog(log = log, autoScroll = chatAutoScroll, modifier = Modifier.weight(1f).fillMaxWidth())
        val visibleSuggestions = commandSuggestions.takeIf {
            connected && commandCompletionEnabled && it.requestInput == input.text && it.hasSuggestions
        }
        if (visibleSuggestions != null) {
            Spacer(Modifier.height(8.dp))
            CommandSuggestionBar(
                state = visibleSuggestions,
                onSelect = { suggestion ->
                    val applied = CommandSuggestions.apply(input.text, visibleSuggestions.start, visibleSuggestions.length, suggestion.text)
                    input = TextFieldValue(applied, selection = TextRange(applied.length))
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("发送聊天…") },
                singleLine = true,
                enabled = connected,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSend(input.text); input = TextFieldValue(""); onRequestCommandSuggestions("") },
                enabled = connected && input.text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("发送")
            }
        }
    }
}

/**
 * 服务器信息条（瘦身版）。
 * 旧版是「标题+地址+三枚 chip+两个整宽按钮」竖排，占了约 5 行。
 * 现在压成单卡片一行：状态圆点 + 名称/地址两行小字 + 单个连接/断开切换按钮 + 编辑图标。
 * 连接态用一个按钮切换语义（未连时=连接，连接/连接中=断开），避免两个整宽按钮浪费纵向空间。
 * 版本号与「强制签名」降级为副标题里的小字，需要修改时进编辑弹窗，不再占主视觉。
 */
@Composable
private fun ServerInfoBar(
    server: ServerConfig,
    conn: ConnectionState,
    connected: Boolean,
    connecting: Boolean,
    isActiveServer: Boolean,
    onConnect: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit
) {
    val active = connected || connecting
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusDot(conn)
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        statusText(conn),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(conn),
                        maxLines = 1
                    )
                    Text(
                        "· ${server.host}:${server.port} · ${server.versionId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (server.signingRequired) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "强制签名",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) {
                FilledTonalButton(
                    onClick = onStop,
                    enabled = isActiveServer,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (connecting && !connected) "连接中" else "断开")
                }
            } else {
                Button(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("连接")
                }
            }
        }
    }
}

@Composable
private fun CommandSuggestionBar(
    state: CommandSuggestionState,
    onSelect: (CommandSuggestion) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.suggestions.take(MAX_VISIBLE_COMMAND_SUGGESTIONS)) { suggestion ->
            AssistChip(
                onClick = { onSelect(suggestion) },
                label = {
                    Text(
                        text = suggestion.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun ChatLog(log: List<ChatEvent>, autoScroll: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var followLatestMessage by rememberSaveable { mutableStateOf(true) }
    // 自动滚到底期间 layout 会短暂「不在底部」，忽略这段对 follow 的误写。
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= listState.layoutInfo.totalItemsCount - 2
        }.collect { isNearLatestMessage ->
            if (!autoScrolling) {
                followLatestMessage = isNearLatestMessage
            }
        }
    }
    // 用 size + 末条时间戳：同文案连刷时 lastOrNull() equals 不变，滚动不会触发。
    LaunchedEffect(log.size, log.lastOrNull()?.timestamp, autoScroll) {
        if (autoScroll && followLatestMessage && log.isNotEmpty()) {
            autoScrolling = true
            try {
                listState.scrollToItem(log.lastIndex)
            } finally {
                autoScrolling = false
                followLatestMessage = true
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ChatSurfaceColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (log.isEmpty()) {
            item { Text("聊天记录为空", color = ChatDefaultTextColor, style = MaterialTheme.typography.bodyMedium) }
        }
        items(
            items = log,
            key = { ev -> "${ev.timestamp}|${ev.sender}|${ev.plainText}|${System.identityHashCode(ev)}" }
        ) { ev ->
            Text(
                text = ev.toAnnotated(),
                style = MaterialTheme.typography.bodyMedium,
                color = ChatDefaultTextColor,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboardManager.setText(AnnotatedString(ev.plainText))
                        Toast.makeText(context, "已复制聊天内容", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ServerEditorDialog(
    title: String,
    initial: ServerConfig?,
    versions: VersionRepository.Grouped,
    selectedVersion: McVersion,
    forceSigning: Boolean,
    onSelectVersion: (McVersion) -> Unit,
    onForceSigning: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, McVersion, Boolean) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial) { mutableStateOf((initial?.port ?: 25565).toString()) }
    var version by remember(initial) { mutableStateOf(initial?.toMcVersion() ?: selectedVersion) }
    var signing by remember(initial) { mutableStateOf(initial?.signingRequired ?: forceSigning) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "配置保存到 UI 仓库；真正连接仍调用现有 ConnectionService。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("服务器地址") }, singleLine = true)
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("端口") },
                    singleLine = true
                )
                VersionDropdown(
                    grouped = versions,
                    selected = version,
                    onSelect = {
                        version = it
                        onSelectVersion(it)
                    }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("强制签名聊天")
                        Text("仅切换现有连接参数，不修改签名算法。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = signing,
                        onCheckedChange = {
                            signing = it
                            onForceSigning(it)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, host, port.toIntOrNull() ?: 25565, version, signing) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private const val COMMAND_COMPLETION_DEBOUNCE_MS = 200L
private const val MAX_VISIBLE_COMMAND_SUGGESTIONS = 6
