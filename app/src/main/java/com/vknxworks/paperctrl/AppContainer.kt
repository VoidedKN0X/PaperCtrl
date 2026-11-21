package com.vknxworks.paperctrl

import android.content.Context
import com.vknxworks.paperctrl.data.auth.SessionManager
import com.vknxworks.paperctrl.data.crypto.DataKeyStore
import com.vknxworks.paperctrl.data.crypto.KeystoreWrapper
import com.vknxworks.paperctrl.data.metadata.MetadataRepository
import com.vknxworks.paperctrl.data.queue.UploadQueue
import com.vknxworks.paperctrl.data.net.CleartextPolicy
import com.vknxworks.paperctrl.data.net.RedirectGuard
import com.vknxworks.paperctrl.data.settings.SettingsRepository
import com.vknxworks.paperctrl.scan.ScanSession
import com.vknxworks.paperctrl.data.files.DocumentFiles
import com.vknxworks.paperctrl.data.files.ListCache
import com.vknxworks.paperctrl.data.files.OfflineStore
import com.vknxworks.paperctrl.data.search.DocumentQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vknxworks.paperctrl.data.lock.LockVault
import com.vknxworks.paperctrl.ui.lock.AppLock
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

    /** The app lock's PIN, fingerprint key and the key that encrypts server tokens while locked. */
    val vault = LockVault(context, "paperctrl")
    val sessions = SessionManager(context, http, vault, appScope)
    val metadata = MetadataRepository()

    /**
     * The key that encrypts the upload queue, the scanned pages and the offline lists at rest. Nothing is
     * read here: the key is unwrapped (by the Android Keystore) the first time it's asked for, which the
     * queue, the scan session and the list cache only do on background threads.
     */
    val dataKeys = DataKeyStore(context.filesDir, KeystoreWrapper(DATA_KEY_ALIAS))

    // These four read their folders and saved lists from storage when they're created. They're created on
    // first use, and the start-up job below creates them on a background thread right away, so opening
    // PaperCtrl doesn't wait for storage on the main thread. Whoever needs one earlier simply gets it then.
    // The queue's list and the check of the scanned pages need the data key: both happen in the background
    // after creation (UploadQueue.loaded, ScanSession.unreadable), never on the thread that creates them.
    val queue: UploadQueue by lazy { UploadQueue(context, dataKeys, appScope) }
    val scan: ScanSession by lazy { ScanSession(context, dataKeys, appScope) }
    val files: DocumentFiles by lazy { DocumentFiles(context) }
    val listCache: ListCache by lazy { ListCache(context, dataKeys) }

    /** Documents kept on the phone for offline reading, always encrypted with the data key. */
    val offline: OfflineStore by lazy { OfflineStore(context, dataKeys) }

    init {
        appScope.launch(Dispatchers.IO) {
            // Viewed documents are only kept while the app runs.
            runCatching { files.clear() }
            // Unfinished uploads get their work scheduled again, as before; the rest is just read ahead.
            runCatching { queue }
            runCatching { scan }
            runCatching { listCache.sealOrdinaryFiles() }
        }
        // Uploads left behind for a server that's no longer saved (removed before PaperCtrl 0.1.3 deleted
        // them along with it): drop them and their files. Skipped when no server is saved at all, so a
        // settings read that failed can never wipe the queue.
        appScope.launch {
            sessions.awaitReady()
            queue.awaitLoaded()
            val known = sessions.servers.value.map { it.id }.toSet()
            runCatching { offline.refresh(known) }
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
        offline.clear(id)
        files.clear()
    }

    /** Turns the app lock on: the PIN encrypts the server tokens from now on. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        settings.update { it.copy(appLock = true) }
        sessions.sealTokens()
    }

    /** Turns the app lock off (after the PIN was confirmed): tokens go back to Keystore-only encryption. */
    suspend fun disableLock() {
        sessions.unsealTokens()
        settings.update { it.copy(appLock = false) }
        vault.disable()
    }

    /** "Forgot PIN?": the tokens can't be decrypted any more, so remove every server and start over. */
    suspend fun forgetEverything() {
        sessions.servers.value.map { it.id }.forEach { removeServer(it) }
        // Pages of an unfinished scan aren't tied to a server: without this, whoever reset the lock would see them.
        runCatching { scan.clear() }
        AppLock.markUnlocked()
        settings.update { it.copy(appLock = false) }
        vault.disable()
    }

    private var closeKeyJob: Job? = null

    /**
     * Locked: close the session that holds the token. The key that decrypts tokens is wiped right away,
     * or, while uploads you started are still running, as soon as they've finished.
     */
    fun onLocked() {
        sessions.onLocked()
        // Viewed and shared document files don't stay on the phone while PaperCtrl is locked.
        if (vault.enabled) appScope.launch(Dispatchers.IO) { runCatching { files.clear() } }
        closeKeyJob?.cancel()
        if (queue.loaded.value && queue.items.value.none { it.isActive }) {
            vault.close()
            return
        }
        closeKeyJob = appScope.launch {
            // Right after the app starts the queue may still be loading: wait for it before deciding.
            queue.awaitLoaded()
            queue.items.first { list -> list.none { it.isActive } }
            if (!AppLock.unlocked) vault.close()
        }
    }

    /**
     * "Try again" for an upload queue or scanned pages that are encrypted and couldn't be opened: asks the
     * phone's Keystore for the data key once more and reads them again.
     */
    suspend fun retryUnreadable() {
        withContext(Dispatchers.IO) { dataKeys.reset() }
        queue.reload()
        scan.reload()
    }

    /**
     * "Start empty": whatever can't be opened (the upload queue, the scanned pages or both) is moved aside,
     * not deleted, and replaced by an empty one. A data key that can't be unwrapped any more is set aside too
     * (as `data.key.unreadable`), so a new key can be made; a key that still works is kept.
     */
    suspend fun startEmptyUnreadable() {
        val queueAside = !queue.unreadable.value || queue.setAside()
        val scanAside = !scan.unreadable.value || scan.setAside()
        if (queueAside && scanAside) {
            withContext(Dispatchers.IO) {
                if (dataKeys.lost()) dataKeys.discard(keep = true)
            }
        }
    }

    /** The app lock is on (read from the vault itself, so this holds before the settings are loaded). */
    val lockOn: Boolean get() = vault.enabled || settings.settings.value.appLock

    /** Locks when PaperCtrl has been away longer than the lock timeout. Main thread. */
    fun lockIfExpired() {
        AppLock.lockIfExpired(lockOn, settings.settings.value.lockTimeout.millis)
    }

    /** Unlocked: open the session again, and send uploads that were waiting for the unlock. */
    fun onUnlocked() {
        closeKeyJob?.cancel()
        sessions.onUnlocked()
        AppLock.markUnlocked()
        queue.resumeAfterUnlock()
        // Seal any token left unsealed (PaperCtrl stopped between setting up the lock and sealing them).
        if (vault.enabled) appScope.launch { runCatching { sessions.sealTokens() } }
    }

    /** A screen to open, set from a notification tap; the main screen consumes it. */
    val pendingOpen = MutableStateFlow<String?>(null)

    /** A search to show in the Documents tab (from Organise, "More like this", a shortcut). */
    val pendingQuery = MutableStateFlow<DocumentQuery?>(null)
}

/** Android Keystore alias of the key that wraps the data key ([AppContainer.dataKeys]). */
private const val DATA_KEY_ALIAS = "paperctrl_data_at_rest"

val Context.container: AppContainer
    get() = (applicationContext as PaperCtrlApp).container
