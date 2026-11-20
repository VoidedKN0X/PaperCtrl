package com.vknxworks.paperctrl.notify

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.paperctrl.MainActivity
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.documents
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Counts the documents in the inbox for the daily reminder. */
class InboxDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sessions.awaitReady()
        val session = c.sessions.active.value ?: return Result.success()
        return try {
            val md = c.metadata.load(session.server.id, session.client)
            val inbox = md.tags.filter { it.isInboxTag }.map { it.id }
            if (inbox.isEmpty()) return Result.success()
            val count = session.client.documents(mapOf("tags__id__in" to inbox.joinToString(","), "fields" to "id"), 1, 1).count
            if (c.settings.settings.value.inboxDigest && count > 0 && tags.contains(TAG_DIGEST)) Notifications.digest(applicationContext, count)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val TAG_DIGEST = "inbox-digest"
        private const val NAME_DIGEST = "inbox-digest"
        private const val NAME_LEGACY_WIDGET = "widget-refresh"
        private const val LEGACY_WIDGET_PREFS = "widget"

        /** Daily at about 9:00, when the reminder is on; otherwise cancelled. */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME_DIGEST)
                return
            }
            val now = LocalDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<InboxDigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG_DIGEST)
                .build()
            wm.enqueueUniquePeriodicWork(NAME_DIGEST, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * PaperCtrl no longer has a home screen widget: stops the background refresh an earlier version
         * scheduled for it every few hours, and deletes the inbox count that version saved for the widget.
         */
        fun cancelLegacyWidgetWork(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME_LEGACY_WIDGET) }
            runCatching { context.deleteSharedPreferences(LEGACY_WIDGET_PREFS) }
        }
    }
}

/** Long-press shortcuts on the launcher icon: Scan, Inbox, Search. */
object Shortcuts {
    fun publish(context: Context) {
        fun shortcut(id: String, label: Int, icon: Int, open: String) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN).putExtra(Notifications.EXTRA_OPEN, open))
            .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut("scan", R.string.shortcut_scan, R.drawable.ic_shortcut_scan, Notifications.OPEN_SCAN),
                    shortcut("inbox", R.string.shortcut_inbox, R.drawable.ic_shortcut_inbox, Notifications.OPEN_INBOX),
                    shortcut("search", R.string.shortcut_search, R.drawable.ic_shortcut_search, Notifications.OPEN_SEARCH),
                ),
            )
        }
    }
}
