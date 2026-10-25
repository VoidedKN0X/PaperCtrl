package com.vknxworks.paperctrl.ui.organise

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import com.vknxworks.paperctrl.ui.components.LoadingView
import com.vknxworks.paperctrl.ui.components.topDivider
import com.vknxworks.paperctrl.ui.theme.CtrlType
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.components.CtrlTextField
import com.vknxworks.paperctrl.ui.components.ctrlSheet
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.CustomField
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.SavedView
import com.vknxworks.paperctrl.data.api.customFields
import com.vknxworks.paperctrl.data.api.deleteObject
import com.vknxworks.paperctrl.data.api.saveObject
import com.vknxworks.paperctrl.data.api.savedViews
import com.vknxworks.paperctrl.data.api.trash
import com.vknxworks.paperctrl.data.api.trashAction
import com.vknxworks.paperctrl.data.search.DocumentQuery
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupChoice
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.GroupSwitch
import com.vknxworks.paperctrl.ui.components.LocalBottomClearance
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.components.TabTitle
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.docs.DocumentRow
import com.vknxworks.paperctrl.ui.docs.rememberMetadata
import com.vknxworks.paperctrl.ui.nav.DocumentsRoute
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.ObjectListRoute
import com.vknxworks.paperctrl.ui.nav.TrashRoute
import com.vknxworks.paperctrl.ui.theme.Gutter
import com.vknxworks.paperctrl.ui.upload.parseColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** The kinds of labels Paperless has, with their API path. */
enum class ObjectKind(val path: String, val title: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TAGS("tags", R.string.meta_tags, Icons.Outlined.Sell),
    CORRESPONDENTS("correspondents", R.string.organise_correspondents, Icons.Outlined.Person),
    TYPES("document_types", R.string.organise_types, Icons.Outlined.Category),
    PATHS("storage_paths", R.string.organise_paths, Icons.Outlined.Folder),
    FIELDS("custom_fields", R.string.filter_custom_fields, Icons.Outlined.EditNote),
    VIEWS("saved_views", R.string.organise_views, Icons.Outlined.Bookmarks),
}

/** A label of any kind, as the lists show it. */
private data class Item(
    val id: Int,
    val name: String,
    val count: Int?,
    val color: Color? = null,
    val detail: String? = null,
    val inbox: Boolean = false,
    val raw: JsonObject? = null,
    val field: CustomField? = null,
    val view: SavedView? = null,
)

@Composable
fun OrganiseTab() {
    val nav = LocalNavigator.current
    val md = rememberMetadata()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_organise))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomClearance.current + 16.dp)) {
            Group {
                GroupRow(stringResource(R.string.meta_tags), pluralStringResource(R.plurals.organise_count, md.tags.size, md.tags.size), icon = Icons.Outlined.Sell, showChevron = true, onClick = { nav.navigate(ObjectListRoute(ObjectKind.TAGS.name)) })
                GroupRow(stringResource(R.string.organise_correspondents), pluralStringResource(R.plurals.organise_count, md.correspondents.size, md.correspondents.size), icon = Icons.Outlined.Person, showChevron = true, onClick = { nav.navigate(ObjectListRoute(ObjectKind.CORRESPONDENTS.name)) })
                GroupRow(stringResource(R.string.organise_types), pluralStringResource(R.plurals.organise_count, md.documentTypes.size, md.documentTypes.size), icon = Icons.Outlined.Category, showChevron = true, onClick = { nav.navigate(ObjectListRoute(ObjectKind.TYPES.name)) })
                GroupRow(stringResource(R.string.organise_paths), pluralStringResource(R.plurals.organise_count, md.storagePaths.size, md.storagePaths.size), icon = Icons.Outlined.Folder, showChevron = true, onClick = { nav.navigate(ObjectListRoute(ObjectKind.PATHS.name)) })
            }
            Group {
                GroupRow(stringResource(R.string.filter_custom_fields), null, icon = Icons.Outlined.EditNote, showChevron = true, onClick = { nav.navigate(ObjectListRoute(ObjectKind.FIELDS.name)) })
                GroupRow(stringResource(R.string.organise_views), null, icon = Icons.Outlined.Bookmarks, showChevron = true, onClick = { nav.navigate(ObjectListRoute(ObjectKind.VIEWS.name)) })
            }
            Group {
                GroupRow(stringResource(R.string.trash), stringResource(R.string.trash_body), icon = Icons.Outlined.DeleteOutline, showChevron = true, onClick = { nav.navigate(TrashRoute) })
            }
        }
    }
}

@Composable
fun ObjectListScreen(kindName: String) {
    val kind = ObjectKind.valueOf(kindName)
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val md = rememberMetadata(if (reload > 0) reload else null)
    var fields by remember { mutableStateOf<List<CustomField>?>(null) }
    var views by remember { mutableStateOf<List<SavedView>?>(null) }
    var raw by remember { mutableStateOf<Map<Int, JsonObject>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Item?>(null) }
    var creating by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Item?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        val cl = c.sessions.active.value?.client ?: return@LaunchedEffect
        when (kind) {
            ObjectKind.FIELDS -> fields = runCatching { cl.customFields() }.getOrDefault(emptyList())
            ObjectKind.VIEWS -> views = runCatching { cl.savedViews() }.getOrDefault(emptyList())
            else -> {
                // Matching settings aren't in the shared metadata; read them once for the edit sheet.
                raw = runCatching {
                    val all = mutableMapOf<Int, JsonObject>()
                    var page = 1
                    while (true) {
                        val j = cl.get("${kind.path}/", mapOf("page_size" to "500", "page" to "$page")) as? JsonObject ?: break
                        (j["results"] as? JsonArray)?.forEach { o -> (o as? JsonObject)?.let { all[(it["id"] as JsonPrimitive).intOrNull ?: 0] = it } }
                        if (j["next"] == null || j["next"] is kotlinx.serialization.json.JsonNull) break
                        page++
                    }
                    all
                }.getOrDefault(raw)
            }
        }
        refreshing = false
    }

    val items: List<Item>? = when (kind) {
        ObjectKind.TAGS -> md.tags.map { Item(it.id, it.name, it.documentCount, parseColor(it.color), inbox = it.isInboxTag, raw = raw[it.id]) }
        ObjectKind.CORRESPONDENTS -> md.correspondents.map { Item(it.id, it.name, it.documentCount, raw = raw[it.id]) }
        ObjectKind.TYPES -> md.documentTypes.map { Item(it.id, it.name, it.documentCount, raw = raw[it.id]) }
        ObjectKind.PATHS -> md.storagePaths.map { Item(it.id, it.name, it.documentCount, detail = it.path, raw = raw[it.id]) }
        ObjectKind.FIELDS -> fields?.map { Item(it.id, it.name, it.documentCount, detail = it.dataType, field = it) }
        ObjectKind.VIEWS -> views?.map { Item(it.id, it.name, null, view = it) }
    }
    val shown = items?.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    fun open(item: Item) {
        c.pendingQuery.value = when (kind) {
            ObjectKind.TAGS -> DocumentQuery(tags = setOf(item.id))
            ObjectKind.CORRESPONDENTS -> DocumentQuery(correspondents = setOf(item.id))
            ObjectKind.TYPES -> DocumentQuery(documentTypes = setOf(item.id))
            ObjectKind.PATHS -> DocumentQuery(storagePaths = setOf(item.id))
            ObjectKind.FIELDS -> DocumentQuery(customFields = setOf(item.id))
            ObjectKind.VIEWS -> item.view?.let { DocumentQuery.fromSavedView(it) } ?: DocumentQuery()
        }
        nav.nav.navigate(DocumentsRoute) { launchSingleTop = true }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(kind.title)) },
        floatingActionButton = {
            if (kind != ObjectKind.VIEWS) {
                FloatingActionButton(onClick = { creating = true }, modifier = Modifier.navigationBarsPadding(), shape = CtrlShapes.Button, containerColor = CtrlColors.Accent, contentColor = CtrlColors.OnAccent) { Icon(Icons.Outlined.Add, stringResource(R.string.organise_add)) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CtrlTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = CtrlShapes.Field,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            )
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.weight(1f)) {
                when {
                    shown == null -> LoadingView()
                    shown.isEmpty() -> MessageView(kind.icon, stringResource(R.string.organise_empty), null)
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { item ->
                            Row(
                                Modifier.fillMaxWidth().clickable { open(item) }.padding(horizontal = Gutter).topDivider().heightIn(min = 60.dp).padding(start = 0.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${item.id}", style = CtrlType.Mono, color = CtrlColors.Muted, modifier = Modifier.widthIn(min = 34.dp))
                                Box(Modifier.size(10.dp).background(item.color ?: CtrlColors.Disabled, CtrlShapes.Dot))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    val sub = listOfNotNull(
                                        item.count?.let { pluralStringResource(R.plurals.documents_count, it, it) },
                                        stringResource(R.string.organise_inbox_tag).takeIf { item.inbox },
                                        item.detail,
                                    ).joinToString("  ·  ")
                                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (kind == ObjectKind.VIEWS) {
                                    IconButton(onClick = { removing = item }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                                } else {
                                    IconButton(onClick = { editing = item }) { Icon(Icons.Outlined.Edit, stringResource(R.string.organise_edit)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun changed() {
        c.sessions.active.value?.server?.id?.let { c.metadata.invalidate(it) }
        reload++
    }

    if (creating || editing != null) {
        ObjectEditSheet(
            kind = kind,
            item = editing,
            onDismiss = { creating = false; editing = null },
            onDelete = { removing = editing; editing = null },
        ) { body ->
            val cl = c.sessions.active.value?.client ?: return@ObjectEditSheet
            val id = editing?.id
            scope.launch {
                runCatching { cl.saveObject(kind.path, id, body) }
                    .onSuccess { creating = false; editing = null; changed() }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            }
        }
    }
    removing?.let { item ->
        ConfirmDialog(
            stringResource(R.string.organise_delete_title, item.name),
            stringResource(if (kind == ObjectKind.VIEWS) R.string.organise_delete_view_body else R.string.organise_delete_body),
            stringResource(R.string.delete),
            onDismiss = { removing = null },
        ) {
            removing = null
            val cl = c.sessions.active.value?.client ?: return@ConfirmDialog
            scope.launch {
                runCatching { cl.deleteObject(kind.path, item.id) }
                    .onSuccess { changed() }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            }
        }
    }
}

private val TAG_COLORS = listOf(
    "#a6cee3", "#1f78b4", "#b2df8a", "#33a02c", "#fb9a99", "#e31a1c",
    "#fdbf6f", "#ff7f00", "#cab2d6", "#6a3d9a", "#b15928", "#000000", "#cccccc",
)

/** Matching algorithms Paperless offers (0 none … 6 auto). */
private val MATCHING = listOf(
    6 to R.string.match_auto, 0 to R.string.match_none, 1 to R.string.match_any, 2 to R.string.match_all,
    3 to R.string.match_exact, 4 to R.string.match_regex, 5 to R.string.match_fuzzy,
)

private val FIELD_TYPES = listOf(
    "string" to R.string.field_string, "longtext" to R.string.field_longtext, "url" to R.string.field_url, "date" to R.string.field_date,
    "boolean" to R.string.field_boolean, "integer" to R.string.field_integer, "float" to R.string.field_float,
    "monetary" to R.string.field_monetary, "select" to R.string.field_select, "documentlink" to R.string.field_documentlink,
)

@Composable
private fun ObjectEditSheet(kind: ObjectKind, item: Item?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (JsonObject) -> Unit) {
    val r = item?.raw
    fun str(k: String) = (r?.get(k) as? JsonPrimitive)?.contentOrNull
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var color by remember { mutableStateOf(str("color") ?: TAG_COLORS.first()) }
    var inbox by remember { mutableStateOf((r?.get("is_inbox_tag") as? JsonPrimitive)?.booleanOrNull ?: false) }
    var algorithm by remember { mutableIntStateOf((r?.get("matching_algorithm") as? JsonPrimitive)?.intOrNull ?: 6) }
    var match by remember { mutableStateOf(str("match").orEmpty()) }
    var insensitive by remember { mutableStateOf((r?.get("is_insensitive") as? JsonPrimitive)?.booleanOrNull ?: true) }
    var path by remember { mutableStateOf(str("path") ?: item?.detail.orEmpty()) }
    var dataType by remember { mutableStateOf(item?.field?.dataType ?: "string") }
    var options by remember { mutableStateOf(item?.field?.selectOptions?.joinToString(", ") { it.second }.orEmpty()) }
    var currency by remember { mutableStateOf(item?.field?.defaultCurrency.orEmpty()) }

    ModalBottomSheet(modifier = Modifier.ctrlSheet(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (item == null) R.string.organise_new else R.string.organise_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (item != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
            CtrlTextField(name, { name = it }, label = { Text(stringResource(R.string.organise_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
            if (kind == ObjectKind.TAGS) {
                FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_COLORS.forEach { hex ->
                        val col = parseColor(hex) ?: Color.Gray
                        Box(
                            Modifier.size(36.dp).background(col, RoundedCornerShape(11.dp))
                                .border(if (hex.equals(color, true)) 3.dp else 1.dp, if (hex.equals(color, true)) CtrlColors.Accent else CtrlColors.ControlOutline, RoundedCornerShape(11.dp))
                                .clickable { color = hex },
                        )
                    }
                }
                Group { GroupSwitch(stringResource(R.string.organise_inbox_tag), stringResource(R.string.organise_inbox_tag_body), inbox, icon = Icons.Outlined.Inbox) { inbox = it } }
            }
            if (kind == ObjectKind.PATHS) {
                CtrlTextField(path, { path = it }, label = { Text(stringResource(R.string.organise_path_template)) }, placeholder = { Text("{{ created_year }}/{{ correspondent }}/{{ title }}") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
            }
            if (kind == ObjectKind.FIELDS) {
                Group {
                    if (item == null) {
                        GroupChoice(stringResource(R.string.organise_field_type), FIELD_TYPES.map { it.first to stringResource(it.second) }, dataType) { dataType = it }
                    } else {
                        GroupRow(stringResource(R.string.organise_field_type), FIELD_TYPES.firstOrNull { it.first == dataType }?.let { stringResource(it.second) } ?: dataType)
                    }
                }
                if (dataType == "select") {
                    CtrlTextField(options, { options = it }, label = { Text(stringResource(R.string.organise_select_options)) }, supportingText = { Text(stringResource(R.string.organise_select_options_hint)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                }
                if (dataType == "monetary") {
                    CtrlTextField(currency, { currency = it.uppercase().take(3) }, label = { Text(stringResource(R.string.organise_currency)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
            if (kind != ObjectKind.FIELDS) {
                Group(stringResource(R.string.organise_matching), footer = stringResource(R.string.organise_matching_footer)) {
                    GroupChoice(stringResource(R.string.organise_algorithm), MATCHING.map { it.first to stringResource(it.second) }, algorithm) { algorithm = it }
                    if (algorithm in 1..5) {
                        CtrlTextField(match, { match = it }, label = { Text(stringResource(R.string.organise_match)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                        GroupSwitch(stringResource(R.string.organise_insensitive), checked = insensitive) { insensitive = it }
                    }
                }
            }
            PillButton(stringResource(R.string.save), enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(20.dp), onClick = {
                onSave(buildJsonObject {
                    put("name", name.trim())
                    when (kind) {
                        ObjectKind.FIELDS -> {
                            if (item == null) put("data_type", dataType)
                            if (dataType == "select" || dataType == "monetary") {
                                put("extra_data", buildJsonObject {
                                    if (dataType == "select") {
                                        val existing = item?.field?.selectOptions.orEmpty()
                                        put("select_options", buildJsonArray {
                                            options.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { label ->
                                                val id = existing.firstOrNull { it.second == label }?.first
                                                add(buildJsonObject { if (id != null) put("id", id); put("label", label) })
                                            }
                                        })
                                    }
                                    if (dataType == "monetary" && currency.length == 3) put("default_currency", currency)
                                })
                            }
                        }
                        else -> {
                            put("matching_algorithm", algorithm)
                            put("match", match)
                            put("is_insensitive", insensitive)
                            if (kind == ObjectKind.TAGS) {
                                put("color", color)
                                put("is_inbox_tag", inbox)
                            }
                            if (kind == ObjectKind.PATHS) put("path", path)
                        }
                    }
                })
            })
        }
    }
}

@Composable
fun TrashScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val md = rememberMetadata()
    var docs by remember { mutableStateOf<List<Document>?>(null) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var emptying by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        val cl = c.sessions.active.value?.client ?: return@LaunchedEffect
        docs = runCatching {
            val all = mutableListOf<Document>()
            var page = 1
            while (page < 40) {
                val p = cl.trash(page)
                all += p.results
                if (p.next == null || all.size >= p.count) break
                page++
            }
            all
        }.getOrElse { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show(); emptyList() }
    }
    fun act(action: String, ids: Collection<Int>?) {
        val cl = c.sessions.active.value?.client ?: return
        scope.launch {
            runCatching { cl.trashAction(action, ids) }
                .onSuccess { selected = emptySet(); reload++ }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.trash)) {
                if (selected.isNotEmpty()) {
                    IconButton(onClick = { act("restore", selected) }) { Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.trash_restore)) }
                } else if (!docs.isNullOrEmpty()) {
                    TextButton(onClick = { emptying = true }) { Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
    ) { padding ->
        val list = docs
        when {
            list == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) { MessageView(Icons.Outlined.DeleteOutline, stringResource(R.string.trash_none), stringResource(R.string.trash_body)) }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text(stringResource(R.string.trash_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp))
                }
                items(list, key = { it.id }) { d ->
                    DocumentRow(
                        d, md, d.id in selected, true,
                        onClick = { selected = if (d.id in selected) selected - d.id else selected + d.id },
                        onLongClick = { selected = if (d.id in selected) selected - d.id else selected + d.id },
                    )
                }
            }
        }
    }
    if (emptying) {
        ConfirmDialog(stringResource(R.string.trash_empty_title), stringResource(R.string.trash_empty_body), stringResource(R.string.trash_empty), onDismiss = { emptying = false }) {
            emptying = false
            act("empty", null)
        }
    }
}
