package com.bilicraft.handheld.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bilicraft.handheld.ui.common.UiConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val avatarHttp = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/** 内存缓存：同一玩家名只拉一次皮肤头（有界 LRU）。 */
private val avatarCache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
        size > UiConstants.AVATAR_CACHE_MAX
}
private val avatarCacheLock = Any()
private val defaultSteveHeadCached by lazy { defaultSteveHead() }

@Composable
internal fun PlayerAvatar(
    name: String,
    online: Boolean,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val key = name.trim()
    var bitmap by remember(key) {
        mutableStateOf(
            synchronized(avatarCacheLock) {
                avatarCache[key.lowercase()] ?: defaultSteveHeadCached
            }
        )
    }
    LaunchedEffect(key) {
        if (key.isEmpty()) {
            bitmap = defaultSteveHeadCached
            return@LaunchedEffect
        }
        val cached = synchronized(avatarCacheLock) { avatarCache[key.lowercase()] }
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { loadSkinHead(key) }
        if (loaded != null) {
            synchronized(avatarCacheLock) { avatarCache[key.lowercase()] = loaded }
            bitmap = loaded
        } else {
            bitmap = defaultSteveHeadCached
        }
    }
    val grayFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(0f) }
        )
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .semantics { contentDescription = if (online) "$name 在线" else "$name 离线" }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
            colorFilter = if (online) null else grayFilter,
            alpha = if (online) 1f else 0.55f
        )
    }
}

private fun loadSkinHead(name: String): Bitmap? {
    val encoded = Uri.encode(name.trim())
    val urls = listOf(
        "https://mc-heads.net/avatar/$encoded/64",
        "https://minotar.net/helm/$encoded/64.png",
        "https://crafthead.net/avatar/$encoded/64",
        "https://crafatar.com/avatars/$encoded?size=64&overlay=true&default=MHF_Steve"
    )
    for (url in urls) {
        val bmp = fetchBitmap(url) ?: continue
        if (bmp.width > 0 && bmp.height > 0) return scaleNearest(bmp, 64)
    }
    return null
}

private fun fetchBitmap(url: String): Bitmap? = runCatching {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", "BilicraftHandheld/1.0 (Android; skin-avatar)")
        .header("Accept", "image/png,image/*;q=0.8,*/*;q=0.5")
        .get()
        .build()
    avatarHttp.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val bytes = resp.body?.bytes() ?: return null
        if (bytes.size < 32) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}.getOrNull()

private fun scaleNearest(src: Bitmap, size: Int): Bitmap {
    if (src.width == size && src.height == size) return src
    return Bitmap.createScaledBitmap(src, size, size, false)
}

/**
 * 经典 Steve 脸部 8×8 像素，放大后作加载中/失败占位，避免字母。
 * 色值大致对齐默认皮肤。
 */
private fun defaultSteveHead(): Bitmap {
    // ARGB rows, top→bottom, left→right；每格 1 像素
    val face = intArrayOf(
        0xFF2F200D.toInt(), 0xFF2F200D.toInt(), 0xFF2F200D.toInt(), 0xFF2F200D.toInt(),
        0xFF2F200D.toInt(), 0xFF2F200D.toInt(), 0xFF2F200D.toInt(), 0xFF2F200D.toInt(),
        0xFF2F200D.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFF2F200D.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFF3B2A1A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFF3B2A1A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFF5A8AD4.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFF5A8AD4.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFF8B5A3C.toInt(),
        0xFF8B5A3C.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFA0724A.toInt(), 0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(),
        0xFFB58A5A.toInt(), 0xFFB58A5A.toInt(), 0xFFA0724A.toInt(), 0xFFB58A5A.toInt(),
        0xFFA0724A.toInt(), 0xFFA0724A.toInt(), 0xFFA0724A.toInt(), 0xFFA0724A.toInt(),
        0xFFA0724A.toInt(), 0xFFA0724A.toInt(), 0xFFA0724A.toInt(), 0xFFA0724A.toInt()
    )
    val small = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    small.setPixels(face, 0, 8, 0, 0, 8, 8)
    val out = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    canvas.drawBitmap(
        small,
        android.graphics.Rect(0, 0, 8, 8),
        android.graphics.Rect(0, 0, 64, 64),
        paint
    )
    return out
}
