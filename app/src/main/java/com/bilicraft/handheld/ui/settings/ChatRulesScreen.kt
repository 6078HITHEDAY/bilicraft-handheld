package com.bilicraft.handheld.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.chat.ChatRule
import com.bilicraft.handheld.chat.ChatRuleKind
import com.bilicraft.handheld.chat.ConversationKind
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.SectionTitle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatRulesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ruleSet by vm.chatRuleSet.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ChatRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var sample by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ruleSet.msgCommandTemplate) }
    val preview = remember(sample, ruleSet) { vm.previewChatRule(sample) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("聊天规则", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "新增规则")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("私聊命令模板") }
            item {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("/msg {player} {message}") },
                    supportingText = { Text("私聊发送时套用。可用 {player} 与 {message}。") }
                )
            }
            item {
                TextButton(
                    onClick = { vm.setMsgCommandTemplate(template) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("保存模板") }
            }
            item { SectionTitle("规则列表") }
            items(ruleSet.rules, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = { Text(rule.note.ifBlank { rule.id }) },
                    supportingContent = { Text("${kindLabel(rule.kind)} · ${rule.pattern}") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { vm.upsertChatRule(rule.copy(enabled = it)) }
                            )
                            if (!rule.builtIn) {
                                IconButton(onClick = { vm.deleteChatRule(rule.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { editing = rule },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("编辑") }
            }
            item { SectionTitle("粘一条真实聊天试跑") }
            item {
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("粘贴服务器原文") },
                    minLines = 2
                )
            }
            item {
                val result = preview
                val text = when {
                    sample.isBlank() -> "粘贴后显示命中的会话类型、玩家与正文。"
                    result == null -> "未命中私聊/频道规则，将进入公屏或系统会话。"
                    else -> buildString {
                        append(kindResult(result.kind))
                        result.peerName?.let { append(" · 玩家 ").append(it) }
                        result.channelName?.let { append(" · 频道 ").append(it) }
                        append("\n正文：").append(result.bodyPlain)
                    }
                }
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val dialogRule = when {
        creating -> ChatRule(
            id = "custom-${UUID.randomUUID()}",
            kind = ChatRuleKind.WhisperIn,
            pattern = "",
            note = "自定义规则"
        )
        else -> editing
    }
    dialogRule?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            onDismiss = { editing = null; creating = false },
            onSave = {
                vm.upsertChatRule(it)
                editing = null
                creating = false
            }
        )
    }
}

@Composable
private fun RuleEditorDialog(
    initial: ChatRule,
    onDismiss: () -> Unit,
    onSave: (ChatRule) -> Unit
) {
    var pattern by remember(initial) { mutableStateOf(initial.pattern) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var kind by remember(initial) { mutableStateOf(initial.kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.builtIn) "编辑内置规则" else "编辑规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, singleLine = true)
                OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("正则（plainText）") }, minLines = 2)
                Text("类型：${kindLabel(kind)}。命名捕获组 player / body / channel。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChatRuleKind.entries.forEach { option ->
                        TextButton(onClick = { kind = option }) {
                            Text(if (option == kind) "• ${kindLabel(option)}" else kindLabel(option))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(pattern = pattern, note = note, kind = kind, builtIn = initial.builtIn))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun kindLabel(kind: ChatRuleKind): String = when (kind) {
    ChatRuleKind.WhisperIn -> "私聊入"
    ChatRuleKind.WhisperOut -> "私聊出"
    ChatRuleKind.Channel -> "频道"
    ChatRuleKind.Mute -> "静音"
}

private fun kindResult(kind: ConversationKind): String = when (kind) {
    ConversationKind.Whisper -> "私聊"
    ConversationKind.Channel -> "频道"
    ConversationKind.Public -> "公屏"
    ConversationKind.System -> "系统"
}
