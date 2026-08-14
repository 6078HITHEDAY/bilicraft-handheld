package com.bilicraft.handheld.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import com.bilicraft.handheld.protocol.ConnectionState
import java.io.File

private val AvatarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFFA726),
    Color(0xFF66BB6A), Color(0xFF8D6E63)
)

@Composable
internal fun FallbackAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val color = AvatarPalette[kotlin.math.abs(name.hashCode()) % AvatarPalette.size]
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.4f).sp)
    }
}

@Composable
internal fun PlayerAvatar(
    name: String,
    uuid: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    if (uuid.isNullOrBlank()) {
        FallbackAvatar(name, modifier, size)
        return
    }
    Box(modifier.size(size).clip(CircleShape)) {
        FallbackAvatar(name, Modifier.fillMaxSize(), size)
        AsyncImage(
            model = "https://mc-heads.net/avatar/$uuid/64",
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun ServerFaviconImage(
    name: String,
    iconFile: File?,
    conn: ConnectionState? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val bitmap = remember(iconFile, iconFile?.lastModified()) {
        iconFile?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    Box(modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            FallbackAvatar(name, Modifier.fillMaxSize(), size)
        }
        if (conn != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .background(statusColor(conn))
            )
        }
    }
}
