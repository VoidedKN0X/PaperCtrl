package com.vknxworks.paperctrl.data.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Plain HTTP is allowed by the platform (many Paperless servers live on a home network), so the app
 * enforces it instead: an unencrypted request only goes out to a server address the user chose (an
 * http:// address they typed, or confirmed after HTTPS failed). Installed as a network interceptor,
 * so every redirect hop is checked: an HTTPS request can never be redirected down to HTTP.
 */
object CleartextPolicy {
    private val allowed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun origin(url: HttpUrl) = "${url.host.lowercase()}:${url.port}"

    /** Allows plain HTTP to the server at [baseUrl]. No-op for https addresses. */
    fun allowServer(baseUrl: String) {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        if (!url.isHttps) allowed += origin(url)
    }

    fun isAllowed(url: HttpUrl): Boolean = url.isHttps || origin(url) in allowed

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps) {
            val original = chain.call().request().url
            // Both the address the call started at and this hop (a redirect may lead elsewhere) must be
            // servers the user chose for plain HTTP, and an HTTPS call never continues over HTTP.
            if (original.isHttps || !isAllowed(original) || !isAllowed(url)) {
                throw com.vknxworks.paperctrl.data.api.PaperlessException(
                    com.vknxworks.paperctrl.data.api.ErrorKind.CLEARTEXT_BLOCKED,
                    url.host,
                )
            }
        }
        chain.proceed(chain.request())
    }
}

/**
 * OkHttp follows redirects on its own and drops the Authorization header when a redirect leaves the
 * host, but a 307/308 redirect sends the request body again, and the sign-in request carries the
 * password. Installed as a network interceptor, this refuses to pass a request with a body on to
 * another server, and strips credentials from any other hop that leaves the server. Moving the same
 * host from HTTP to HTTPS counts as the same server.
 */
object RedirectGuard {
    private val SENSITIVE_HEADERS = listOf("Authorization", "Cookie", "Proxy-Authorization")

    private fun sameServer(hop: HttpUrl, original: HttpUrl): Boolean {
        if (!hop.host.equals(original.host, ignoreCase = true)) return false
        if (hop.scheme == original.scheme) return hop.port == original.port
        return !original.isHttps && hop.isHttps
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val original = chain.call().request()
        if (sameServer(request.url, original.url)) return@Interceptor chain.proceed(request)
        if (request.body != null) {
            throw java.io.IOException("Refused to send this request on to another server (${request.url.host})")
        }
        chain.proceed(request.newBuilder().apply { SENSITIVE_HEADERS.forEach { removeHeader(it) } }.build())
    }
}
