package com.bilicraft.handheld.server

import java.security.MessageDigest

/**
 * MC Server List Ping 的 favicon 解析。
 *
 * 协议层只把 data URI 原样带出；这里负责抽出 base64、校验 PNG 头、算 sha256。
 * 解码本身交给调用方（Android 用 android.util.Base64，JVM 单测用 java.util.Base64），
 * 因为 minSdk 24 没有 java.util.Base64。
 */
object ServerFavicon {

    val PNG_MAGIC: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun extractBase64(dataUri: String?): String? {
        val raw = dataUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val payload = if (raw.startsWith("data:", ignoreCase = true)) {
            val comma = raw.indexOf(',')
            if (comma < 0) return null
            val header = raw.substring(0, comma).lowercase()
            if (!header.contains("image/png")) return null
            if (!header.contains("base64")) return null
            raw.substring(comma + 1)
        } else {
            raw
        }
        val compact = payload.filterNot { it.isWhitespace() }
        return compact.takeIf { it.isNotEmpty() }
    }

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < PNG_MAGIC.size || bytes.size > MAX_PNG_BYTES) return false
        for (i in PNG_MAGIC.indices) {
            if (bytes[i] != PNG_MAGIC[i]) return false
        }
        return true
    }

    fun decodePng(dataUri: String?, decodeBase64: (String) -> ByteArray): ByteArray? {
        val payload = extractBase64(dataUri) ?: return null
        val bytes = runCatching { decodeBase64(payload) }.getOrNull() ?: return null
        return bytes.takeIf(::isPng)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { b -> append("%02x".format(b)) }
        }
    }

    const val MAX_PNG_BYTES = 256 * 1024
}
