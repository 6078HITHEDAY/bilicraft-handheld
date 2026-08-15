package com.bilicraft.handheld.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.chat.filterPublicChat
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.config.ServerContact
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.protocol.RosterPlayer
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.StatusDot
import com.bilicraft.handheld.ui.common.statusColor
import com.bilicraft.handheld.ui.common.statusText
import com.bilicraft.handheld.ui.plugins.PluginEntrypointEdgeToggle
import com.bilicraft.handheld.ui.plugins.PluginEntrypointSidePanel
import com.bilicraft.handheld.ui.server.ServerEditorDialog

private data class GroupMember(
    val name: String,
    val uuid: String?,
    val online: Boolean,
    val latencyMs: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelChatScreen(
    vm: MainViewModel,
    server: ServerConfig,
    onBack: () -> Unit
) {
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val commandSuggestions by vm.commandSuggestions.collectAsStateWithLifecycle()
    val pluginEntrypoints by vm.externalPluginEntrypoints.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    var pluginMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showMembers by rememberSaveable { mutableStateOf(false) }
    var dmContact by remember { mutableStateOf<ServerContact?>(null) }

    val conn = runtime.connectionStates[server.id] ?: ConnectionState.Disconnected
    val selfName = vm.currentAccountName
    val log = remember(runtime.chatLogs[server.id], selfName) {
        filterPublicChat(runtime.chatLogs[server.id].orEmpty(), selfName)
    }
    val roster = runtime.rosters[server.id].orEmpty()
    val members = remember(roster, contacts, server.id) {
        buildGroupMembers(roster, contacts.filter { it.serverId == server.id })
    }
    val isActiveServer = runtime.activeServerId == server.id
    val connected = isActiveServer && conn is ConnectionState.Connected
    val connecting = isActiveServer && conn !is ConnectionState.Disconnected && conn !is ConnectionState.Failed

    BackHandler(onBack = {
        when {
            dmContact != null -> dmContact = null
            else -> onBack()
        }
    })

    dmContact?.let { contact ->
        DmChatScreen(
            vm = vm,
            server = server,
            contact = contact,
            onBack = { dmContact = null }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChannelTopBar(
                title = server.name,
                subtitle = if (members.isEmpty()) statusText(conn)
                else "${statusText(conn)} · ${members.count { it.online }} 在线",
                conn = conn,
                connected = connected,
                onBack = onBack,
                onRespawn = vm::respawn,
                onEdit = { editing = true },
                onMembers = { showMembers = true }
            )
            ChannelInfoBar(
                server = server,
                conn = conn,
                connected = connected,
                connecting = connecting,
                isActiveServer = isActiveServer,
                onConnect = { vm.connect(server) },
                onStop = vm::stopConnection
            )
            if (members.isNotEmpty()) {
                GroupMembersStrip(
                    members = members,
                    onOpen = { member ->
                        vm.openAutoContact(server.id, member.name, member.uuid) { dmContact = it }
                    }
                )
            }
            BubbleChatLog(
                log = log,
                selfName = selfName,
                autoScroll = preferences.chatAutoScroll,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            ChatComposer(
                connected = connected,
                commandCompletionEnabled = preferences.commandCompletionEnabled,
                commandSuggestions = commandSuggestions,
                onSend = { vm.sendChat(server.id, it) },
                onRequestCommandSuggestions = { vm.requestCommandSuggestions(server.id, it) }
            )
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

    if (showMembers) {
        ModalBottomSheet(
            onDismissRequest = { showMembers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Text(
                "群成员 · ${members.count { it.online }} 在线 / ${members.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn {
                items(members, key = { "${it.uuid}|${it.name}" }) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMembers = false
                                vm.openAutoContact(server.id, member.name, member.uuid) { dmContact = it }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(name = member.name, online = member.online, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (member.online) {
                                    if (member.latencyMs >= 0) "在线 · ${member.latencyMs} ms" else "在线"
                                } else "离线",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (member.online) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editing) {
        ServerEditorDialog(
            title = "编辑频道",
            initial = server,
            versions = versions,
            selectedVersion = server.toMcVersion(),
            forceSigning = server.signingRequired,
            onSelectVersion = {},
            onForceSigning = {},
            onDismiss = { editing = false },
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
                editing = false
            }
        )
    }
}

@Composable
private fun GroupMembersStrip(
    members: List<GroupMember>,
    onOpen: (GroupMember) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(members.take(24), key = { "${it.uuid}|${it.name}" }) { member ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clickable { onOpen(member) }
            ) {
                PlayerAvatar(name = member.name, online = member.online, size = 40.dp)
                Text(
                    member.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (member.online) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildGroupMembers(roster: List<RosterPlayer>, contacts: List<ServerContact>): List<GroupMember> {
    val byName = LinkedHashMap<String, GroupMember>()
    roster.forEach { p ->
        byName[p.name.lowercase()] = GroupMember(
            name = p.name,
            uuid = p.uuid,
            online = p.online,
            latencyMs = p.latencyMs
        )
    }
    contacts.forEach { c ->
        val key = c.playerName.lowercase()
        if (!byName.containsKey(key)) {
            byName[key] = GroupMember(
                name = c.playerName,
                uuid = c.id,
                online = false,
                latencyMs = -1
            )
        }
    }
    return byName.values.sortedWith(
        compareByDescending<GroupMember> { it.online }
            .thenBy { it.name.lowercase() }
    )
}

@Composable
private fun ChannelTopBar(
    title: String,
    subtitle: String,
    conn: ConnectionState,
    connected: Boolean,
    onBack: () -> Unit,
    onRespawn: () -> Unit,
    onEdit: () -> Unit,
    onMembers: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        StatusDot(conn)
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onMembers)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(conn),
                maxLines = 1
            )
        }
        IconButton(onClick = onMembers) {
            Icon(Icons.Default.Group, contentDescription = "成员")
        }
        IconButton(onClick = onRespawn, enabled = connected) {
            Icon(Icons.Default.Favorite, contentDescription = "复活")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑")
        }
    }
}

@Composable
private fun ChannelInfoBar(
    server: ServerConfig,
    conn: ConnectionState,
    connected: Boolean,
    connecting: Boolean,
    isActiveServer: Boolean,
    onConnect: () -> Unit,
    onStop: () -> Unit
) {
    val active = connected || connecting
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${server.host}:${server.port} · ${server.versionId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (server.signingRequired) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("强制签名", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (active) {
                FilledTonalButton(
                    onClick = onStop,
                    enabled = isActiveServer,
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (connecting && !connected) "连接中" else "断开")
                }
            } else {
                Button(onClick = onConnect, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("连接")
                }
            }
        }
    }
}
