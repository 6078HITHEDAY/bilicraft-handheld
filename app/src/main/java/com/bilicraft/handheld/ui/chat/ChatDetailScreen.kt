package com.bilicraft.handheld.ui.chat

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.chat.Conversation
import com.bilicraft.handheld.chat.ConversationKind
import com.bilicraft.handheld.chat.DeliveryStatus
import com.bilicraft.handheld.chat.MessageDirection
import com.bilicraft.handheld.chat.StoredMessage
import com.bilicraft.handheld.protocol.CommandSuggestion
import com.bilicraft.handheld.protocol.CommandSuggestions
import com.bilicraft.handheld.protocol.ConnectionState
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.FallbackAvatar
import com.bilicraft.handheld.ui.common.PlayerAvatar
import com.bilicraft.handheld.ui.common.ServerFaviconImage
import com.bilicraft.handheld.ui.common.toAnnotated
import com.bilicraft.handheld.ui.theme.LocalChatColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatDetailScreen(
    vm: MainViewModel,
    conversationId: String,
    onBack: () -> Unit,
    onOpenWhisper: (String) -> Unit
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val messagesMap by vm.chatMessages.collectAsStateWithLifecycle()
    val chatServerId by vm.chatServerId.collectAsStateWithLifecycle()
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val suggestions by vm.commandSuggestions.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val onlinePlayers by vm.onlinePlayers.collectAsStateWithLifecycle()
    val icons by vm.serverIcons.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val conversation = conversations.firstOrNull { it.id == conversationId }
    val messages = messagesMap[conversationId].orEmpty()
    val connected = chatServerId != null && runtime.activeServerId == chatServerId &&
        runtime.connectionStates[chatServerId] is ConnectionState.Connected
    val server = servers.firstOrNull { it.id == chatServerId }
    val icon = chatServerId?.let { icons[it] }
    val colors = LocalChatColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf(TextFieldValue()) }
    var menuMessage by remember { mutableStateOf<StoredMessage?>(null) }
    var quoted by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId) {
        vm.markConversationRead(conversationId)
    }

    LaunchedEffect(draft.text, preferences.commandCompletionEnabled, chatServerId) {
        val serverId = chatServerId ?: return@LaunchedEffect
        if (!preferences.commandCompletionEnabled) return@LaunchedEffect
        delay(200)
        vm.requestCommandSuggestions(serverId, draft.text)
    }

    val rows = remember(messages) { buildChatRows(messages) }
    val showJump by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                ConversationAvatar(conversation, server?.name ?: "服务器", icon?.file, vm.playerUuid(conversation?.peerName))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(conversation?.title ?: "会话", fontWeight = FontWeight.Bold, maxLines = 1)
                    val subtitle = when (conversation?.kind) {
                        ConversationKind.Public -> if (icon != null && icon.onlinePlayers >= 0) {
                            "${icon.onlinePlayers}/${icon.maxPlayers} 在线"
                        } else "公屏"
                        ConversationKind.Whisper -> conversation.peerName ?: "私聊"
                        ConversationKind.Channel -> conversation.channelName ?: "频道"
                        ConversationKind.System -> "系统消息"
                        null -> ""
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                    when (row) {
                        is ChatRow.DateSep -> DateCapsule(row.label)
                        is ChatRow.Bubble -> MessageBubble(
                            message = row.message,
                            showHeader = row.showHeader,
                            colors = colors,
                            playerUuid = vm.playerUuid(row.message.sender ?: conversation?.peerName),
                            onLongClick = { menuMessage = row.message }
                        )
                    }
                }
            }

            val atQuery = remember(draft.text) {
                Regex("""@(\w{0,16})$""").find(draft.text)?.groupValues?.get(1)
            }
            if (atQuery != null) {
                val matches = onlinePlayers.values
                    .map { it.name }
                    .filter { it.contains(atQuery, ignoreCase = true) }
                    .distinct()
                    .take(8)
                if (matches.isNotEmpty()) {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(matches) { name ->
                            AssistChip(
                                onClick = {
                                    val replaced = draft.text.replace(Regex("""@\w{0,16}$"""), "@$name ")
                                    draft = TextFieldValue(replaced, TextRange(replaced.length))
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            if (preferences.commandCompletionEnabled && draft.text.startsWith("/") && suggestions.hasSuggestions) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions.suggestions.take(8)) { suggestion: CommandSuggestion ->
                        AssistChip(
                            onClick = {
                                val next = CommandSuggestions.apply(draft.text, suggestions.start, suggestions.length, suggestion.text)
                                draft = TextFieldValue(next, TextRange(next.length))
                            },
                            label = { Text(suggestion.text, maxLines = 1) }
                        )
                    }
                }
            }

            quoted?.let { quote ->
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("引用：$quote", modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { quoted = null }) { Text("取消") }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp),
                    placeholder = { Text(if (connected) "发送消息" else "未连接") },
                    enabled = connected,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = {
                        sendDraft(vm, conversationId, draft.text, quoted) {
                            draft = TextFieldValue()
                            quoted = null
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    })
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        sendDraft(vm, conversationId, draft.text, quoted) {
                            draft = TextFieldValue()
                            quoted = null
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    },
                    enabled = connected && draft.text.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }

        if (showJump) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "跳到最新")
            }
        }
    }

    menuMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { menuMessage = null },
            title = { Text("消息") },
            text = {
                Column {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(message.bodyPlain))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        menuMessage = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("复制") }
                    TextButton(onClick = {
                        quoted = message.bodyPlain
                        menuMessage = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("引用") }
                    message.sender?.takeIf { it.isNotBlank() }?.let { sender ->
                        TextButton(onClick = {
                            onOpenWhisper(sender)
                            menuMessage = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("私聊 $sender") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuMessage = null }) { Text("关闭") } }
        )
    }
}

private fun sendDraft(
    vm: MainViewModel,
    conversationId: String,
    text: String,
    quoted: String?,
    onSent: () -> Unit
) {
    val body = buildString {
        if (!quoted.isNullOrBlank()) append(quoted).append('\n')
        append(text)
    }
    if (body.isBlank()) return
    vm.sendToConversation(conversationId, body)
    onSent()
}

private sealed class ChatRow(val key: String) {
    class DateSep(val label: String, timestamp: Long) : ChatRow("date-$timestamp-$label")
    class Bubble(val message: StoredMessage, val showHeader: Boolean) : ChatRow(message.id)
}

private fun buildChatRows(messages: List<StoredMessage>): List<ChatRow> {
    if (messages.isEmpty()) return emptyList()
    val chronological = messages.sortedBy { it.timestamp }
    val out = ArrayList<ChatRow>(chronological.size * 2)
    chronological.forEachIndexed { index, message ->
        val prev = chronological.getOrNull(index - 1)
        val showHeader = prev == null ||
            prev.sender != message.sender ||
            prev.direction != message.direction ||
            message.timestamp - prev.timestamp > 5 * 60 * 1000L
        if (prev == null || !sameCalendarDay(prev.timestamp, message.timestamp)) {
            out.add(ChatRow.DateSep(formatDateSeparator(message.timestamp), message.timestamp))
        }
        out.add(ChatRow.Bubble(message, showHeader))
    }
    return out.asReversed()
}

@Composable
private fun DateCapsule(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = LocalChatColors.current.systemCapsule) {
            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConversationAvatar(
    conversation: Conversation?,
    serverName: String,
    iconFile: java.io.File?,
    playerUuid: String?
) {
    when (conversation?.kind) {
        ConversationKind.Public -> ServerFaviconImage(serverName, iconFile, size = 36.dp)
        ConversationKind.Whisper -> PlayerAvatar(conversation.peerName ?: conversation.title, playerUuid, size = 36.dp)
        ConversationKind.Channel -> FallbackAvatar(conversation.channelName ?: conversation.title, size = 36.dp)
        ConversationKind.System, null -> FallbackAvatar(conversation?.title ?: "会话", size = 36.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: StoredMessage,
    showHeader: Boolean,
    colors: com.bilicraft.handheld.ui.theme.ChatColors,
    playerUuid: String?,
    onLongClick: () -> Unit
) {
    if (message.kind == ConversationKind.System) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.systemCapsule,
                modifier = Modifier.widthIn(max = 280.dp).combinedClickable(onClick = {}, onLongClick = onLongClick)
            ) {
                Text(
                    text = message.spans.toAnnotated().ifEmpty { AnnotatedString(message.bodyPlain) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }
    val outgoing = message.direction == MessageDirection.Out
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        if (!outgoing) {
            if (showHeader) {
                PlayerAvatar(message.sender ?: "?", playerUuid, size = 32.dp)
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            if (showHeader && !outgoing && !message.sender.isNullOrBlank()) {
                Text(message.sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (outgoing) 16.dp else 4.dp,
                    bottomEnd = if (outgoing) 4.dp else 16.dp
                ),
                color = if (outgoing) colors.outgoingBubble else colors.incomingBubble,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongClick)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.spans.toAnnotated().ifEmpty { AnnotatedString(message.bodyPlain) },
                        color = if (outgoing) colors.outgoingText else colors.incomingText,
                        fontFamily = if (message.isCommand) FontFamily.Monospace else FontFamily.Default,
                        style = if (message.isCommand) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        Text(
                            formatMessageTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (outgoing) colors.outgoingText else colors.incomingText).copy(alpha = 0.7f)
                        )
                        if (outgoing && message.delivery == DeliveryStatus.Unconfirmed) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "未确认",
                                modifier = Modifier.size(12.dp),
                                tint = colors.outgoingText.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.ifEmpty(block: () -> AnnotatedString): AnnotatedString =
    if (this.text.isEmpty()) block() else this
