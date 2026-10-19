package com.vknxworks.paperctrl.data.queue

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A file copied into app-private storage, ready to queue. */
data class StagedFile(
    val file: File,
    val name: String,
    val mimeType: String,
) {
    val size: Long get() = file.length()
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

object Staging {

    /**
     * Copies shared or picked files into [dir]. Shared links are only readable for a short time, so
     * everything is copied straight away. Only content:// URIs are accepted: a file:// URI could point
     * at this app's own private files.
     */
    suspend fun copyIn(context: Context, uris: List<Uri>, dir: File): List<StagedFile> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        uris.distinct().filter { it.scheme == ContentResolver.SCHEME_CONTENT }.mapNotNull { uri ->
            runCatching {
                val mime = resolver.getType(uri)?.lowercase() ?: guessMime(displayName(resolver, uri))
                val name = safeName(displayName(resolver, uri) ?: "document", mime)
                val target = File(dir, UUID.randomUUID().toString())
                resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: return@runCatching null
                StagedFile(target, name, mime)
            }.getOrNull()
        }
    }

    /** Text shared without a file (a note, a copied message) becomes a .txt document. */
    suspend fun fromText(dir: File, text: String, title: String?): StagedFile = withContext(Dispatchers.IO) {
        val target = File(dir, UUID.randomUUID().toString())
        target.writeText(text)
        val base = title?.takeIf { it.isNotBlank() } ?: "Shared text ${timestamp()}"
        StagedFile(target, safeName(base, "text/plain"), "text/plain")
    }

    /** Merges images into one PDF named "<prefix> <date time>.pdf". The images' own files are kept. */
    suspend fun mergeImages(dir: File, images: List<StagedFile>, prefix: String, quality: PdfQuality = PdfQuality()): StagedFile =
        withContext(Dispatchers.IO) {
            val target = File(dir, UUID.randomUUID().toString())
            ImagePdf.write(target, images.size, quality) { i -> ImagePdf.pageFromImage(images[i].file, quality) }
            StagedFile(target, "$prefix ${timestamp()}.pdf", "application/pdf")
        }

    fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"))

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun guessMime(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** A plain file name with the right extension, without path separators or control characters. */
    fun safeName(raw: String, mime: String): String {
        var name = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimStart('.').take(150)
        if (name.isEmpty()) name = "document"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (ext != null && !name.contains('.')) name = "$name.$ext"
        return name
    }
}
