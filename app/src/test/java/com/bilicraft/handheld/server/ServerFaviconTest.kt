package com.bilicraft.handheld.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ServerFaviconTest {
    @Test
    fun `extracts payload from data uri`() {
        val uri = "data:image/png;base64,$ONE_PX"
        assertEquals(ONE_PX, ServerFavicon.extractBase64(uri))
    }

    @Test
    fun `rejects jpeg`() {
        assertNull(ServerFavicon.extractBase64("data:image/jpeg;base64,AAAA"))
    }

    @Test
    fun `strips whitespace in payload`() {
        val uri = "data:image/png;base64, $ONE_PX"
        val decoded = ServerFavicon.decodePng(uri) { Base64.getDecoder().decode(it) }
        assertTrue(decoded != null && ServerFavicon.isPng(decoded))
    }

    @Test
    fun `sha256 is stable`() {
        val bytes = Base64.getDecoder().decode(ONE_PX)
        val first = ServerFavicon.sha256Hex(bytes)
        val second = ServerFavicon.sha256Hex(bytes)
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun `repository writes png and skips rewrite on same hash`() {
        val dir = kotlin.io.path.createTempDirectory("server-icons").toFile()
        val status = com.bilicraft.handheld.protocol.ServerPinger.Status(
            protocol = 773,
            versionName = "1.21.11",
            description = "hi",
            favicon = "data:image/png;base64,$ONE_PX",
            onlinePlayers = 3,
            maxPlayers = 100
        )
        val repo = ServerIconRepository(
            dir = dir,
            ping = { Result.success(status) },
            decodeBase64 = { Base64.getDecoder().decode(it) },
            clock = { 1_000L }
        )
        repo.ingest("srv", "mc.example.com", status)
        val png = dir.listFiles()?.first { it.extension == "png" }!!
        val modified = png.lastModified()
        repo.ingest("srv", "mc.example.com", status)
        assertEquals(modified, png.lastModified())
        assertEquals(3, repo.icons.value["srv"]?.onlinePlayers)
    }

    companion object {
        const val ONE_PX =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    }
}
