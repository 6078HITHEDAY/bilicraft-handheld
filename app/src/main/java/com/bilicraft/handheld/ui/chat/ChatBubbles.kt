package com.bilicraft.handheld.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilicraft.handheld.config.ChatParseConfig
import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.CommandSuggestion
import com.bilicraft.handheld.protocol.CommandSuggestionState
import com.bilicraft.handheld.protocol.CommandSuggestions
import com.bilicraft.handheld.protocol.RosterPlayer
import com.bilicraft.handheld.ui.common.UiConstants
import com.bilicraft.handheld.ui.common.toAnnotated
import com.bilicraft.handheld.ui.theme.BilicraftChatTypography
import com.bilicraft.handheld.ui.theme.BilicraftSpacing
import com.bilicraft.handheld.ui.theme.bubbleOtherColor
import com.bilicraft.handheld.ui.theme.bubbleSelfColor
import com.bilicraft.handheld.ui.theme.bubbleSystemColor
import com.bilicraft.handheld.ui.theme.bubbleTimestampColor
import com.bilicraft.handheld.ui.theme.chatDefaultTextColor
import com.bilicraft.handheld.ui.theme.chatSurfaceColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val MENTION_REGEX = Regex("""@([A-Za-z0-9_]{2,16})""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BubbleChatLog(
    log: List<ChatEvent>,
    selfName: String,
    autoScroll: Boolean,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    quickReplies: List<String> = emptyList(),
    roster: List<RosterPlayer> = emptyList(),
    selfUuid: String? = null,
    chatParse: ChatParseConfig = ChatParseConfig(),
    /** 私聊页传入对方正版名，避免气泡用昵称查皮肤。 */
    preferredPeerName: String? = null,
    preferredPeerUuid: String? = null,
    /** 进入会话时快照的已读水位；用于「以下为新消息」分隔，不随后续 markRead 变化。 */
    lastReadAt: Long? = null,
    onDeleteLocal: ((ChatEvent) -> Unit)? = null,
    onForward: ((String) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var followLatestMessage by rememberSaveable { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<ClassifiedChat?>(null) }
    val classified = remember(log, selfName, selfUuid, chatParse) {
        log.map { classifyChat(it, selfName, selfUuid, chatParse) }
    }
    val firstUnreadIndex = remember(classified, lastReadAt) {
        if (lastReadAt == null) -1
        else classified.indexOfFirst { it.event.timestamp > lastReadAt }
    }
    val surface = chatSurfaceColor()
    val defaultText = chatDefaultTextColor()
    val dayFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val dayGroups = remember(classified) {
        val map = linkedMapOf<String, MutableList<Pair<Int, ClassifiedChat>>>()
        classified.forEachIndexed { index, item ->
            val day = dayFmt.format(Date(item.event.timestamp))
            map.getOrPut(day) { mutableListOf() }.add(index to item)
        }
        map.entries.map { it.key to it.value.toList() }
    }
    val lastListIndex = remember(dayGroups) {
        dayGroups.sumOf { 1 + it.second.size } - 1
    }

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
    LaunchedEffect(classified.size, classified.lastOrNull()?.event?.timestamp, autoScroll, lastListIndex) {
        if (autoScroll && followLatestMessage && lastListIndex >= 0) {
            autoScrolling = true
            try {
                listState.scrollToItem(lastListIndex)
            } finally {
                autoScrolling = false
                followLatestMessage = true
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val maxBubbleWidth = maxWidth * UiConstants.BUBBLE_WIDTH_FRACTION
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(surface)
                    .padding(horizontal = 10.dp, vertical = BilicraftSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (classified.isEmpty()) {
                    item {
                        Text(
                            "还没有消息",
                            color = defaultText.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(BilicraftSpacing.xl),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                dayGroups.forEach { (dayKey, entries) ->
                    stickyHeader(key = "day-$dayKey") {
                        DateSeparator(dayLabel(entries.first().second.event.timestamp))
                    }
                    items(
                        items = entries,
                        key = { (index, c) ->
                            "${c.event.timestamp}|${c.senderLabel}|${c.bodyPlain}|$index"
                        }
                    ) { (index, item) ->
                        if (index == firstUnreadIndex && firstUnreadIndex > 0) {
                            UnreadSeparator()
                        }
                        ChatBubble(
                            item = item,
                            selfName = selfName,
                            selfUuid = selfUuid,
                            maxWidth = maxBubbleWidth,
                            fontScale = fontScale,
                            roster = roster,
                            preferredPeerName = preferredPeerName,
                            preferredPeerUuid = preferredPeerUuid,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(item.event.plainText))
                                Toast.makeText(context, "已复制聊天内容", Toast.LENGTH_SHORT).show()
                            },
                            onLongClick = { menuItem = item }
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = !followLatestMessage && classified.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        followLatestMessage = true
                        autoScrolling = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "跳到最新")
                }
            }
        }
    }

    LaunchedEffect(followLatestMessage, lastListIndex) {
        if (followLatestMessage && autoScrolling && lastListIndex >= 0) {
            try {
                listState.scrollToItem(lastListIndex)
            } finally {
                autoScrolling = false
            }
        }
    }

    menuItem?.let { item ->
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = { Text("消息操作") },
            text = {
                Column {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(item.event.plainText))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        menuItem = null
                    }) { Text("复制") }
                    if (onForward != null) {
                        TextButton(onClick = {
                            onForward(item.event.plainText)
                            menuItem = null
                        }) { Text("转发到频道…") }
                    }
                    if (onDeleteLocal != null) {
                        TextButton(onClick = {
                            onDeleteLocal(item.event)
                            menuItem = null
                        }) { Text("删除本地") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuItem = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UnreadSeparator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
        Text(
            "以下为新消息",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
    }
}

private fun dayLabel(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "今天"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    item: ClassifiedChat,
    selfName: String,
    selfUuid: String?,
    maxWidth: androidx.compose.ui.unit.Dp,
    fontScale: Float,
    roster: List<RosterPlayer>,
    preferredPeerName: String?,
    preferredPeerUuid: String?,
    onCopy: () -> Unit,
    onLongClick: () -> Unit
) {
    val defaultText = chatDefaultTextColor()
    val bodyStyle = BilicraftChatTypography.bubble.copy(fontSize = (14 * fontScale).sp)
    when (item.kind) {
        BubbleKind.System -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bubbleSystemColor())
                    .combinedClickable(onClick = onCopy, onLongClick = onLongClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = highlightMentions(item.bodyPlain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        BubbleKind.Self, BubbleKind.Other -> {
            val self = item.kind == BubbleKind.Self
            val label = item.senderLabel?.takeIf { it.isNotBlank() }
                ?: selfName.takeIf { self && it.isNotBlank() && it != "未登录" }.orEmpty()
            val deco = item.event.decorations
            val avatar = remember(
                label,
                item.senderUuid,
                deco?.player,
                item.bodyPlain,
                item.event.plainText,
                roster,
                preferredPeerName,
                preferredPeerUuid,
                self,
                selfName,
                selfUuid
            ) {
                if (self) {
                    resolveAvatarIdentity(
                        label = label,
                        senderUuid = item.senderUuid ?: selfUuid,
                        roster = roster,
                        preferredName = selfName.takeIf { it.isNotBlank() && it != "未登录" }
                            ?: deco?.player,
                        preferredUuid = selfUuid,
                        bodyHint = item.bodyPlain.ifBlank { item.event.plainText }
                    )
                } else if (preferredPeerName != null) {
                    resolveAvatarIdentity(
                        label = label,
                        senderUuid = item.senderUuid ?: preferredPeerUuid,
                        roster = roster,
                        preferredName = preferredPeerName,
                        preferredUuid = preferredPeerUuid,
                        bodyHint = item.bodyPlain.ifBlank { item.event.plainText }
                    )
                } else {
                    resolveAvatarIdentity(
                        label = label,
                        senderUuid = item.senderUuid,
                        roster = roster,
                        preferredName = deco?.player,
                        bodyHint = item.bodyPlain.ifBlank { item.event.plainText }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!self) {
                    PlayerAvatar(
                        name = avatar.name,
                        uuid = avatar.uuid,
                        online = true,
                        size = 34.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(
                            RoundedCornerShape(
                                topStart = BilicraftSpacing.bubbleRadius,
                                topEnd = BilicraftSpacing.bubbleRadius,
                                bottomStart = if (self) BilicraftSpacing.bubbleRadius else 4.dp,
                                bottomEnd = if (self) 4.dp else BilicraftSpacing.bubbleRadius
                            )
                        )
                        .background(if (self) bubbleSelfColor() else bubbleOtherColor())
                        .combinedClickable(onClick = onCopy, onLongClick = onLongClick)
                        .padding(horizontal = BilicraftSpacing.md - 4.dp, vertical = BilicraftSpacing.sm)
                ) {
                    if (!self) {
                        val senderLine = buildString {
                            deco?.server?.takeIf { it.isNotBlank() }?.let {
                                append(it)
                                append(' ')
                            }
                            deco?.faction?.takeIf { it.isNotBlank() }?.let {
                                append(it)
                                append(' ')
                            }
                            deco?.title?.takeIf { it.isNotBlank() }?.let {
                                append('[')
                                append(it)
                                append("] ")
                            }
                            append(item.senderLabel.orEmpty())
                        }
                        Text(
                            text = senderLine,
                            style = BilicraftChatTypography.sender,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    val textColor = if (self) MaterialTheme.colorScheme.onPrimaryContainer else defaultText
                    if (item.bodyPlain.isNotBlank()) {
                        Text(
                            text = highlightMentions(item.bodyPlain),
                            style = bodyStyle,
                            color = textColor
                        )
                    } else {
                        Text(
                            text = item.event.toAnnotated(),
                            style = bodyStyle,
                            color = textColor
                        )
                    }
                    Text(
                        text = formatChatTime(item.event.timestamp),
                        style = BilicraftChatTypography.timestamp,
                        color = bubbleTimestampColor(),
                        modifier = Modifier.align(Alignment.End).padding(top = BilicraftSpacing.xs)
                    )
                }
                if (self && avatar.name.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    PlayerAvatar(
                        name = avatar.name,
                        uuid = avatar.uuid,
                        online = true,
                        size = 34.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun highlightMentions(text: String): AnnotatedString {
    val mentionColor = MaterialTheme.colorScheme.tertiary
    return buildAnnotatedString {
        var last = 0
        for (match in MENTION_REGEX.findAll(text)) {
            append(text.substring(last, match.range.first))
            withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold)) {
                append(match.value)
            }
            last = match.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}

@Composable
internal fun ChatComposer(
    connected: Boolean,
    commandCompletionEnabled: Boolean,
    commandSuggestions: CommandSuggestionState,
    onSend: (String) -> Unit,
    onRequestCommandSuggestions: (String) -> Unit,
    placeholder: String = "发送消息…",
    quickReplies: List<String> = emptyList()
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var showQuickReplies by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(input.text, connected, commandCompletionEnabled) {
        if (!connected || !commandCompletionEnabled || !input.text.startsWith("/")) {
            onRequestCommandSuggestions("")
            return@LaunchedEffect
        }
        delay(COMMAND_COMPLETION_DEBOUNCE_MS)
        onRequestCommandSuggestions(input.text)
    }

    Column(Modifier.fillMaxWidth()) {
        if (showQuickReplies && quickReplies.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickReplies) { phrase ->
                    AssistChip(
                        onClick = {
                            onSend(phrase)
                            showQuickReplies = false
                        },
                        enabled = connected,
                        label = { Text(phrase) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
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
            if (quickReplies.isNotEmpty()) {
                AssistChip(
                    onClick = { showQuickReplies = !showQuickReplies },
                    label = { Text("短语") }
                )
                Spacer(Modifier.width(6.dp))
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                enabled = connected,
                leadingIcon = if (input.text.startsWith("/")) {
                    {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "命令",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
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
                },
                leadingIcon = {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
        }
    }
}

private fun formatChatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private const val COMMAND_COMPLETION_DEBOUNCE_MS = 200L
private const val MAX_VISIBLE_COMMAND_SUGGESTIONS = 6
