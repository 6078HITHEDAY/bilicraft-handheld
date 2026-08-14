package com.bilicraft.handheld.ui.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.config.QuickToolLink
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.WebViewActivity
import com.bilicraft.handheld.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickToolsScreen(vm: MainViewModel) {
    val tools by vm.quickTools.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editingTool by remember { mutableStateOf<QuickToolLink?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加") }
            )
        }
    ) { padding ->
        if (tools.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "还没有快捷工具",
                    message = "可添加 Web 链接。首次默认项由 UI 仓库写入，用户删除后不会强制恢复。",
                    actionText = "添加链接",
                    onAction = { showEditor = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(tools, key = { it.id }) { link ->
                    QuickToolItem(
                        link = link,
                        onOpen = {
                            context.startActivity(WebViewActivity.intent(context, link.title, link.url))
                        },
                        onEdit = { editingTool = link },
                        onDelete = { vm.deleteTool(link.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showEditor) {
        ToolEditorDialog(
            initial = null,
            onDismiss = { showEditor = false },
            onSave = { title, url, desc ->
                vm.createTool(title, url, desc)
                showEditor = false
            }
        )
    }

    editingTool?.let { link ->
        ToolEditorDialog(
            initial = link,
            onDismiss = { editingTool = null },
            onSave = { title, url, desc ->
                vm.saveTool(link.copy(title = title, url = url, description = desc))
                editingTool = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QuickToolItem(
    link: QuickToolLink,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(link.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = link.description.takeIf { it.isNotBlank() }?.let {
            { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onEdit)
    )
}

@Composable
private fun ToolEditorDialog(
    initial: QuickToolLink?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var desc by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增快捷工具" else "编辑快捷工具") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("快捷工具只保存标题和 URL；WebView 配置在独立 Activity 中完成。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("说明") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim(), url.trim(), desc.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
