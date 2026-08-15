package com.bilicraft.handheld.ui.chat

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.ChannelPingUi
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.EmptyState
import com.bilicraft.handheld.ui.common.ScreenHeader
import com.bilicraft.handheld.ui.common.StatusDot
import com.bilicraft.handheld.ui.common.statusText
import com.bilicraft.handheld.ui.server.ServerEditorDialog
import kotlin.system.exitProcess

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatChannelsScreen(
    vm: MainViewModel,
    onOpenChannel: (String) -> Unit
) {
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedVersion by vm.selectedVersion.collectAsStateWithLifecycle()
    val forceSigning by vm.forceSigning.collectAsStateWithLifecycle()
    val pings by vm.channelPings.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingServer by remember { mutableStateOf<ServerConfig?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var menuServer by remember { mutableStateOf<ServerConfig?>(null) }

    LaunchedEffect(servers) {
        servers.forEach { vm.refreshChannelPing(it) }
    }

    val orderedServers = remember(servers, preferences.pinnedChannelIds, preferences.archivedChannelIds) {
        val pinned = preferences.pinnedChannelIds.toSet()
        val archived = preferences.archivedChannelIds.toSet()
        val active = servers.filter { it.id !in archived }
        val archivedList = servers.filter { it.id in archived }
        active.sortedByDescending { it.id in pinned } + archivedList
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "聊天",
            actions = {
                IconButton(onClick = { showExitConfirm = true }) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "退出应用")
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增频道")
                }
            }
        )
        Text(
            text = vm.currentAccountName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        if (servers.isEmpty()) {
            EmptyState(
                title = "还没有频道",
                message = "把 Minecraft 服务器加为频道后，即可像 Telegram 一样打开聊天。",
                actionText = "新增频道",
                onAction = { showCreateDialog = true }
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(orderedServers, key = { it.id }) { server ->
                    val conn = runtime.connectionStates[server.id] ?: ConnectionState.Disconnected
                    val last = runtime.chatLogs[server.id]?.lastOrNull()
                    val unread = vm.unreadCount(server.id)
                    val pinned = server.id in preferences.pinnedChannelIds
                    ChannelRow(
                        server = server,
                        conn = conn,
                        preview = last?.plainText.orEmpty(),
                        ping = pings[server.id],
                        unread = unread,
                        pinned = pinned,
                        archived = server.id in preferences.archivedChannelIds,
                        onClick = {
                            vm.markChannelRead(server.id)
                            onOpenChannel(server.id)
                        },
                        onLongClick = { menuServer = server }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreateDialog) {
        ServerEditorDialog(
            title = "新增频道",
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
        com.bilicraft.handheld.ui.common.ConfirmDialog(
            title = "退出应用",
            message = "将断开连接并完全退出，不会保留后台。",
            confirmText = "退出",
            onConfirm = {
                showExitConfirm = false
                vm.prepareFullExit()
                (context as? Activity)?.finishAndRemoveTask()
                exitProcess(0)
            },
            onDismiss = { showExitConfirm = false }
        )
    }

    editingServer?.let { server ->
        ServerEditorDialog(
            title = "编辑频道",
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
        val pinned = server.id in preferences.pinnedChannelIds
        val archived = server.id in preferences.archivedChannelIds
        AlertDialog(
            onDismissRequest = { menuServer = null },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            title = { Text(server.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        vm.togglePinnedChannel(server.id)
                        menuServer = null
                    }) { Text(if (pinned) "取消置顶" else "置顶") }
                    TextButton(onClick = {
                        vm.toggleArchivedChannel(server.id)
                        menuServer = null
                    }) { Text(if (archived) "取消归档" else "归档") }
                    TextButton(onClick = {
                        vm.clearChannelChat(server.id)
                        menuServer = null
                    }) { Text("清空聊天") }
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "server",
                                "${server.host}:${server.port}"
                            )
                        )
                        menuServer = null
                    }) { Text("复制地址") }
                    TextButton(onClick = { editingServer = server; menuServer = null }) { Text("编辑") }
                    TextButton(onClick = { vm.deleteServer(server.id); menuServer = null }) { Text("删除") }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuServer = null }) { Text("关闭") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    server: ServerConfig,
    conn: ConnectionState,
    preview: String,
    ping: ChannelPingUi?,
    unread: Int,
    pinned: Boolean,
    archived: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelAvatar(server.name, ping)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) {
                    Text("📌 ", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    server.name + if (archived) "（已归档）" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                StatusDot(conn)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview.ifBlank {
                    when {
                        ping != null && ping.onlinePlayers >= 0 && ping.maxPlayers >= 0 ->
                            "${statusText(conn)} · ${ping.onlinePlayers}/${ping.maxPlayers}"
                        else -> statusText(conn)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 服务器列表左侧图标：优先 favicon，否则用首字母色块（不是玩家皮肤头）。 */
@Composable
private fun ChannelAvatar(name: String, ping: ChannelPingUi?) {
    val bytes = ping?.faviconPng
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
        )
    } else {
        val bg = remember(name) {
            val palette = listOf(
                Color(0xFF5390D9), Color(0xFF2A9D8F), Color(0xFFE76F51),
                Color(0xFF9B5DE5), Color(0xFFF4A261), Color(0xFF457B9D)
            )
            palette[kotlin.math.abs(name.hashCode()) % palette.size]
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}
