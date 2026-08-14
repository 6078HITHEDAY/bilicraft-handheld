package com.bilicraft.handheld.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilicraft.handheld.AppContainer
import com.bilicraft.handheld.notify.ChatNotificationManager
import com.bilicraft.handheld.ui.theme.BilicraftTheme

/**
 * 唯一入口 Activity：初始化依赖容器、申请通知权限、承载 Compose UI。
 * 品牌配色由 BilicraftTheme 统一提供。
 */
class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户选择即可 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        requestNotificationPermissionIfNeeded()
        offerChatOpen(intent)

        setContent {
            val vm: MainViewModel = viewModel()
            val preferences by vm.preferences.collectAsStateWithLifecycle()
            BilicraftTheme(themeMode = preferences.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerChatOpen(intent)
    }

    private fun offerChatOpen(intent: Intent?) {
        AppContainer.offerChatOpen(
            intent?.getStringExtra(ChatNotificationManager.EXTRA_SERVER_ID),
            intent?.getStringExtra(ChatNotificationManager.EXTRA_CONVERSATION_ID)
        )
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
