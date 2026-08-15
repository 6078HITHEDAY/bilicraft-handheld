package com.bilicraft.handheld.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.config.ServerContact
import com.bilicraft.handheld.chat.filterDirectMessages
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.statusText

@Composable
internal fun DmChatScreen(
    vm: MainViewModel,
    server: ServerConfig,
    contact: ServerContact,
    onBack: () -> Unit
) {
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val commandSuggestions by vm.commandSuggestions.collectAsStateWithLifecycle()

    val selfName = vm.currentAccountName
    val fullLog = runtime.chatLogs[server.id].orEmpty()
    val dmLog = remember(fullLog, contact.playerName, selfName) {
        filterDirectMessages(fullLog, contact.playerName, selfName, fallbackPeer = contact.playerName)
    }
    val isActive = runtime.activeServerId == server.id
    val conn = runtime.connectionStates[server.id] ?: ConnectionState.Disconnected
    val connected = isActive && conn is ConnectionState.Connected

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            val online = runtime.rosters[server.id].orEmpty().any {
                it.online && (
                    it.uuid == contact.id ||
                        it.name.equals(contact.playerName, ignoreCase = true)
                    )
            }
            PlayerAvatar(name = contact.playerName, online = online, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    contact.playerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (online) "在线" else "离线")
                        append(" · ")
                        append(server.name)
                        append(" · ")
                        append(statusText(conn))
                        if (contact.note.isNotBlank()) {
                            append(" · ")
                            append(contact.note)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        BubbleChatLog(
            log = dmLog,
            selfName = selfName,
            autoScroll = preferences.chatAutoScroll,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        ChatComposer(
            connected = connected,
            commandCompletionEnabled = preferences.commandCompletionEnabled,
            commandSuggestions = commandSuggestions,
            onSend = { vm.sendDirectMessage(server.id, contact.playerName, it) },
            onRequestCommandSuggestions = { vm.requestCommandSuggestions(server.id, it) },
            placeholder = "私聊 ${contact.playerName}…"
        )
    }
}
