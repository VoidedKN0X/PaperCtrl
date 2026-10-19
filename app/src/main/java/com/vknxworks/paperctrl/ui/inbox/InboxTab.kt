package com.vknxworks.paperctrl.ui.inbox

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.Suggestions
import com.vknxworks.paperctrl.data.api.bulkEdit
import com.vknxworks.paperctrl.data.api.suggestions
import com.vknxworks.paperctrl.data.api.updateDocument
import com.vknxworks.paperctrl.data.metadata.ServerMetadata
import com.vknxworks.paperctrl.data.search.DocumentQuery
import com.vknxworks.paperctrl.data.search.SortField
import com.vknxworks.paperctrl.notify.WidgetState
import com.vknxworks.paperctrl.ui.components.LocalBottomClearance
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.SwitchAddressBar
import com.vknxworks.paperctrl.ui.components.TabTitle
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.docs.BulkBar
import com.vknxworks.paperctrl.ui.docs.DocumentListModel
import com.vknxworks.paperctrl.ui.docs.DocumentRow
import com.vknxworks.paperctrl.ui.docs.OfflineBanner
import com.vknxworks.paperctrl.ui.docs.rememberMetadata
import com.vknxworks.paperctrl.ui.docs.tagChange
import com.vknxworks.paperctrl.ui.document.DocEdit
import com.vknxworks.paperctrl.ui.document.DetailsEditor
import com.vknxworks.paperctrl.ui.nav.DocumentRoute
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.theme.Gutter
import com.vknxworks.paperctrl.ui.upload.formatDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * New documents to sort: swipe right when a document is done (its inbox tags are removed), swipe
 * left to fix its details with Paperless' suggestions, long-press to select several.
 */
@Composable
fun InboxTab() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val serverId = session?.server?.id ?: return
    val model: DocumentListModel = viewModel(key = "inbox-$serverId") { DocumentListModel(c, context.applicationContext, "inbox") }
    val md = rememberMetadata()
    val inboxTags = md.tags.filter { it.isInboxTag }.map { it.id }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var editing by remember { mutableStateOf<Document?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(serverId, inboxTags) {
        if (inboxTags.isNotEmpty() && (!model.loaded || model.query.tags.toList().sorted() != inboxTags.sorted())) {
            model.load(DocumentQuery(tags = inboxTags.toSet(), sort = SortField.ADDED, reverse = true))
        }
    }
    LaunchedEffect(model.total, model.loaded) {
        if (model.loaded && !model.offline) WidgetState.setInboxCount(context, model.total)
    }

    /** Removes the inbox tags, with an undo that puts them back. */
    fun markDone(doc: Document) {
        val tags = doc.tags.filter { it in inboxTags }
        val at = model.items.indexOfFirst { it.id == doc.id }
        model.remove(listOf(doc.id))
        scope.launch {
            val client = c.sessions.active.value?.client ?: return@launch
            val ok = runCatching { client.bulkEdit(listOf(doc.id), "modify_tags", tagChange(emptyList(), tags)) }
            if (ok.isFailure) {
                model.restore(listOf(doc), at)
                snackbar.showSnackbar(ok.exceptionOrNull()!!.friendlyMessage(context))
                return@launch
            }
            val r = snackbar.showSnackbar(context.getString(R.string.inbox_done_snack, doc.title), context.getString(R.string.undo), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) {
                runCatching { client.bulkEdit(listOf(doc.id), "modify_tags", tagChange(tags, emptyList())) }
                model.restore(listOf(doc), at)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_inbox))
            if (model.offline) OfflineBanner()
            if (model.offline || (model.loaded && model.items.isEmpty() && model.error != null)) SwitchAddressBar { model.refresh() }
            Box(Modifier.weight(1f)) {
                PullToRefreshBox(isRefreshing = model.refreshing, onRefresh = { model.refresh() }, modifier = Modifier.fillMaxSize()) {
                    when {
                        md.tags.isNotEmpty() && inboxTags.isEmpty() -> MessageView(Icons.Outlined.Inbox, stringResource(R.string.inbox_no_tag), stringResource(R.string.inbox_no_tag_body))
                        !model.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        model.items.isEmpty() && model.error != null -> MessageView(Icons.Outlined.CloudOff, stringResource(R.string.load_failed), model.error, actionLabel = stringResource(R.string.retry), onAction = { model.refresh() })
                        model.items.isEmpty() -> MessageView(Icons.Outlined.DoneAll, stringResource(R.string.inbox_empty_done), stringResource(R.string.inbox_empty_done_body))
                        else -> {
                            val state = rememberLazyListState()
                            val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
                            LaunchedEffect(state) { snapshotFlow { nearEnd }.distinctUntilChanged().collect { if (it) model.loadMore() } }
                            LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp), modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        pluralStringResource(R.plurals.inbox_count, model.total, model.total) + "  ·  " + stringResource(R.string.inbox_swipe_hint),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                                    )
                                }
                                items(model.items, key = { it.id }) { doc ->
                                    SwipeRow(
                                        enabled = selected.isEmpty(),
                                        onDone = { markDone(doc) },
                                        onEdit = { editing = doc },
                                    ) {
                                        DocumentRow(
                                            doc, md, doc.id in selected, selected.isNotEmpty(),
                                            onClick = {
                                                if (selected.isNotEmpty()) {
                                                    selected = if (doc.id in selected) selected - doc.id else selected + doc.id
                                                } else {
                                                    nav.navigate(DocumentRoute(doc.id))
                                                }
                                            },
                                            onLongClick = { selected = if (doc.id in selected) selected - doc.id else selected + doc.id },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (selected.isNotEmpty()) {
                BulkBar(selected, md, onClear = { selected = emptySet() }, inboxTags = inboxTags, onChanged = { removed ->
                    if (removed) model.remove(selected)
                    selected = emptySet()
                    model.refresh()
                })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current))
    }

    editing?.let { doc ->
        QuickEditSheet(doc, md, inboxTags, onDismiss = { editing = null }) { updated, done ->
            editing = null
            if (done) model.remove(listOf(updated.id)) else model.replace(updated)
        }
    }
}

@Composable
private fun SwipeRow(enabled: Boolean, onDone: () -> Unit, onEdit: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { onDone(); true }
                SwipeToDismissBoxValue.EndToStart -> { onEdit(); false }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize()
                    .background(if (toEnd) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (toEnd) Icons.Outlined.DoneAll else Icons.Outlined.Edit,
                    null,
                    tint = if (toEnd) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "  " + stringResource(if (toEnd) R.string.inbox_mark_done else R.string.inbox_quick_edit),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (toEnd) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) { content() }
}

/** Fix a document's details right from the inbox, with Paperless' suggestions one tap away. */
@Composable
private fun QuickEditSheet(doc: Document, md: ServerMetadata, inboxTags: List<Int>, onDismiss: () -> Unit, onSaved: (Document, Boolean) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(DocEdit.of(doc)) }
    var suggestions by remember { mutableStateOf<Suggestions?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(doc.id) {
        suggestions = runCatching { c.sessions.active.value?.client?.suggestions(doc.id) }.getOrNull()
    }

    fun save(done: Boolean) {
        val client = c.sessions.active.value?.client ?: return
        busy = true
        scope.launch {
            val tags = if (done) edit.tags.filterNot { it in inboxTags } else edit.tags
            runCatching {
                client.updateDocument(doc.id, buildJsonObject {
                    put("title", JsonPrimitive(edit.title))
                    put("created", edit.created?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("correspondent", edit.correspondent?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("document_type", edit.documentType?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("storage_path", edit.storagePath?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
                    put("archive_serial_number", edit.asn?.let { JsonPrimitive(it) } ?: JsonNull)
                })
            }.onSuccess { onSaved(it, done) }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show(); busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            val s = suggestions
            if (s != null && (s.correspondents.isNotEmpty() || s.documentTypes.isNotEmpty() || s.tags.isNotEmpty() || s.dates.isNotEmpty() || s.storagePaths.isNotEmpty())) {
                Text(stringResource(R.string.inbox_suggestions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, bottom = 6.dp))
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.correspondents.mapNotNull { md.correspondent(it) }.filter { it.id != edit.correspondent }.forEach { o ->
                        AssistChip(onClick = { edit = edit.copy(correspondent = o.id) }, label = { Text(o.name) }, leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(14.dp)) })
                    }
                    s.documentTypes.mapNotNull { md.documentType(it) }.filter { it.id != edit.documentType }.forEach { o ->
                        AssistChip(onClick = { edit = edit.copy(documentType = o.id) }, label = { Text(o.name) })
                    }
                    s.storagePaths.mapNotNull { md.storagePath(it) }.filter { it.id != edit.storagePath }.forEach { o ->
                        AssistChip(onClick = { edit = edit.copy(storagePath = o.id) }, label = { Text(o.name) })
                    }
                    s.tags.mapNotNull { md.tag(it) }.filter { it.id !in edit.tags }.forEach { t ->
                        AssistChip(onClick = { edit = edit.copy(tags = edit.tags + t.id) }, label = { Text("+ " + t.name) })
                    }
                    s.dates.filter { it != edit.created }.take(3).forEach { d ->
                        AssistChip(onClick = { edit = edit.copy(created = d) }, label = { Text(formatDate(d)) })
                    }
                }
            }
            DetailsEditor(
                edit = edit,
                md = md,
                fields = emptyList(),
                canEdit = doc.userCanChange,
                linkTitles = emptyMap(),
                onChange = { edit = it.copy(customFields = doc.customFields) },
                onPickDocument = {},
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    androidx.compose.material3.OutlinedButton(onClick = { save(false) }, modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
                        Text(stringResource(R.string.save))
                    }
                    PillButton(stringResource(R.string.inbox_save_done), onClick = { save(true) }, modifier = Modifier.weight(1.4f))
                }
            }
        }
    }
}
