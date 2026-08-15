package com.bilicraft.handheld.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.config.NotificationTapBehavior
import com.bilicraft.handheld.config.ThemeMode
import com.bilicraft.handheld.ui.common.UiConstants
import kotlin.math.abs

@Composable
internal fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    PreferenceRadioDialog(
        title = "外观主题",
        options = ThemeMode.entries.map { it to it.displayName },
        selected = current,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
internal fun ChatFontScaleDialog(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0.9f to "小 (90%)",
        1f to "标准 (100%)",
        1.15f to "大 (115%)",
        1.3f to "更大 (130%)"
    )
    PreferenceRadioDialog(
        title = "聊天字号",
        options = options,
        selected = options.minByOrNull { abs(it.first - current) }?.first ?: 1f,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
internal fun MaxUiLogDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    PreferenceRadioDialog(
        title = "聊天记录上限",
        options = UiConstants.MAX_UI_LOG_PRESETS.map { it to "$it 条" },
        selected = current.coerceIn(UiConstants.MAX_UI_LOG_MIN, UiConstants.MAX_UI_LOG_MAX)
            .let { clamped ->
                UiConstants.MAX_UI_LOG_PRESETS.minByOrNull { abs(it - clamped) } ?: clamped
            },
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
internal fun NotificationTapDialog(
    current: NotificationTapBehavior,
    onSelect: (NotificationTapBehavior) -> Unit,
    onDismiss: () -> Unit
) {
    PreferenceRadioDialog(
        title = "通知点击行为",
        options = listOf(
            NotificationTapBehavior.OpenHome to "打开应用主页",
            NotificationTapBehavior.OpenChannel to "打开对应频道"
        ),
        selected = current,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
private fun <T> PreferenceRadioDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
