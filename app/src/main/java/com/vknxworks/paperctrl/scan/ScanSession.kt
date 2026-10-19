package com.vknxworks.paperctrl.scan

import android.content.Context
import android.graphics.Bitmap
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.queue.ImagePdf
import com.vknxworks.paperctrl.data.queue.JpegPage
import com.vknxworks.paperctrl.data.queue.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** One scanned page: the captured photo and how to turn it into a clean page. */
@Serializable
data class ScanPage(
    val id: String,
    /** Corners of the page in the photo; [ImageOps.FULL_PAGE] for the whole photo. */
    val corners: List<Corner> = ImageOps.FULL_PAGE,
    val rotation: Int = 0,
    val filter: ScanFilter = ScanFilter.ENHANCED,
    /** Bumped on every change, so previews know to redraw. */
    val version: Int = 0,
)

/**
 * The document being scanned: its pages, in order. Photos live in app-private storage and survive
 * the app being closed; they're deleted once the document is queued for upload or discarded.
 */
class ScanSession(context: Context) {
    private val dir = File(context.filesDir, "scan-session").apply { mkdirs() }
    private val index = File(dir, "pages.json")
    private val mutex = Mutex()

    private val _pages = MutableStateFlow(read())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    fun photo(id: String) = File(dir, "$id.jpg")
    private fun preview(page: ScanPage) = File(dir, "${page.id}.p${page.version}.jpg")

    /**
     * Adds a photo (already upright) as a page, or replaces [replaceId]'s photo. The photo is scaled
     * down to at most [MAX_PHOTO_SIDE] pixels: phone cameras capture far more than a page needs.
     */
    suspend fun addPhoto(source: File, filter: ScanFilter, replaceId: String? = null): ScanPage = withContext(Dispatchers.Default) {
        val bitmap = ImageOps.decode(source, MAX_PHOTO_SIDE)
        val corners = ImageOps.detect(bitmap) ?: ImageOps.FULL_PAGE
        val id = replaceId ?: UUID.randomUUID().toString()
        withContext(Dispatchers.IO) { ImageOps.saveJpeg(bitmap, photo(id), 90) }
        bitmap.recycle()
        val existing = replaceId?.let { r -> _pages.value.firstOrNull { it.id == r } }
        val page = (existing ?: ScanPage(id = id, filter = filter)).let {
            it.copy(corners = corners, rotation = 0, version = it.version + 1)
        }
        mutate { list -> if (existing != null) list.map { if (it.id == id) page else it } else list + page }
        page
    }

    suspend fun update(page: ScanPage) = mutate { list -> list.map { if (it.id == page.id) page.copy(version = page.version + 1) else it } }

    suspend fun remove(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
        withContext(Dispatchers.IO) { dir.listFiles()?.filter { it.name.startsWith(id) }?.forEach { it.delete() } }
    }

    suspend fun reorder(ids: List<String>) = mutate { list ->
        val byId = list.associateBy { it.id }
        ids.mapNotNull { byId[it] } + list.filterNot { it.id in ids }
    }

    suspend fun applyFilterToAll(filter: ScanFilter) = mutate { list -> list.map { it.copy(filter = filter, version = it.version + 1) } }

    /** Deletes every page and photo. */
    suspend fun clear() {
        mutate { emptyList() }
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { if (it != index) it.delete() } }
    }

    /** A small processed image of [page] for lists and the editor, cached per page version. */
    suspend fun previewFile(page: ScanPage, maxSide: Int = 900): File? = withContext(Dispatchers.Default) {
        val out = preview(page)
        if (out.exists()) return@withContext out
        val src = photo(page.id)
        if (!src.exists()) return@withContext null
        runCatching {
            val bitmap = ImageOps.decode(src, maxSide * 2)
            val rendered = ImageOps.render(bitmap, page.corners, page.filter, page.rotation, maxSide)
            bitmap.recycle()
            withContext(Dispatchers.IO) {
                dir.listFiles()?.filter { it.name.startsWith("${page.id}.p") }?.forEach { it.delete() }
                ImageOps.saveJpeg(rendered, out, 80)
            }
            rendered.recycle()
            out
        }.getOrNull()
    }

    /** Builds the PDF of all pages into [target], one page at a time. */
    suspend fun writePdf(target: File, quality: PdfQuality) = withContext(Dispatchers.Default) {
        val list = _pages.value
        ImagePdf.write(target, list.size, quality) { i -> renderPage(list[i], quality) }
    }

    private fun renderPage(page: ScanPage, quality: PdfQuality): JpegPage {
        val bitmap = ImageOps.decode(photo(page.id), MAX_PHOTO_SIDE)
        val rendered: Bitmap = ImageOps.render(bitmap, page.corners, page.filter, page.rotation, quality.maxSidePx)
        bitmap.recycle()
        return ImagePdf.pageFromBitmap(rendered, quality).also { rendered.recycle() }
    }

    private suspend fun mutate(transform: (List<ScanPage>) -> List<ScanPage>) = mutex.withLock {
        val updated = transform(_pages.value)
        _pages.value = updated
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "pages.json.tmp")
            tmp.writeText(ApiJson.encodeToString(ListSerializer(ScanPage.serializer()), updated))
            if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
        }
    }

    private fun read(): List<ScanPage> = runCatching {
        if (!index.exists()) emptyList()
        else ApiJson.decodeFromString(ListSerializer(ScanPage.serializer()), index.readText()).filter { photo(it.id).exists() }
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_PHOTO_SIDE = 3000
    }
}
