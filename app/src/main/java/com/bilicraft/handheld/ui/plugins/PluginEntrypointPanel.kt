package com.bilicraft.handheld.ui.plugins

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.config.PluginPanelLayout
import com.bilicraft.handheld.externalplugin.ExternalPluginEntrypoint
import com.bilicraft.handheld.externalplugin.ExternalPluginPanelHandle

@Composable
internal fun PluginEntrypointEdgeToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier
            .padding(vertical = 8.dp)
            .height(96.dp)
            .width(28.dp),
        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.height(4.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    },
                    contentDescription = if (expanded) "收起插件菜单" else "展开插件菜单",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PluginEntrypointCard(
    entry: ExternalPluginEntrypoint,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.title.ifBlank { entry.pluginName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val subtitle = buildList {
                if (entry.title.isNotBlank() && entry.pluginName.isNotBlank()) {
                    add(entry.pluginName)
                }
                entry.description.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun PluginEntrypointSidePanel(
    expanded: Boolean,
    entrypoints: List<ExternalPluginEntrypoint>,
    layout: PluginPanelLayout,
    onLayoutChange: (PluginPanelLayout) -> Unit,
    onOpen: (ExternalPluginEntrypoint) -> Unit,
    onDismiss: () -> Unit
) {
    if (expanded) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss)
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(com.bilicraft.handheld.ui.common.UiConstants.PLUGIN_PANEL_WIDTH_FRACTION)
                    .widthIn(max = com.bilicraft.handheld.ui.common.UiConstants.PLUGIN_PANEL_MAX_WIDTH_DP.dp)
                    // 吞掉面板上的点击，避免穿透到背后遮罩触发关闭。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "插件功能",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        val switchToCenter = layout == PluginPanelLayout.Top
                        IconButton(
                            onClick = {
                                onLayoutChange(
                                    if (switchToCenter) PluginPanelLayout.Center else PluginPanelLayout.Top
                                )
                            }
                        ) {
                            Icon(
                                imageVector = if (switchToCenter) {
                                    Icons.Default.VerticalAlignCenter
                                } else {
                                    Icons.Default.VerticalAlignTop
                                },
                                contentDescription = if (switchToCenter) {
                                    "切换为居中排列"
                                } else {
                                    "切换为靠上排列"
                                }
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭插件菜单")
                        }
                    }
                    HorizontalDivider()
                    val topAligned = layout == PluginPanelLayout.Top
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = if (topAligned) Alignment.TopCenter else Alignment.Center
                    ) {
                        if (entrypoints.isEmpty()) {
                            Text(
                                text = "当前没有可用插件入口。请到「设置 → 插件管理」安装并启用插件，然后点屏幕右侧的插件条打开。",
                                modifier = Modifier.padding(top = if (topAligned) 12.dp else 0.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (topAligned) 12.dp else 0.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                entrypoints.forEach { entry ->
                                    PluginEntrypointCard(
                                        entry = entry,
                                        onOpen = { onOpen(entry) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExternalPluginPanelScreen(
    handle: ExternalPluginPanelHandle,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        handle.panel.Content(host = handle.host, onClose = onClose)
    }
}
