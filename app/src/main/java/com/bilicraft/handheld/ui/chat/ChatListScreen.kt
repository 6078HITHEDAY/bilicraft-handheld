package com.bilicraft.handheld.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.chat.Conversation
import com.bilicraft.handheld.chat.ConversationIds
import com.bilicraft.handheld.chat.ConversationKind
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.FallbackAvatar
import com.bilicraft.handheld.ui.common.PlayerAvatar
import com.bilicraft.handheld.ui.common.ScreenHeader
import com.bilicraft.handheld.ui.common.ServerFaviconImage
import com.bilicraft.handheld.ui.common.statusText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatListScreen(
    vm: MainViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenServers: () -> Unit
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val chatServerId by vm.chatServerId.collectAsStateWithLifecycle()
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val icons by vm.serverIcons.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<Conversation?>(null) }

    val server = servers.firstOrNull { it.id == chatServerId } ?: servers.firstOrNull()
    val conn = server?.id?.let { runtime.connectionStates[it] } ?: ConnectionState.Disconnected
    val icon = server?.id?.let { icons[it] }
    val filtered = remember(conversations, query) {
        val q = query.trim()
        val list = if (q.isEmpty()) conversations else conversations.filter {
            it.title.contains(q, ignoreCase = true) || it.lastPreview.contains(q, ignoreCase = true)
        }
        list.sortedWith(
            compareByDescending<Conversation> { it.pinned }
                .thenByDescending { it.lastTimestamp }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenContacts) {
                Icon(Icons.Default.Edit, contentDescription = "联系人")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(
                title = server?.name ?: "聊天",
                actions = {
                    TextButton(onClick = onOpenServers) {
                        Text(statusText(conn))
                    }
                }
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpenServers, onLongClick = onOpenServers)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServerFaviconImage(
                    name = server?.name ?: "服务器",
                    iconFile = icon?.file,
                    conn = conn,
                    size = 40.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(server?.name ?: "未选择服务器", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        statusText(conn) + (server?.let { " · ${it.host}:${it.port}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索会话") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(filtered, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        publicOnline = if (conversation.id == ConversationIds.PUBLIC && icon != null && icon.onlinePlayers >= 0) {
                            "${icon.onlinePlayers}/${icon.maxPlayers} 在线"
                        } else null,
                        iconFile = icon?.file,
                        serverName = server?.name ?: "服务器",
                        playerUuid = vm.playerUuid(conversation.peerName),
                        onClick = {
                            vm.markConversationRead(conversation.id)
                            onOpenConversation(conversation.id)
                        },
                        onLongClick = { menuTarget = conversation }
                    )
                }
            }
        }
    }

    menuTarget?.let { target ->
        ConversationMenu(
            conversation = target,
            onDismiss = { menuTarget = null },
            onPin = { vm.toggleConversationPinned(target.id); menuTarget = null },
            onMute = { vm.toggleConversationMuted(target.id); menuTarget = null },
            onRead = { vm.markConversationRead(target.id); menuTarget = null },
            onClear = { vm.clearConversation(target.id); menuTarget = null },
            onDelete = {
                vm.deleteConversation(target.id)
                menuTarget = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    publicOnline: String?,
    iconFile: java.io.File?,
    serverName: String,
    playerUuid: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (conversation.kind) {
            ConversationKind.Public -> ServerFaviconImage(serverName, iconFile, size = 48.dp)
            ConversationKind.System -> FallbackAvatar("系统", size = 48.dp)
            ConversationKind.Whisper -> PlayerAvatar(conversation.peerName ?: conversation.title, playerUuid)
            ConversationKind.Channel -> FallbackAvatar(conversation.channelName ?: conversation.title)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.pinned) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    conversation.title,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatConversationTime(conversation.lastTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.muted) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    publicOnline ?: conversation.lastPreview.ifBlank { "暂无消息" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0 && !conversation.muted) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationMenu(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onRead: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(conversation.title) },
        text = {
            Column {
                TextButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
                    Text(if (conversation.pinned) "取消置顶" else "置顶")
                }
                TextButton(onClick = onMute, modifier = Modifier.fillMaxWidth()) {
                    Text(if (conversation.muted) "关闭免打扰" else "免打扰")
                }
                TextButton(onClick = onRead, modifier = Modifier.fillMaxWidth()) {
                    Text("标记已读")
                }
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("清空聊天")
                }
                if (conversation.id != ConversationIds.PUBLIC && conversation.id != ConversationIds.SYSTEM) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("删除会话")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
