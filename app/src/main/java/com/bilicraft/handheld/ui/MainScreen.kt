package com.bilicraft.handheld.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.ui.chat.ChatTabScreen
import com.bilicraft.handheld.ui.plugins.ExternalPluginPanelScreen
import com.bilicraft.handheld.ui.plugins.PluginCenterScreen
import com.bilicraft.handheld.ui.settings.SettingsScreen
import com.bilicraft.handheld.ui.tools.QuickToolsScreen

private enum class MainTab(val title: String, val icon: ImageVector) {
    Chat("聊天", Icons.Default.Chat),
    Tools("快捷工具", Icons.Default.Web),
    Plugins("插件管理", Icons.Default.Build),
    Settings("设置", Icons.Default.Settings)
}

/**
 * Material 3 主界面。
 * 底部导航：聊天 / 工具 / 插件 / 设置。连接、聊天、插件操作只通过 MainViewModel 转发到底层模块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val uiMessage by vm.uiMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Chat) }
    val mainTabStateHolder = rememberSaveableStateHolder()
    val activeExternalPluginPanel by vm.activeExternalPluginPanel.collectAsStateWithLifecycle()
    val officialMarket by vm.officialMarket.collectAsStateWithLifecycle()
    val pluginUpdateCount = officialMarket.entries.count { it.updateAvailable }

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeUiMessage()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Plugins) {
            vm.refreshOfficialPluginMarket(silent = true)
        }
        if (selectedTab == MainTab.Chat) {
            vm.refreshServerIcons()
        }
    }

    activeExternalPluginPanel?.let { request ->
        val handle = remember(request.pluginId, request.entrypointId) {
            vm.externalPluginPanel(request.pluginId, request.entrypointId)
        }
        if (handle != null) {
            ExternalPluginPanelScreen(handle = handle, onClose = vm::closeExternalPlugin)
            return
        }
        LaunchedEffect(request) { vm.closeExternalPlugin() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (tab == MainTab.Plugins && pluginUpdateCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(if (pluginUpdateCount > 9) "9+" else pluginUpdateCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            mainTabStateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    MainTab.Chat -> ChatTabScreen(vm)
                    MainTab.Tools -> QuickToolsScreen(vm)
                    MainTab.Plugins -> PluginCenterScreen(vm)
                    MainTab.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}
