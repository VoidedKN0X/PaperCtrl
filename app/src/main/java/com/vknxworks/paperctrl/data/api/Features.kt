package com.vknxworks.paperctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

// ---------- What the server's version offers ----------

/**
 * Features that depend on the Paperless-ngx version, the same way the page manager does
 * ([pageEditSupport]): what's offered follows the server.
 *
 * - Paperless-ngx 3 (API 10): document versions, share-link bundles, removing a PDF's password.
 * - Paperless-ngx 3.2: the "has duplicates" document filter.
 * - Paperless-ngx 3.3: barcode contents in the search index ("barcodes:" in a search).
 */
data class ServerFeatures(
    val versions: Boolean,
    val bundles: Boolean,
    val removePassword: Boolean,
    val duplicatesFilter: Boolean,
    val barcodeSearch: Boolean,
)

/** [serverVersion] ("3.3.4", "v2.20.1") as numbers, or null when it can't be read. */
internal fun versionNumbers(serverVersion: String?): List<Int>? =
    serverVersion?.trim()?.removePrefix("v")?.split('.', '-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }

/** True when [serverVersion] is [want] or newer. An unknown version is never "at least" anything. */
internal fun versionAtLeast(serverVersion: String?, vararg want: Int): Boolean {
    val have = versionNumbers(serverVersion) ?: return false
    for (i in want.indices) {
        val h = have.getOrElse(i) { 0 }
        if (h != want[i]) return h > want[i]
    }
    return true
}

fun serverFeatures(apiVersion: Int, serverVersion: String?): ServerFeatures {
    val three = apiVersion >= 10 || versionAtLeast(serverVersion, 3)
    return ServerFeatures(
        versions = three,
        bundles = three,
        removePassword = three,
        duplicatesFilter = versionAtLeast(serverVersion, 3, 2),
        barcodeSearch = versionAtLeast(serverVersion, 3, 3),
    )
}

// ---------- Failed imports (tasks) ----------

/** A consume task that failed and that nobody has dismissed yet. [id] is the task's row id, used to dismiss it. */
data class FailedImport(
    val id: Int,
    val taskId: String,
    val fileName: String?,
    /** ISO date-time the task was created. */
    val created: String?,
    val message: String?,
    /** The document this file duplicates, when that's why it wasn't added. */
    val duplicateOf: String?,
)

internal fun parseFailedImport(t: JsonObject): FailedImport? {
    val id = (t["id"] as? JsonPrimitive)?.intOrNull ?: return null
    val taskId = (t["task_id"] as? JsonPrimitive)?.contentOrNull ?: return null
    val status = parseTask(t)
    if (status.state != TaskState.FAILURE) return null
    val name = ((t["input_data"] as? JsonObject)?.get("filename") as? JsonPrimitive)?.contentOrNull
        ?: (t["task_file_name"] as? JsonPrimitive)?.contentOrNull
    return FailedImport(id, taskId, name, (t["date_created"] as? JsonPrimitive)?.contentOrNull, status.message, status.duplicateOf)
}

/**
 * Imports (consume tasks) that failed and haven't been dismissed. API 10 pages the task list and filters by
 * status on the server; API 9 answers with one plain list, which is filtered here.
 */
suspend fun PaperlessClient.failedImports(): List<FailedImport> {
    val out = mutableListOf<FailedImport>()
    var page = 1
    while (page <= 10) {
        val query = if (apiVersion >= 10) {
            mapOf("task_type" to "consume_file", "status" to "failure", "acknowledged" to "false", "page_size" to "100", "page" to "$page")
        } else {
            mapOf("task_name" to "consume_file", "acknowledged" to "false")
        }
        val json = get("tasks/", query)
        val list = when (json) {
            is JsonArray -> json
            is JsonObject -> json["results"] as? JsonArray
            else -> null
        } ?: break
        list.forEach { e -> (e as? JsonObject)?.let(::parseFailedImport)?.let { out += it } }
        val next = (json as? JsonObject)?.get("next")
        val more = next is JsonPrimitive && next !is JsonNull && !next.contentOrNull.isNullOrBlank()
        if (!more || list.isEmpty()) break
        page++
    }
    return out.distinctBy { it.id }
}

/** tasks/acknowledge/: marks the tasks with these row ids as seen, which takes them off the list for everyone. */
suspend fun PaperlessClient.acknowledgeTasks(ids: Collection<Int>) {
    post("tasks/acknowledge/", buildJsonObject { put("tasks", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) }.body())
}

// ---------- Remove a PDF's password (Paperless-ngx 3) ----------

/**
 * documents/remove_password/: Paperless opens the PDF with [password] and saves the unprotected file as a
 * new version of the same document, through a consume task (its temporary file is "<id>_unprotected.pdf").
 */
suspend fun PaperlessClient.removePassword(id: Int, password: String) {
    post(
        "documents/remove_password/",
        buildJsonObject {
            put("documents", buildJsonArray { add(JsonPrimitive(id)) })
            put("password", password)
            put("update_document", true)
            put("include_metadata", true)
        }.body(),
    )
}

// ---------- E-mail through the server ----------

/** True when the server says e-mail is set up (ui_settings → settings.email_enabled); null when it doesn't say. */
suspend fun PaperlessClient.emailEnabled(): Boolean? =
    (uiSettings().settings["email_enabled"] as? JsonPrimitive)?.booleanOrNull

/** documents/email/: Paperless sends the documents as attachments from its own mail account. */
suspend fun PaperlessClient.emailDocuments(ids: Collection<Int>, addresses: String, subject: String, message: String, archive: Boolean) {
    post(
        "documents/email/",
        buildJsonObject {
            put("documents", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            put("addresses", addresses)
            put("subject", subject)
            put("message", message)
            put("use_archive_version", archive)
        }.body(),
    )
}

// ---------- Document versions (Paperless-ngx 3) ----------

@Serializable
data class DocumentVersion(
    val id: Int,
    val added: String? = null,
    @SerialName("version_label") val label: String? = null,
    @SerialName("is_root") val isRoot: Boolean = false,
)

/** documents/{id}/update_version/: uploads [file] as a new version; returns the consume task's id. */
suspend fun PaperlessClient.uploadVersion(id: Int, file: File, fileName: String, mimeType: String, label: String?): String {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
        addFormDataPart("document", fileName, file.asRequestBody(mimeType.toMediaTypeOrNull()))
        label?.trim()?.takeIf { it.isNotEmpty() }?.let { addFormDataPart("version_label", it.take(64)) }
    }.build()
    val json = execute(request(url("documents/$id/update_version/")).post(body).build())
    return (json as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: throw PaperlessException(ErrorKind.NO_UPLOAD_TASK)
}

/** PATCH documents/{id}/versions/{version}/: sets or clears a version's label. */
suspend fun PaperlessClient.labelVersion(id: Int, versionId: Int, label: String?) {
    val value = label?.trim()?.takeIf { it.isNotEmpty() }?.take(64)
    patch("documents/$id/versions/$versionId/", buildJsonObject { put("version_label", value?.let { JsonPrimitive(it) } ?: JsonNull) }.body())
}

/** DELETE documents/{id}/versions/{version}/: removes one version for good (never the first one). */
suspend fun PaperlessClient.deleteVersion(id: Int, versionId: Int) {
    delete("documents/$id/versions/$versionId/")
}

/** The OCR text of one version (documents/{id}/?version=…). */
suspend fun PaperlessClient.versionContent(id: Int, versionId: Int): String? =
    ((get("documents/$id/", mapOf("version" to "$versionId", "fields" to "id,content")) as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull

// ---------- Share-link bundles (Paperless-ngx 3) ----------

@Serializable
data class ShareBundle(
    val id: Int,
    val created: String? = null,
    val expiration: String? = null,
    val slug: String = "",
    @SerialName("file_version") val fileVersion: String? = null,
    /** pending, processing, ready or failed. */
    val status: String = "",
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("last_error") val lastError: kotlinx.serialization.json.JsonElement? = null,
    val documents: List<Int> = emptyList(),
    @SerialName("document_count") val documentCount: Int = 0,
) {
    val isReady: Boolean get() = status == "ready"
    val isFailed: Boolean get() = status == "failed"
    val isBuilding: Boolean get() = status == "pending" || status == "processing"
}

suspend fun PaperlessClient.shareBundles(): List<ShareBundle> = listAll("share_link_bundles/", ShareBundle.serializer(), pageSize = 100)

/** Creates one link for several documents; Paperless builds the zip in the background. */
suspend fun PaperlessClient.createShareBundle(ids: Collection<Int>, expirationDays: Int?, original: Boolean): ShareBundle =
    ApiJson.decodeFromJsonElement(
        ShareBundle.serializer(),
        post(
            "share_link_bundles/",
            buildJsonObject {
                put("document_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                put("expiration_days", expirationDays?.let { JsonPrimitive(it) } ?: JsonNull)
                put("file_version", if (original) "original" else "archive")
            }.body(),
        ),
    )

suspend fun PaperlessClient.rebuildShareBundle(id: Int) {
    post("share_link_bundles/$id/rebuild/", JsonObject(emptyMap()).body())
}

suspend fun PaperlessClient.deleteShareBundle(id: Int) {
    delete("share_link_bundles/$id/")
}

// ---------- Global search ----------

enum class FoundKind { DOCUMENT, TAG, CORRESPONDENT, TYPE, PATH, VIEW, FIELD }

/** One thing the server's global search found by name. */
data class Found(val kind: FoundKind, val id: Int, val name: String)

/** The global search's answer as a flat list, documents first. */
internal fun parseGlobalSearch(json: JsonObject): List<Found> {
    fun list(key: String, kind: FoundKind, nameKey: String = "name"): List<Found> =
        (json[key] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val name = (o[nameKey] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Found(kind, id, name)
        }
    return list("documents", FoundKind.DOCUMENT, "title") + list("saved_views", FoundKind.VIEW) + list("tags", FoundKind.TAG) +
        list("correspondents", FoundKind.CORRESPONDENT) + list("document_types", FoundKind.TYPE) +
        list("storage_paths", FoundKind.PATH) + list("custom_fields", FoundKind.FIELD)
}

/**
 * search/?query=…: documents by title and tags, correspondents, document types, storage paths, saved views
 * and custom fields by name (a few of each). The query needs at least three characters.
 */
suspend fun PaperlessClient.globalSearch(query: String): List<Found> {
    val q = query.trim()
    if (q.length < 3) return emptyList()
    val json = get("search/", mapOf("query" to q, "db_only" to "true")) as? JsonObject ?: return emptyList()
    return parseGlobalSearch(json)
}
