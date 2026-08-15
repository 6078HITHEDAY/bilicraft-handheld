package com.bilicraft.handheld.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.AppContainer
import com.bilicraft.handheld.config.NotificationTapBehavior
import com.bilicraft.handheld.ui.nav.DeepLinkExtras
import com.bilicraft.handheld.ui.theme.BilicraftTheme

/**
 * 唯一入口 Activity：初始化依赖容器、申请通知权限、承载 Compose UI。
 */
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户选择即可 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        requestNotificationPermissionIfNeeded()
        handleDeepLink(intent)

        setContent {
            val preferences by vm.preferences.collectAsStateWithLifecycle()
            BilicraftTheme(themeMode = preferences.themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        if (vm.preferences.value.notificationTapBehavior != NotificationTapBehavior.OpenChannel) return
        val serverId = DeepLinkExtras.serverIdFrom(intent) ?: return
        vm.offerDeepLinkServerId(serverId)
        intent.removeExtra(DeepLinkExtras.SERVER_ID)
        intent.removeExtra(DeepLinkExtras.SERVER_ID_LEGACY)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
