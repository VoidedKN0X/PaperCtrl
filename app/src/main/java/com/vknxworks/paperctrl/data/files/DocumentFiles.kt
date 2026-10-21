package com.vknxworks.paperctrl.data.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.Page
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.api.errorFor
import com.vknxworks.paperctrl.data.queue.Staging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** A document file kept on the phone for viewing: always in app-private cache storage. */
data class LocalFile(val file: File, val name: String, val mimeType: String)

/**
 * Downloads documents into app-private cache storage for viewing, and copies them out only when
 * the user saves or shares them. Cached files are deleted when the app starts, when a server is removed and,
 * with the app lock on, every time PaperCtrl locks.
 */
class DocumentFiles(private val context: Context) {
    private val root = File(context.cacheDir, "docs")

    private fun dirFor(client: PaperlessClient) = File(root, hash(client.baseUrl)).apply { mkdirs() }

    /**
     * The document's file: the archived (searchable PDF) version, or the original. Downloaded once
     * per app run; [onProgress] reports 0..1 while downloading.
     */
    suspend fun fetch(client: PaperlessClient, doc: Document, original: Boolean, onProgress: (Float) -> Unit = {}): LocalFile =
        withContext(Dispatchers.IO) {
            val useOriginal = original || !doc.hasArchive
            val name = if (useOriginal) doc.originalFileName ?: "document-${doc.id}" else (doc.archivedFileName ?: "document-${doc.id}.pdf")
            val mime = if (useOriginal) doc.mimeType ?: "application/octet-stream" else "application/pdf"
            val target = File(dirFor(client), "${doc.id}-${if (useOriginal) "o" else "a"}-${hash(doc.modified ?: "")}")
            if (target.exists() && target.length() > 0) return@withContext LocalFile(target, Staging.safeName(name, mime), mime)
            val url = client.url("documents/${doc.id}/download/", if (useOriginal) mapOf("original" to "true") else emptyMap())
            client.http.newCall(client.request(url, accept = "*/*").get().build()).execute().use { resp ->
                if (!resp.isSuccessful) throw errorFor(resp.code, resp.body?.string().orEmpty())
                val body = resp.body ?: throw PaperlessException(com.vknxworks.paperctrl.data.api.ErrorKind.UNEXPECTED)
                val total = body.contentLength()
                val tmp = File(target.path + ".part")
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
                tmp.renameTo(target)
            }
            LocalFile(target, Staging.safeName(name, mime), mime)
        }

    /** Saves a copy to the phone's Downloads folder (Downloads/PaperCtrl). Returns its name. */
    suspend fun saveToDownloads(file: LocalFile): String = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, file.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PaperCtrl")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Couldn't create the file in Downloads")
        resolver.openOutputStream(uri)?.use { out -> file.file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        file.name
    }

    /** A content:// link other apps can read once, for sharing or "Open with…". */
    fun shareUri(file: LocalFile): Uri {
        val shared = File(context.cacheDir, "shared").apply { mkdirs() }
        shared.listFiles()?.forEach { it.delete() }
        val copy = File(shared, file.name)
        file.file.copyTo(copy, overwrite = true)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
    }

    fun shareIntent(file: LocalFile): Intent = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, shareUri(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun openWithIntent(file: LocalFile): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(shareUri(file), file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Removes every cached document file (on start-up, when a server is removed, and when the app lock locks). */
    fun clear() {
        root.deleteRecursively()
        File(context.cacheDir, "shared").deleteRecursively()
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}

/**
 * The first page of the document list and the inbox per server, kept in app-private storage so
 * they can be browsed without a connection. Only titles and IDs of labels, never contents.
 */
class ListCache(context: Context) {
    private val root = File(context.filesDir, "list-cache").apply { mkdirs() }

    private fun file(serverId: String, key: String) =
        File(root, "${serverId.take(36)}-${MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(8).joinToString("") { "%02x".format(it) }}.json")

    fun save(serverId: String, key: String, page: Page<Document>) {
        runCatching { file(serverId, key).writeText(ApiJson.encodeToString(Page.serializer(Document.serializer()), page.copy(results = page.results.map { it.copy(content = null) }))) }
    }

    fun load(serverId: String, key: String): Page<Document>? = runCatching {
        val f = file(serverId, key)
        if (!f.exists()) null else ApiJson.decodeFromString(Page.serializer(Document.serializer()), f.readText())
    }.getOrNull()

    fun clear(serverId: String) {
        root.listFiles()?.filter { it.name.startsWith(serverId.take(36)) }?.forEach { it.delete() }
    }
}
