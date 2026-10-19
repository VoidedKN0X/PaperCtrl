package com.vknxworks.paperctrl.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

// ---------- Models ----------

@Serializable
data class Page<T>(
    val count: Int = 0,
    val next: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class Tag(
    val id: Int,
    val name: String = "",
    val color: String? = null,
    @SerialName("text_color") val textColor: String? = null,
    @SerialName("is_inbox_tag") val isInboxTag: Boolean = false,
    @SerialName("document_count") val documentCount: Int = 0,
)

/** Correspondents, document types and storage paths share these fields. */
@Serializable
data class NamedObject(
    val id: Int,
    val name: String = "",
    @SerialName("document_count") val documentCount: Int = 0,
    /** Storage paths only: the path template. */
    val path: String? = null,
)

/** Metadata a new document can be given when it's uploaded. Null / empty means "let Paperless decide". */
@Serializable
data class UploadMeta(
    val title: String? = null,
    /** ISO date, yyyy-MM-dd. */
    val created: String? = null,
    val correspondent: Int? = null,
    val documentType: Int? = null,
    val storagePath: Int? = null,
    val tags: List<Int> = emptyList(),
    val asn: Int? = null,
) {
    val isEmpty: Boolean
        get() = title.isNullOrBlank() && created == null && correspondent == null && documentType == null &&
            storagePath == null && tags.isEmpty() && asn == null
}

enum class TaskState { PENDING, RUNNING, SUCCESS, FAILURE }

/** A consumption task, read the same way from API 9 (upper-case states, "result") and 10 ("result_data"). */
data class TaskStatus(val state: TaskState, val documentId: Int?, val message: String?)

// ---------- Calls ----------

/** Reads every page of a list endpoint. Page numbers are used rather than "next", whose host may be the proxy's internal one. */
suspend fun <T> PaperlessClient.listAll(path: String, item: KSerializer<T>, pageSize: Int = 500): List<T> {
    val serializer = Page.serializer(item)
    val all = mutableListOf<T>()
    var page = 1
    while (true) {
        val json = get(path, mapOf("page_size" to "$pageSize", "page" to "$page"))
        // Some list endpoints answer with a bare array.
        if (json is JsonArray) return ApiJson.decodeFromJsonElement(ListSerializer(item), json)
        val p = ApiJson.decodeFromJsonElement(serializer, json)
        all += p.results
        if (p.results.size < pageSize || all.size >= p.count || p.next == null) return all
        page++
    }
}

suspend fun PaperlessClient.tags() = listAll("tags/", Tag.serializer())
suspend fun PaperlessClient.correspondents() = listAll("correspondents/", NamedObject.serializer())
suspend fun PaperlessClient.documentTypes() = listAll("document_types/", NamedObject.serializer())
suspend fun PaperlessClient.storagePaths() = listAll("storage_paths/", NamedObject.serializer())

/** The next archive serial number nobody uses yet. */
suspend fun PaperlessClient.nextAsn(): Int? = (get("documents/next_asn/") as? JsonPrimitive)?.intOrNull

/** A document's title, for the "document added" notification. */
suspend fun PaperlessClient.documentTitle(id: Int): String? =
    ((get("documents/$id/", mapOf("fields" to "id,title")) as? JsonObject)?.get("title") as? JsonPrimitive)?.contentOrNull

/**
 * Uploads [file] for Paperless to consume and returns the consumption task's id.
 * [onProgress] gets the bytes sent so far and the total.
 */
suspend fun PaperlessClient.postDocument(
    file: File,
    fileName: String,
    mimeType: String,
    meta: UploadMeta,
    onProgress: (Long, Long) -> Unit,
): String {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
        addFormDataPart("document", fileName, ProgressFileBody(file, mimeType, onProgress))
        meta.title?.takeIf { it.isNotBlank() }?.let { addFormDataPart("title", it.trim()) }
        // Noon, so the date stays the same in any server time zone.
        meta.created?.let { addFormDataPart("created", "${it}T12:00:00") }
        meta.correspondent?.let { addFormDataPart("correspondent", "$it") }
        meta.documentType?.let { addFormDataPart("document_type", "$it") }
        meta.storagePath?.let { addFormDataPart("storage_path", "$it") }
        meta.tags.forEach { addFormDataPart("tags", "$it") }
        meta.asn?.let { addFormDataPart("archive_serial_number", "$it") }
    }.build()
    val json = execute(request(url("documents/post_document/")).post(body).build())
    return (json as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: throw PaperlessException(ErrorKind.UNEXPECTED, "The server didn't return a task for the upload.")
}

/** The consumption task with [taskId], or null if the server doesn't list it (yet). */
suspend fun PaperlessClient.task(taskId: String): TaskStatus? {
    val json = get("tasks/", mapOf("task_id" to taskId))
    val list = when (json) {
        is JsonArray -> json
        is JsonObject -> json["results"] as? JsonArray
        else -> null
    } ?: return null
    val t = list.firstOrNull { (it as? JsonObject)?.str("task_id") == taskId } as? JsonObject ?: return null
    return parseTask(t)
}

internal fun parseTask(t: JsonObject): TaskStatus {
    val state = when (t.str("status")?.lowercase()) {
        "success" -> TaskState.SUCCESS
        "failure", "revoked" -> TaskState.FAILURE
        "started", "retry" -> TaskState.RUNNING
        else -> TaskState.PENDING
    }
    val data = t["result_data"] as? JsonObject
    val docId = (t["related_document_ids"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.intOrNull
        ?: t.str("related_document")?.toIntOrNull()
        ?: (data?.get("document_id") as? JsonPrimitive)?.intOrNull
    val message = data?.str("error_message")
        ?: data?.str("reason")
        ?: data?.get("duplicate_of")?.let { (it as? JsonPrimitive)?.contentOrNull }?.let { "It is a duplicate of document #$it." }
        ?: t.str("result")
    return TaskStatus(state, docId, message?.take(500))
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** A file request body that reports how much has been sent. */
private class ProgressFileBody(
    private val file: File,
    mimeType: String,
    private val onProgress: (Long, Long) -> Unit,
) : RequestBody() {
    private val type = mimeType.toMediaTypeOrNull()
    override fun contentType() = type
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var sent = 0L
        file.source().use { src ->
            while (true) {
                val read = src.read(sink.buffer, 64 * 1024L)
                if (read == -1L) break
                sent += read
                sink.flush()
                onProgress(sent, total)
            }
        }
    }
}
