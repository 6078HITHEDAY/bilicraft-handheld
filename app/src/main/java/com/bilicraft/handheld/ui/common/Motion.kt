package com.bilicraft.handheld.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * 系统「动画时长缩放」；0 表示关闭动画（无障碍「删除动画」）。
 * 当前 Compose UI 版本未暴露 LocalMotionDurationScale CompositionLocal，故读系统设置。
 */
@Composable
internal fun rememberAnimatorDurationScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f).coerceAtLeast(0f)
    }
}

@Composable
internal fun motionDurationMs(baseMs: Int): Int {
    val scale = rememberAnimatorDurationScale()
    if (scale == 0f) return 0
    return (baseMs * scale).roundToInt().coerceAtLeast(1)
}

@Composable
internal fun motionEnabled(): Boolean = rememberAnimatorDurationScale() > 0f
