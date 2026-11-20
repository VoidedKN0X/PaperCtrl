package com.vknxworks.paperctrl.data.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.api.UploadMeta
import com.vknxworks.paperctrl.data.crypto.DataKeyStore
import com.vknxworks.paperctrl.data.crypto.SealedDataException
import com.vknxworks.paperctrl.data.crypto.SealedFile
import com.vknxworks.paperctrl.notify.Notifications
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
import java.util.concurrent.TimeUnit

@Serializable
enum class QueueStatus { WAITING, UPLOADING, CONSUMING, DONE, FAILED }

/** One file on its way to Paperless. Its payload lives in app-private storage until it's consumed. */
@Serializable
data class QueueItem(
    val id: String,
    val serverId: String,
    /** File name sent to Paperless (and its title when none is set). */
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val meta: UploadMeta = UploadMeta(),
    val status: QueueStatus = QueueStatus.WAITING,
    /** 0..1 while uploading. */
    val progress: Float = 0f,
    /** Set once the server accepted the file; from then on only its consumption is followed. */
    val taskId: String? = null,
    val documentId: Int? = null,
    val documentTitle: String? = null,
    /** Paperless' explanation when it failed, or why the app is waiting. */
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = status == QueueStatus.WAITING || status == QueueStatus.UPLOADING || status == QueueStatus.CONSUMING
    /** Metadata can still change: nothing has been sent yet. */
    val isEditable: Boolean get() = taskId == null && (status == QueueStatus.WAITING || status == QueueStatus.FAILED)
}

/**
 * The persistent upload queue. Items and their files survive app restarts and reboots; uploads run
 * through WorkManager, so they wait for a connection and resume on their own.
 *
 * The list (`queue.json`: file names, titles, details) and each waiting file of at most
 * [MAX_SEALED_PAYLOAD] are encrypted at rest ([SealedFile]) with the app's data key ([keys]). Ordinary
 * files from before are still read and are sealed the next time the app starts. The list is read in the
 * background ([loaded]); a sealed list that can't be opened turns [unreadable] on: nothing is written,
 * deleted or uploaded then, until it can be opened again or is set aside ([setAside]).
 */
class UploadQueue(private val context: Context, private val keys: DataKeyStore, scope: CoroutineScope) {
    private val dir = File(context.filesDir, "upload-queue").apply { mkdirs() }
    private val stagingDir = File(context.filesDir, "upload-staging").apply { mkdirs() }
    private val indexFile = File(dir, "queue.json")
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** False until the queue has been read from storage, which happens in the background when the app starts. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _unreadable = MutableStateFlow(false)
    /** The saved queue is encrypted and can't be opened (its key is gone, or the file is damaged). */
    val unreadable: StateFlow<Boolean> = _unreadable.asStateFlow()

    /** Waits until the queue has been read from storage. */
    suspend fun awaitLoaded() {
        _loaded.first { it }
    }

    fun get(id: String): QueueItem? = _items.value.firstOrNull { it.id == id }

    fun payload(id: String): File = File(File(dir, id), "payload")

    /** A fresh folder for files being prepared (copied from a share, merged into a PDF) before they're queued. */
    fun newStagingDir(): File = File(stagingDir, UUID.randomUUID().toString()).apply { mkdirs() }

    /** Moves [file] into the queue and starts uploading it as soon as the server can be reached. */
    suspend fun enqueue(serverId: String, file: File, fileName: String, mimeType: String, meta: UploadMeta): QueueItem {
        awaitLoaded()
        // A queue that can't be opened takes nothing new: the file stays where it is and the caller says it failed.
        if (_unreadable.value) throw IOException("the upload queue can't be opened")
        val id = UUID.randomUUID().toString()
        val size = withContext(Dispatchers.IO) {
            val target = payload(id)
            target.parentFile?.mkdirs()
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
            val length = target.length()
            // Sealed before anything can start uploading it; when that doesn't work it simply stays an ordinary file.
            keys.get(create = true)?.let { sealPayload(it, id) }
            length
        }
        val item = QueueItem(
            id = id,
            serverId = serverId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = size,
            meta = meta,
        )
        mutate { it + item }
        schedule(id, ExistingWorkPolicy.KEEP)
        return item
    }

    suspend fun update(id: String, transform: (QueueItem) -> QueueItem): QueueItem? {
        var result: QueueItem? = null
        mutate { list ->
            list.map {
                if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()).also { n -> result = n } else it
            }
        }
        return result
    }

    /** Progress updates are frequent: kept in memory only, without rewriting the index each time. */
    fun setProgress(id: String, progress: Float) {
        _items.value = _items.value.map { if (it.id == id) it.copy(progress = progress) else it }
    }

    suspend fun updateMeta(id: String, meta: UploadMeta) {
        update(id) { if (it.isEditable) it.copy(meta = meta) else it }
    }

    /** Tries a failed or waiting item again now. A failed upload is sent again from the start. */
    suspend fun retry(id: String) {
        awaitLoaded()
        val item = get(id) ?: return
        if (!payload(id).exists()) {
            update(id) { it.copy(status = QueueStatus.FAILED, message = context.getString(R.string.queue_file_missing)) }
            return
        }
        update(id) {
            val restart = it.status == QueueStatus.FAILED
            it.copy(
                status = if (restart || it.taskId == null) QueueStatus.WAITING else QueueStatus.CONSUMING,
                taskId = if (restart) null else it.taskId,
                progress = 0f,
                message = null,
            )
        }
        if (item.status != QueueStatus.DONE) schedule(id, ExistingWorkPolicy.REPLACE)
    }

    suspend fun remove(id: String) {
        awaitLoaded()
        if (_unreadable.value) return
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        withContext(Dispatchers.IO) { File(dir, id).deleteRecursively() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    /**
     * Drops everything queued for a server that was removed from the phone: its uploads stop, and
     * their files, details and notifications are deleted.
     */
    suspend fun removeServer(serverId: String) {
        awaitLoaded()
        val gone = _items.value.filter { it.serverId == serverId }
        if (gone.isEmpty()) return
        val ids = gone.map { it.id }.toSet()
        mutate { list -> list.filterNot { it.id in ids } }
        withContext(Dispatchers.IO) { gone.forEach { File(dir, it.id).deleteRecursively() } }
        // Last, because an upload worker calls this for its own item and is cancelled with its work.
        val work = WorkManager.getInstance(context)
        gone.forEach {
            work.cancelUniqueWork(workName(it.id))
            Notifications.cancel(context, it)
        }
    }

    suspend fun clearFinished() {
        awaitLoaded()
        val done = _items.value.filter { it.status == QueueStatus.DONE }
        withContext(Dispatchers.IO) { done.forEach { File(dir, it.id).deleteRecursively() } }
        mutate { list -> list.filterNot { it.status == QueueStatus.DONE } }
    }

    /**
     * The decrypted contents of [id]'s file when it's stored sealed, or null when it's an ordinary file
     * (large files are; the caller then sends the file itself). Throws [SealedDataException] when a sealed
     * file can't be opened. Reads the whole file: call it off the main thread.
     */
    fun sealedPayload(id: String): ByteArray? {
        val file = payload(id)
        if (!SealedFile.isSealed(file)) return null
        val key = keys.get(create = false) ?: throw SealedDataException("no key for the sealed file")
        return SealedFile.open(key, file.readBytes())
    }

    /** Seals [id]'s waiting file where it is, unless it's too large to hold in memory or already sealed. */
    private fun sealPayload(key: ByteArray, id: String) {
        val file = payload(id)
        if (!file.exists() || file.length() > MAX_SEALED_PAYLOAD || SealedFile.isSealed(file)) return
        SealedFile.sealInPlace(key, file, File(file.parentFile, "payload.seal.tmp"))
    }

    /** Deletes an item's file once Paperless has it: scans and shared files don't linger on the phone. */
    suspend fun dropPayload(id: String) = withContext(Dispatchers.IO) {
        payload(id).delete()
    }

    fun discardStaging(folder: File) {
        if (folder.parentFile == stagingDir) folder.deleteRecursively()
    }

    /** Uploads that found PaperCtrl locked (their token encrypted) and wait for the next unlock. */
    private val waitingForUnlock = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun waitForUnlock(id: String) { waitingForUnlock += id }

    /** Sends the uploads that were waiting for an unlock right away, instead of after WorkManager's back-off. */
    fun resumeAfterUnlock() {
        val ids = synchronized(waitingForUnlock) { waitingForUnlock.toList().also { waitingForUnlock.clear() } }
        ids.filter { get(it)?.isActive == true }.forEach { schedule(it, ExistingWorkPolicy.REPLACE) }
    }

    private fun schedule(id: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(UploadWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), policy, request)
    }

    private fun workName(id: String) = "upload-$id"

    private suspend fun mutate(transform: (List<QueueItem>) -> List<QueueItem>) {
        awaitLoaded()
        mutex.withLock {
            // Nothing is changed or written while the saved queue can't be opened.
            if (!_unreadable.value) {
                val updated = transform(_items.value)
                _items.value = updated
                withContext(Dispatchers.IO) { writeIndex(updated) }
            }
        }
    }

    /**
     * The saved queue, or null when it's a sealed file that can't be opened. An ordinary file that can't be
     * read counts as an empty queue, as it always did.
     */
    private fun readIndex(key: ByteArray?): List<QueueItem>? {
        if (!indexFile.exists()) return emptyList()
        val bytes = try {
            indexFile.readBytes()
        } catch (e: Exception) {
            return emptyList()
        }
        val plain = if (SealedFile.isSealed(bytes)) {
            if (key == null) return null
            try {
                SealedFile.open(key, bytes)
            } catch (e: SealedDataException) {
                return null
            }
        } else {
            bytes
        }
        return runCatching {
            ApiJson.decodeFromString(ListSerializer(QueueItem.serializer()), String(plain, Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    /**
     * Written to a temporary file first, so a crash mid-write never loses the queue. Sealed with the data key
     * when there is one; an ordinary file otherwise, as before.
     */
    private fun writeIndex(list: List<QueueItem>) {
        val json = ApiJson.encodeToString(ListSerializer(QueueItem.serializer()), list).toByteArray(Charsets.UTF_8)
        SealedFile.write(keys.get(create = true), indexFile, File(dir, "queue.json.tmp"), json)
    }

    /**
     * Reads the saved queue (in the background, once per app start, and again for "Try again").
     *
     * Finished items older than a week and folders of items that are no longer listed are removed, anything
     * that was mid-upload when the app stopped starts over, and every unfinished item gets its work scheduled
     * (WorkManager keeps it, this is a safety net). An ordinary `queue.json` and ordinary waiting files from
     * before encryption are sealed here, before any upload can start reading them.
     *
     * A sealed queue that can't be opened is left exactly as it is: nothing is cleaned up, written or scheduled.
     */
    private fun load() {
        val key = keys.get(create = true)
        val saved = readIndex(key)
        if (saved == null) {
            _items.value = emptyList()
            _unreadable.value = true
            return
        }
        _unreadable.value = false
        val now = System.currentTimeMillis()
        val fixed = saved
            .filterNot { it.status == QueueStatus.DONE && now - it.updatedAt > 7 * DAY_MS }
            .map { if (it.status == QueueStatus.UPLOADING) it.copy(status = QueueStatus.WAITING, progress = 0f) else it }
        _items.value = fixed
        runCatching {
            if (fixed != saved) {
                writeIndex(fixed)
            } else if (key != null && indexFile.exists() && !SealedFile.isSealed(indexFile)) {
                SealedFile.sealInPlace(key, indexFile, File(dir, "queue.json.seal.tmp"))
            }
        }
        val ids = fixed.map { it.id }.toSet()
        dir.listFiles()?.forEach { if (it.isDirectory && it.name !in ids) it.deleteRecursively() }
        if (key != null) fixed.forEach { sealPayload(key, it.id) }
        fixed.filter { it.isActive }.forEach { schedule(it.id, ExistingWorkPolicy.KEEP) }
    }

    /** "Try again": reads the saved queue once more, after the data key was asked for again. */
    suspend fun reload() {
        awaitLoaded()
        mutex.withLock {
            if (_unreadable.value) withContext(Dispatchers.IO) { runCatching { load() } }
        }
    }

    /**
     * "Start empty": moves the queue that can't be opened aside (to `upload-queue.unreadable-<time>` in the
     * app's private storage; it is kept, not deleted) and starts a new, empty queue. False when that didn't work.
     */
    suspend fun setAside(): Boolean {
        awaitLoaded()
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!_unreadable.value) {
                    true
                } else {
                    val aside = File(context.filesDir, "upload-queue.unreadable-${System.currentTimeMillis()}")
                    if (dir.renameTo(aside)) {
                        dir.mkdirs()
                        _items.value = emptyList()
                        _unreadable.value = false
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    // Last in the class, so everything above exists before the background read starts.
    init {
        // Leftover staging folders. This runs when the queue is first used in a process, before any share
        // screen can make a staging folder, so every folder still there belongs to a share that never
        // finished (the app was closed or killed) and is deleted right away.
        stagingDir.listFiles()?.forEach { it.deleteRecursively() }
        scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock { load() }
            } catch (e: Throwable) {
                // Reading failed in a way nothing above expected: better no queue shown than a wrong one written.
                _items.value = emptyList()
                _unreadable.value = indexFile.exists()
            } finally {
                _loaded.value = true
            }
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Waiting files up to this size are encrypted; larger ones would have to be held in memory twice to do it. */
        const val MAX_SEALED_PAYLOAD = 16L * 1024 * 1024
    }
}
