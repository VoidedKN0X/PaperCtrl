package com.vknxworks.paperctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

// ---------- Models ----------

@Serializable
data class Document(
    val id: Int,
    val title: String = "",
    val correspondent: Int? = null,
    @SerialName("document_type") val documentType: Int? = null,
    @SerialName("storage_path") val storagePath: Int? = null,
    val tags: List<Int> = emptyList(),
    /** yyyy-MM-dd (API 9+). */
    val created: String? = null,
    val added: String? = null,
    val modified: String? = null,
    @SerialName("archive_serial_number") val asn: Int? = null,
    @SerialName("original_file_name") val originalFileName: String? = null,
    @SerialName("archived_file_name") val archivedFileName: String? = null,
    val owner: Int? = null,
    @SerialName("user_can_change") val userCanChange: Boolean = true,
    @SerialName("custom_fields") val customFields: List<CustomFieldValue> = emptyList(),
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    /** OCR text; only on the detail request. */
    val content: String? = null,
    /** A list of notes or a count, depending on the request. */
    val notes: JsonElement? = null,
    /** View/change users and groups; only with full_perms=true. */
    val permissions: JsonObject? = null,
    @SerialName("__search_hit__") val searchHit: SearchHit? = null,
) {
    val hasArchive: Boolean get() = !archivedFileName.isNullOrBlank()
    val noteCount: Int get() = when (val n = notes) {
        is JsonArray -> n.size
        is JsonPrimitive -> n.intOrNull ?: 0
        else -> 0
    }
    val createdDate: String? get() = created?.take(10)
}

@Serializable
data class SearchHit(
    val score: Double? = null,
    val highlights: String? = null,
    @SerialName("note_highlights") val noteHighlights: String? = null,
    val rank: Int? = null,
)

@Serializable
data class CustomFieldValue(val field: Int, val value: JsonElement? = null)

@Serializable
data class CustomField(
    val id: Int,
    val name: String = "",
    @SerialName("data_type") val dataType: String = "string",
    @SerialName("extra_data") val extraData: JsonObject? = null,
    @SerialName("document_count") val documentCount: Int = 0,
) {
    /** Select options as (id, label); older servers list plain strings, whose index is the id. */
    val selectOptions: List<Pair<String, String>>
        get() = (extraData?.get("select_options") as? JsonArray)?.mapIndexedNotNull { i, e ->
            when (e) {
                is JsonObject -> {
                    val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                    id to ((e["label"] as? JsonPrimitive)?.contentOrNull ?: id)
                }
                is JsonPrimitive -> i.toString() to (e.contentOrNull ?: "")
                else -> null
            }
        }.orEmpty()
    val defaultCurrency: String? get() = (extraData?.get("default_currency") as? JsonPrimitive)?.contentOrNull
}

@Serializable
data class Note(
    val id: Int,
    val note: String = "",
    val created: String? = null,
    val user: JsonElement? = null,
) {
    val author: String? get() = when (val u = user) {
        is JsonObject -> listOfNotNull(
            (u["first_name"] as? JsonPrimitive)?.contentOrNull,
            (u["last_name"] as? JsonPrimitive)?.contentOrNull,
        ).joinToString(" ").trim().ifEmpty { (u["username"] as? JsonPrimitive)?.contentOrNull }
        else -> null
    }
}

@Serializable
data class HistoryEntry(
    val id: Int,
    val timestamp: String? = null,
    val action: String? = null,
    val changes: JsonElement? = null,
    val actor: JsonObject? = null,
) {
    val actorName: String? get() = (actor?.get("username") as? JsonPrimitive)?.contentOrNull
}

@Serializable
data class ShareLink(
    val id: Int,
    val created: String? = null,
    val expiration: String? = null,
    val slug: String = "",
    val document: Int? = null,
    @SerialName("file_version") val fileVersion: String? = null,
)

@Serializable
data class FilterRule(@SerialName("rule_type") val ruleType: Int, val value: String? = null)

@Serializable
data class SavedView(
    val id: Int,
    val name: String = "",
    @SerialName("show_on_dashboard") val showOnDashboard: Boolean = false,
    @SerialName("show_in_sidebar") val showInSidebar: Boolean = false,
    @SerialName("sort_field") val sortField: String? = null,
    @SerialName("sort_reverse") val sortReverse: Boolean = false,
    @SerialName("filter_rules") val filterRules: List<FilterRule> = emptyList(),
)

@Serializable
data class Suggestions(
    val correspondents: List<Int> = emptyList(),
    val tags: List<Int> = emptyList(),
    @SerialName("document_types") val documentTypes: List<Int> = emptyList(),
    @SerialName("storage_paths") val storagePaths: List<Int> = emptyList(),
    val dates: List<String> = emptyList(),
)

@Serializable
data class User(
    val id: Int,
    val username: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
) {
    val displayName: String get() = listOfNotNull(firstName, lastName).joinToString(" ").trim().ifEmpty { username }
}

@Serializable
data class Group(val id: Int, val name: String = "")

/** Who may see and change an object. */
data class Permissions(
    val viewUsers: Set<Int> = emptySet(),
    val viewGroups: Set<Int> = emptySet(),
    val changeUsers: Set<Int> = emptySet(),
    val changeGroups: Set<Int> = emptySet(),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("view", buildJsonObject {
            put("users", buildJsonArray { viewUsers.forEach { add(JsonPrimitive(it)) } })
            put("groups", buildJsonArray { viewGroups.forEach { add(JsonPrimitive(it)) } })
        })
        put("change", buildJsonObject {
            put("users", buildJsonArray { changeUsers.forEach { add(JsonPrimitive(it)) } })
            put("groups", buildJsonArray { changeGroups.forEach { add(JsonPrimitive(it)) } })
        })
    }

    companion object {
        fun from(json: JsonObject?): Permissions {
            fun ids(kind: String, who: String): Set<Int> =
                ((json?.get(kind) as? JsonObject)?.get(who) as? JsonArray)
                    ?.mapNotNull { e -> (e as? JsonPrimitive)?.intOrNull ?: ((e as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull }
                    ?.toSet().orEmpty()
            return Permissions(ids("view", "users"), ids("view", "groups"), ids("change", "users"), ids("change", "groups"))
        }
    }
}

// ---------- Requests ----------

private val JSON_TYPE = "application/json".toMediaType()
fun JsonElement.body(): RequestBody = ApiJson.encodeToString(JsonElement.serializer(), this).toRequestBody(JSON_TYPE)

/** Fields the lists need; leaving out the OCR text keeps pages small. */
private const val LIST_FIELDS = "id,title,correspondent,document_type,storage_path,tags,created,added,modified," +
    "archive_serial_number,original_file_name,archived_file_name,owner,user_can_change,custom_fields,page_count,mime_type,notes"

suspend fun PaperlessClient.documents(params: Map<String, String>, page: Int, pageSize: Int = 25): Page<Document> {
    val query = params + mapOf("page" to "$page", "page_size" to "$pageSize", "fields" to LIST_FIELDS) +
        (if (params.containsKey("query")) mapOf("fields" to "$LIST_FIELDS,__search_hit__") else emptyMap())
    return ApiJson.decodeFromJsonElement(Page.serializer(Document.serializer()), get("documents/", query))
}

suspend fun PaperlessClient.document(id: Int, fullPerms: Boolean = false): Document =
    ApiJson.decodeFromJsonElement(Document.serializer(), get("documents/$id/", if (fullPerms) mapOf("full_perms" to "true") else emptyMap()))

suspend fun PaperlessClient.updateDocument(id: Int, patch: JsonObject): Document =
    ApiJson.decodeFromJsonElement(Document.serializer(), patch("documents/$id/", patch.body()))

suspend fun PaperlessClient.notes(id: Int): List<Note> =
    ApiJson.decodeFromJsonElement(ListSerializer(Note.serializer()), get("documents/$id/notes/"))

suspend fun PaperlessClient.addNote(id: Int, text: String): List<Note> =
    ApiJson.decodeFromJsonElement(ListSerializer(Note.serializer()), post("documents/$id/notes/", buildJsonObject { put("note", text) }.body()))

suspend fun PaperlessClient.deleteNote(id: Int, noteId: Int): List<Note> =
    ApiJson.decodeFromJsonElement(ListSerializer(Note.serializer()), execute(request(url("documents/$id/notes/", mapOf("id" to "$noteId"))).delete().build()))

suspend fun PaperlessClient.history(id: Int): List<HistoryEntry> =
    ApiJson.decodeFromJsonElement(ListSerializer(HistoryEntry.serializer()), get("documents/$id/history/"))

suspend fun PaperlessClient.suggestions(id: Int): Suggestions =
    ApiJson.decodeFromJsonElement(Suggestions.serializer(), get("documents/$id/suggestions/"))

suspend fun PaperlessClient.shareLinks(id: Int): List<ShareLink> =
    ApiJson.decodeFromJsonElement(ListSerializer(ShareLink.serializer()), get("documents/$id/share_links/"))

suspend fun PaperlessClient.createShareLink(id: Int, expiration: String?, original: Boolean): ShareLink =
    ApiJson.decodeFromJsonElement(
        ShareLink.serializer(),
        post(
            "share_links/",
            buildJsonObject {
                put("document", id)
                put("expiration", expiration?.let { JsonPrimitive(it) } ?: JsonNull)
                put("file_version", if (original) "original" else "archive")
            }.body(),
        ),
    )

suspend fun PaperlessClient.deleteShareLink(linkId: Int) {
    delete("share_links/$linkId/")
}

suspend fun PaperlessClient.autocomplete(term: String): List<String> =
    runCatching {
        ApiJson.decodeFromJsonElement(ListSerializer(String.serializer()), get("search/autocomplete/", mapOf("term" to term, "limit" to "8")))
    }.getOrDefault(emptyList())

suspend fun PaperlessClient.savedViews() = listAll("saved_views/", SavedView.serializer())
suspend fun PaperlessClient.customFields() = listAll("custom_fields/", CustomField.serializer())
suspend fun PaperlessClient.users() = runCatching { listAll("users/", User.serializer()) }.getOrDefault(emptyList())
suspend fun PaperlessClient.groups() = runCatching { listAll("groups/", Group.serializer()) }.getOrDefault(emptyList())

/** documents/bulk_edit/ with one of Paperless' methods (set_correspondent, modify_tags, …). */
suspend fun PaperlessClient.bulkEdit(ids: Collection<Int>, method: String, parameters: JsonObject = JsonObject(emptyMap())) {
    post(
        "documents/bulk_edit/",
        buildJsonObject {
            put("documents", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            put("method", method)
            put("parameters", parameters)
        }.body(),
    )
}

/**
 * Document operations that moved to their own endpoints in API 10 (documents/delete/, rotate/,
 * merge/, reprocess/); older servers take them as bulk_edit methods.
 */
suspend fun PaperlessClient.documentOperation(op: String, ids: Collection<Int>, extra: Map<String, JsonElement> = emptyMap()) {
    val docs = buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }
    if (apiVersion >= 10) {
        post("documents/$op/", JsonObject(mapOf("documents" to docs) + extra).body())
    } else {
        bulkEdit(ids, op, JsonObject(extra))
    }
}

suspend fun PaperlessClient.trash(page: Int): Page<Document> =
    ApiJson.decodeFromJsonElement(Page.serializer(Document.serializer()), get("trash/", mapOf("page" to "$page", "page_size" to "50")))

suspend fun PaperlessClient.trashAction(action: String, ids: Collection<Int>?) {
    post(
        "trash/",
        buildJsonObject {
            put("action", action)
            if (ids != null) put("documents", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
        }.body(),
    )
}

/** Creates, changes or deletes a tag, correspondent, document type, storage path or custom field. */
suspend fun PaperlessClient.saveObject(path: String, id: Int?, body: JsonObject): JsonElement =
    if (id == null) post("$path/", body.body()) else patch("$path/$id/", body.body())

suspend fun PaperlessClient.deleteObject(path: String, id: Int) {
    delete("$path/$id/")
}

/** The document page in the Paperless web app. */
fun PaperlessClient.webUrl(id: Int): String = "${baseUrl}documents/$id/details"
fun PaperlessClient.shareUrl(slug: String): String = "${baseUrl}share/$slug"
