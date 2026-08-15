package com.bilicraft.handheld.ui.nav

/** 根图与主框架 Navigation-Compose route 常量。 */
object AppRoutes {
    const val LOGIN = "login"
    const val MAIN = "main"

    const val TAB_CHAT = "chat"
    const val TAB_CONTACTS = "contacts"
    const val TAB_SETTINGS = "settings"

    const val CHANNEL = "channel/{serverId}"
    const val DM = "dm/{serverId}/{contactId}"
    const val PLUGIN_CENTER = "pluginCenter"
    const val APP_ICON = "appIconPicker"
    const val PLUGIN_PANEL = "plugin/{pluginId}/{entrypointId}"

    fun channel(serverId: String) = "channel/$serverId"
    fun dm(serverId: String, contactId: String) = "dm/$serverId/$contactId"
    fun pluginPanel(pluginId: String, entrypointId: String) = "plugin/$pluginId/$entrypointId"

    val tabRoutes: Set<String> = setOf(TAB_CHAT, TAB_CONTACTS, TAB_SETTINGS)

    fun isTabRoute(route: String?): Boolean {
        val base = route?.substringBefore('?') ?: return false
        return base in tabRoutes
    }
}

/** 通知 / 深链 Intent extras。 */
object DeepLinkExtras {
    /** 通知点击跳转频道用；与 ConnectionService 启动参数的 server_id 区分，避免互相覆盖。 */
    const val SERVER_ID = "extra_server_id"
    /** 兼容读取连接 Service 写入的 server_id。 */
    const val SERVER_ID_LEGACY = "server_id"

    fun serverIdFrom(intent: android.content.Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra(SERVER_ID)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(SERVER_ID_LEGACY)?.takeIf { it.isNotBlank() }
    }
}
