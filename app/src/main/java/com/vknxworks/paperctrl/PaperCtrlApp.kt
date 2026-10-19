package com.vknxworks.paperctrl

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.vknxworks.paperctrl.notify.Notifications

class PaperCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
    }

    /**
     * Images go through the app's base HTTP client, so plain HTTP only reaches a server the user chose
     * and redirects can't carry credentials elsewhere. That client has no token and no pinned
     * certificate: document thumbnails must be loaded with the server's own client.
     * No disk cache: thumbnails and previews of private documents are never written to the phone's
     * storage, where they'd outlive removing the server.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .diskCache { null }
            .build()
}
