package com.vknxworks.paperctrl.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.paperctrl.MainActivity
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.queue.QueueItem

/**
 * Upload progress and results. Notification text never includes document contents; file names and
 * titles are private notification content, replaced by a bare title on a lock screen that hides
 * sensitive content.
 */
object Notifications {
    const val CH_UPLOADS = "uploads"
    const val CH_RESULTS = "upload_results"
    const val CH_DIGEST = "inbox_digest"
    const val CH_EVENTS = "paperless_events"

    /** Extra on MainActivity's intent: which screen to open. */
    const val EXTRA_OPEN = "open"
    const val OPEN_QUEUE = "queue"
    const val OPEN_SCAN = "scan"
    const val OPEN_INBOX = "inbox"
    const val OPEN_SEARCH = "search"
    /** Followed by the document id. */
    const val OPEN_DOCUMENT = "document:"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_UPLOADS, context.getString(R.string.channel_uploads), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_uploads_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULTS, context.getString(R.string.channel_results), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_results_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DIGEST, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_digest_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_EVENTS, context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_events_desc)
            },
        )
    }

    /** Opens MainActivity on [what] (one of the OPEN_ values). */
    fun open(context: Context, what: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, what)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The daily "documents in your inbox" reminder. */
    fun digest(context: Context, count: Int) {
        post(
            context,
            DIGEST_ID,
            NotificationCompat.Builder(context, CH_DIGEST)
                .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ctrl_accent_text))
                .setContentTitle(context.resources.getQuantityString(R.plurals.digest_title, count, count))
                .setContentText(context.getString(R.string.digest_body))
                .setAutoCancel(true)
                .setContentIntent(open(context, OPEN_INBOX, 2))
                .build(),
        )
    }

    /** A Paperless event pushed through UnifiedPush (a workflow webhook to ntfy). */
    fun event(context: Context, title: String, text: String?, documentId: Int?) {
        val id = documentId?.let { 200000 + it } ?: (300000 + (System.currentTimeMillis() % 10000).toInt())
        post(
            context,
            id,
            NotificationCompat.Builder(context, CH_EVENTS)
                .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ctrl_accent_text))
                .setContentTitle(title)
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(context, CH_EVENTS, title))
                .setAutoCancel(true)
                .setContentIntent(open(context, documentId?.let { OPEN_DOCUMENT + it } ?: OPEN_INBOX, id))
                .build(),
        )
    }

    private const val DIGEST_ID = 4242

    fun progressId(item: QueueItem) = 1000 + (item.id.hashCode() and 0xFFFF)
    private fun resultId(item: QueueItem) = 100000 + (item.id.hashCode() and 0xFFFF)

    private fun openQueue(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_QUEUE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Ongoing notification while a file uploads or Paperless processes it. [progress] null = indeterminate. */
    fun progress(context: Context, item: QueueItem, progress: Float?): Notification {
        ensureChannels(context)
        val text = if (progress != null) {
            context.getString(R.string.notif_uploading, item.fileName)
        } else {
            context.getString(R.string.notif_consuming, item.fileName)
        }
        return NotificationCompat.Builder(context, CH_UPLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ctrl_accent_text))
            .setContentTitle(context.getString(R.string.notif_upload_title))
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, CH_UPLOADS, context.getString(R.string.notif_upload_title)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setContentIntent(openQueue(context))
            .build()
    }

    fun added(context: Context, item: QueueItem, title: String?) {
        if (!context.container.settings.settings.value.notifyAdded) return
        post(
            context,
            resultId(item),
            NotificationCompat.Builder(context, CH_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ctrl_accent_text))
                .setContentTitle(context.getString(R.string.notif_added_title))
                .setContentText(title ?: item.fileName)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(context, CH_RESULTS, context.getString(R.string.notif_added_title)))
                .setAutoCancel(true)
                .setContentIntent(item.documentId?.let { open(context, OPEN_DOCUMENT + it, resultId(item)) } ?: openQueue(context))
                .build(),
        )
    }

    fun failed(context: Context, item: QueueItem, message: String?) {
        if (!context.container.settings.settings.value.notifyFailed) return
        val text = listOfNotNull(item.fileName, message).joinToString(": ")
        post(
            context,
            resultId(item),
            NotificationCompat.Builder(context, CH_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ctrl_accent_text))
                .setContentTitle(context.getString(R.string.notif_failed_title))
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(context, CH_RESULTS, context.getString(R.string.notif_failed_title)))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openQueue(context))
                .build(),
        )
    }

    /** Removes an item's progress and result notifications (its server was removed). */
    fun cancel(context: Context, item: QueueItem) {
        val nm = NotificationManagerCompat.from(context)
        runCatching {
            nm.cancel(progressId(item))
            nm.cancel(resultId(item))
        }
    }

    /**
     * What the lock screen shows when the user hides sensitive notification content: the title alone,
     * never a file name, document title or Paperless' message (which can name the document).
     */
    private fun publicVersion(context: Context, channel: String, title: String): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ctrl_accent_text))
            .setContentTitle(title)
            .build()

    private fun post(context: Context, id: Int, notification: Notification) {
        ensureChannels(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
