package com.vknxworks.paperctrl.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

/** Lowest and highest Paperless-ngx REST API versions PaperCtrl is written against. */
const val MIN_API_VERSION = 9
const val MAX_API_VERSION = 10

/**
 * Talks to one Paperless-ngx server with one token. [baseUrl] ends in "/" and may include a path
 * prefix when Paperless runs under a sub-path. Every request asks for [apiVersion] explicitly.
 *
 * Nothing here logs: requests carry the token, and responses carry document contents.
 */
class PaperlessClient(
    val baseUrl: String,
    private val token: String,
    val apiVersion: Int,
    val http: OkHttpClient,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    /** Resolves an API path such as "documents/" or "documents/12/thumb/" under /api/. */
    fun url(path: String, query: Map<String, String?> = emptyMap()): HttpUrl =
        base.resolve("api/$path")!!.newBuilder().apply {
            query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()

    fun request(url: HttpUrl, accept: String = "application/json; version=$apiVersion"): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Token $token")
            .header("Accept", accept)

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        execute(request(url(path, query)).get().build())

    suspend fun post(path: String, body: RequestBody): JsonElement =
        execute(request(url(path)).post(body).build())

    suspend fun patch(path: String, body: RequestBody): JsonElement =
        execute(request(url(path)).patch(body).build())

    suspend fun delete(path: String): JsonElement =
        execute(request(url(path)).delete().build())

    suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { resp -> resp.jsonOrThrow() }
    }

    suspend fun uiSettings(): UiSettingsResponse =
        ApiJson.decodeFromJsonElement(UiSettingsResponse.serializer(), get("ui_settings/"))
}

/** Parses a successful JSON response, or turns an error response into a [PaperlessException]. */
internal fun Response.jsonOrThrow(): JsonElement {
    val text = body?.string().orEmpty()
    if (isSuccessful) {
        if (text.isBlank()) return JsonObject(emptyMap())
        return runCatching { ApiJson.parseToJsonElement(text) }
            .getOrElse { throw PaperlessException(ErrorKind.NOT_PAPERLESS, httpCode = code) }
    }
    throw errorFor(code, text)
}

internal fun errorFor(code: Int, body: String): PaperlessException {
    val detail = serverMessage(body)
    val kind = when (code) {
        401 -> ErrorKind.BAD_TOKEN
        403 -> ErrorKind.FORBIDDEN
        404 -> ErrorKind.NOT_FOUND
        406 -> ErrorKind.TOO_OLD
        413 -> ErrorKind.TOO_LARGE
        429 -> ErrorKind.THROTTLED
        in 500..599 -> ErrorKind.SERVER
        else -> ErrorKind.UNEXPECTED
    }
    return PaperlessException(kind, detail, code)
}

/**
 * Paperless (Django REST framework) explains errors as {"detail": "…"}, {"non_field_errors": […]}
 * or {"field": ["…"]}. Picks the first readable sentence; HTML error pages give nothing.
 */
internal fun serverMessage(body: String): String? {
    val json = runCatching { ApiJson.parseToJsonElement(body) }.getOrNull() ?: return null
    fun first(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstNotNullOfOrNull { first(it) }
        is JsonObject -> e["detail"]?.let { first(it) }
            ?: e["non_field_errors"]?.let { first(it) }
            ?: e.entries.firstNotNullOfOrNull { (k, v) -> first(v)?.let { msg -> if (k == "error") msg else "$k: $msg" } }
        else -> null
    }
    return first(json)?.take(300)
}

/** True when [t] (or a cause) is a TLS handshake or certificate failure rather than a network one. */
fun isTlsProblem(t: Throwable): Boolean {
    var e: Throwable? = t
    while (e != null) {
        if (e is javax.net.ssl.SSLException ||
            e is java.security.cert.CertificateException ||
            e is java.security.cert.CertPathValidatorException
        ) return true
        e = e.cause
    }
    return false
}

/** Network failures that mean "couldn't reach the server" (as opposed to an answer from it). */
fun isUnreachable(t: Throwable): Boolean = t is IOException && t !is PaperlessException && !isTlsProblem(t)
