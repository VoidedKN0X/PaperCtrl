package com.vknxworks.paperctrl.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * What a server can do to a document's pages.
 *
 * - Every server PaperCtrl supports (API 9+, Paperless-ngx 2.16 and later) has the bulk_edit methods
 *   `delete_pages` and `split` (pages as "1-2,3,4-5").
 * - Paperless-ngx 2.18 added `edit_pdf`: a list of `{page, rotate, doc}` operations that describes the
 *   new PDF(s) page by page, so it reorders, rotates single pages, drops pages and splits in one go.
 *   There is no separate "reorder" method: the order of the operations is the new page order.
 * - Paperless-ngx 3 (API 10) moved it to documents/edit_pdf/, and saves edits to the same document as a
 *   new version through a consume task. 2.x changes the file in place.
 */
data class PageEditSupport(
    /** edit_pdf is there: reorder and per-page rotation work. */
    val editPdf: Boolean,
    /** Paperless-ngx 3+: every change goes through a consume task (2.x edits the file in place). */
    val consumesEdits: Boolean,
)

fun pageEditSupport(apiVersion: Int, serverVersion: String?): PageEditSupport {
    val v = serverVersion?.trim()?.removePrefix("v")?.split('.', '-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }
    fun atLeast(vararg want: Int): Boolean {
        val have = v ?: return false
        for (i in want.indices) {
            val h = have.getOrElse(i) { 0 }
            if (h != want[i]) return h > want[i]
        }
        return true
    }
    val three = apiVersion >= 10 || atLeast(3)
    return PageEditSupport(editPdf = three || atLeast(2, 18), consumesEdits = three)
}

/** One page of the new PDF: [page] is the 1-based page in the current file, [doc] the output document. */
data class PageOp(val page: Int, val rotate: Int = 0, val doc: Int = 0)

/**
 * edit_pdf: [updateDocument] writes the result back to this document (one output only); otherwise every
 * output [PageOp.doc] becomes a new document and [deleteOriginal] removes this one once they're in.
 */
suspend fun PaperlessClient.editPdf(id: Int, ops: List<PageOp>, updateDocument: Boolean, deleteOriginal: Boolean) {
    val operations = buildJsonArray {
        ops.forEach { op ->
            add(
                buildJsonObject {
                    put("page", op.page)
                    val r = ((op.rotate % 360) + 360) % 360
                    if (r != 0) put("rotate", r)
                    if (op.doc != 0) put("doc", op.doc)
                },
            )
        }
    }
    val params: Map<String, JsonElement> = mapOf(
        "operations" to operations,
        "update_document" to JsonPrimitive(updateDocument),
        "delete_original" to JsonPrimitive(deleteOriginal),
        "include_metadata" to JsonPrimitive(true),
    )
    if (apiVersion >= 10) {
        post("documents/edit_pdf/", JsonObject(mapOf("documents" to buildJsonArray { add(JsonPrimitive(id)) }) + params).body())
    } else {
        bulkEdit(listOf(id), "edit_pdf", JsonObject(params))
    }
}

/** bulk_edit delete_pages: removes the 1-based [pages] from the document. */
suspend fun PaperlessClient.deletePages(id: Int, pages: List<Int>) {
    bulkEdit(listOf(id), "delete_pages", buildJsonObject { put("pages", buildJsonArray { pages.forEach { add(JsonPrimitive(it)) } }) })
}

/** bulk_edit split: [groups] like "1-2,3,4-5", one new document per group. */
suspend fun PaperlessClient.splitDocument(id: Int, groups: String, deleteOriginals: Boolean) {
    bulkEdit(listOf(id), "split", buildJsonObject { put("pages", groups); put("delete_originals", deleteOriginals) })
}

/** A consume task Paperless started for a page edit. */
data class PageEditTask(val taskId: String, val status: TaskStatus)

/**
 * Consume tasks for files Paperless made from document [docId] while editing its pages. Page edits don't
 * answer with a task id, but their temporary files are always named after the document
 * ("12_edited.pdf", "12_edit_1.pdf", "12_pages_deleted.pdf", "12_1-3.pdf"), which the task list shows.
 * Only tasks nobody has dismissed yet are asked for, which keeps the list short on servers that don't
 * page it (API 9).
 */
suspend fun PaperlessClient.pageEditTasks(docId: Int): List<PageEditTask> {
    val query = if (apiVersion >= 10) {
        mapOf("task_type" to "consume_file", "name" to "${docId}_", "acknowledged" to "false", "page_size" to "100")
    } else {
        mapOf("task_name" to "consume_file", "name" to "${docId}_", "acknowledged" to "false")
    }
    val list = when (val json = get("tasks/", query)) {
        is JsonArray -> json
        is JsonObject -> json["results"] as? JsonArray
        else -> null
    } ?: return emptyList()
    return list.mapNotNull { e ->
        val t = e as? JsonObject ?: return@mapNotNull null
        val id = (t["task_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val name = ((t["input_data"] as? JsonObject)?.get("filename") as? JsonPrimitive)?.contentOrNull
            ?: (t["task_file_name"] as? JsonPrimitive)?.contentOrNull
            ?: return@mapNotNull null
        if (!name.startsWith("${docId}_") || !name.endsWith(".pdf", ignoreCase = true)) return@mapNotNull null
        PageEditTask(id, parseTask(t))
    }
}

// ---------- Statistics ----------

data class FileTypeCount(val mimeType: String, val count: Int)

data class ServerStatistics(
    val documentsTotal: Int,
    val documentsInbox: Int?,
    val inboxTag: Int?,
    val fileTypes: List<FileTypeCount>,
    val characterCount: Long,
    val tagCount: Int,
    val correspondentCount: Int,
    val documentTypeCount: Int,
    val storagePathCount: Int,
    val currentAsn: Int?,
)

/** GET statistics/: totals for the documents this account can see. */
suspend fun PaperlessClient.statistics(): ServerStatistics {
    val j = get("statistics/") as? JsonObject ?: JsonObject(emptyMap())
    fun int(key: String): Int? = (j[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()
    val types = (j["document_file_type_counts"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val mime = (o["mime_type"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val count = (o["mime_type_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        FileTypeCount(mime, count)
    }.sortedByDescending { it.count }
    return ServerStatistics(
        documentsTotal = int("documents_total") ?: 0,
        documentsInbox = int("documents_inbox"),
        inboxTag = int("inbox_tag"),
        fileTypes = types,
        characterCount = (j["character_count"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L,
        tagCount = int("tag_count") ?: 0,
        correspondentCount = int("correspondent_count") ?: 0,
        documentTypeCount = int("document_type_count") ?: 0,
        storagePathCount = int("storage_path_count") ?: 0,
        currentAsn = int("current_asn")?.takeIf { it > 0 },
    )
}
