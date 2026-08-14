package com.bilicraft.handheld.ui.chat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun formatConversationTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        sameDay(now, then) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        yesterday(now, then) -> "昨天"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

internal fun formatMessageTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

internal fun formatDateSeparator(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        sameDay(now, then) -> "今天"
        yesterday(now, then) -> "昨天"
        else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}

internal fun sameCalendarDay(a: Long, b: Long): Boolean {
    val left = Calendar.getInstance().apply { timeInMillis = a }
    val right = Calendar.getInstance().apply { timeInMillis = b }
    return sameDay(left, right)
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun yesterday(now: Calendar, then: Calendar): Boolean {
    val copy = now.clone() as Calendar
    copy.add(Calendar.DAY_OF_YEAR, -1)
    return sameDay(copy, then)
}
