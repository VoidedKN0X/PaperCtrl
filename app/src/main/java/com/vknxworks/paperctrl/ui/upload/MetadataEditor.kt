package com.vknxworks.paperctrl.ui.upload

import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.components.CtrlTextField
import com.vknxworks.paperctrl.ui.components.ctrlFieldColors
import com.vknxworks.paperctrl.ui.components.ctrlSheet
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.UploadMeta
import com.vknxworks.paperctrl.data.api.nextAsn
import com.vknxworks.paperctrl.data.metadata.ServerMetadata
import com.vknxworks.paperctrl.scan.AsnScanDialog
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** An entry in a picker: a tag, correspondent, document type or storage path. */
data class PickOption(val id: Int, val label: String, val color: Color? = null, val detail: String? = null)

private enum class Picker { CORRESPONDENT, TYPE, PATH, TAGS }

/**
 * Optional details for a new document: everything left empty is decided by Paperless' own
 * matching. Pickers are filled from the server and searchable.
 */
@Composable
fun MetadataEditor(
    serverId: String,
    meta: UploadMeta,
    onChange: (UploadMeta) -> Unit,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var data by remember(serverId) { mutableStateOf(c.metadata.cached(serverId)) }
    var loading by remember(serverId) { mutableStateOf(false) }
    var loadError by remember(serverId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var asnText by remember(meta.asn) { mutableStateOf(meta.asn?.toString().orEmpty()) }
    var asnBusy by remember { mutableStateOf(false) }
    var scanAsn by remember { mutableStateOf(false) }

    LaunchedEffect(serverId, reload) {
        val client = c.sessions.clientFor(serverId) ?: return@LaunchedEffect
        loading = true
        loadError = null
        try {
            data = c.metadata.load(serverId, client, force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.friendlyMessage(context)
        } finally {
            loading = false
        }
    }

    val md = data ?: ServerMetadata()
    val auto = stringResource(R.string.meta_auto)

    Column(modifier) {
        if (showTitle) {
            CtrlTextField(
                value = meta.title.orEmpty(),
                onValueChange = { onChange(meta.copy(title = it.ifEmpty { null })) },
                label = { Text(stringResource(R.string.meta_title)) },
                placeholder = { Text(stringResource(R.string.meta_title_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Group(footer = stringResource(R.string.meta_footer)) {
            if (loadError != null) {
                GroupRow(
                    stringResource(R.string.meta_load_failed),
                    loadError,
                    trailing = { TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) } },
                )
            }
            GroupRow(
                stringResource(R.string.meta_created),
                meta.created?.let { formatDate(it) } ?: stringResource(R.string.meta_created_auto),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { pickDate = true },
                trailing = clearButton(meta.created != null) { onChange(meta.copy(created = null)) },
            )
            GroupRow(
                stringResource(R.string.meta_correspondent),
                md.correspondent(meta.correspondent)?.name ?: auto,
                icon = Icons.Outlined.Person,
                onClick = { picker = Picker.CORRESPONDENT },
                trailing = clearButton(meta.correspondent != null) { onChange(meta.copy(correspondent = null)) } ?: loadingMark(loading),
            )
            GroupRow(
                stringResource(R.string.meta_type),
                md.documentType(meta.documentType)?.name ?: auto,
                icon = Icons.Outlined.Category,
                onClick = { picker = Picker.TYPE },
                trailing = clearButton(meta.documentType != null) { onChange(meta.copy(documentType = null)) } ?: loadingMark(loading),
            )
            GroupRow(
                stringResource(R.string.meta_tags),
                meta.tags.mapNotNull { md.tag(it)?.name }.joinToString(", ").ifEmpty { auto },
                icon = Icons.Outlined.Sell,
                onClick = { picker = Picker.TAGS },
                trailing = clearButton(meta.tags.isNotEmpty()) { onChange(meta.copy(tags = emptyList())) } ?: loadingMark(loading),
            )
            GroupRow(
                stringResource(R.string.meta_storage_path),
                md.storagePath(meta.storagePath)?.name ?: auto,
                icon = Icons.Outlined.Folder,
                onClick = { picker = Picker.PATH },
                trailing = clearButton(meta.storagePath != null) { onChange(meta.copy(storagePath = null)) } ?: loadingMark(loading),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(CtrlColors.Raised),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Numbers, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                CtrlTextField(
                    value = asnText,
                    onValueChange = { v ->
                        val digits = v.filter(Char::isDigit).take(9)
                        asnText = digits
                        onChange(meta.copy(asn = digits.toIntOrNull()))
                    },
                    label = { Text(stringResource(R.string.meta_asn)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scanAsn = true }) { Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.asn_scan)) }
                if (asnBusy) {
                    CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        val client = c.sessions.clientFor(serverId) ?: return@TextButton
                        asnBusy = true
                        scope.launch {
                            runCatching { client.nextAsn() }.getOrNull()?.let {
                                asnText = it.toString()
                                onChange(meta.copy(asn = it))
                            }
                            asnBusy = false
                        }
                    }) { Text(stringResource(R.string.meta_asn_next)) }
                }
            }
        }
    }

    if (scanAsn) {
        AsnScanDialog(onDismiss = { scanAsn = false }) { asn ->
            scanAsn = false
            asnText = asn.toString()
            onChange(meta.copy(asn = asn))
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (meta.created?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onChange(meta.copy(created = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()))
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    picker?.let { p ->
        val (title, options, selected) = when (p) {
            Picker.CORRESPONDENT -> Triple(
                stringResource(R.string.meta_correspondent),
                md.correspondents.map { PickOption(it.id, it.name) },
                setOfNotNull(meta.correspondent),
            )
            Picker.TYPE -> Triple(
                stringResource(R.string.meta_type),
                md.documentTypes.map { PickOption(it.id, it.name) },
                setOfNotNull(meta.documentType),
            )
            Picker.PATH -> Triple(
                stringResource(R.string.meta_storage_path),
                md.storagePaths.map { PickOption(it.id, it.name, detail = it.path) },
                setOfNotNull(meta.storagePath),
            )
            Picker.TAGS -> Triple(
                stringResource(R.string.meta_tags),
                md.tags.map { PickOption(it.id, it.name, color = parseColor(it.color)) },
                meta.tags.toSet(),
            )
        }
        PickerSheet(
            title = title,
            options = options,
            selected = selected,
            multi = p == Picker.TAGS,
            loading = loading && data == null,
            onDismiss = { picker = null },
        ) { ids ->
            picker = null
            onChange(
                when (p) {
                    Picker.CORRESPONDENT -> meta.copy(correspondent = ids.firstOrNull())
                    Picker.TYPE -> meta.copy(documentType = ids.firstOrNull())
                    Picker.PATH -> meta.copy(storagePath = ids.firstOrNull())
                    Picker.TAGS -> meta.copy(tags = ids.toList())
                },
            )
        }
    }
}

/**
 * Searchable list to pick one item (tapping picks it) or several (with a Done button). The first
 * entry of a single-choice list is "Automatic", which leaves the choice to Paperless.
 */
@Composable
fun PickerSheet(
    title: String,
    options: List<PickOption>,
    selected: Set<Int>,
    multi: Boolean,
    loading: Boolean,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
    onDone: (Set<Int>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(selected) }
    val filtered = remember(query, options) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.label.contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(modifier = Modifier.ctrlSheet(), 
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CtrlColors.Surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (multi) TextButton(onClick = { onDone(chosen) }) { Text(stringResource(R.string.done)) }
        }
        CtrlTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = CtrlShapes.Field,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding().imePadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!multi && query.isBlank()) {
                item(key = "auto") {
                    OptionRow(noneLabel ?: stringResource(R.string.meta_auto_long), null, null, selected = selected.isEmpty(), multi = false) { onDone(emptySet()) }
                }
            }
            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(filtered, key = { it.id }) { o ->
                val isSel = o.id in chosen
                OptionRow(o.label, o.detail, o.color, isSel, multi) {
                    if (multi) {
                        chosen = if (isSel) chosen - o.id else chosen + o.id
                    } else {
                        onDone(setOf(o.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, detail: String?, color: Color?, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (color != null) Box(Modifier.size(10.dp).clip(CtrlShapes.Dot).background(color))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected && !multi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (multi) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else if (selected) {
            Icon(Icons.Filled.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun clearButton(show: Boolean, onClear: () -> Unit): (@Composable () -> Unit)? {
    if (!show) return null
    val content: @Composable () -> Unit = { IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } }
    return content
}

private fun loadingMark(loading: Boolean): (@Composable () -> Unit)? {
    if (!loading) return null
    val content: @Composable () -> Unit = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
    return content
}

@Composable
private fun fieldColors() = ctrlFieldColors()

fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault(iso)

/** Paperless tag colours are "#rrggbb". */
fun parseColor(hex: String?): Color? = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
