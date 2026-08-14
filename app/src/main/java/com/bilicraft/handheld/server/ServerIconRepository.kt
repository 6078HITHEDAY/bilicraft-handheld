package com.bilicraft.handheld.server

import com.bilicraft.handheld.config.ServerConfig
import com.bilicraft.handheld.protocol.ServerAddress
import com.bilicraft.handheld.protocol.ServerPinger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class ServerIconSnapshot(
    val serverId: String,
    val file: File?,
    val onlinePlayers: Int = -1,
    val maxPlayers: Int = -1,
    val fetchedAt: Long = 0L
)

class ServerIconRepository(
    private val dir: File,
    private val ping: suspend (ServerAddress) -> Result<ServerPinger.Status> = { ServerPinger().ping(it) },
    private val decodeBase64: (String) -> ByteArray,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val locks = mutableMapOf<String, Mutex>()
    private val _icons = MutableStateFlow<Map<String, ServerIconSnapshot>>(emptyMap())
    val icons: StateFlow<Map<String, ServerIconSnapshot>> = _icons.asStateFlow()

    init {
        dir.mkdirs()
        loadCached()
    }

    suspend fun refresh(config: ServerConfig, force: Boolean = false) {
        val mutex = lockFor(config.id)
        mutex.withLock {
            val meta = readMeta(config.id)
            val now = clock()
            if (!force && meta != null && now - meta.fetchedAt < TTL_MS) return
            val status = ping(ServerAddress(config.host, config.port)).getOrNull() ?: return
            ingestLocked(config.id, config.host, status)
        }
    }

    fun ingest(serverId: String, host: String, status: ServerPinger.Status) {
        ingestLocked(serverId, host, status)
    }

    fun delete(serverId: String) {
        pngFile(serverId).delete()
        metaFile(serverId).delete()
        _icons.update { it - serverId }
    }

    private fun ingestLocked(serverId: String, host: String, status: ServerPinger.Status) {
        val now = clock()
        val existing = snapshotOf(serverId)
        val pngBytes = ServerFavicon.decodePng(status.favicon, decodeBase64)
        var file = existing?.file?.takeIf { it.exists() }
        if (pngBytes != null) {
            val sha = ServerFavicon.sha256Hex(pngBytes)
            val meta = readMeta(serverId)
            if (meta?.sha256 != sha) {
                file = writePng(serverId, pngBytes)
            }
            writeMeta(
                serverId,
                ServerIconMeta(
                    sha256 = sha,
                    fetchedAt = now,
                    sourceHost = host,
                    onlinePlayers = status.onlinePlayers,
                    maxPlayers = status.maxPlayers
                )
            )
        } else {
            val meta = readMeta(serverId)
            if (meta != null) {
                writeMeta(
                    serverId,
                    meta.copy(
                        fetchedAt = now,
                        sourceHost = host,
                        onlinePlayers = status.onlinePlayers.takeIf { it >= 0 } ?: meta.onlinePlayers,
                        maxPlayers = status.maxPlayers.takeIf { it >= 0 } ?: meta.maxPlayers
                    )
                )
            }
        }
        val meta = readMeta(serverId)
        _icons.update {
            it + (serverId to ServerIconSnapshot(
                serverId = serverId,
                file = file?.takeIf { png -> png.exists() },
                onlinePlayers = status.onlinePlayers.takeIf { n -> n >= 0 } ?: meta?.onlinePlayers ?: -1,
                maxPlayers = status.maxPlayers.takeIf { n -> n >= 0 } ?: meta?.maxPlayers ?: -1,
                fetchedAt = meta?.fetchedAt ?: now
            ))
        }
    }

    private fun loadCached() {
        val loaded = dir.listFiles()?.mapNotNull { file ->
            if (file.extension != "png") return@mapNotNull null
            val id = file.nameWithoutExtension
            val meta = readMeta(id)
            id to ServerIconSnapshot(
                serverId = id,
                file = file,
                onlinePlayers = meta?.onlinePlayers ?: -1,
                maxPlayers = meta?.maxPlayers ?: -1,
                fetchedAt = meta?.fetchedAt ?: file.lastModified()
            )
        }.orEmpty().toMap()
        _icons.value = loaded
    }

    private fun snapshotOf(serverId: String): ServerIconSnapshot? = _icons.value[serverId]

    private fun pngFile(serverId: String): File = File(dir, "${safeId(serverId)}.png")
    private fun metaFile(serverId: String): File = File(dir, "${safeId(serverId)}.meta.json")

    private fun readMeta(serverId: String): ServerIconMeta? =
        runCatching {
            val file = metaFile(serverId)
            if (!file.exists()) null else json.decodeFromString<ServerIconMeta>(file.readText())
        }.getOrNull()

    private fun writeMeta(serverId: String, meta: ServerIconMeta) {
        metaFile(serverId).writeText(json.encodeToString(meta))
    }

    private fun writePng(serverId: String, bytes: ByteArray): File {
        val dest = pngFile(serverId)
        val tmp = File(dir, "${safeId(serverId)}.png.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(dest)) {
            dest.delete()
            tmp.renameTo(dest)
        }
        return dest
    }

    private fun lockFor(id: String): Mutex = synchronized(locks) { locks.getOrPut(id) { Mutex() } }

    private fun safeId(serverId: String): String =
        serverId.map { ch -> if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_' }.joinToString("")

    @Serializable
    private data class ServerIconMeta(
        val sha256: String,
        val fetchedAt: Long,
        val sourceHost: String,
        val onlinePlayers: Int = -1,
        val maxPlayers: Int = -1
    )

    companion object {
        const val TTL_MS = 6 * 60 * 60 * 1000L
    }
}
