package com.vknxworks.paperctrl.ui.document

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.paperctrl.AppContainer
import com.vknxworks.paperctrl.data.api.CustomField
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.HistoryEntry
import com.vknxworks.paperctrl.data.api.Note
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.api.ShareLink
import com.vknxworks.paperctrl.data.api.addNote
import com.vknxworks.paperctrl.data.api.customFields
import com.vknxworks.paperctrl.data.api.deleteNote
import com.vknxworks.paperctrl.data.api.document
import com.vknxworks.paperctrl.data.api.documents
import com.vknxworks.paperctrl.data.api.history
import com.vknxworks.paperctrl.data.api.notes
import com.vknxworks.paperctrl.data.api.shareLinks
import com.vknxworks.paperctrl.data.api.updateDocument
import com.vknxworks.paperctrl.data.files.LocalFile
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.PageEditSupport
import com.vknxworks.paperctrl.data.api.PageOp
import com.vknxworks.paperctrl.data.api.TaskState
import com.vknxworks.paperctrl.data.api.deletePages
import com.vknxworks.paperctrl.data.api.editPdf
import com.vknxworks.paperctrl.data.api.pageEditTasks
import com.vknxworks.paperctrl.data.api.splitDocument
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One document and everything around it, loaded when its screen opens. */
class DocumentModel(private val c: AppContainer, private val context: Context, val id: Int) : ViewModel() {
    var doc by mutableStateOf<Document?>(null)
        private set
    var edit by mutableStateOf<DocEdit?>(null)
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var fields by mutableStateOf<List<CustomField>>(emptyList())
        private set
    var linkTitles by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    var notes by mutableStateOf<List<Note>?>(null)
        private set
    var history by mutableStateOf<List<HistoryEntry>?>(null)
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    var links by mutableStateOf<List<ShareLink>>(emptyList())
        private set
    var file by mutableStateOf<LocalFile?>(null)
        private set
    var fileError by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var original by mutableStateOf(false)
        private set
    private var fileJob: Job? = null

    /** A page edit was sent and PaperCtrl is waiting for Paperless to finish it. */
    var pageEditRunning by mutableStateOf(false)
        private set
    /** The document was replaced by the documents a split made (original deleted): leave its screen. */
    var gone by mutableStateOf(false)
        private set

    val client: PaperlessClient? get() = c.sessions.active.value?.client
    val dirty: Boolean get() = doc?.let { edit != null && edit != DocEdit.of(it) } ?: false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val cl = client ?: return@launch
            try {
                val d = cl.document(id, fullPerms = true)
                doc = d
                edit = DocEdit.of(d)
                error = null
                if (file == null) loadFile()
                fields = runCatching { cl.customFields() }.getOrDefault(emptyList())
                loadLinkTitles(d)
                links = runCatching { cl.shareLinks(id) }.getOrDefault(emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.friendlyMessage(context)
            }
        }
    }

    private suspend fun loadLinkTitles(d: Document) {
        val ids = d.customFields.flatMap { cf -> (cf.value as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty() }
        if (ids.isEmpty()) return
        val cl = client ?: return
        linkTitles = linkTitles + runCatching {
            cl.documents(mapOf("id__in" to ids.distinct().joinToString(",")), 1, 100).results.associate { it.id to it.title }
        }.getOrDefault(emptyMap())
    }

    fun addLinkTitle(id: Int, title: String) {
        linkTitles = linkTitles + (id to title)
    }

    fun showOriginal(value: Boolean) {
        if (value == original) return
        original = value
        file = null
        loadFile()
    }

    /** The file was deleted (PaperCtrl locked meanwhile): download it again. */
    fun reloadFile() {
        file = null
        loadFile()
    }

    fun loadFile() {
        val d = doc ?: return
        val cl = client ?: return
        fileJob?.cancel()
        fileError = null
        progress = 0f
        fileJob = viewModelScope.launch {
            try {
                file = c.files.fetch(cl, d, original) { p -> progress = p }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fileError = e.friendlyMessage(context)
            }
        }
    }

    /** Saves the edited fields; returns an error message or null. */
    fun save(onDone: (String?) -> Unit) {
        val d = doc ?: return
        val e = edit ?: return
        val cl = client ?: return
        saving = true
        viewModelScope.launch {
            try {
                val patch = buildJsonObject {
                    put("title", e.title)
                    put("created", e.created?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("correspondent", e.correspondent?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("document_type", e.documentType?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("storage_path", e.storagePath?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("tags", buildJsonArray { e.tags.forEach { add(JsonPrimitive(it)) } })
                    put("archive_serial_number", e.asn?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("custom_fields", buildJsonArray {
                        e.customFields.forEach { cf -> add(buildJsonObject { put("field", cf.field); put("value", cf.value ?: JsonNull) }) }
                    })
                }
                val updated = cl.updateDocument(d.id, patch)
                doc = updated.copy(permissions = d.permissions)
                edit = DocEdit.of(updated)
                onDone(null)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                onDone(ex.friendlyMessage(context))
            } finally {
                saving = false
            }
        }
    }

    fun loadNotes() {
        viewModelScope.launch { client?.let { cl -> notes = runCatching { cl.notes(id) }.getOrDefault(emptyList()) } }
    }

    fun addNote(text: String, onError: (String) -> Unit) {
        val cl = client ?: return
        viewModelScope.launch {
            runCatching { cl.addNote(id, text) }.onSuccess { notes = it }.onFailure { onError(it.friendlyMessage(context)) }
        }
    }

    fun deleteNote(noteId: Int, onError: (String) -> Unit) {
        val cl = client ?: return
        viewModelScope.launch {
            runCatching { cl.deleteNote(id, noteId) }.onSuccess { notes = it }.onFailure { onError(it.friendlyMessage(context)) }
        }
    }

    fun loadHistory() {
        val cl = client ?: return
        viewModelScope.launch {
            runCatching { cl.history(id) }
                .onSuccess { history = it.sortedByDescending { h -> h.timestamp } }
                .onFailure { historyError = it.friendlyMessage(context); history = emptyList() }
        }
    }

    fun refreshLinks() {
        viewModelScope.launch { client?.let { cl -> links = runCatching { cl.shareLinks(id) }.getOrDefault(links) } }
    }

    fun replaceDoc(d: Document) {
        doc = d
        edit = DocEdit.of(d)
    }

    /**
     * Sends a page edit and follows it: the consume tasks Paperless starts for it are found in the task
     * list by their file names, then the document is loaded again (or the screen closes when the
     * original was deleted after a split).
     */
    fun applyPageEdit(plan: PagePlan, support: PageEditSupport) {
        val cl = client ?: return
        if (pageEditRunning) return
        pageEditRunning = true
        viewModelScope.launch {
            fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            try {
                val expected = when (plan) {
                    is PagePlan.Edit -> if (plan.outputs > 1) plan.outputs else if (support.consumesEdits) 1 else 0
                    is PagePlan.DeletePages -> if (support.consumesEdits) 1 else 0
                    is PagePlan.Split -> plan.groupCount
                }
                val before = if (expected > 0) runCatching { cl.pageEditTasks(id).map { it.taskId }.toSet() }.getOrDefault(emptySet()) else emptySet()
                when (plan) {
                    is PagePlan.Edit -> cl.editPdf(id, plan.ops, updateDocument = plan.outputs == 1, deleteOriginal = plan.outputs > 1 && plan.deleteOriginal)
                    is PagePlan.DeletePages -> cl.deletePages(id, plan.pages)
                    is PagePlan.Split -> cl.splitDocument(id, plan.groups, plan.deleteOriginal)
                }
                val replaces = plan.outputs > 1
                if (expected == 0) {
                    // Paperless 2.x changed the file right away; its text is read again in the background.
                    delay(1500)
                    refreshAfterEdit()
                    toast(context.getString(R.string.pages_saved))
                    return@launch
                }
                val until = System.currentTimeMillis() + 5 * 60_000L
                var finished: List<com.vknxworks.paperctrl.data.api.PageEditTask>? = null
                while (System.currentTimeMillis() < until) {
                    delay(2500)
                    val fresh = runCatching { cl.pageEditTasks(id) }.getOrNull()?.filter { it.taskId !in before } ?: continue
                    if (fresh.size >= expected && fresh.all { it.status.state == TaskState.SUCCESS || it.status.state == TaskState.FAILURE }) {
                        finished = fresh
                        break
                    }
                }
                if (finished == null) {
                    toast(context.getString(R.string.pages_still_working))
                    return@launch
                }
                when {
                    finished.any { it.status.state == TaskState.FAILURE } -> {
                        val why = finished.firstNotNullOfOrNull { t -> t.status.message?.takeIf { t.status.state == TaskState.FAILURE } }
                        toast(context.getString(R.string.pages_failed) + (why?.let { "\n\n$it" } ?: ""))
                        if (!replaces) refreshAfterEdit()
                    }
                    replaces -> {
                        toast(context.resources.getQuantityString(R.plurals.pages_split_done, finished.size, finished.size))
                        if (plan.deleteOriginal) gone = true
                    }
                    else -> {
                        refreshAfterEdit()
                        toast(context.getString(R.string.pages_saved))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.friendlyMessage(context))
            } finally {
                pageEditRunning = false
            }
        }
    }

    private suspend fun refreshAfterEdit() {
        val cl = client ?: return
        runCatching { c.files.forget(cl, id) }
        runCatching { cl.document(id, fullPerms = true) }.onSuccess { d ->
            doc = d
            edit = DocEdit.of(d)
            file = null
            loadFile()
        }
    }
}

/** What the page manager asks Paperless to do. */
sealed class PagePlan {
    abstract val outputs: Int
    abstract val deleteOriginal: Boolean

    /** edit_pdf: the new page list (order, rotation, output document). */
    data class Edit(val ops: List<PageOp>, override val outputs: Int, override val deleteOriginal: Boolean) : PagePlan()

    /** delete_pages (servers without edit_pdf). */
    data class DeletePages(val pages: List<Int>) : PagePlan() {
        override val outputs: Int get() = 1
        override val deleteOriginal: Boolean get() = false
    }

    /** split (servers without edit_pdf): [groups] like "1-2,3,4-5". */
    data class Split(val groups: String, val groupCount: Int, override val deleteOriginal: Boolean) : PagePlan() {
        override val outputs: Int get() = groupCount
    }
}
