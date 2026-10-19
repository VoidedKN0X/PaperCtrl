package com.vknxworks.paperctrl.data.metadata

import com.vknxworks.paperctrl.data.api.NamedObject
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.api.Tag
import com.vknxworks.paperctrl.data.api.correspondents
import com.vknxworks.paperctrl.data.api.documentTypes
import com.vknxworks.paperctrl.data.api.storagePaths
import com.vknxworks.paperctrl.data.api.tags
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything a document can be labelled with on one server. */
data class ServerMetadata(
    val tags: List<Tag> = emptyList(),
    val correspondents: List<NamedObject> = emptyList(),
    val documentTypes: List<NamedObject> = emptyList(),
    val storagePaths: List<NamedObject> = emptyList(),
) {
    fun tag(id: Int) = tags.firstOrNull { it.id == id }
    fun correspondent(id: Int?) = id?.let { i -> correspondents.firstOrNull { it.id == i } }
    fun documentType(id: Int?) = id?.let { i -> documentTypes.firstOrNull { it.id == i } }
    fun storagePath(id: Int?) = id?.let { i -> storagePaths.firstOrNull { it.id == i } }
}

/**
 * Tags, correspondents, document types and storage paths per server, kept for a few minutes so
 * pickers open instantly. A list the account isn't allowed to see just comes back empty.
 */
class MetadataRepository {
    private data class Entry(val at: Long, val data: ServerMetadata)

    private val cache = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    fun cached(serverId: String): ServerMetadata? = cache[serverId]?.data

    suspend fun load(serverId: String, client: PaperlessClient, force: Boolean = false): ServerMetadata = mutex.withLock {
        val hit = cache[serverId]
        if (!force && hit != null && System.currentTimeMillis() - hit.at < MAX_AGE_MS) return hit.data
        val data = coroutineScope {
            val tags = async { runCatching { client.tags() } }
            val correspondents = async { runCatching { client.correspondents() } }
            val types = async { runCatching { client.documentTypes() } }
            val paths = async { runCatching { client.storagePaths() } }
            val results = listOf(tags.await(), correspondents.await(), types.await(), paths.await())
            // Nothing at all came back: the server isn't reachable, so report that instead of empty lists.
            results.firstOrNull { it.isFailure }?.let { failed -> if (results.all { it.isFailure }) throw failed.exceptionOrNull()!! }
            ServerMetadata(
                tags = tags.await().getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                correspondents = correspondents.await().getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                documentTypes = types.await().getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                storagePaths = paths.await().getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
            )
        }
        cache[serverId] = Entry(System.currentTimeMillis(), data)
        data
    }

    fun invalidate(serverId: String) {
        cache.remove(serverId)
    }

    private companion object {
        const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}
