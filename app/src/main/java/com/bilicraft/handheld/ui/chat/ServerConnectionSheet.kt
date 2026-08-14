package com.bilicraft.handheld.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.ServerFaviconImage
import com.bilicraft.handheld.ui.server.ServerEditorDialog
import com.bilicraft.handheld.ui.server.ServerInfoBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerConnectionSheet(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onExitApp: () -> Unit
) {
    val servers by vm.servers.collectAsStateWithLifecycle()
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val chatServerId by vm.chatServerId.collectAsStateWithLifecycle()
    val icons by vm.serverIcons.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedVersion by vm.selectedVersion.collectAsStateWithLifecycle()
    val forceSigning by vm.forceSigning.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ServerConfig?>(null) }
    var creating by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("服务器 / 连接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refreshServerIcons(force = true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新图标")
                }
                IconButton(onClick = { vm.respawn() }) {
                    Icon(Icons.Default.Favorite, contentDescription = "复活")
                }
                IconButton(onClick = onExitApp) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "退出应用")
                }
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增服务器")
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servers, key = { it.id }) { server ->
                    val conn = runtime.connectionStates[server.id] ?: ConnectionState.Disconnected
                    val icon = icons[server.id]
                    val snapshotLine = if (icon != null && icon.onlinePlayers >= 0) {
                        "${icon.onlinePlayers}/${icon.maxPlayers} 在线"
                    } else null
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServerFaviconImage(server.name, icon?.file, conn, size = 36.dp)
                            Spacer(Modifier.width(8.dp))
                            if (snapshotLine != null) {
                                Text(snapshotLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { vm.selectChatServer(server.id) }) {
                                Text(if (chatServerId == server.id) "当前会话" else "查看聊天")
                            }
                        }
                        ServerInfoBar(
                            server = server,
                            conn = conn,
                            connected = conn is ConnectionState.Connected,
                            connecting = conn is ConnectionState.Connecting || conn is ConnectionState.LoggingIn || conn is ConnectionState.Reconnecting,
                            isActiveServer = runtime.activeServerId == server.id,
                            onConnect = { vm.connect(server) },
                            onStop = vm::stopConnection,
                            onEdit = { editing = server }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (creating) {
        ServerEditorDialog(
            title = "新增服务器",
            initial = null,
            versions = versions,
            selectedVersion = selectedVersion,
            forceSigning = forceSigning,
            onSelectVersion = vm::selectVersion,
            onForceSigning = vm::setForceSigning,
            onDismiss = { creating = false },
            onSave = { name, host, port, version, signing ->
                vm.createServer(name, host, port, version, signing)
                creating = false
            }
        )
    }
    editing?.let { server ->
        ServerEditorDialog(
            title = "编辑服务器",
            initial = server,
            versions = versions,
            selectedVersion = server.toMcVersion(),
            forceSigning = server.signingRequired,
            onSelectVersion = {},
            onForceSigning = {},
            onDismiss = { editing = null },
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
                editing = null
            }
        )
    }
}
