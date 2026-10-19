package com.vknxworks.paperctrl

import android.content.Context
import com.vknxworks.paperctrl.data.auth.SessionManager
import com.vknxworks.paperctrl.data.metadata.MetadataRepository
import com.vknxworks.paperctrl.data.queue.UploadQueue
import com.vknxworks.paperctrl.data.net.CleartextPolicy
import com.vknxworks.paperctrl.data.net.RedirectGuard
import com.vknxworks.paperctrl.data.settings.SettingsRepository
import com.vknxworks.paperctrl.data.telemetry.Telemetry
import com.vknxworks.paperctrl.scan.ScanSession
import com.vknxworks.paperctrl.data.files.DocumentFiles
import com.vknxworks.paperctrl.data.files.ListCache
import com.vknxworks.paperctrl.data.search.DocumentQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Base HTTP client. Per-server clients (with that server's pinned certificate) are derived
     * from it and share its connection pool. No logging interceptor: requests carry API tokens.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addNetworkInterceptor(CleartextPolicy.interceptor)
        .addNetworkInterceptor(RedirectGuard.interceptor)
        .build()

    val settings = SettingsRepository(context, appScope)
    val telemetry = Telemetry(context, settings, appScope)
    val sessions = SessionManager(context, http, appScope)
    val metadata = MetadataRepository()
    val queue = UploadQueue(context)
    val scan = ScanSession(context)
    val files = DocumentFiles(context)
    val listCache = ListCache(context)

    init {
        // Viewed documents are only kept while the app runs.
        appScope.launch(Dispatchers.IO) { files.clear() }
        // Uploads left behind for a server that's no longer saved (removed before PaperCtrl 0.1.3 deleted
        // them along with it): drop them and their files. Skipped when no server is saved at all, so a
        // settings read that failed can never wipe the queue.
        appScope.launch {
            sessions.awaitReady()
            val known = sessions.servers.value.map { it.id }.toSet()
            if (known.isNotEmpty()) {
                queue.items.value.map { it.serverId }.distinct().filterNot { it in known }.forEach { queue.removeServer(it) }
            }
        }
    }

    /**
     * Forgets a server and everything PaperCtrl kept for it on this phone: its token, its queued
     * uploads with their files and notifications, and the cached tags and correspondents.
     */
    suspend fun removeServer(id: String) {
        sessions.remove(id)
        queue.removeServer(id)
        metadata.invalidate(id)
        listCache.clear(id)
        files.clear()
    }

    /** A screen to open, set from a notification tap; the main screen consumes it. */
    val pendingOpen = MutableStateFlow<String?>(null)

    /** A search to show in the Documents tab (from Organise, "More like this", a shortcut). */
    val pendingQuery = MutableStateFlow<DocumentQuery?>(null)
}

val Context.container: AppContainer
    get() = (applicationContext as PaperCtrlApp).container
