package com.bilicraft.handheld.chat

data class PendingSend(
    val messageId: String,
    val conversationId: String,
    val normalizedBody: String,
    val sentAtMillis: Long
)

class ChatEchoMatcher(
    private val windowMs: Long = 8_000L,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val pending = ArrayDeque<PendingSend>()

    fun track(send: PendingSend) {
        pending.addLast(send)
        prune(now())
    }

    fun match(conversationId: String, bodyPlain: String): String? {
        val t = now()
        prune(t)
        val normalized = normalize(bodyPlain)
        val found = pending.indexOfFirst {
            it.conversationId == conversationId && it.normalizedBody == normalized
        }
        if (found < 0) return null
        return pending.removeAt(found).messageId
    }

    fun expire(): List<PendingSend> {
        val t = now()
        val expired = pending.filter { t - it.sentAtMillis >= windowMs }
        pending.removeAll(expired.toSet())
        return expired
    }

    private fun prune(nowMs: Long) {
        while (pending.isNotEmpty() && nowMs - pending.first().sentAtMillis >= windowMs * 4) {
            pending.removeFirst()
        }
    }

    companion object {
        fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")
    }
}
