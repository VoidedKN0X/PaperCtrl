package com.vknxworks.paperctrl.data.files

import android.content.Context
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.crypto.DataKeyStore
import com.vknxworks.paperctrl.data.crypto.SealedDataException
import com.vknxworks.paperctrl.data.crypto.SealedFile
import com.vknxworks.paperctrl.data.queue.Staging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/** What is kept on the phone about one document: its details (with the OCR text) and which file was saved. */
@Serializable
data class OfflineEntry(
    val doc: Document,
    val fileName: String,
    val mimeType: String,
    /** True when the original file was kept rather than the searchable PDF. */
    val original: Boolean,
    /** When the copy was saved (milliseconds since 1970). */
    val savedAt: Long,
    val size: Long,
)

/** A kept document as the list shows it. [entry] is null when its details can't be opened any more. */
data class OfflineItem(val id: Int, val entry: OfflineEntry?)

/** The document is too large to keep encrypted on the phone. */
class OfflineTooLargeException : IOException("too large to keep offline")

/** The phone's secure hardware didn't give a key, so nothing can be kept encrypted. */
class OfflineNoKeyException : IOException("no data key")

/**
 * Documents the user chose to keep on the phone for reading without a connection, per server:
 *
 *     filesDir/offline/<server id>/<document id>.json   details and OCR text
 *     filesDir/offline/<server id>/<document id>.bin    the file itself
 *
 * Both are personal data and are **always** encrypted at rest ([SealedFile], the app's data key). Unlike the
 * upload queue there is no ordinary-file fallback: without a key, or when the sealed copy doesn't check out,
 * the document simply isn't kept. To look at a kept file it is decrypted into the same cache folder viewed
 * documents use (PdfRenderer needs a real file), which is emptied when the app starts and when it locks.
 *
 * A kept copy that can't be opened is never deleted or overwritten by the app on its own: it shows as
 * unreadable until the user removes it, and keeping the document again moves the old files aside first.
 */
class OfflineStore(private val context: Context, private val keys: DataKeyStore) {
    private val root = File(context.filesDir, "offline")

    /** "<server id>/<document id>" of every kept document; filled by [refresh]. */
    private val _kept = MutableStateFlow<Set<String>>(emptySet())
    val kept: StateFlow<Set<String>> = _kept.asStateFlow()

    private fun dir(serverId: String) = File(root, serverId.filter { it.isLetterOrDigit() || it == '-' }.take(36).ifEmpty { "server" })
    private fun meta(serverId: String, id: Int) = File(dir(serverId), "$id.json")
    private fun data(serverId: String, id: Int) = File(dir(serverId), "$id.bin")
    private fun key(serverId: String, id: Int) = "$serverId/$id"

    fun isKept(serverId: String, id: Int): Boolean = key(serverId, id) in _kept.value

    /** Reads which documents are kept (file names only; nothing is decrypted). */
    suspend fun refresh(serverIds: Collection<String>) = withContext(Dispatchers.IO) {
        val all = mutableSetOf<String>()
        serverIds.forEach { sid -> ids(sid).forEach { all += key(sid, it) } }
        _kept.value = all
    }

    private fun ids(serverId: String): List<Int> =
        dir(serverId).listFiles()?.mapNotNull { f -> f.name.removeSuffix(".json").takeIf { f.name.endsWith(".json") }?.toIntOrNull() }.orEmpty()

    /**
     * Keeps [doc] and its downloaded [file] on the phone, encrypted. Throws [OfflineTooLargeException] for a
     * file over [MAX_BYTES] and [OfflineNoKeyException] when it can't be encrypted.
     */
    suspend fun keep(serverId: String, doc: Document, file: LocalFile, original: Boolean): OfflineEntry = withContext(Dispatchers.IO) {
        if (file.file.length() > MAX_BYTES) throw OfflineTooLargeException()
        val k = keys.get(create = true) ?: throw OfflineNoKeyException()
        val folder = dir(serverId).apply { mkdirs() }
        val entry = OfflineEntry(doc, file.name, file.mimeType, original, System.currentTimeMillis(), file.file.length())
        val plain = try {
            file.file.readBytes()
        } catch (e: OutOfMemoryError) {
            throw OfflineTooLargeException()
        }
        val sealedData = try {
            SealedFile.sealChecked(k, plain)
        } catch (e: OutOfMemoryError) {
            throw OfflineTooLargeException()
        } ?: throw OfflineNoKeyException()
        val sealedMeta = SealedFile.sealChecked(k, ApiJson.encodeToString(OfflineEntry.serializer(), entry).toByteArray(Charsets.UTF_8))
            ?: throw OfflineNoKeyException()
        val dataFile = data(serverId, doc.id)
        val metaFile = meta(serverId, doc.id)
        // An older copy this key can't open isn't overwritten: it's moved aside.
        setAsideIfUnreadable(k, dataFile)
        setAsideIfUnreadable(k, metaFile)
        SealedFile.writeAtomic(dataFile, File(folder, "${doc.id}.bin.tmp"), sealedData)
        SealedFile.writeAtomic(metaFile, File(folder, "${doc.id}.json.tmp"), sealedMeta)
        _kept.value = _kept.value + key(serverId, doc.id)
        entry
    }

    private fun setAsideIfUnreadable(k: ByteArray, f: File) {
        if (!f.exists()) return
        val readable = try {
            SealedFile.read(k, f)
            true
        } catch (e: SealedDataException) {
            false
        } catch (e: OutOfMemoryError) {
            true
        }
        if (!readable) f.renameTo(File(f.path + ".unreadable-" + System.currentTimeMillis()))
    }

    /** The kept details of document [id], or null when it isn't kept or can't be opened. */
    suspend fun entry(serverId: String, id: Int): OfflineEntry? = withContext(Dispatchers.IO) { readEntry(serverId, id) }

    private fun readEntry(serverId: String, id: Int): OfflineEntry? {
        val f = meta(serverId, id)
        if (!f.exists()) return null
        return try {
            val k = keys.get(create = false) ?: return null
            // Only sealed files are ever written here; anything else isn't ours to trust.
            if (!SealedFile.isSealed(f)) return null
            ApiJson.decodeFromString(OfflineEntry.serializer(), String(SealedFile.read(k, f), Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    /** Every kept document of [serverId], newest copy first; unreadable ones last. */
    suspend fun list(serverId: String): List<OfflineItem> = withContext(Dispatchers.IO) {
        ids(serverId).map { OfflineItem(it, readEntry(serverId, it)) }
            .sortedWith(compareBy<OfflineItem> { it.entry == null }.thenByDescending { it.entry?.savedAt ?: 0L })
    }

    /**
     * The kept file of document [id], decrypted into the viewer's cache folder so it can be shown, shared or
     * opened. Throws when it isn't kept or can't be opened.
     */
    suspend fun open(serverId: String, id: Int): LocalFile = withContext(Dispatchers.IO) {
        val entry = readEntry(serverId, id) ?: throw IOException("not kept")
        val source = data(serverId, id)
        val k = keys.get(create = false) ?: throw IOException("no key")
        if (!SealedFile.isSealed(source)) throw IOException("not a sealed file")
        val target = File(File(context.cacheDir, "docs/offline").apply { mkdirs() }, "${dir(serverId).name}-$id-${entry.savedAt}")
        if (!target.exists() || target.length() != entry.size) {
            val plain = try {
                SealedFile.read(k, source)
            } catch (e: SealedDataException) {
                throw IOException("can't open the kept file", e)
            }
            val tmp = File(target.path + ".part")
            tmp.writeBytes(plain)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("can't prepare the kept file")
            }
        }
        LocalFile(target, Staging.safeName(entry.fileName, entry.mimeType), entry.mimeType)
    }

    /** The user took document [id] off the phone: its kept copy is deleted (the document on the server isn't touched). */
    suspend fun remove(serverId: String, id: Int) = withContext(Dispatchers.IO) {
        data(serverId, id).delete()
        meta(serverId, id).delete()
        _kept.value = _kept.value - key(serverId, id)
    }

    /** The server was removed from the app: everything kept for it goes too, like its queue and lists. */
    fun clear(serverId: String) {
        dir(serverId).deleteRecursively()
        _kept.value = _kept.value.filterNot { it.startsWith("$serverId/") }.toSet()
    }

    companion object {
        /** GCM needs the whole file in memory (several times over while it's checked), so larger files aren't kept. */
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}
