package com.bilicraft.handheld.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.ui.VersionDropdown
import com.bilicraft.handheld.version.McVersion
import com.bilicraft.handheld.version.VersionRepository

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
    onSave: (String, String, Int, McVersion, Boolean) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial) { mutableStateOf((initial?.port ?: 25565).toString()) }
    var version by remember(initial) { mutableStateOf(initial?.toMcVersion() ?: selectedVersion) }
    var signing by remember(initial) { mutableStateOf(initial?.signingRequired ?: forceSigning) }

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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, host, port.toIntOrNull() ?: 25565, version, signing) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
