package com.vknxworks.paperctrl

import android.content.Context
import com.vknxworks.paperctrl.data.auth.SessionManager
import com.vknxworks.paperctrl.data.net.CleartextPolicy
import com.vknxworks.paperctrl.data.net.RedirectGuard
import com.vknxworks.paperctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val sessions = SessionManager(context, http, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as PaperCtrlApp).container
