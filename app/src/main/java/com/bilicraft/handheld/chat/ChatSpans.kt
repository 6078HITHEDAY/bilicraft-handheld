package com.bilicraft.handheld.chat

import com.bilicraft.handheld.protocol.ChatSpan

/** 按 plainText 的字符区间切出对应 spans，跨 span 边界时拆分该 span，保留其样式。 */
fun List<ChatSpan>.sliceByPlainRange(start: Int, endExclusive: Int): List<ChatSpan> {
    if (isEmpty() || start >= endExclusive) return emptyList()
    val loBound = start.coerceAtLeast(0)
    val out = ArrayList<ChatSpan>()
    var cursor = 0
    for (span in this) {
        val spanStart = cursor
        val spanEnd = cursor + span.text.length
        cursor = spanEnd
        val lo = maxOf(loBound, spanStart)
        val hi = minOf(endExclusive, spanEnd)
        if (lo < hi) {
            out.add(span.copy(text = span.text.substring(lo - spanStart, hi - spanStart)))
        }
        if (cursor >= endExclusive) break
    }
    return out
}
