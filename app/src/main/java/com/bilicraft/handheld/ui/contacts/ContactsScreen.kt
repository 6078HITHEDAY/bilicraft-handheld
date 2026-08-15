package com.bilicraft.handheld.ui.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.config.ServerContact
import com.bilicraft.handheld.protocol.RosterPlayer
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.chat.DmChatScreen
import com.bilicraft.handheld.ui.chat.PlayerAvatar
import com.bilicraft.handheld.ui.common.EmptyState
import com.bilicraft.handheld.ui.common.ScreenHeader

private data class ContactRow(
    val contact: ServerContact,
    val online: Boolean,
    val latencyMs: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactsScreen(
    vm: MainViewModel,
    onDetailOpen: (Boolean) -> Unit = {}
) {
    val servers by vm.servers.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()

    val primaryServerId = preferences.primaryContactServerId
        ?: servers.firstOrNull()?.id
    val primaryServer = servers.firstOrNull { it.id == primaryServerId }
    val roster = runtime.rosters[primaryServerId].orEmpty()
    val scoped = remember(contacts, primaryServerId, roster) {
        mergeContactRows(
            serverId = primaryServerId.orEmpty(),
            contacts = contacts.filter { it.serverId == primaryServerId },
            roster = roster
        )
    }

    var openContactId by remember { mutableStateOf<String?>(null) }
    var menuContact by remember { mutableStateOf<ServerContact?>(null) }
    var showServerPicker by remember { mutableStateOf(false) }

    val openContact = scoped.firstOrNull { it.contact.id == openContactId }?.contact
        ?: contacts.firstOrNull { it.id == openContactId }
    val dmOpen = openContact != null && servers.any { it.id == openContact.serverId }
    LaunchedEffect(dmOpen) { onDetailOpen(dmOpen) }
    if (openContact != null) {
        val server = servers.firstOrNull { it.id == openContact.serverId }
        if (server != null) {
            DmChatScreen(
                vm = vm,
                server = server,
                contact = openContact,
                onBack = { openContactId = null }
            )
            return
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "联系人")

        if (servers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有服务器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "连接频道后会自动识别在线玩家，无需手动添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showServerPicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("主服务器", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    primaryServer?.name ?: "选择服务器",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
        HorizontalDivider()

        val onlineCount = scoped.count { it.online }
        Text(
            text = if (scoped.isEmpty()) "连接后自动同步成员"
            else "在线 $onlineCount · 共 ${scoped.size} 人",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (scoped.isEmpty()) {
            EmptyState(
                title = "暂无联系人",
                message = "进入「${primaryServer?.name ?: "频道"}」并连接后，会从服务器名单自动识别玩家。"
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(scoped, key = { it.contact.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    val sid = primaryServerId ?: return@combinedClickable
                                    vm.openAutoContact(sid, row.contact.playerName, row.contact.id) {
                                        openContactId = it.id
                                    }
                                },
                                onLongClick = { menuContact = row.contact }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(name = row.contact.playerName, online = row.online, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.contact.playerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    row.online && row.latencyMs >= 0 -> "在线 · ${row.latencyMs} ms"
                                    row.online -> "在线"
                                    else -> "离线"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.online) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showServerPicker) {
        AlertDialog(
            onDismissRequest = { showServerPicker = false },
            title = { Text("选择主服务器") },
            text = {
                Column {
                    servers.forEach { server ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setPrimaryContactServerId(server.id)
                                    showServerPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = server.id == primaryServerId,
                                onClick = {
                                    vm.setPrimaryContactServerId(server.id)
                                    showServerPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(server.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerPicker = false }) { Text("关闭") }
            }
        )
    }

    menuContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { menuContact = null },
            title = { Text(contact.playerName) },
            text = { Text("从本机通讯录移除（不影响服务器名单；下次上线会再次同步）。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteContact(contact.id); menuContact = null }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { menuContact = null }) { Text("取消") }
            }
        )
    }
}

private fun mergeContactRows(
    serverId: String,
    contacts: List<ServerContact>,
    roster: List<RosterPlayer>
): List<ContactRow> {
    val byName = roster.associateBy { it.name.lowercase() }
    val byUuid = roster.associateBy { it.uuid }
    val rows = contacts.map { contact ->
        val match = byUuid[contact.id] ?: byName[contact.playerName.lowercase()]
        ContactRow(
            contact = contact,
            online = match?.online == true,
            latencyMs = match?.latencyMs ?: -1
        )
    }.toMutableList()
    // 名单里有、本地尚未落盘的瞬间：也展示出来（ensureContact 异步补齐）
    roster.filter { it.online }.forEach { player ->
        val exists = rows.any {
            it.contact.id == player.uuid ||
                it.contact.playerName.equals(player.name, ignoreCase = true)
        }
        if (!exists && serverId.isNotEmpty()) {
            rows.add(
                ContactRow(
                    contact = ServerContact(
                        id = player.uuid,
                        serverId = serverId,
                        playerName = player.name
                    ),
                    online = true,
                    latencyMs = player.latencyMs
                )
            )
        }
    }
    return rows.sortedWith(
        compareByDescending<ContactRow> { it.online }
            .thenBy { it.contact.playerName.lowercase() }
    )
}
