package com.vknxworks.paperctrl.data.auth

import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.api.ErrorKind
import com.vknxworks.paperctrl.data.api.MAX_API_VERSION
import com.vknxworks.paperctrl.data.api.MIN_API_VERSION
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.api.TokenResponse
import com.vknxworks.paperctrl.data.api.UiSettingsResponse
import com.vknxworks.paperctrl.data.api.errorFor
import com.vknxworks.paperctrl.data.api.isTlsProblem
import com.vknxworks.paperctrl.data.api.serverMessage
import com.vknxworks.paperctrl.data.net.CertInfo
import com.vknxworks.paperctrl.data.net.CleartextPolicy
import com.vknxworks.paperctrl.data.net.PinnedTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** How to sign in. */
sealed interface Credentials {
    data class Password(val username: String, val password: String, val code: String? = null) : Credentials
    data class Token(val token: String) : Credentials
}

/**
 * The server presented a certificate the system doesn't trust (self-signed, private CA, or a
 * name that doesn't match the address). The UI shows [cert] and asks before trusting it.
 * [changed] is true when a different certificate was pinned for this address before.
 */
class NeedsTrust(val address: String, val cert: CertInfo, val changed: Boolean) :
    IOException("Certificate needs confirmation")

/**
 * HTTPS couldn't be reached for an address typed without a scheme. The UI asks whether to try
 * plain HTTP (which sends the password and documents unencrypted) and retries with [httpUrl].
 */
class HttpFallbackNeeded(val httpUrl: String) : IOException("HTTPS not reachable")

/** A verified sign-in, ready to save. */
data class Connected(
    val baseUrl: String,
    val token: String,
    val apiVersion: Int,
    val serverVersion: String?,
    val settings: UiSettingsResponse,
)

/** "scheme://host:port", the key under which a pinned certificate is saved. */
fun originOf(url: String): String {
    val u = url.toHttpUrlOrNull() ?: return url
    return "${u.scheme}://${u.host.lowercase()}:${u.port}"
}

/**
 * Normalises what the user typed into a base URL ending in "/": adds https:// when no scheme was
 * given, keeps a sub-path, and drops a trailing "/api" (people often paste the API address).
 * Returns null for something that isn't an address.
 */
fun normaliseAddress(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
    if (segments.lastOrNull().equals("api", ignoreCase = true)) segments.removeAt(segments.lastIndex)
    val path = if (segments.isEmpty()) "/" else "/" + segments.joinToString("/") + "/"
    return url.newBuilder().encodedPath(path).query(null).fragment(null).build().toString()
}

fun hasExplicitScheme(raw: String) = raw.trim().contains("://")

/**
 * Checks a server address step by step so each failure gets its own explanation: reachable at all,
 * certificate trusted, a Paperless-ngx API there, credentials valid, API version new enough.
 */
class ServerConnector(private val baseHttp: OkHttpClient) {

    private fun clientFor(trust: PinnedTrust, quick: Boolean = false): OkHttpClient =
        baseHttp.newBuilder()
            .sslSocketFactory(trust.sslSocketFactory, trust.trustManager)
            .hostnameVerifier(trust.hostnameVerifier)
            .apply { if (quick) connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS) }
            .build()

    /**
     * Runs the whole sign-in for [address]. [pins] holds certificates the user already confirmed,
     * by origin. Throws [NeedsTrust], [HttpFallbackNeeded] or [PaperlessException].
     */
    suspend fun connect(address: String, credentials: Credentials, pins: Map<String, String>): Connected {
        val base = normaliseAddress(address) ?: throw PaperlessException(ErrorKind.UNREACHABLE)
        // Only reached for an http:// address the user typed or confirmed in the "unencrypted" dialog.
        CleartextPolicy.allowServer(base)
        val trust = PinnedTrust(pins[originOf(base)])
        val http = clientFor(trust)

        probe(base, http, trust, pins, allowHttpFallback = !hasExplicitScheme(address))
        val token = when (credentials) {
            is Credentials.Token -> credentials.token.trim().removePrefix("Token ").trim()
            is Credentials.Password -> fetchToken(base, http, credentials)
        }
        if (token.isEmpty()) throw PaperlessException(ErrorKind.BAD_TOKEN)
        return whoAmI(base, token, http)
    }

    /**
     * Checks a second address (the home-network one) for the same server with a token we already
     * have. Throws [NeedsTrust] or [PaperlessException]; returns the normalised address.
     */
    suspend fun verifyAlternate(address: String, token: String, pins: Map<String, String>, expected: Connected): String {
        val base = normaliseAddress(address) ?: throw PaperlessException(ErrorKind.UNREACHABLE)
        CleartextPolicy.allowServer(base)
        val trust = PinnedTrust(pins[originOf(base)])
        val http = clientFor(trust)
        probe(base, http, trust, pins, allowHttpFallback = false)
        val other = whoAmI(base, token, http)
        // Same user on a server of the same version: close enough to be sure it's the same Paperless.
        if (other.settings.user.id != expected.settings.user.id || other.serverVersion != expected.serverVersion) {
            throw PaperlessException(ErrorKind.UNEXPECTED, "This address leads to a different Paperless server.")
        }
        return base
    }

    /** True when [base] answers quickly (any HTTP response, with a trusted certificate). */
    suspend fun isReachable(base: String, pin: String?): Boolean = withContext(Dispatchers.IO) {
        val http = clientFor(PinnedTrust(pin), quick = true)
        val url = base.toHttpUrlOrNull()?.resolve("api/") ?: return@withContext false
        runCatching {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").get().build()).execute().use { true }
        }.getOrDefault(false)
    }

    private suspend fun probe(
        base: String,
        http: OkHttpClient,
        trust: PinnedTrust,
        pins: Map<String, String>,
        allowHttpFallback: Boolean,
    ) = withContext(Dispatchers.IO) {
        val url = apiUrl(base, "")
        try {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").get().build()).execute().use { resp ->
                // Paperless answers /api/ with JSON: the API root (200) or "not signed in" (401/403).
                val type = resp.header("Content-Type").orEmpty()
                val looksLikeApi = resp.code in listOf(200, 401, 403) && type.contains("json", ignoreCase = true)
                if (!looksLikeApi) throw PaperlessException(ErrorKind.NOT_PAPERLESS, httpCode = resp.code)
            }
        } catch (e: PaperlessException) {
            throw e
        } catch (e: IOException) {
            if (isTlsProblem(e)) {
                val cert = trust.rejected
                if (cert != null) {
                    val info = CertInfo.of(cert)
                    throw NeedsTrust(base, info, changed = pins.containsKey(originOf(base)))
                }
                throw PaperlessException(ErrorKind.TLS, e.message, cause = e)
            }
            if (allowHttpFallback && base.startsWith("https://")) {
                throw HttpFallbackNeeded("http://" + base.removePrefix("https://"))
            }
            throw PaperlessException(ErrorKind.UNREACHABLE, e.message, cause = e)
        }
    }

    private suspend fun fetchToken(base: String, http: OkHttpClient, c: Credentials.Password): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("username", c.username.trim())
            .add("password", c.password)
            .apply { if (!c.code.isNullOrBlank()) add("code", c.code.trim()) }
            .build()
        val request = Request.Builder().url(apiUrl(base, "token/")).header("Accept", "application/json").post(form).build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return@use runCatching { ApiJson.decodeFromString(TokenResponse.serializer(), text).token }
                    .getOrElse { throw PaperlessException(ErrorKind.NOT_PAPERLESS) }
            }
            val msg = serverMessage(text)
            throw when {
                resp.code == 400 && msg?.contains("MFA code is required", ignoreCase = true) == true ->
                    PaperlessException(ErrorKind.MFA_REQUIRED, httpCode = 400)
                resp.code == 400 && msg?.contains("Invalid MFA", ignoreCase = true) == true ->
                    PaperlessException(ErrorKind.MFA_INVALID, httpCode = 400)
                resp.code == 400 || resp.code == 401 -> PaperlessException(ErrorKind.BAD_CREDENTIALS, msg, resp.code)
                resp.code == 404 || resp.code == 405 -> PaperlessException(ErrorKind.NOT_PAPERLESS, httpCode = resp.code)
                else -> errorFor(resp.code, text)
            }
        }
    }

    /**
     * Signed-in request without an API version, so any Paperless answers; its version headers then
     * say which API versions it speaks (they're only sent to signed-in users).
     */
    private suspend fun whoAmI(base: String, token: String, http: OkHttpClient): Connected = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl(base, "ui_settings/"))
            .header("Authorization", "Token $token")
            .header("Accept", "application/json")
            .get()
            .build()
        val (serverVersion, maxApi) = http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) throw PaperlessException(ErrorKind.BAD_TOKEN, serverMessage(text), resp.code)
            if (!resp.isSuccessful) throw errorFor(resp.code, text)
            val max = resp.header("X-Api-Version")?.trim()?.toIntOrNull()
                ?: throw PaperlessException(ErrorKind.NOT_PAPERLESS)
            resp.header("X-Version") to max
        }
        if (maxApi < MIN_API_VERSION) throw PaperlessException(ErrorKind.TOO_OLD, serverVersion)
        val apiVersion = minOf(maxApi, MAX_API_VERSION)

        // Read who we are again in the version we'll use: older servers default to an older format.
        val versioned = request.newBuilder().header("Accept", "application/json; version=$apiVersion").build()
        val settings = http.newCall(versioned).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 406) throw PaperlessException(ErrorKind.TOO_OLD, serverVersion, 406)
            if (!resp.isSuccessful) throw errorFor(resp.code, text)
            runCatching { ApiJson.decodeFromString(UiSettingsResponse.serializer(), text) }
                .getOrElse { throw PaperlessException(ErrorKind.NOT_PAPERLESS) }
        }
        Connected(
            baseUrl = base,
            token = token,
            apiVersion = apiVersion,
            serverVersion = serverVersion,
            settings = settings,
        )
    }

    private fun apiUrl(base: String, path: String): HttpUrl =
        base.toHttpUrlOrNull()?.resolve("api/$path") ?: throw PaperlessException(ErrorKind.UNREACHABLE)
}
