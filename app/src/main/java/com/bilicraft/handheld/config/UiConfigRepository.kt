package com.bilicraft.handheld.config

import android.content.Context
import com.bilicraft.handheld.update.DownloadSource
import com.bilicraft.handheld.version.McVersion
import com.bilicraft.handheld.version.ProtocolTable
import com.bilicraft.handheld.version.VersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val versionId: String,
    val protocolNumber: Int? = null,
    val signingRequired: Boolean = false
) {
    fun toMcVersion(): McVersion = McVersion(
        id = versionId,
        type = VersionType.RELEASE,
        protocolNumber = ProtocolTable.byId[versionId] ?: protocolNumber
    )
}

/** 按服务器软链接的联系人（私聊走 /msg）。 */
@Serializable
data class ServerContact(
    val id: String,
    val serverId: String,
    val playerName: String,
    val note: String = ""
)

@Serializable
enum class ThemeMode(val displayName: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色")
}

/** 插件功能面板列表布局：居中（文档式居中）或靠上（原列表顶对齐）。 */
@Serializable
enum class PluginPanelLayout {
    Center,
    Top
}

@Serializable
enum class NotificationTapBehavior {
    OpenHome,
    OpenChannel
}

@Serializable
data class UiPreferences(
    val chatAutoScroll: Boolean = true,
    val commandCompletionEnabled: Boolean = true,
    val downloadSource: DownloadSource = DownloadSource.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.System,
    val backgroundLowPowerEnabled: Boolean = false,
    val pluginPanelLayout: PluginPanelLayout = PluginPanelLayout.Top,
    val primaryContactServerId: String? = null,
    val pinnedChannelIds: List<String> = emptyList(),
    val archivedChannelIds: List<String> = emptyList(),
    /** serverId → 上次已读消息时间戳 */
    val lastReadTimestamps: Map<String, Long> = emptyMap(),
    val chatFontScale: Float = 1f,
    val maxUiLog: Int = 500, // 与 UiConstants.MAX_UI_LOG_DEFAULT 对齐；config 层不依赖 ui 包
    val notificationTapBehavior: NotificationTapBehavior = NotificationTapBehavior.OpenChannel,
    val quickReplies: List<String> = listOf("在的", "稍等", "收到", "好的"),
    val contactsGroupByServer: Boolean = false
)

/**
 * UI-facing 本地配置仓库。
 *
 * 依赖现有逻辑：连接仍由 SessionController / ConnectionService 执行，仓库只保存 UI 可选项。
 * 纯 UI 补足：服务器配置与联系人用 app 私有 JSON 文件承载。
 * 边界约束：不读写 SecureStore，不实例化 Room，不改变微软登录、协议、插件或 Token 状态机。
 */
class UiConfigRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serverFile = File(context.filesDir, "ui_servers.json")
    private val contactsFile = File(context.filesDir, "ui_contacts.json")
    private val preferencesFile = File(context.filesDir, "ui_preferences.json")
    private val officialSigningMigrationFile = File(context.filesDir, "ui_official_server_signing_v1.migrated")

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _contacts = MutableStateFlow<List<ServerContact>>(emptyList())
    val contacts: StateFlow<List<ServerContact>> = _contacts.asStateFlow()

    private val _preferences = MutableStateFlow(UiPreferences())
    val preferences: StateFlow<UiPreferences> = _preferences.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _servers.value = migrateOfficialServerSigning(
            loadList(serverFile) ?: defaultServers().also { saveList(serverFile, it) }
        ).let { servers ->
            servers.map(::normalizeServerProtocol).also { normalized ->
                if (normalized != servers) saveList(serverFile, normalized)
            }
        }
        _contacts.value = loadList(contactsFile) ?: emptyList()
        _preferences.value = loadValue(preferencesFile) ?: UiPreferences().also { saveValue(preferencesFile, it) }
        reconcilePrimaryContactServer()
    }

    suspend fun setChatAutoScroll(enabled: Boolean) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(chatAutoScroll = enabled)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun setCommandCompletionEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(commandCompletionEnabled = enabled)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun setDownloadSource(source: DownloadSource) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(downloadSource = source)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun setBackgroundLowPowerEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(backgroundLowPowerEnabled = enabled)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun setThemeMode(themeMode: ThemeMode) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(themeMode = themeMode)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun setPluginPanelLayout(layout: PluginPanelLayout) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(pluginPanelLayout = layout)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun setPrimaryContactServerId(serverId: String?) = withContext(Dispatchers.IO) {
        val next = _preferences.value.copy(primaryContactServerId = serverId)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun updatePreferences(transform: (UiPreferences) -> UiPreferences) = withContext(Dispatchers.IO) {
        val next = transform(_preferences.value)
        _preferences.value = next
        saveValue(preferencesFile, next)
    }

    suspend fun togglePinnedChannel(serverId: String) = updatePreferences { prefs ->
        val pinned = prefs.pinnedChannelIds.toMutableList()
        if (serverId in pinned) pinned.remove(serverId) else pinned.add(0, serverId)
        prefs.copy(pinnedChannelIds = pinned)
    }

    suspend fun toggleArchivedChannel(serverId: String) = updatePreferences { prefs ->
        val archived = prefs.archivedChannelIds.toMutableList()
        if (serverId in archived) archived.remove(serverId) else archived.add(serverId)
        prefs.copy(archivedChannelIds = archived)
    }

    suspend fun markChannelRead(serverId: String, timestamp: Long) = updatePreferences { prefs ->
        prefs.copy(lastReadTimestamps = prefs.lastReadTimestamps + (serverId to timestamp))
    }

    suspend fun setChatFontScale(scale: Float) = updatePreferences {
        it.copy(chatFontScale = scale.coerceIn(0.85f, 1.4f))
    }

    suspend fun setMaxUiLog(limit: Int) = updatePreferences {
        it.copy(maxUiLog = limit.coerceIn(100, 5000))
    }

    suspend fun setNotificationTapBehavior(behavior: NotificationTapBehavior) = updatePreferences {
        it.copy(notificationTapBehavior = behavior)
    }

    suspend fun setQuickReplies(replies: List<String>) = updatePreferences {
        it.copy(quickReplies = replies)
    }

    suspend fun setContactsGroupByServer(enabled: Boolean) = updatePreferences {
        it.copy(contactsGroupByServer = enabled)
    }

    suspend fun upsertServer(config: ServerConfig) = withContext(Dispatchers.IO) {
        val normalizedConfig = normalizeServerProtocol(config)
        val current = _servers.value
        val next = if (current.any { it.id == normalizedConfig.id }) {
            current.map { if (it.id == normalizedConfig.id) normalizedConfig else it }
        } else {
            current + normalizedConfig
        }
        _servers.value = next
        saveList(serverFile, next)
        if (_preferences.value.primaryContactServerId == null) {
            setPrimaryContactServerId(normalizedConfig.id)
        }
    }

    suspend fun deleteServer(id: String) = withContext(Dispatchers.IO) {
        val next = _servers.value.filterNot { it.id == id }
        _servers.value = next
        saveList(serverFile, next)
        val remainingContacts = _contacts.value.filterNot { it.serverId == id }
        if (remainingContacts.size != _contacts.value.size) {
            _contacts.value = remainingContacts
            saveList(contactsFile, remainingContacts)
        }
        if (_preferences.value.primaryContactServerId == id) {
            setPrimaryContactServerId(next.firstOrNull()?.id)
        }
    }

    suspend fun upsertContact(contact: ServerContact) = withContext(Dispatchers.IO) {
        val current = _contacts.value
        val next = if (current.any { it.id == contact.id }) {
            current.map { if (it.id == contact.id) contact else it }
        } else {
            current + contact
        }
        _contacts.value = next
        saveList(contactsFile, next)
    }

    suspend fun deleteContact(id: String) = withContext(Dispatchers.IO) {
        val next = _contacts.value.filterNot { it.id == id }
        _contacts.value = next
        saveList(contactsFile, next)
    }

    fun newContact(serverId: String, playerName: String, note: String = ""): ServerContact =
        ServerContact(
            id = UUID.randomUUID().toString(),
            serverId = serverId,
            playerName = playerName.trim(),
            note = note.trim()
        )

    private fun reconcilePrimaryContactServer() {
        val primary = _preferences.value.primaryContactServerId
        val servers = _servers.value
        if (servers.isEmpty()) return
        if (primary != null && servers.any { it.id == primary }) return
        val fallback = servers.first().id
        _preferences.value = _preferences.value.copy(primaryContactServerId = fallback)
        saveValue(preferencesFile, _preferences.value)
    }

    private fun normalizeServerProtocol(config: ServerConfig): ServerConfig = config.copy(
        protocolNumber = ProtocolTable.byId[config.versionId] ?: config.protocolNumber
    )

    fun newServer(
        name: String,
        host: String,
        port: Int,
        version: McVersion,
        signingRequired: Boolean
    ): ServerConfig = ServerConfig(
        id = UUID.randomUUID().toString(),
        name = name,
        host = host,
        port = port,
        versionId = version.id,
        protocolNumber = version.protocolNumber,
        signingRequired = signingRequired
    )

    private inline fun <reified T> loadList(file: File): List<T>? =
        runCatching {
            if (!file.exists()) null else json.decodeFromString<List<T>>(file.readText())
        }.getOrNull()

    private inline fun <reified T> loadValue(file: File): T? =
        runCatching {
            if (!file.exists()) null else json.decodeFromString<T>(file.readText())
        }.getOrNull()

    private inline fun <reified T> saveList(file: File, value: List<T>) {
        file.writeText(json.encodeToString(value))
    }

    private inline fun <reified T> saveValue(file: File, value: T) {
        file.writeText(json.encodeToString(value))
    }

    /**
     * UI 配置迁移：旧版本默认主服曾写成未强制签名，这里只迁移一次。
     * 用户后续如果手动关闭强制签名，不会被下一次启动重新覆盖。
     */
    private fun migrateOfficialServerSigning(servers: List<ServerConfig>): List<ServerConfig> {
        if (officialSigningMigrationFile.exists()) return servers
        val next = servers.map { server ->
            if (server.id == OFFICIAL_SERVER_ID && !server.signingRequired) {
                server.copy(signingRequired = true)
            } else {
                server
            }
        }
        saveList(serverFile, next)
        officialSigningMigrationFile.writeText("done")
        return next
    }

    private fun defaultServers(): List<ServerConfig> = listOf(
        ServerConfig(
            id = OFFICIAL_SERVER_ID,
            name = "碧玺官方主服",
            host = "mc.bilicraft.com",
            port = 25577,
            versionId = "1.21.11",
            protocolNumber = ProtocolTable.byId["1.21.11"],
            signingRequired = true
        )
    )

    private companion object {
        const val OFFICIAL_SERVER_ID = "bilicraft-official-main"
    }
}
