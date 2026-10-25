package com.vknxworks.paperctrl.ui.docs

import com.vknxworks.paperctrl.ui.components.LoadingView
import com.vknxworks.paperctrl.ui.components.SectionLabel
import com.vknxworks.paperctrl.ui.components.CtrlTopBar
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import com.vknxworks.paperctrl.ui.components.StatusDot
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.components.CtrlTextField
import com.vknxworks.paperctrl.ui.components.ctrlFieldColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.SavedView
import com.vknxworks.paperctrl.data.api.autocomplete
import com.vknxworks.paperctrl.data.api.savedViews
import com.vknxworks.paperctrl.data.search.DocumentQuery
import com.vknxworks.paperctrl.data.search.SortField
import com.vknxworks.paperctrl.data.search.TextMode
import com.vknxworks.paperctrl.ui.components.LocalBottomClearance
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.SwitchAddressBar
import com.vknxworks.paperctrl.ui.nav.DocumentRoute
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The whole library: search, saved views, filters, list or grid, selection for bulk edits. */
@Composable
fun DocumentsTab() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val serverId = session?.server?.id ?: return
    val model: DocumentListModel = viewModel(key = "documents-$serverId") { DocumentListModel(c, context.applicationContext, "documents") }
    val md = rememberMetadata()
    var text by rememberSaveable { mutableStateOf(model.query.text) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var filters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var views by remember { mutableStateOf<List<SavedView>>(emptyList()) }
    val pending by c.pendingQuery.collectAsStateWithLifecycle()

    // Opened with a filter from elsewhere (Organise, "More like this").
    LaunchedEffect(pending) {
        pending?.let { q ->
            text = q.text
            model.load(q)
            c.pendingQuery.value = null
        }
    }
    LaunchedEffect(serverId) {
        if (!model.loaded && pending == null) model.load(model.query)
        val s = session ?: return@LaunchedEffect
        views = runCatching { orderViews(s.client.savedViews(), runCatching { s.client.uiSettings().settings }.getOrNull()) }.getOrDefault(emptyList())
    }
    // Search as you type, after a short pause.
    LaunchedEffect(text) {
        if (text == model.query.text) return@LaunchedEffect
        delay(400)
        model.load(model.query.copy(text = text, savedViewId = null))
        val s = session
        suggestions = if (s != null && text.trim().length >= 2 && model.query.textMode != TextMode.ASN) {
            s.client.autocomplete(text.trim().substringAfterLast(' ')).filterNot { it.equals(text.trim(), true) }.take(5)
        } else {
            emptyList()
        }
    }

    val q = model.query
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        CtrlTopBar()
        Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.documents_search_hint, stringResource(q.textMode.label))) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = ""; suggestions = emptyList() }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
                },
                singleLine = true,
                shape = CtrlShapes.Field,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); suggestions = emptyList() }),
                colors = ctrlFieldColors(),
                modifier = Modifier.weight(1f),
            )
        }
        if (suggestions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { s ->
                    Surface(
                        shape = CtrlShapes.Chip,
                        color = CtrlColors.Raised,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.ControlOutline),
                        modifier = Modifier.clickable {
                            val words = text.trim().split(' ').dropLast(1)
                            text = (words + s).joinToString(" ")
                            suggestions = emptyList()
                        },
                    ) { Text(s, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                }
            }
        }
        // Saved views and the toolbar.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter - 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(badge = { if (q.filterCount > 0) Badge { Text("${q.filterCount}") } }) {
                FilterChip(selected = q.filterCount > 0, onClick = { filters = true }, label = { Text(stringResource(R.string.filters)) }, leadingIcon = { Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)) })
            }
            Box {
                FilterChip(
                    selected = false,
                    onClick = { sortMenu = true },
                    label = { Text(stringResource(q.sort.label) + if (q.reverse) " ↓" else " ↑") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortField.entries.filter { it != SortField.SCORE || q.isFullText }.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(stringResource(f.label) + if (f == q.sort) (if (q.reverse) "  ↓" else "  ↑") else "") },
                            onClick = {
                                sortMenu = false
                                model.load(q.copy(sort = f, reverse = if (f == q.sort) !q.reverse else f != SortField.TITLE && f != SortField.CORRESPONDENT, savedViewId = null))
                            },
                        )
                    }
                }
            }
            IconButton(onClick = { c.appScope.launch { c.settings.update { it.copy(documentsGrid = !settings.documentsGrid) } } }) {
                Icon(if (settings.documentsGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView, stringResource(R.string.documents_toggle_view))
            }
            views.forEach { v ->
                FilterChip(
                    selected = q.savedViewId == v.id,
                    onClick = {
                        text = ""
                        model.load(if (q.savedViewId == v.id) DocumentQuery() else DocumentQuery.fromSavedView(v))
                    },
                    label = { Text(v.name) },
                )
            }
        }
        if (q.moreLike != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.documents_more_like, q.moreLikeTitle ?: "#${q.moreLike}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { model.load(DocumentQuery()) }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
            }
        }
        if (model.offline) OfflineBanner()
        if (model.offline || (model.loaded && model.items.isEmpty() && model.error != null)) SwitchAddressBar { model.refresh() }

        Box(Modifier.weight(1f)) {
            PullToRefreshBox(isRefreshing = model.refreshing, onRefresh = { model.refresh() }, modifier = Modifier.fillMaxSize()) {
                when {
                    !model.loaded && model.loading -> LoadingView()
                    model.items.isEmpty() && model.error != null -> MessageView(
                        Icons.Outlined.CloudOff, stringResource(R.string.load_failed), model.error,
                        actionLabel = stringResource(R.string.retry), onAction = { model.refresh() },
                    )
                    model.items.isEmpty() && model.loaded -> MessageView(
                        Icons.Outlined.Description,
                        stringResource(R.string.documents_none),
                        stringResource(if (q.filterCount > 0 || text.isNotBlank()) R.string.documents_none_filtered else R.string.documents_none_body),
                    )
                    else -> DocumentResults(model, md, settings.documentsGrid, selected, onSelect = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    }) { id ->
                        if (selected.isNotEmpty()) {
                            selected = if (id in selected) selected - id else selected + id
                        } else {
                            nav.navigate(DocumentRoute(id))
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            BulkBar(selected, md, onClear = { selected = emptySet() }, extended = true, onChanged = { removed ->
                if (removed) model.remove(selected)
                selected = emptySet()
                model.refresh()
            })
        }
    }

    if (filters) {
        FilterSheet(q, md, onDismiss = { filters = false }) { updated ->
            filters = false
            model.load(updated.copy(text = text, savedViewId = null))
        }
    }
}

@Composable
private fun DocumentResults(
    model: DocumentListModel,
    md: com.vknxworks.paperctrl.data.metadata.ServerMetadata,
    grid: Boolean,
    selected: Set<Int>,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val bottom = LocalBottomClearance.current + 16.dp
    val header: @Composable () -> Unit = {
        SectionLabel(
            pluralStringResource(R.plurals.documents_count, model.total, model.total),
            Modifier.padding(horizontal = if (grid) 4.dp else Gutter, vertical = 8.dp),
        )
    }
    val footer: @Composable () -> Unit = {
        if (model.loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = CtrlColors.AccentText) }
    }
    if (grid) {
        val state = rememberLazyGridState()
        val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
        LaunchedEffect(state) { snapshotFlow { nearEnd }.distinctUntilChanged().collect { if (it) model.loadMore() } }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = state,
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
            items(model.items, key = { it.id }) { d ->
                DocumentCard(d, md, d.id in selected, selected.isNotEmpty(), onClick = { onOpen(d.id) }, onLongClick = { onSelect(d.id) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    } else {
        val state = rememberLazyListState()
        val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
        LaunchedEffect(state) { snapshotFlow { nearEnd }.distinctUntilChanged().collect { if (it) model.loadMore() } }
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = bottom), modifier = Modifier.fillMaxSize()) {
            item { header() }
            items(model.items, key = { it.id }) { d ->
                DocumentRow(d, md, d.id in selected, selected.isNotEmpty(), onClick = { onOpen(d.id) }, onLongClick = { onSelect(d.id) })
            }
            item { footer() }
        }
    }
}

@Composable
fun OfflineBanner() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp).clip(CtrlShapes.Control).background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.Outline, CtrlShapes.Control).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(CtrlColors.Disabled)
        Spacer(Modifier.size(10.dp))
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp), tint = CtrlColors.Muted)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
    }
}

/** Saved views marked for the dashboard or sidebar first, then the rest by name. */
private fun orderViews(all: List<SavedView>, uiSettings: JsonObject?): List<SavedView> {
    val sv = uiSettings?.get("saved_views") as? JsonObject
    fun ids(key: String) = (sv?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.orEmpty()
    val featured = (ids("dashboard_views_visible_ids") + ids("sidebar_views_visible_ids")).distinct()
    val flagged = all.filter { it.showOnDashboard || it.showInSidebar }.map { it.id }
    val first = (featured + flagged).distinct()
    return all.sortedWith(compareBy<SavedView> { v -> first.indexOf(v.id).let { if (it < 0) Int.MAX_VALUE else it } }.thenBy { it.name.lowercase() })
}
