package com.vknxworks.paperctrl

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.vknxworks.paperctrl.notify.Notifications
import com.vknxworks.paperctrl.ui.lock.AppLock
import okhttp3.Call

class PaperCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        AppLock.install()
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
