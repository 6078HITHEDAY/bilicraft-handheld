package com.bilicraft.handheld.ui.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.externalplugin.ExternalPluginEntry
import com.bilicraft.handheld.pluginmarket.OfficialPluginMarketEntry
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.SectionTitle
import com.bilicraft.handheld.ui.common.SettingAction
import com.bilicraft.handheld.ui.common.SettingActions

@Composable
internal fun PluginCenterScreen(vm: MainViewModel) {
    val officialMarket by vm.officialMarket.collectAsStateWithLifecycle()
    val externalPlugins by vm.externalPlugins.collectAsStateWithLifecycle()
    val pluginImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importExternalPlugin(uri)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("已安装插件") }
        item {
            ListItem(
                headlineContent = { Text("插件目录") },
                supportingContent = { Text(vm.pluginDropDirText) },
                leadingContent = { Icon(Icons.Default.Build, contentDescription = null) }
            )
        }
        item {
            SettingActions(
                actions = listOf(
                    SettingAction("导入插件包", Icons.Default.Add) {
                        pluginImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    SettingAction("重新扫描", Icons.Default.Refresh, vm::refreshExternalPlugins)
                )
            )
        }
        if (externalPlugins.isEmpty()) {
            item {
                Text(
                    "把 .bhplugin 文件放入上方目录，或点击“导入插件包”选择外部文件。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(externalPlugins, key = { "installed-${it.id}" }) { plugin ->
                ExternalPluginRow(
                    plugin = plugin,
                    onEnabledChange = { enabled -> vm.setExternalPluginEnabled(plugin.id, enabled) },
                    onRemove = { vm.uninstallExternalPlugin(plugin.id) }
                )
                HorizontalDivider()
            }
        }

        item { SectionTitle("官方插件市场") }
        item {
            OfficialPluginMarketHeader(
                updatedAt = officialMarket.updatedAt,
                loading = officialMarket.loading,
                errorMessage = officialMarket.errorMessage,
                onRefresh = { vm.refreshOfficialPluginMarket() }
            )
        }
        if (officialMarket.entries.isEmpty() && !officialMarket.loading) {
            item {
                Text(
                    "官方源暂无可展示插件。刷新失败时会保留本地缓存。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(officialMarket.entries, key = { "market-${it.id}" }) { entry ->
                OfficialPluginMarketRow(
                    entry = entry,
                    onInstall = { vm.installOfficialPlugin(entry.id) }
                )
                HorizontalDivider()
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OfficialPluginMarketHeader(
    updatedAt: String?,
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("官方精选", fontWeight = FontWeight.SemiBold)
                    Text(
                        "经过官方审核的插件，安全可靠",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新官方插件源")
                }
            }
            updatedAt?.takeIf { it.isNotBlank() }?.let {
                Text("索引更新时间：$it", style = MaterialTheme.typography.bodySmall)
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OfficialPluginMarketRow(
    entry: OfficialPluginMarketEntry,
    onInstall: () -> Unit
) {
    val actionText = when {
        !entry.compatible -> "不兼容"
        entry.updateAvailable -> "更新"
        entry.installed -> "已安装"
        else -> "安装"
    }
    val detail = buildList {
        add("最新 ${entry.latestVersion}" + (entry.installedVersion?.let { " · 已装 $it" } ?: ""))
        entry.author.takeIf { it.isNotBlank() }?.let { add("作者：$it") }
        entry.summary.takeIf { it.isNotBlank() }?.let { add(it) }
        entry.description.takeIf { it.isNotBlank() && it != entry.summary }?.let { add(it) }
        entry.downloadSize?.let { add("大小：${formatPluginPackageSize(it)}") }
        if (entry.permissions.isNotEmpty()) add("权限：${entry.permissions.joinToString()}")
        entry.changelog.takeIf { it.isNotBlank() }?.let { add("更新：$it") }
    }.joinToString("\n")

    ListItem(
        headlineContent = { Text(entry.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                detail,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { Icon(Icons.Default.Build, contentDescription = null) },
        trailingContent = {
            Button(
                onClick = onInstall,
                enabled = entry.compatible && (!entry.installed || entry.updateAvailable)
            ) {
                Text(actionText)
            }
        }
    )
}

private fun formatPluginPackageSize(size: Long): String = when {
    size >= 1024L * 1024L -> "%.1f MB".format(size / 1024f / 1024f)
    size >= 1024L -> "%.1f KB".format(size / 1024f)
    else -> "$size B"
}

@Composable
private fun ExternalPluginRow(
    plugin: ExternalPluginEntry,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val status = when {
        !plugin.enabled -> "已禁用"
        plugin.loaded -> "已启用"
        else -> "加载失败"
    }
    ListItem(
        headlineContent = { Text(plugin.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    "$status · ${plugin.version}",
                    plugin.description.takeIf { it.isNotBlank() },
                    plugin.statusMessage
                ).joinToString("\n")
            )
        },
        leadingContent = { Icon(Icons.Default.Build, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = onEnabledChange
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "移除外部插件")
                }
            }
        }
    )
}
