package com.vknxworks.paperctrl.data.queue

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.ErrorKind
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.api.TaskState
import com.vknxworks.paperctrl.data.api.documentTitle
import com.vknxworks.paperctrl.data.api.postDocument
import com.vknxworks.paperctrl.data.api.task
import com.vknxworks.paperctrl.notify.Notifications
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Uploads one queued file and follows Paperless' consumption task until the document exists.
 * Network trouble means "try again later" (WorkManager backs off and waits for a connection);
 * a refusal from Paperless means the item failed, with Paperless' reason.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val c = context.container
    private val id: String get() = inputData.getString(KEY_ID).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val item = c.queue.get(id)
        val notification = if (item != null) Notifications.progress(applicationContext, item, null) else null
        return ForegroundInfo(
            item?.let { Notifications.progressId(it) } ?: 999,
            notification ?: Notifications.progress(applicationContext, placeholder(), null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    override suspend fun doWork(): Result {
        val start = c.queue.get(id) ?: return Result.success()
        if (!start.isActive) return Result.success()
        c.sessions.awaitReady()
        val client = c.sessions.clientFor(start.serverId)
            ?: return fail(start, applicationContext.getString(R.string.queue_server_removed))

        var item: QueueItem = start
        if (item.taskId == null) {
            val payload = c.queue.payload(item.id)
            if (!payload.exists()) return fail(item, applicationContext.getString(R.string.queue_file_missing))
            item = c.queue.update(item.id) { it.copy(status = QueueStatus.UPLOADING, progress = 0f, message = null) } ?: return Result.success()
            showProgress(item, 0f)
            val taskId = try {
                var lastShown = 0L
                client.postDocument(payload, item.fileName, item.mimeType, item.meta) { sent, total ->
                    val p = if (total > 0) sent.toFloat() / total else 0f
                    c.queue.setProgress(item.id, p)
                    val now = System.currentTimeMillis()
                    if (now - lastShown > 700) {
                        lastShown = now
                        runCatching { setForegroundAsync(ForegroundInfo(Notifications.progressId(item), Notifications.progress(applicationContext, item, p), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return handleError(item, e)
            }
            item = c.queue.update(item.id) { it.copy(status = QueueStatus.CONSUMING, taskId = taskId, progress = 1f) } ?: return Result.success()
        }
        showProgress(item, null)
        return follow(client, item)
    }

    /** Polls the consumption task: quickly at first, then less often, for up to [FOLLOW_MS]. */
    private suspend fun follow(client: PaperlessClient, start: QueueItem): Result {
        val taskId = start.taskId ?: return Result.retry()
        val until = System.currentTimeMillis() + FOLLOW_MS
        var wait = 1500L
        while (System.currentTimeMillis() < until) {
            val status = try {
                client.task(taskId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return handleError(start, e)
            }
            when (status?.state) {
                TaskState.SUCCESS -> {
                    val docId = status?.documentId
                    val title = docId?.let { runCatching { client.documentTitle(it) }.getOrNull() }
                    val done = c.queue.update(start.id) {
                        it.copy(status = QueueStatus.DONE, documentId = docId, documentTitle = title, progress = 1f, message = null)
                    } ?: start
                    c.queue.dropPayload(start.id)
                    Notifications.added(applicationContext, done, title)
                    return Result.success()
                }
                TaskState.FAILURE -> return fail(start, status?.message ?: applicationContext.getString(R.string.queue_consume_failed))
                else -> Unit
            }
            delay(wait)
            wait = (wait * 1.5).toLong().coerceAtMost(15_000)
        }
        // Still processing (a long OCR run, or a busy server): check again later without holding a worker.
        c.queue.update(start.id) { it.copy(message = applicationContext.getString(R.string.queue_still_processing)) }
        return Result.retry()
    }

    private suspend fun handleError(item: QueueItem, e: Exception): Result {
        val permanent = e is PaperlessException && e.kind in setOf(
            ErrorKind.BAD_TOKEN, ErrorKind.FORBIDDEN, ErrorKind.TOO_LARGE, ErrorKind.UNEXPECTED, ErrorKind.NOT_FOUND, ErrorKind.NOT_PAPERLESS,
            ErrorKind.CLEARTEXT_BLOCKED, ErrorKind.TOO_OLD,
        )
        if (permanent) return fail(item, e.friendlyMessage(applicationContext))
        // Offline, server down, certificate trouble, throttled, 5xx: wait and try again.
        val waiting = if (e is IOException) applicationContext.getString(R.string.queue_waiting_server) else e.friendlyMessage(applicationContext)
        c.queue.update(item.id) {
            it.copy(
                status = if (it.taskId == null) QueueStatus.WAITING else QueueStatus.CONSUMING,
                progress = 0f,
                message = waiting,
            )
        }
        return Result.retry()
    }

    private suspend fun fail(item: QueueItem, message: String): Result {
        val failed = c.queue.update(item.id) { it.copy(status = QueueStatus.FAILED, progress = 0f, message = message) } ?: item
        Notifications.failed(applicationContext, failed, message)
        return Result.success()
    }

    private suspend fun showProgress(item: QueueItem, progress: Float?) {
        runCatching {
            setForeground(ForegroundInfo(Notifications.progressId(item), Notifications.progress(applicationContext, item, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        }
    }

    private fun placeholder() = QueueItem(id = id, serverId = "", fileName = applicationContext.getString(R.string.app_name), mimeType = "", sizeBytes = 0)

    companion object {
        const val KEY_ID = "id"
        const val TAG = "upload"
        private const val FOLLOW_MS = 10 * 60 * 1000L
    }
}
