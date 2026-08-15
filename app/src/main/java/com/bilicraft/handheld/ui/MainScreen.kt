package com.bilicraft.handheld.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bilicraft.handheld.ui.chat.ChannelChatScreen
import com.bilicraft.handheld.ui.chat.ChatChannelsScreen
import com.bilicraft.handheld.ui.chat.DmChatScreen
import com.bilicraft.handheld.ui.contacts.ContactsScreen
import com.bilicraft.handheld.ui.nav.AppRoutes
import com.bilicraft.handheld.ui.plugins.ExternalPluginPanelScreen
import com.bilicraft.handheld.ui.plugins.PluginCenterScreen
import com.bilicraft.handheld.ui.settings.AppIconPickerScreen
import com.bilicraft.handheld.ui.settings.SettingsScreen

private enum class MainTab(val route: String, val title: String, val icon: ImageVector) {
    Chat(AppRoutes.TAB_CHAT, "聊天", Icons.AutoMirrored.Filled.Chat),
    Contacts(AppRoutes.TAB_CONTACTS, "联系人", Icons.Default.Person),
    Settings(AppRoutes.TAB_SETTINGS, "设置", Icons.Default.Settings)
}

/**
 * Material 3 主界面：Scaffold + NavHost。
 * 底部栏显隐由当前 route 是否为 Tab 顶层决定。
 */
@Composable
fun MainScreen(vm: MainViewModel) {
    val uiMessage by vm.uiMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = AppRoutes.isTabRoute(currentRoute)
    val officialMarket by vm.officialMarket.collectAsStateWithLifecycle()
    val pluginUpdateCount = officialMarket.entries.count { it.updateAvailable }
    val servers by vm.servers.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val runtime by vm.serverRuntime.collectAsStateWithLifecycle()
    val chatUnreadTotal = remember(servers, preferences, runtime.chatLogs) {
        vm.totalUnreadCount()
    }
    val pendingDeepLink by vm.pendingDeepLinkServerId.collectAsStateWithLifecycle()
    val activePlugin by vm.activeExternalPluginPanel.collectAsStateWithLifecycle()

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeUiMessage()
    }

    LaunchedEffect(pendingDeepLink, servers) {
        val serverId = pendingDeepLink ?: return@LaunchedEffect
        if (servers.none { it.id == serverId }) return@LaunchedEffect
        navController.navigate(AppRoutes.channel(serverId)) {
            launchSingleTop = true
        }
        vm.consumeDeepLink()
    }

    LaunchedEffect(activePlugin) {
        val request = activePlugin ?: return@LaunchedEffect
        navController.navigate(AppRoutes.pluginPanel(request.pluginId, request.entrypointId)) {
            launchSingleTop = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                when {
                                    tab == MainTab.Settings && pluginUpdateCount > 0 -> {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(if (pluginUpdateCount > 9) "9+" else pluginUpdateCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(tab.icon, contentDescription = tab.title)
                                        }
                                    }
                                    tab == MainTab.Chat && chatUnreadTotal > 0 -> {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(if (chatUnreadTotal > 99) "99+" else chatUnreadTotal.toString())
                                                }
                                            }
                                        ) {
                                            Icon(tab.icon, contentDescription = tab.title)
                                        }
                                    }
                                    else -> Icon(tab.icon, contentDescription = tab.title)
                                }
                            },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = AppRoutes.TAB_CHAT,
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(220)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(220)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(220)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(220)
                    )
                }
            ) {
                composable(AppRoutes.TAB_CHAT) {
                    ChatChannelsScreen(
                        vm = vm,
                        onOpenChannel = { navController.navigate(AppRoutes.channel(it)) }
                    )
                }
                composable(AppRoutes.TAB_CONTACTS) {
                    ContactsScreen(
                        vm = vm,
                        onOpenDm = { serverId, contactId ->
                            navController.navigate(AppRoutes.dm(serverId, contactId))
                        }
                    )
                }
                composable(AppRoutes.TAB_SETTINGS) {
                    SettingsScreen(
                        vm = vm,
                        onOpenPluginCenter = { navController.navigate(AppRoutes.PLUGIN_CENTER) },
                        onOpenAppIconPicker = { navController.navigate(AppRoutes.APP_ICON) }
                    )
                }
                composable(
                    route = AppRoutes.CHANNEL,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType })
                ) { entry ->
                    val serverId = entry.arguments?.getString("serverId").orEmpty()
                    val server = servers.firstOrNull { it.id == serverId }
                    if (server == null) {
                        LaunchedEffect(serverId) { navController.popBackStack() }
                    } else {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wide = maxWidth.value >= com.bilicraft.handheld.ui.common.UiConstants.WIDE_LAYOUT_MIN_WIDTH_DP
                            if (wide) {
                                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.weight(0.38f).fillMaxSize()
                                    ) {
                                        ChatChannelsScreen(
                                            vm = vm,
                                            onOpenChannel = {
                                                navController.navigate(AppRoutes.channel(it)) {
                                                    popUpTo(AppRoutes.TAB_CHAT)
                                                    launchSingleTop = true
                                                }
                                            }
                                        )
                                    }
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.weight(0.62f).fillMaxSize()
                                    ) {
                                        ChannelChatScreen(
                                            vm = vm,
                                            server = server,
                                            onBack = { navController.popBackStack() },
                                            onOpenDm = { contact ->
                                                navController.navigate(AppRoutes.dm(server.id, contact.id))
                                            }
                                        )
                                    }
                                }
                            } else {
                                ChannelChatScreen(
                                    vm = vm,
                                    server = server,
                                    onBack = { navController.popBackStack() },
                                    onOpenDm = { contact ->
                                        navController.navigate(AppRoutes.dm(server.id, contact.id))
                                    }
                                )
                            }
                        }
                    }
                }
                composable(
                    route = AppRoutes.DM,
                    arguments = listOf(
                        navArgument("serverId") { type = NavType.StringType },
                        navArgument("contactId") { type = NavType.StringType }
                    )
                ) { entry ->
                    val serverId = entry.arguments?.getString("serverId").orEmpty()
                    val contactId = entry.arguments?.getString("contactId").orEmpty()
                    val server = servers.firstOrNull { it.id == serverId }
                    val contact = contacts.firstOrNull { it.id == contactId }
                    if (server == null || contact == null) {
                        LaunchedEffect(serverId, contactId) { navController.popBackStack() }
                    } else {
                        DmChatScreen(
                            vm = vm,
                            server = server,
                            contact = contact,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(AppRoutes.PLUGIN_CENTER) {
                    LaunchedEffect(Unit) { vm.refreshOfficialPluginMarket(silent = true) }
                    PluginCenterRoute(
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoutes.APP_ICON) {
                    val currentAppIcon by vm.currentAppIcon.collectAsStateWithLifecycle()
                    AppIconPickerScreen(
                        icons = vm.appIcons,
                        current = currentAppIcon,
                        onSelect = vm::selectAppIcon,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = AppRoutes.PLUGIN_PANEL,
                    arguments = listOf(
                        navArgument("pluginId") { type = NavType.StringType },
                        navArgument("entrypointId") { type = NavType.StringType }
                    )
                ) { entry ->
                    val pluginId = entry.arguments?.getString("pluginId").orEmpty()
                    val entrypointId = entry.arguments?.getString("entrypointId").orEmpty()
                    val handle = remember(pluginId, entrypointId) {
                        vm.externalPluginPanel(pluginId, entrypointId)
                    }
                    if (handle != null) {
                        ExternalPluginPanelScreen(
                            handle = handle,
                            onClose = {
                                vm.closeExternalPlugin()
                                navController.popBackStack()
                            }
                        )
                    } else {
                        LaunchedEffect(pluginId, entrypointId) {
                            vm.closeExternalPlugin()
                            navController.popBackStack()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCenterRoute(vm: MainViewModel, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        com.bilicraft.handheld.ui.common.DetailHeader(
            title = "插件管理",
            onBack = onBack
        )
        com.bilicraft.handheld.ui.plugins.PluginCenterScreen(vm)
    }
}
