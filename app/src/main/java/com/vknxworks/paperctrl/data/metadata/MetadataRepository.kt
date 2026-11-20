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
    // Looked up for every chip in every list row, so each list is indexed by ID once (first entry wins,
    // the same answer a search through the list gives).
    private val tagsById: Map<Int, Tag> by lazy { firstById(tags) { it.id } }
    private val correspondentsById: Map<Int, NamedObject> by lazy { firstById(correspondents) { it.id } }
    private val documentTypesById: Map<Int, NamedObject> by lazy { firstById(documentTypes) { it.id } }
    private val storagePathsById: Map<Int, NamedObject> by lazy { firstById(storagePaths) { it.id } }

    fun tag(id: Int): Tag? = tagsById[id]
    fun correspondent(id: Int?): NamedObject? = id?.let { i -> correspondentsById[i] }
    fun documentType(id: Int?): NamedObject? = id?.let { i -> documentTypesById[i] }
    fun storagePath(id: Int?): NamedObject? = id?.let { i -> storagePathsById[i] }
}

private inline fun <T> firstById(list: List<T>, id: (T) -> Int): Map<Int, T> {
    val map = HashMap<Int, T>(list.size * 2)
    for (item in list) {
        val key = id(item)
        if (!map.containsKey(key)) map[key] = item
    }
    return map
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
