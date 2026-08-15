package com.bilicraft.handheld.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.CommandSuggestion
import com.bilicraft.handheld.protocol.CommandSuggestionState
import com.bilicraft.handheld.protocol.CommandSuggestions
import com.bilicraft.handheld.ui.common.toAnnotated
import com.bilicraft.handheld.ui.theme.bubbleOtherColor
import com.bilicraft.handheld.ui.theme.bubbleSelfColor
import com.bilicraft.handheld.ui.theme.bubbleSystemColor
import com.bilicraft.handheld.ui.theme.bubbleTimestampColor
import com.bilicraft.handheld.ui.theme.chatDefaultTextColor
import com.bilicraft.handheld.ui.theme.chatSurfaceColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun BubbleChatLog(
    log: List<ChatEvent>,
    selfName: String,
    autoScroll: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var followLatestMessage by rememberSaveable { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val classified = remember(log, selfName) { log.map { classifyChat(it, selfName) } }
    val surface = chatSurfaceColor()
    val defaultText = chatDefaultTextColor()

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
    LaunchedEffect(classified.size, classified.lastOrNull()?.event?.timestamp, autoScroll) {
        if (autoScroll && followLatestMessage && classified.isNotEmpty()) {
            autoScrolling = true
            try {
                listState.scrollToItem(classified.lastIndex)
            } finally {
                autoScrolling = false
                followLatestMessage = true
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (classified.isEmpty()) {
            item {
                Text(
                    "还没有消息",
                    color = defaultText.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        items(
            items = classified,
            key = { c -> "${c.event.timestamp}|${c.senderLabel}|${c.bodyPlain}|${System.identityHashCode(c.event)}" }
        ) { item ->
            ChatBubble(
                item = item,
                selfName = selfName,
                maxWidth = maxBubbleWidth,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(item.event.plainText))
                    Toast.makeText(context, "已复制聊天内容", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(
    item: ClassifiedChat,
    selfName: String,
    maxWidth: androidx.compose.ui.unit.Dp,
    onCopy: () -> Unit
) {
    val defaultText = chatDefaultTextColor()
    when (item.kind) {
        BubbleKind.System -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bubbleSystemColor())
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.bodyPlain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        BubbleKind.Self, BubbleKind.Other -> {
            val self = item.kind == BubbleKind.Self
            val avatarName = item.senderLabel?.takeIf { it.isNotBlank() }
                ?: selfName.takeIf { self && it.isNotBlank() && it != "未登录" }.orEmpty()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!self) {
                    PlayerAvatar(name = avatarName, online = true, size = 34.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (self) 16.dp else 4.dp,
                                bottomEnd = if (self) 4.dp else 16.dp
                            )
                        )
                        .background(if (self) bubbleSelfColor() else bubbleOtherColor())
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (!self) {
                        Text(
                            text = item.senderLabel.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (item.bodyPlain.isNotBlank()) {
                        Text(
                            text = item.bodyPlain,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (self) MaterialTheme.colorScheme.onPrimaryContainer else defaultText
                        )
                    } else {
                        Text(
                            text = item.event.toAnnotated(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (self) MaterialTheme.colorScheme.onPrimaryContainer else defaultText
                        )
                    }
                    Text(
                        text = formatChatTime(item.event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleTimestampColor(),
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                        fontSize = 10.sp
                    )
                }
                if (self && avatarName.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    PlayerAvatar(name = avatarName, online = true, size = 34.dp)
                }
            }
        }
    }
}

@Composable
internal fun ChatComposer(
    connected: Boolean,
    commandCompletionEnabled: Boolean,
    commandSuggestions: CommandSuggestionState,
    onSend: (String) -> Unit,
    onRequestCommandSuggestions: (String) -> Unit,
    placeholder: String = "发送消息…"
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(input.text, connected, commandCompletionEnabled) {
        if (!connected || !commandCompletionEnabled || !input.text.startsWith("/")) {
            onRequestCommandSuggestions("")
            return@LaunchedEffect
        }
        delay(COMMAND_COMPLETION_DEBOUNCE_MS)
        onRequestCommandSuggestions(input.text)
    }

    Column(Modifier.fillMaxWidth()) {
        val visibleSuggestions = commandSuggestions.takeIf {
            connected && commandCompletionEnabled && it.requestInput == input.text && it.hasSuggestions
        }
        if (visibleSuggestions != null) {
            CommandSuggestionBar(
                state = visibleSuggestions,
                onSelect = { suggestion ->
                    val applied = CommandSuggestions.apply(
                        input.text,
                        visibleSuggestions.start,
                        visibleSuggestions.length,
                        suggestion.text
                    )
                    input = TextFieldValue(applied, selection = TextRange(applied.length))
                }
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                enabled = connected,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSend(input.text)
                    input = TextFieldValue("")
                    onRequestCommandSuggestions("")
                },
                enabled = connected && input.text.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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

private fun formatChatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private const val COMMAND_COMPLETION_DEBOUNCE_MS = 200L
private const val MAX_VISIBLE_COMMAND_SUGGESTIONS = 6
