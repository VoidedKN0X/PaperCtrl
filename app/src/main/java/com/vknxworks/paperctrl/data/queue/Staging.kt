package com.vknxworks.paperctrl.data.queue

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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

    /** Largest file PaperCtrl copies in from a share; anything bigger is skipped. */
    const val MAX_FILE_BYTES = 1024L * 1024 * 1024

    /** Most files taken from one share. */
    const val MAX_FILES = 100

    /** Copying stops when the phone would be left with less free space than this. */
    private const val MIN_FREE_BYTES = 256L * 1024 * 1024
    private const val SPACE_CHECK_BYTES = 8L * 1024 * 1024

    /**
     * Copies shared or picked files into [dir]. Shared links are only readable for a short time, so
     * everything is copied straight away. Only content:// URIs from another app are accepted: a
     * file:// URI or one of this app's own providers could point at its private files (other queued
     * documents, the saved token), which another app could otherwise get uploaded on its behalf.
     * Files over [MAX_FILE_BYTES], or that would nearly fill the phone, are skipped.
     */
    suspend fun copyIn(context: Context, uris: List<Uri>, dir: File): List<StagedFile> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        uris.distinct().filter { isForeignContentUri(context, it) }.take(MAX_FILES).mapNotNull { uri ->
            val target = File(dir, UUID.randomUUID().toString())
            runCatching {
                val mime = resolver.getType(uri)?.lowercase() ?: guessMime(displayName(resolver, uri))
                val name = safeName(displayName(resolver, uri) ?: "document", mime)
                val copied = resolver.openInputStream(uri)?.use { input -> target.outputStream().use { copyBounded(input, it, dir) } }
                if (copied != true) {
                    target.delete()
                    return@runCatching null
                }
                StagedFile(target, name, mime)
            }.getOrElse {
                target.delete()
                null
            }
        }
    }

    /**
     * Copies at most [MAX_FILE_BYTES], checking free space as it goes. False (with a partial file
     * the caller deletes) when the file is too large or the phone is running out of space.
     */
    private fun copyBounded(input: InputStream, output: OutputStream, dir: File): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var sinceCheck = SPACE_CHECK_BYTES // check before the first write
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > MAX_FILE_BYTES) return false
            sinceCheck += read
            if (sinceCheck >= SPACE_CHECK_BYTES) {
                if (dir.usableSpace < MIN_FREE_BYTES) return false
                sinceCheck = 0
            }
            output.write(buffer, 0, read)
        }
    }

    /**
     * Whether a URI another app handed over is safe to read: only content:// from another app's
     * provider, never file:// and never a provider that belongs to PaperCtrl itself.
     */
    fun isForeignContentUri(context: Context, uri: Uri): Boolean {
        if (!uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return false
        // "content://0@<authority>" still reaches <authority> (the number is a user id), so look past it.
        val authority = uri.authority?.substringAfterLast('@')?.takeIf { it.isNotBlank() } ?: return false
        if (authority.startsWith(context.packageName)) return false
        // Whatever its name, a provider that belongs to this app is never "another app's".
        val owner = runCatching {
            context.packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0))?.packageName
        }.getOrNull()
        return owner != context.packageName
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
