package com.bilicraft.handheld.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.chat.ConversationKind
import com.bilicraft.handheld.ui.MainViewModel
import com.bilicraft.handheld.ui.common.PlayerAvatar
import com.bilicraft.handheld.ui.common.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onPick: (String) -> Unit
) {
    val online by vm.onlinePlayers.collectAsStateWithLifecycle()
    val suggestions by vm.commandSuggestions.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.probeContacts() }

    val probed = remember(suggestions) {
        if (suggestions.requestInput.startsWith("/msg")) {
            suggestions.suggestions.map { it.text.substringAfterLast(' ').trim() }.filter { it.isNotBlank() }
        } else emptyList()
    }
    val names = remember(online, probed, query, conversations) {
        val fromProtocol = online.values.map { it.name }
        val recent = conversations.filter { it.kind == ConversationKind.Whisper }.mapNotNull { it.peerName }
        (fromProtocol + probed + recent)
            .distinctBy { it.lowercase() }
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("联系人", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isNotBlank()) vm.probeContactsPrefix(it)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索玩家") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        if (names.isEmpty()) {
            Text(
                "暂无在线玩家。连接后会通过玩家列表或 /msg 补全探测。",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SectionTitle("在线与最近")
            LazyColumn(Modifier.fillMaxSize()) {
                items(names, key = { it }) { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(name) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(name, vm.playerUuid(name))
                        Spacer(Modifier.width(12.dp))
                        Text(name, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
