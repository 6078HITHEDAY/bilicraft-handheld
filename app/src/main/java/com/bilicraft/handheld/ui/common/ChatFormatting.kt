package com.bilicraft.handheld.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.bilicraft.handheld.protocol.ChatEvent
import com.bilicraft.handheld.protocol.ChatSpan

/** 协议层的富文本片段 → Compose 可渲染文本。spans 为空时退回纯文本。 */
internal fun ChatEvent.toAnnotated(): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(plainText)
    return spans.toAnnotated()
}

internal fun List<ChatSpan>.toAnnotated(): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        withStyle(
            SpanStyle(
                color = span.color?.let { Color(0xFF000000.toInt() or it) } ?: Color.Unspecified,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = when {
                    span.underline && span.strikethrough -> TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                    span.underline -> TextDecoration.Underline
                    span.strikethrough -> TextDecoration.LineThrough
                    else -> null
                }
            )
        ) { append(span.text) }
    }
}
