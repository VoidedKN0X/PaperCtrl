package com.vknxworks.paperctrl

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.vknxworks.paperctrl.notify.Notifications
import com.vknxworks.paperctrl.ui.lock.AppLock
import okhttp3.Call
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class PaperCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        AppLock.onLocked = { container.onLocked() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lockTimeoutJob?.cancel()
                val s = container.settings.settings.value
                AppLock.onForeground(s.appLock || container.vault.enabled, s.lockTimeout.millis)
            }

            override fun onStop(owner: LifecycleOwner) {
                val s = container.settings.settings.value
                AppLock.onBackground(s.appLock || container.vault.enabled, s.lockTimeout.millis)
                scheduleTimeoutLock(s.lockTimeout.millis)
            }
        })
    }

    private var lockTimeoutJob: Job? = null

    /**
     * With a lock timeout ("after 1 minute" and so on) PaperCtrl only counts as locked once that time has
     * passed. Lock then (and check once more after the 5 minutes a trip to the camera or a picker may
     * take), so the login is closed and viewed documents are deleted without waiting for the app to be
     * opened again.
     */
    private fun scheduleTimeoutLock(timeoutMillis: Long) {
        lockTimeoutJob?.cancel()
        if (!container.lockOn || !AppLock.unlocked || timeoutMillis <= 0) return
        lockTimeoutJob = container.appScope.launch(Dispatchers.Main) {
            delay(timeoutMillis + 1_000)
            runCatching { container.lockIfExpired() }
            val exemptMax = 5 * 60_000L
            if (timeoutMillis < exemptMax) {
                delay(exemptMax - timeoutMillis)
                runCatching { container.lockIfExpired() }
            }
        }
    }

    /**
     * Images for the active server (document thumbnails) go through that server's own client, with its
     * pinned certificate and token; anything else uses the base client without credentials.
     * No disk cache: thumbnails and previews of private documents are never written to the phone's
     * storage, where they'd outlive removing the server.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Call.Factory { container.sessions.imageCall(it) } })) }
            .diskCache { null }
            .build()
}
