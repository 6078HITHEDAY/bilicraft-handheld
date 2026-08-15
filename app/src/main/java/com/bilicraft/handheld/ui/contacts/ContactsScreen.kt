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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.bilicraft.handheld.ui.chat.PlayerAvatar
import com.bilicraft.handheld.ui.common.EmptyState
import com.bilicraft.handheld.ui.common.ScreenHeader
import com.bilicraft.handheld.ui.common.UiConstants

private data class ContactRow(
    val contact: ServerContact,
    val online: Boolean,
    val latencyMs: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactsScreen(
    vm: MainViewModel,
    onOpenDm: (serverId: String, contactId: String) -> Unit
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
    val grouped = remember(contacts, servers, runtime.rosters, preferences.contactsGroupByServer) {
        if (!preferences.contactsGroupByServer) emptyMap()
        else servers.associateWith { server ->
            mergeContactRows(
                serverId = server.id,
                contacts = contacts.filter { it.serverId == server.id },
                roster = runtime.rosters[server.id].orEmpty()
            )
        }.filterValues { it.isNotEmpty() }
    }

    var menuContact by remember { mutableStateOf<ServerContact?>(null) }
    var noteContact by remember { mutableStateOf<ServerContact?>(null) }
    var showServerPicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "联系人",
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加联系人")
                }
            }
        )

        if (servers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有服务器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "连接频道后会自动识别在线玩家，也可手动添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiConstants.MIN_TOUCH_TARGET_DP.dp)
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
            Icon(Icons.Default.ChevronRight, contentDescription = "选择主服务器")
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("按服务器分组", modifier = Modifier.weight(1f))
            Switch(
                checked = preferences.contactsGroupByServer,
                onCheckedChange = vm::setContactsGroupByServer
            )
        }
        HorizontalDivider()

        if (preferences.contactsGroupByServer) {
            if (grouped.isEmpty()) {
                EmptyState(
                    title = "暂无联系人",
                    message = "连接频道或手动添加后会显示在此。"
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (server, rows) ->
                        item(key = "header-${server.id}") {
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        items(rows, key = { "${server.id}-${it.contact.id}" }) { row ->
                            ContactListRow(
                                row = row,
                                onClick = {
                                    vm.openAutoContact(server.id, row.contact.playerName, row.contact.id) {
                                        onOpenDm(server.id, it.id)
                                    }
                                },
                                onLongClick = { menuContact = row.contact }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        } else {
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
                        ContactListRow(
                            row = row,
                            onClick = {
                                val sid = primaryServerId ?: return@ContactListRow
                                vm.openAutoContact(sid, row.contact.playerName, row.contact.id) {
                                    onOpenDm(sid, it.id)
                                }
                            },
                            onLongClick = { menuContact = row.contact }
                        )
                        HorizontalDivider()
                    }
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

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var serverId by remember { mutableStateOf(primaryServerId.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加联系人") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("玩家名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    servers.forEach { server ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { serverId = server.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = serverId == server.id, onClick = { serverId = server.id })
                            Text(server.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (serverId.isNotBlank() && name.isNotBlank()) {
                        vm.createContact(serverId, name, note)
                        showAddDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    noteContact?.let { contact ->
        var note by remember(contact.id) { mutableStateOf(contact.note) }
        AlertDialog(
            onDismissRequest = { noteContact = null },
            title = { Text("编辑备注 · ${contact.playerName}") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveContact(contact.copy(note = note))
                    noteContact = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { noteContact = null }) { Text("取消") }
            }
        )
    }

    menuContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { menuContact = null },
            title = { Text(contact.playerName) },
            text = {
                Column {
                    TextButton(onClick = {
                        noteContact = contact
                        menuContact = null
                    }) { Text("编辑备注") }
                    TextButton(onClick = {
                        vm.deleteContact(contact.id)
                        menuContact = null
                    }) { Text("移除") }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuContact = null }) { Text("关闭") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactListRow(
    row: ContactRow,
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
                text = buildString {
                    append(
                        when {
                            row.online && row.latencyMs >= 0 -> "在线 · ${row.latencyMs} ms"
                            row.online -> "在线"
                            else -> "离线"
                        }
                    )
                    if (row.contact.note.isNotBlank()) {
                        append(" · ")
                        append(row.contact.note)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (row.online) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
