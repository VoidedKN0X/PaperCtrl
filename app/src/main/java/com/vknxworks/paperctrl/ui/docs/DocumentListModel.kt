package com.vknxworks.paperctrl.ui.docs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.paperctrl.AppContainer
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.api.documents
import com.vknxworks.paperctrl.data.api.isUnreachable
import com.vknxworks.paperctrl.data.search.DocumentQuery
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A paged document list (Documents tab, Inbox): loads page by page as you scroll, and falls back to
 * the last saved first page when the server can't be reached.
 */
class DocumentListModel(private val c: AppContainer, private val context: Context, private val cacheName: String) : ViewModel() {
    var query by mutableStateOf(DocumentQuery())
        private set
    var items by mutableStateOf<List<Document>>(emptyList())
        private set
    var total by mutableStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var offline by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    private var page = 0
    private var endReached = false
    private var job: Job? = null
    private var extraParams: Map<String, String> = emptyMap()

    /** Loads the first page for [q] (and extra raw parameters, e.g. the inbox tags). */
    fun load(q: DocumentQuery, extra: Map<String, String> = extraParams, pull: Boolean = false) {
        query = q
        extraParams = extra
        job?.cancel()
        page = 0
        endReached = false
        if (pull) refreshing = true else loading = true
        error = null
        job = viewModelScope.launch {
            fetch(reset = true)
            refreshing = false
        }
    }

    fun refresh() = load(query, extraParams, pull = true)

    fun loadMore() {
        if (loading || endReached || job?.isActive == true || offline) return
        loading = true
        job = viewModelScope.launch { fetch(reset = false) }
    }

    private suspend fun fetch(reset: Boolean) {
        val session = c.sessions.active.value ?: run { loading = false; return }
        val params = query.toParams() + extraParams
        val cacheKey = "$cacheName|${params.toSortedMap()}"
        try {
            val next = page + 1
            val result = session.client.documents(params, next)
            page = next
            items = if (reset) result.results else (items + result.results).distinctBy { it.id }
            total = result.count
            endReached = result.next == null || items.size >= result.count
            offline = false
            if (next == 1) c.listCache.save(session.server.id, cacheKey, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = if (reset && (isUnreachable(e) || e is PaperlessException && e.httpCode == null)) {
                c.listCache.load(session.server.id, cacheKey)
            } else {
                null
            }
            if (cached != null) {
                items = cached.results
                total = cached.count
                offline = true
                endReached = true
            } else {
                error = e.friendlyMessage(context)
            }
        } finally {
            loading = false
            loaded = true
        }
    }

    /** Local changes after an edit, so the list doesn't wait for a reload. */
    fun replace(doc: Document) {
        items = items.map { if (it.id == doc.id) doc.copy(searchHit = it.searchHit) else it }
    }

    fun remove(ids: Collection<Int>) {
        items = items.filterNot { it.id in ids }
        total = (total - ids.size).coerceAtLeast(0)
    }

    fun restore(docs: List<Document>, at: Int) {
        val list = items.toMutableList()
        list.addAll(at.coerceIn(0, list.size), docs.filterNot { d -> list.any { it.id == d.id } })
        items = list
        total += docs.size
    }
}
