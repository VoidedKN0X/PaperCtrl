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
import com.vknxworks.paperctrl.data.api.deleteVersion
import com.vknxworks.paperctrl.data.api.labelVersion
import com.vknxworks.paperctrl.data.api.removePassword
import com.vknxworks.paperctrl.data.api.task
import com.vknxworks.paperctrl.data.api.uploadVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.api.isUnreachable
import com.vknxworks.paperctrl.data.files.OfflineEntry
import com.vknxworks.paperctrl.data.files.OfflineNoKeyException
import com.vknxworks.paperctrl.data.files.OfflineTooLargeException
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

    /** What the "working" line under the tabs says while [pageEditRunning]. */
    var workingText by mutableStateOf(R.string.pages_working)
        private set

    /** The version of the file shown in the preview (Paperless-ngx 3); null is the current one. */
    var version by mutableStateOf<Int?>(null)
        private set

    /** A page edit was sent and PaperCtrl is waiting for Paperless to finish it. */
    var pageEditRunning by mutableStateOf(false)
        private set
    /** The document was replaced by the documents a split made (original deleted): leave its screen. */
    var gone by mutableStateOf(false)
        private set

    /** Set when the server couldn't be reached and the copy kept on this phone is shown instead. */
    var offlineCopy by mutableStateOf<OfflineEntry?>(null)
        private set
    var keepBusy by mutableStateOf(false)
        private set

    val client: PaperlessClient? get() = c.sessions.active.value?.client
    private val serverId: String? get() = c.sessions.active.value?.server?.id
    val dirty: Boolean get() = doc?.let { edit != null && edit != DocEdit.of(it) } ?: false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val cl = client ?: return@launch
            try {
                val d = cl.document(id, fullPerms = true)
                val wasOffline = offlineCopy != null
                offlineCopy = null
                doc = d
                edit = DocEdit.of(d)
                error = null
                if (file == null || wasOffline) {
                    file = null
                    loadFile()
                }
                fields = runCatching { cl.customFields() }.getOrDefault(emptyList())
                loadLinkTitles(d)
                links = runCatching { cl.shareLinks(id) }.getOrDefault(emptyList())
                refreshKeptCopy(d)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The server can't be reached: show the copy kept on this phone, when there is one.
                val sid = serverId
                val kept = if (doc == null && sid != null && (isUnreachable(e) || e is PaperlessException && e.httpCode == null)) {
                    runCatching { c.offline.entry(sid, id) }.getOrNull()
                } else {
                    null
                }
                if (kept != null) {
                    // Shown read-only: nothing can be saved to the server from here.
                    val d = kept.doc.copy(userCanChange = false)
                    doc = d
                    edit = DocEdit.of(d)
                    offlineCopy = kept
                    error = null
                    loadFile()
                } else {
                    error = e.friendlyMessage(context)
                }
            }
        }
    }

    /** Keeps this document on the phone (encrypted), or takes the kept copy off again. */
    fun setKept(keep: Boolean) {
        val sid = serverId ?: return
        val d = doc ?: return
        if (keepBusy) return
        keepBusy = true
        viewModelScope.launch {
            try {
                if (!keep) {
                    c.offline.remove(sid, id)
                    toast(context.getString(R.string.offline_removed))
                    return@launch
                }
                val cl = client ?: return@launch
                // The file as the preview shows it by default: the searchable PDF when there is one.
                val f = c.files.fetch(cl, d, original = false)
                c.offline.keep(sid, d, f, original = !d.hasArchive)
                toast(context.getString(R.string.offline_kept))
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineTooLargeException) {
                toast(context.getString(R.string.offline_too_large))
            } catch (e: OfflineNoKeyException) {
                toast(context.getString(R.string.offline_no_key))
            } catch (e: Exception) {
                toast(e.friendlyMessage(context))
            } finally {
                keepBusy = false
            }
        }
    }

    /** A kept copy follows the document: when it changed on the server since it was saved, it's saved again. */
    private fun refreshKeptCopy(d: Document) {
        val sid = serverId ?: return
        if (!c.offline.isKept(sid, id)) return
        val cl = client ?: return
        viewModelScope.launch {
            runCatching {
                val saved = c.offline.entry(sid, id)
                if (saved != null && saved.doc.modified == d.modified) return@runCatching
                val f = c.files.fetch(cl, d, original = false)
                c.offline.keep(sid, d, f, original = !d.hasArchive)
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
        val sid = serverId
        if (offlineCopy != null && sid != null) {
            fileJob?.cancel()
            fileError = null
            fileJob = viewModelScope.launch {
                try {
                    file = c.offline.open(sid, id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fileError = context.getString(R.string.offline_unreadable)
                }
            }
            return
        }
        val cl = client ?: return
        fileJob?.cancel()
        fileError = null
        progress = 0f
        fileJob = viewModelScope.launch {
            try {
                file = c.files.fetch(cl, d, original, version) { p -> progress = p }
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
        workingText = R.string.pages_working
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

    /** Shows version [v] of the file in the preview; null (or the newest one) goes back to the current file. */
    fun showVersion(v: Int?) {
        val pick = v?.takeIf { it != doc?.versions?.firstOrNull()?.id }
        if (pick == version) return
        version = pick
        file = null
        loadFile()
    }

    /** Sets or clears a version's label. */
    fun labelVersion(versionId: Int, label: String?) {
        val cl = client ?: return
        viewModelScope.launch {
            runCatching { cl.labelVersion(id, versionId, label) }
                .onSuccess { reloadDetails() }
                .onFailure { toast(it.friendlyMessage(context)) }
        }
    }

    /** Deletes one version for good; the preview goes back to the current file. */
    fun deleteVersion(versionId: Int) {
        val cl = client ?: return
        viewModelScope.launch {
            runCatching { cl.deleteVersion(id, versionId) }
                .onSuccess {
                    version = null
                    refreshAfterEdit()
                    toast(context.getString(R.string.versions_deleted))
                }
                .onFailure { toast(it.friendlyMessage(context)) }
        }
    }

    /**
     * Uploads [upload] as a new version of this document and waits for Paperless to finish reading it.
     * [upload] is a temporary copy and is deleted afterwards.
     */
    fun addVersion(upload: java.io.File, fileName: String, mimeType: String, label: String?) {
        val cl = client ?: run { upload.delete(); return }
        if (pageEditRunning) { upload.delete(); return }
        workingText = R.string.doc_working
        pageEditRunning = true
        viewModelScope.launch {
            try {
                val taskId = try {
                    cl.uploadVersion(id, upload, fileName, mimeType, label)
                } finally {
                    withContext(Dispatchers.IO) { upload.delete() }
                }
                val until = System.currentTimeMillis() + 5 * 60_000L
                var result: com.vknxworks.paperctrl.data.api.TaskStatus? = null
                while (System.currentTimeMillis() < until) {
                    delay(2500)
                    val t = runCatching { cl.task(taskId) }.getOrNull() ?: continue
                    if (t.state == TaskState.SUCCESS || t.state == TaskState.FAILURE) {
                        result = t
                        break
                    }
                }
                when {
                    result == null -> toast(context.getString(R.string.doc_still_working))
                    result.state == TaskState.FAILURE ->
                        toast(context.getString(R.string.versions_add_failed) + (result.message?.let { "\n\n$it" } ?: ""))
                    else -> {
                        version = null
                        refreshAfterEdit()
                        toast(context.getString(R.string.versions_added))
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

    /**
     * Asks Paperless to remove the PDF's password. The unprotected file comes back as a new version through
     * a consume task, found in the task list the same way page edits are.
     */
    fun removePassword(password: String) {
        val cl = client ?: return
        if (pageEditRunning) return
        workingText = R.string.doc_working
        pageEditRunning = true
        viewModelScope.launch {
            try {
                val before = runCatching { cl.pageEditTasks(id).map { it.taskId }.toSet() }.getOrDefault(emptySet())
                try {
                    cl.removePassword(id, password)
                } catch (e: com.vknxworks.paperctrl.data.api.PaperlessException) {
                    // Paperless answers a wrong password with a plain "check the logs" error.
                    if (e.httpCode == 400) {
                        toast(context.getString(R.string.password_remove_failed))
                        return@launch
                    }
                    throw e
                }
                val until = System.currentTimeMillis() + 5 * 60_000L
                var finished: com.vknxworks.paperctrl.data.api.PageEditTask? = null
                var polls = 0
                while (System.currentTimeMillis() < until) {
                    delay(2500)
                    polls++
                    val fresh = runCatching { cl.pageEditTasks(id) }.getOrNull()?.filter { it.taskId !in before } ?: continue
                    val done = fresh.firstOrNull { it.status.state == TaskState.SUCCESS || it.status.state == TaskState.FAILURE }
                    if (done != null) {
                        finished = done
                        break
                    }
                    // No task at all after a while: the PDF had no password, so Paperless left it alone.
                    if (fresh.isEmpty() && polls >= 6) break
                }
                when {
                    finished == null && polls >= 6 && System.currentTimeMillis() < until -> toast(context.getString(R.string.password_remove_nothing))
                    finished == null -> toast(context.getString(R.string.doc_still_working))
                    finished.status.state == TaskState.FAILURE ->
                        toast(context.getString(R.string.password_remove_failed) + (finished.status.message?.let { "\n\n$it" } ?: ""))
                    else -> {
                        version = null
                        refreshAfterEdit()
                        toast(context.getString(R.string.password_removed))
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

    /**
     * Live updates: asks the server whether the document changed since it was loaded, and loads it again when
     * it did. Left alone while there are unsaved edits, a save or a page edit is running, or the kept copy is shown.
     */
    suspend fun quietRefresh() {
        val cl = client ?: return
        val d = doc ?: return
        if (dirty || saving || pageEditRunning || offlineCopy != null) return
        val modified = try {
            ((cl.get("documents/$id/", mapOf("fields" to "id,modified")) as? kotlinx.serialization.json.JsonObject)?.get("modified") as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (modified == null || modified == d.modified) return
        if (dirty || saving || pageEditRunning) return
        runCatching { c.files.forget(cl, id) }
        runCatching { cl.document(id, fullPerms = true) }.onSuccess { fresh ->
            if (dirty || saving || pageEditRunning) return@onSuccess
            doc = fresh
            edit = DocEdit.of(fresh)
            if (version == null) {
                file = null
                loadFile()
            }
            refreshKeptCopy(fresh)
        }
    }

    /** Reads the document's details again without touching the edits or the file on screen. */
    private suspend fun reloadDetails() {
        val cl = client ?: return
        runCatching { cl.document(id, fullPerms = true) }.onSuccess { d ->
            val untouched = !dirty
            doc = d
            if (untouched) edit = DocEdit.of(d)
        }
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

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
