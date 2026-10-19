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
import com.vknxworks.paperctrl.notify.Notifications
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
 */
class UploadQueue(private val context: Context) {
    private val dir = File(context.filesDir, "upload-queue").apply { mkdirs() }
    private val stagingDir = File(context.filesDir, "upload-staging").apply { mkdirs() }
    private val indexFile = File(dir, "queue.json")
    private val mutex = Mutex()

    private val _items = MutableStateFlow(readIndex())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    init {
        cleanUp()
        // Anything that was mid-upload when the app stopped starts over; then make sure every
        // unfinished item has its work scheduled (WorkManager keeps it, this is a safety net).
        val fixed = _items.value.map { if (it.status == QueueStatus.UPLOADING) it.copy(status = QueueStatus.WAITING, progress = 0f) else it }
        if (fixed != _items.value) {
            _items.value = fixed
            writeIndex(fixed)
        }
        fixed.filter { it.isActive }.forEach { schedule(it.id, ExistingWorkPolicy.KEEP) }
    }

    fun get(id: String): QueueItem? = _items.value.firstOrNull { it.id == id }

    fun payload(id: String): File = File(File(dir, id), "payload")

    /** A fresh folder for files being prepared (copied from a share, merged into a PDF) before they're queued. */
    fun newStagingDir(): File = File(stagingDir, UUID.randomUUID().toString()).apply { mkdirs() }

    /** Moves [file] into the queue and starts uploading it as soon as the server can be reached. */
    suspend fun enqueue(serverId: String, file: File, fileName: String, mimeType: String, meta: UploadMeta): QueueItem {
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            val target = payload(id)
            target.parentFile?.mkdirs()
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
        val item = QueueItem(
            id = id,
            serverId = serverId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = payload(id).length(),
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
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        withContext(Dispatchers.IO) { File(dir, id).deleteRecursively() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    /**
     * Drops everything queued for a server that was removed from the phone: its uploads stop, and
     * their files, details and notifications are deleted.
     */
    suspend fun removeServer(serverId: String) {
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
        val done = _items.value.filter { it.status == QueueStatus.DONE }
        withContext(Dispatchers.IO) { done.forEach { File(dir, it.id).deleteRecursively() } }
        mutate { list -> list.filterNot { it.status == QueueStatus.DONE } }
    }

    /** Deletes an item's file once Paperless has it: scans and shared files don't linger on the phone. */
    suspend fun dropPayload(id: String) = withContext(Dispatchers.IO) {
        payload(id).delete()
    }

    fun discardStaging(folder: File) {
        if (folder.parentFile == stagingDir) folder.deleteRecursively()
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

    private suspend fun mutate(transform: (List<QueueItem>) -> List<QueueItem>) = mutex.withLock {
        val updated = transform(_items.value)
        _items.value = updated
        withContext(Dispatchers.IO) { writeIndex(updated) }
    }

    private fun readIndex(): List<QueueItem> = runCatching {
        if (!indexFile.exists()) emptyList() else ApiJson.decodeFromString(ListSerializer(QueueItem.serializer()), indexFile.readText())
    }.getOrDefault(emptyList())

    /** Written to a temporary file first, so a crash mid-write never loses the queue. */
    private fun writeIndex(list: List<QueueItem>) {
        val tmp = File(dir, "queue.json.tmp")
        tmp.writeText(ApiJson.encodeToString(ListSerializer(QueueItem.serializer()), list))
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            tmp.renameTo(indexFile)
        }
    }

    /**
     * Leftover staging folders and finished items older than a week. This runs once when the app's
     * process starts, before any share screen can make a staging folder, so every folder still there
     * belongs to a share that never finished (the app was closed or killed) and is deleted right away.
     */
    private fun cleanUp() {
        val now = System.currentTimeMillis()
        stagingDir.listFiles()?.forEach { it.deleteRecursively() }
        val keep = _items.value.filterNot { it.status == QueueStatus.DONE && now - it.updatedAt > 7 * DAY_MS }
        if (keep.size != _items.value.size) {
            _items.value = keep
            writeIndex(keep)
        }
        val ids = keep.map { it.id }.toSet()
        dir.listFiles()?.forEach { if (it.isDirectory && it.name !in ids) it.deleteRecursively() }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
