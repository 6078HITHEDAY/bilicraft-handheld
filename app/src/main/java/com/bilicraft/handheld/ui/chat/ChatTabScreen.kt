package com.bilicraft.handheld.ui.chat

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.plugins.PluginEntrypointEdgeToggle
import com.bilicraft.handheld.ui.plugins.PluginEntrypointSidePanel
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTabScreen(vm: MainViewModel) {
    var conversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var showContacts by rememberSaveable { mutableStateOf(false) }
    var showServerSheet by rememberSaveable { mutableStateOf(false) }
    var pluginMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val pending by vm.pendingChatOpen.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val pluginEntrypoints by vm.externalPluginEntrypoints.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(pending) {
        val open = pending ?: return@LaunchedEffect
        vm.selectChatServer(open.serverId)
        conversationId = open.conversationId
        showContacts = false
        vm.consumePendingChatOpen()
    }

    Box(Modifier.fillMaxSize()) {
        when {
            showContacts -> ContactsScreen(
                vm = vm,
                onBack = { showContacts = false },
                onPick = { name ->
                    conversationId = vm.openWhisper(name)
                    showContacts = false
                }
            )
            conversationId != null -> {
                val id = conversationId!!
                BackHandler { conversationId = null }
                ChatDetailScreen(
                    vm = vm,
                    conversationId = id,
                    onBack = { conversationId = null },
                    onOpenWhisper = { name -> conversationId = vm.openWhisper(name) }
                )
            }
            else -> ChatListScreen(
                vm = vm,
                onOpenConversation = { conversationId = it },
                onOpenContacts = { showContacts = true },
                onOpenServers = { showServerSheet = true }
            )
        }

        PluginEntrypointSidePanel(
            expanded = pluginMenuExpanded,
            entrypoints = pluginEntrypoints,
            layout = preferences.pluginPanelLayout,
            onLayoutChange = vm::setPluginPanelLayout,
            onOpen = { entry ->
                pluginMenuExpanded = false
                vm.openExternalPluginEntrypoint(entry.pluginId, entry.entrypointId)
            },
            onDismiss = { pluginMenuExpanded = false }
        )
        PluginEntrypointEdgeToggle(
            expanded = pluginMenuExpanded,
            onToggle = { pluginMenuExpanded = !pluginMenuExpanded },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }

    if (showServerSheet) {
        ServerConnectionSheet(
            vm = vm,
            onDismiss = { showServerSheet = false },
            onExitApp = {
                vm.prepareFullExit()
                (context as? Activity)?.finishAndRemoveTask()
                exitProcess(0)
            }
        )
    }
}
