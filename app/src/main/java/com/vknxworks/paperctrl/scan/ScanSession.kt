package com.vknxworks.paperctrl.scan

import android.content.Context
import android.graphics.Bitmap
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.crypto.DataKeyStore
import com.vknxworks.paperctrl.data.crypto.SealedFile
import com.vknxworks.paperctrl.data.queue.ImagePdf
import com.vknxworks.paperctrl.data.queue.JpegPage
import com.vknxworks.paperctrl.data.queue.PdfQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException
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
 *
 * The photos and the processed previews are encrypted at rest ([SealedFile]) with the app's data key
 * ([keys]) and only ever decrypted in memory; ordinary files from before are still read and are sealed
 * when the app starts. `pages.json` (page order, corners, filter) holds nothing of the pages themselves
 * and stays an ordinary file. Sealed photos that can't be opened (the key is gone) turn [unreadable] on:
 * nothing is added, changed or deleted then, until they can be opened again or are set aside ([setAside]).
 */
class ScanSession(private val context: Context, private val keys: DataKeyStore, scope: CoroutineScope) {
    private val dir = File(context.filesDir, "scan-session").apply { mkdirs() }
    private val index = File(dir, "pages.json")
    private val mutex = Mutex()

    private val _pages = MutableStateFlow(read())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    private val _unreadable = MutableStateFlow(false)
    /** The scanned pages are encrypted and can't be opened (their key is gone). */
    val unreadable: StateFlow<Boolean> = _unreadable.asStateFlow()

    /** Set once the start-up check (can the sealed photos be opened? seal the ordinary ones) is done. */
    private val checked = MutableStateFlow(false)

    private fun photo(id: String) = File(dir, "$id.jpg")
    private fun preview(page: ScanPage) = File(dir, "${page.id}.p${page.version}.jpg")

    /** A photo or preview from storage, decrypted when it's stored sealed. Throws when it can't be opened. */
    private fun readImage(file: File): ByteArray = SealedFile.read(keys.get(create = true), file)

    /** Stores a photo or preview: sealed when there's a data key, as an ordinary file otherwise. */
    private fun writeImage(file: File, jpeg: ByteArray) {
        SealedFile.write(keys.get(create = true), file, File(dir, "${file.name}.tmp"), jpeg)
    }

    /** True when pages can be added, changed and deleted: always, except while sealed photos can't be opened. */
    private suspend fun writable(): Boolean {
        checked.first { it }
        return !_unreadable.value
    }

    /** The captured photo of page [id], decoded with its longest side at most [maxSide]. Off the main thread. */
    fun decodePhoto(id: String, maxSide: Int): Bitmap = ImageOps.decode(readImage(photo(id)), maxSide)

    /**
     * Adds a photo (already upright) as a page, or replaces [replaceId]'s photo. The photo is scaled
     * down to at most [MAX_PHOTO_SIDE] pixels: phone cameras capture far more than a page needs.
     */
    suspend fun addPhoto(source: File, filter: ScanFilter, replaceId: String? = null): ScanPage = withContext(Dispatchers.Default) {
        if (!writable()) throw IOException("the scanned pages can't be opened")
        val bitmap = ImageOps.decode(source, MAX_PHOTO_SIDE)
        val corners = ImageOps.detect(bitmap) ?: ImageOps.FULL_PAGE
        val id = replaceId ?: UUID.randomUUID().toString()
        val jpeg = ImageOps.jpegBytes(bitmap, 90)
        bitmap.recycle()
        withContext(Dispatchers.IO) { writeImage(photo(id), jpeg) }
        val existing = replaceId?.let { r -> _pages.value.firstOrNull { it.id == r } }
        val page = (existing ?: ScanPage(id = id, filter = filter)).let {
            it.copy(corners = corners, rotation = 0, version = it.version + 1)
        }
        mutate { list -> if (existing != null) list.map { if (it.id == id) page else it } else list + page }
        page
    }

    suspend fun update(page: ScanPage) = mutate { list -> list.map { if (it.id == page.id) page.copy(version = page.version + 1) else it } }

    suspend fun remove(id: String) {
        if (!writable()) return
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
        if (!writable()) return
        mutate { emptyList() }
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { if (it != index) it.delete() } }
    }

    /**
     * A small processed image of [page] (a JPEG, in memory) for lists and the editor. It's kept in storage
     * per page version, sealed like the photos.
     */
    suspend fun previewImage(page: ScanPage, maxSide: Int = 900): ByteArray? = withContext(Dispatchers.Default) {
        val out = preview(page)
        if (out.exists()) {
            // A stored preview that can't be read is only a cache: it's drawn again below.
            val stored = runCatching { withContext(Dispatchers.IO) { readImage(out) } }.getOrNull()
            if (stored != null) return@withContext stored
        }
        val src = photo(page.id)
        if (!src.exists()) return@withContext null
        runCatching {
            val bitmap = ImageOps.decode(readImage(src), maxSide * 2)
            val rendered = ImageOps.render(bitmap, page.corners, page.filter, page.rotation, maxSide)
            bitmap.recycle()
            val jpeg = ImageOps.jpegBytes(rendered, 80)
            rendered.recycle()
            withContext(Dispatchers.IO) {
                dir.listFiles()?.filter { it.name.startsWith("${page.id}.p") }?.forEach { it.delete() }
                writeImage(out, jpeg)
            }
            jpeg
        }.getOrNull()
    }

    /** Builds the PDF of all pages into [target], one page at a time. */
    suspend fun writePdf(target: File, quality: PdfQuality) = withContext(Dispatchers.Default) {
        val list = _pages.value
        ImagePdf.write(target, list.size, quality) { i -> renderPage(list[i], quality) }
    }

    private fun renderPage(page: ScanPage, quality: PdfQuality): JpegPage {
        val bitmap = ImageOps.decode(readImage(photo(page.id)), MAX_PHOTO_SIDE)
        val rendered: Bitmap = ImageOps.render(bitmap, page.corners, page.filter, page.rotation, quality.maxSidePx)
        bitmap.recycle()
        return ImagePdf.pageFromBitmap(rendered, quality).also { rendered.recycle() }
    }

    private suspend fun mutate(transform: (List<ScanPage>) -> List<ScanPage>) {
        if (!writable()) return
        mutex.withLock {
            val updated = transform(_pages.value)
            _pages.value = updated
            withContext(Dispatchers.IO) {
                val tmp = File(dir, "pages.json.tmp")
                tmp.writeText(ApiJson.encodeToString(ListSerializer(ScanPage.serializer()), updated))
                if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
            }
        }
    }

    private fun read(): List<ScanPage> = runCatching {
        if (!index.exists()) emptyList()
        else ApiJson.decodeFromString(ListSerializer(ScanPage.serializer()), index.readText()).filter { photo(it.id).exists() }
    }.getOrDefault(emptyList())

    /**
     * Can the sealed photos be opened? Without a data key none can; with one, the first sealed photo is
     * tried (a key that opens one opens them all). When they can, the ordinary photos and previews left from
     * before encryption are sealed where they are, one checked file at a time.
     */
    private fun check() {
        val key = keys.get(create = true)
        val sealed = _pages.value.map { photo(it.id) }.filter { SealedFile.isSealed(it) }
        val bad = sealed.isNotEmpty() && (key == null || runCatching { SealedFile.open(key, sealed.first().readBytes()) }.isFailure)
        _unreadable.value = bad
        if (!bad && key != null) {
            dir.listFiles()?.filter { it.name.endsWith(".jpg") && !SealedFile.isSealed(it) }?.forEach {
                SealedFile.sealInPlace(key, it, File(dir, "${it.name}.seal.tmp"))
            }
        }
    }

    /** "Try again": checks once more whether the sealed photos can be opened, after the data key was asked for again. */
    suspend fun reload() {
        checked.first { it }
        if (_unreadable.value) withContext(Dispatchers.IO) { runCatching { check() } }
    }

    /**
     * "Start empty": moves the pages that can't be opened aside (to `scan-session.unreadable-<time>` in the
     * app's private storage; they are kept, not deleted) and starts a new, empty scan. False when that didn't work.
     */
    suspend fun setAside(): Boolean {
        checked.first { it }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!_unreadable.value) {
                    true
                } else {
                    val aside = File(context.filesDir, "scan-session.unreadable-${System.currentTimeMillis()}")
                    if (dir.renameTo(aside)) {
                        dir.mkdirs()
                        _pages.value = emptyList()
                        _unreadable.value = false
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    // Last in the class, so everything above exists before the background check starts.
    init {
        scope.launch(Dispatchers.IO) {
            try {
                check()
            } catch (e: Throwable) {
                // Not sure the sealed photos can be opened: leave them alone.
                _unreadable.value = runCatching { _pages.value.any { SealedFile.isSealed(photo(it.id)) } }.getOrDefault(false)
            } finally {
                checked.value = true
            }
        }
    }

    companion object {
        const val MAX_PHOTO_SIDE = 3000
    }
}
