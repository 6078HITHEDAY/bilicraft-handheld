package com.bilicraft.handheld

import android.app.Application
import android.content.Context
import android.util.Base64
import com.bilicraft.handheld.appicon.AppIconManager
import com.bilicraft.handheld.auth.AuthClient
import com.bilicraft.handheld.auth.AuthManager
import com.bilicraft.handheld.cdk.CdkRepository
import com.bilicraft.handheld.chat.ChatCoordinator
import com.bilicraft.handheld.chat.ChatRuleRepository
import com.bilicraft.handheld.config.UiConfigRepository
import com.bilicraft.handheld.externalplugin.ExternalPluginManager
import com.bilicraft.handheld.notify.ChatNotificationManager
import com.bilicraft.handheld.pluginmarket.OfficialPluginMarketRepository
import com.bilicraft.handheld.power.AppVisibilityTracker
import com.bilicraft.handheld.protocol.MinecraftTranslations
import com.bilicraft.handheld.server.ServerIconRepository
import com.bilicraft.handheld.service.ConnectionHandoffStore
import com.bilicraft.handheld.session.SessionController
import com.bilicraft.handheld.storage.SecureStore
import com.bilicraft.handheld.update.UpdateClient
import com.bilicraft.handheld.update.UpdateManager
import com.bilicraft.handheld.version.VersionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 进程级依赖容器（手写轻量 DI）。
 *
 * 为什么需要它：Service 与 UI 必须共享同一个 SessionController，
 * 否则会出现两套连接状态。这里用 application context 惰性构建单例，
 * 避免引入 Hilt/Koin 这类重框架——本项目模块边界清晰，手写足够。
 */
object AppContainer {

    @Volatile private var initialized = false

    lateinit var secureStore: SecureStore
        private set
    lateinit var authManager: AuthManager
        private set
    lateinit var versionRepo: VersionRepository
        private set
    lateinit var uiConfigRepo: UiConfigRepository
        private set
    lateinit var session: SessionController
        private set
    lateinit var updateManager: UpdateManager
        private set
    lateinit var appIconManager: AppIconManager
        private set
    lateinit var externalPluginManager: ExternalPluginManager
        private set
    lateinit var officialPluginMarket: OfficialPluginMarketRepository
        private set
    lateinit var cdkRepository: CdkRepository
        private set
    lateinit var connectionHandoffStore: ConnectionHandoffStore
        private set
    lateinit var appVisibility: AppVisibilityTracker
        private set
    lateinit var chatRuleRepository: ChatRuleRepository
        private set
    lateinit var serverIconRepository: ServerIconRepository
        private set
    lateinit var chatCoordinator: ChatCoordinator
        private set
    val chatDispatcher: ChatCoordinator
        get() = chatCoordinator

    data class PendingChatOpen(val serverId: String, val conversationId: String)
    val pendingChatOpen = kotlinx.coroutines.flow.MutableStateFlow<PendingChatOpen?>(null)

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun offerChatOpen(serverId: String?, conversationId: String?) {
        if (serverId.isNullOrBlank() || conversationId.isNullOrBlank()) return
        pendingChatOpen.value = PendingChatOpen(serverId, conversationId)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            app.assets.open("minecraft/zh_cn.json").bufferedReader().use { reader ->
                MinecraftTranslations.loadJson(reader.readText())
            }
            secureStore = SecureStore(app)
            authManager = AuthManager(AuthClient(BuildConfig.MS_CLIENT_ID), secureStore)
            versionRepo = VersionRepository(app)
            uiConfigRepo = UiConfigRepository(app)
            session = SessionController(authManager, versionRepo)
            updateManager = UpdateManager(
                appContext = app,
                client = UpdateClient(owner = "yangyv1016", repo = "bilicraft-handheld"),
                currentVersionName = BuildConfig.VERSION_NAME
            )
            appIconManager = AppIconManager(app)
            externalPluginManager = ExternalPluginManager(app, session)
            officialPluginMarket = OfficialPluginMarketRepository(app, externalPluginManager)
            cdkRepository = CdkRepository(app)
            connectionHandoffStore = ConnectionHandoffStore(app)
            appVisibility = AppVisibilityTracker.install(app as Application)
            chatRuleRepository = ChatRuleRepository(File(app.filesDir, "ui_chat_rules.json"))
            serverIconRepository = ServerIconRepository(
                dir = File(app.filesDir, "server-icons"),
                decodeBase64 = { encoded -> Base64.decode(encoded, Base64.DEFAULT) }
            )
            chatCoordinator = ChatCoordinator(
                filesDir = app.filesDir,
                session = session,
                rules = chatRuleRepository,
                icons = serverIconRepository,
                notifications = ChatNotificationManager(app),
                preferences = { uiConfigRepo.preferences.value },
                selfName = { authManager.currentSession()?.mcUsername },
                activeServerId = { session.activeServerId },
                serverHost = { id -> uiConfigRepo.servers.value.firstOrNull { it.id == id }?.host.orEmpty() },
                isForeground = { appVisibility.isForeground.value },
                scope = processScope
            )
            processScope.launch { chatRuleRepository.load() }
            initialized = true
        }
    }
}