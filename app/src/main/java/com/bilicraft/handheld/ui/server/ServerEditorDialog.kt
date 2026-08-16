package com.bilicraft.handheld.ui.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.config.ChatParseConfig
import com.bilicraft.handheld.config.ChatParsePreset
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.ui.VersionDropdown
import com.bilicraft.handheld.ui.chat.BILICRAFT_CHAT_PATTERN
import com.bilicraft.handheld.version.McVersion
import com.bilicraft.handheld.version.VersionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerEditorDialog(
    title: String,
    initial: ServerConfig?,
    versions: VersionRepository.Grouped,
    selectedVersion: McVersion,
    forceSigning: Boolean,
    onSelectVersion: (McVersion) -> Unit,
    onForceSigning: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, McVersion, Boolean, ChatParseConfig) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial) { mutableStateOf((initial?.port ?: 25565).toString()) }
    var version by remember(initial) { mutableStateOf(initial?.toMcVersion() ?: selectedVersion) }
    var signing by remember(initial) { mutableStateOf(initial?.signingRequired ?: forceSigning) }
    var chatParse by remember(initial) { mutableStateOf(initial?.chatParse ?: ChatParseConfig()) }
    var advancedOpen by remember { mutableStateOf(chatParse.preset != ChatParsePreset.Vanilla) }
    var parseMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "配置保存到 UI 仓库；真正连接仍调用现有 ConnectionService。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("服务器地址") }, singleLine = true)
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("端口") },
                    singleLine = true
                )
                VersionDropdown(
                    grouped = versions,
                    selected = version,
                    onSelect = {
                        version = it
                        onSelectVersion(it)
                    }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("强制签名聊天")
                        Text("仅切换现有连接参数，不修改签名算法。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = signing,
                        onCheckedChange = {
                            signing = it
                            onForceSigning(it)
                        }
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { advancedOpen = !advancedOpen },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("高级", style = MaterialTheme.typography.titleSmall)
                    Icon(
                        if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (advancedOpen) "收起" else "展开"
                    )
                }

                if (advancedOpen) {
                    Text("解析聊天信息", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "从公屏拆出子服名、玩家、称号、阵营；碧玺多服里 HY 等是子服标签。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ExposedDropdownMenuBox(
                        expanded = parseMenuExpanded,
                        onExpandedChange = { parseMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = chatParse.preset.displayLabel(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("解析规则") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parseMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = parseMenuExpanded,
                            onDismissRequest = { parseMenuExpanded = false }
                        ) {
                            ChatParsePreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.displayLabel()) },
                                    onClick = {
                                        chatParse = when (preset) {
                                            ChatParsePreset.Custom -> chatParse.copy(
                                                preset = preset,
                                                pattern = chatParse.pattern.ifBlank { BILICRAFT_CHAT_PATTERN }
                                            )
                                            else -> chatParse.copy(preset = preset)
                                        }
                                        parseMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    when (chatParse.preset) {
                        ChatParsePreset.Bilicraft -> {
                            Text(
                                "示例：sender=HY，正文 आ[建筑大师] │ Name: 消息 → 子服 HY",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ChatParsePreset.Custom -> {
                            OutlinedTextField(
                                value = chatParse.pattern,
                                onValueChange = { chatParse = chatParse.copy(pattern = it) },
                                label = { Text("自定义正则") },
                                supportingText = {
                                    Text("可用命名组：(?<server>) (?<player>) (?<message>) (?<title>) (?<faction>)")
                                },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ChatParsePreset.Vanilla -> Unit
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name,
                        host,
                        port.toIntOrNull() ?: 25565,
                        version,
                        signing,
                        chatParse
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun ChatParsePreset.displayLabel(): String = when (this) {
    ChatParsePreset.Vanilla -> "原版（<玩家> 正文）"
    ChatParsePreset.Bilicraft -> "碧玺（阵营+称号+玩家+正文）"
    ChatParsePreset.Custom -> "自定义正则"
}
