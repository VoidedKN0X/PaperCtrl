package com.vknxworks.paperctrl.ui.document

import com.vknxworks.paperctrl.ui.lock.AppLock
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.documentOperation
import com.vknxworks.paperctrl.data.api.webUrl
import com.vknxworks.paperctrl.data.search.DocumentQuery
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.docs.rememberMetadata
import com.vknxworks.paperctrl.ui.nav.DocumentsRoute
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.theme.Gutter
import com.vknxworks.paperctrl.ui.upload.formatDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private enum class DocTab(val label: Int) { PREVIEW(R.string.doc_tab_preview), DETAILS(R.string.doc_tab_details), CONTENT(R.string.doc_tab_content), NOTES(R.string.doc_tab_notes), HISTORY(R.string.doc_tab_history) }

@Composable
fun DocumentScreen(id: Int) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val model: DocumentModel = viewModel(key = "doc-$id") { DocumentModel(c, context.applicationContext, id) }
    val md = rememberMetadata()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var linkPicker by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    val doc = model.doc

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    fun withFile(action: (com.vknxworks.paperctrl.data.files.LocalFile) -> Unit) {
        val f = model.file
        if (f != null) action(f) else toast(context.getString(R.string.doc_file_loading))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(doc?.title ?: "") {
                if (doc != null) {
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.Link, stringResource(R.string.share_links)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.doc_download)) }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = {
                                menu = false
                                withFile { f -> scope.launch { runCatching { c.files.saveToDownloads(f) }.onSuccess { toast(context.getString(R.string.download_saved, it)) }.onFailure { toast(it.friendlyMessage(context)) } } }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.doc_share_file)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                menu = false
                                withFile { f -> AppLock.expectReturn(); context.startActivity(Intent.createChooser(c.files.shareIntent(f), null)) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.viewer_open_with)) }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) }, onClick = {
                                menu = false
                                withFile { f -> AppLock.expectReturn(); runCatching { context.startActivity(c.files.openWithIntent(f)) }.onFailure { toast(context.getString(R.string.open_link_failed)) } }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.doc_open_browser)) }, leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) }, onClick = {
                                menu = false
                                val url = model.client?.webUrl(id) ?: return@DropdownMenuItem
                                AppLock.expectReturn(); try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: ActivityNotFoundException) { toast(context.getString(R.string.open_link_failed)) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.doc_more_like)) }, leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) }, onClick = {
                                menu = false
                                c.pendingQuery.value = DocumentQuery(moreLike = id, moreLikeTitle = doc.title, sort = com.vknxworks.paperctrl.data.search.SortField.SCORE)
                                nav.nav.navigate(DocumentsRoute) { launchSingleTop = true }
                            })
                            if (doc.userCanChange) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.doc_reprocess)) }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = {
                                    menu = false
                                    scope.launch {
                                        runCatching { model.client?.documentOperation("reprocess", listOf(id)) }
                                            .onSuccess { toast(context.getString(R.string.doc_reprocess_started)) }
                                            .onFailure { toast(it.friendlyMessage(context)) }
                                    }
                                })
                            }
                            val me = session?.server
                            if (me != null && (doc.owner == null || doc.owner == me.userId || me.isSuperuser)) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.doc_permissions)) }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = { menu = false; permissions = true })
                            }
                            if (doc.userCanChange) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; deleting = true },
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == DocTab.DETAILS.ordinal && model.dirty) {
                ExtendedFloatingActionButton(
                    onClick = { model.save { err -> if (err != null) toast(err) else toast(context.getString(R.string.doc_saved)) } },
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    if (model.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        when {
            doc == null && model.error != null -> Box(Modifier.padding(padding).fillMaxSize()) {
                MessageView(Icons.Outlined.Description, stringResource(R.string.load_failed), model.error, actionLabel = stringResource(R.string.retry), onAction = { model.load() })
            }
            doc == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = Gutter, containerColor = MaterialTheme.colorScheme.background) {
                    DocTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t.ordinal,
                            onClick = { tab = t.ordinal },
                            text = { Text(stringResource(t.label) + if (t == DocTab.NOTES && doc.noteCount > 0) " (${doc.noteCount})" else "") },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (DocTab.entries[tab]) {
                        DocTab.PREVIEW -> Column(Modifier.fillMaxSize()) {
                            if (doc.hasArchive) {
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                                    SegmentedButton(selected = !model.original, onClick = { model.showOriginal(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.doc_archived)) }
                                    SegmentedButton(selected = model.original, onClick = { model.showOriginal(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.doc_original)) }
                                }
                            }
                            // Deleted when PaperCtrl locked (app lock on): download it again after unlocking.
                            val f = model.file?.takeIf { it.file.exists() }
                            LaunchedEffect(model.file, f) { if (model.file != null && f == null) model.reloadFile() }
                            when {
                                f != null -> DocumentViewer(f, onOpenWith = { AppLock.expectReturn(); runCatching { context.startActivity(c.files.openWithIntent(f)) } }, modifier = Modifier.weight(1f))
                                model.fileError != null -> MessageView(Icons.Outlined.Description, stringResource(R.string.load_failed), model.fileError, actionLabel = stringResource(R.string.retry), onAction = { model.loadFile() })
                                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    if (model.progress > 0f) LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.padding(24.dp).fillMaxWidth())
                                }
                            }
                        }
                        DocTab.DETAILS -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 96.dp)) {
                            model.edit?.let { e ->
                                DetailsEditor(
                                    edit = e,
                                    md = md,
                                    fields = model.fields,
                                    canEdit = doc.userCanChange,
                                    linkTitles = model.linkTitles,
                                    onChange = { model.edit = it },
                                    onPickDocument = { onPicked -> linkPicker = onPicked },
                                )
                            }
                            Text(
                                listOfNotNull(
                                    doc.added?.let { stringResource(R.string.doc_added_on, formatDate(it.take(10))) },
                                    doc.pageCount?.let { stringResource(R.string.doc_pages, it) },
                                    doc.originalFileName,
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                            )
                        }
                        DocTab.CONTENT -> ContentTab(doc.content.orEmpty())
                        DocTab.NOTES -> NotesTab(model)
                        DocTab.HISTORY -> HistoryTab(model, md)
                    }
                }
            }
        }
    }

    if (sharing && doc != null) ShareLinksSheet(model, onDismiss = { sharing = false })
    if (permissions && doc != null) PermissionsSheet(model, onDismiss = { permissions = false })
    linkPicker?.let { onPicked ->
        DocumentSearchSheet(excludeId = id, onDismiss = { linkPicker = null }) { picked ->
            linkPicker = null
            model.addLinkTitle(picked.id, picked.title)
            onPicked(picked.id)
        }
    }
    if (deleting) {
        val trashDays = remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(Unit) {
            trashDays.value = runCatching { (model.client?.uiSettings()?.settings?.get("trash_delay") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.getOrNull()
        }
        ConfirmDialog(
            stringResource(R.string.doc_delete_title),
            trashDays.value?.let { stringResource(R.string.doc_delete_body_trash, it) } ?: stringResource(R.string.delete_documents_body),
            stringResource(R.string.delete),
            onDismiss = { deleting = false },
        ) {
            deleting = false
            scope.launch {
                runCatching { model.client?.documentOperation("delete", listOf(id)) }
                    .onSuccess { nav.back() }
                    .onFailure { toast(it.friendlyMessage(context)) }
            }
        }
    }
}

@Composable
private fun ContentTab(content: String) {
    var find by remember { mutableStateOf("") }
    val matches = remember(find, content) {
        if (find.length < 2) emptyList() else Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(content).map { it.range }.toList()
    }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = find,
            onValueChange = { find = it },
            placeholder = { Text(stringResource(R.string.doc_find)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            supportingText = if (find.length >= 2) { { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.doc_matches, matches.size, matches.size)) } } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
        )
        if (content.isBlank()) {
            MessageView(Icons.Outlined.Description, stringResource(R.string.doc_no_content), stringResource(R.string.doc_no_content_body))
            return
        }
        val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        val text = remember(content, matches) {
            buildAnnotatedString {
                append(content)
                matches.forEach { addStyle(SpanStyle(background = highlight), it.first, it.last + 1) }
            }
        }
        SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp).navigationBarsPadding()) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NotesTab(model: DocumentModel) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { if (model.notes == null) model.loadNotes() }
    val notes = model.notes
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f)) {
            when {
                notes == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                notes.isEmpty() -> MessageView(Icons.Outlined.Description, stringResource(R.string.notes_none), stringResource(R.string.notes_none_body))
                else -> LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(notes.sortedByDescending { it.created }, key = { it.id }) { n ->
                        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        listOfNotNull(n.author, n.created?.let { formatDate(it.take(10)) }).joinToString("  ·  "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { removing = n.id }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), Modifier.size(20.dp)) }
                                }
                                SelectionContainer { Text(n.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp)) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.notes_add)) },
                maxLines = 5,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    model.addNote(t) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    text = ""
                }
            }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.notes_send)) }
        }
    }
    removing?.let { noteId ->
        ConfirmDialog(stringResource(R.string.notes_delete_title), stringResource(R.string.notes_delete_body), stringResource(R.string.delete), onDismiss = { removing = null }) {
            removing = null
            model.deleteNote(noteId) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun HistoryTab(model: DocumentModel, md: com.vknxworks.paperctrl.data.metadata.ServerMetadata) {
    LaunchedEffect(Unit) { if (model.history == null) model.loadHistory() }
    val history = model.history
    when {
        history == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        history.isEmpty() -> MessageView(Icons.Outlined.Description, stringResource(R.string.history_none), model.historyError ?: stringResource(R.string.history_none_body))
        else -> LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history, key = { it.id }) { h ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large).padding(14.dp)) {
                    Text(
                        listOfNotNull(h.action?.replaceFirstChar { it.uppercase() }, h.actorName, h.timestamp?.let { formatDate(it.take(10)) + " " + it.drop(11).take(5) }).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    (h.changes as? JsonObject)?.forEach { (field, change) ->
                        Text(
                            "$field: ${describeChange(field, change, md)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "old → new" for an audit log change, with names for tags and other labels where they're known. */
private fun describeChange(field: String, change: JsonElement, md: com.vknxworks.paperctrl.data.metadata.ServerMetadata): String {
    fun name(v: JsonElement?): String {
        val s = (v as? JsonPrimitive)?.contentOrNull ?: return v?.toString()?.take(120) ?: "–"
        val id = s.toIntOrNull()
        return when {
            s == "None" || s == "null" -> "–"
            id != null && field == "correspondent" -> md.correspondent(id)?.name ?: s
            id != null && field == "document_type" -> md.documentType(id)?.name ?: s
            id != null && field == "storage_path" -> md.storagePath(id)?.name ?: s
            else -> s.take(120)
        }
    }
    return when (change) {
        is JsonArray -> if (change.size == 2) "${name(change[0])} → ${name(change[1])}" else change.toString().take(160)
        is JsonObject -> {
            val objects = change["objects"] as? JsonArray
            val op = (change["operation"] as? JsonPrimitive)?.contentOrNull
            if (objects != null) {
                val names = objects.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.map { o -> o.toIntOrNull()?.let { md.tag(it)?.name } ?: o }
                "${op ?: ""} ${names.joinToString(", ")}".trim()
            } else {
                change.toString().take(160)
            }
        }
        else -> name(change)
    }
}

