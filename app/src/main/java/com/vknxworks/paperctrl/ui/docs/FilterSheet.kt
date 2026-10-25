package com.vknxworks.paperctrl.ui.docs

import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.paperctrl.ui.components.ctrlSheet
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.CustomField
import com.vknxworks.paperctrl.data.api.User
import com.vknxworks.paperctrl.data.api.customFields
import com.vknxworks.paperctrl.data.api.users
import com.vknxworks.paperctrl.data.metadata.ServerMetadata
import com.vknxworks.paperctrl.data.search.DocumentQuery
import com.vknxworks.paperctrl.data.search.TextMode
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.GroupSwitch
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.upload.PickOption
import com.vknxworks.paperctrl.ui.upload.PickerSheet
import com.vknxworks.paperctrl.ui.upload.formatDate
import com.vknxworks.paperctrl.ui.upload.parseColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class FilterPick { TAGS, EXCLUDE, CORRESPONDENTS, TYPES, PATHS, OWNERS, FIELDS }
private enum class DateField { CREATED_FROM, CREATED_TO, ADDED_FROM, ADDED_TO }

/** Every filter of the Documents tab in one sheet. Changes apply when you tap Show results. */
@Composable
fun FilterSheet(initial: DocumentQuery, md: ServerMetadata, onDismiss: () -> Unit, onApply: (DocumentQuery) -> Unit) {
    val c = LocalContext.current.container
    var q by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf<FilterPick?>(null) }
    var date by remember { mutableStateOf<DateField?>(null) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var fields by remember { mutableStateOf<List<CustomField>>(emptyList()) }
    LaunchedEffect(Unit) {
        val s = c.sessions.active.value ?: return@LaunchedEffect
        users = s.client.users()
        fields = runCatching { s.client.customFields() }.getOrDefault(emptyList())
    }
    val any = stringResource(R.string.filter_any)
    fun names(ids: Set<Int>, lookup: (Int) -> String?) = ids.mapNotNull(lookup).joinToString(", ").ifEmpty { any }

    ModalBottomSheet(modifier = Modifier.ctrlSheet(), 
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CtrlColors.Surface,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp)) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { q = DocumentQuery(text = q.text, textMode = q.textMode, sort = q.sort, reverse = q.reverse) }) {
                    Text(stringResource(R.string.filters_reset))
                }
            }
            Text(
                stringResource(R.string.filter_search_in),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 6.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                TextMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = q.textMode == m,
                        onClick = { q = q.copy(textMode = m) },
                        shape = SegmentedButtonDefaults.itemShape(i, TextMode.entries.size, baseShape = CtrlShapes.Control),
                    ) { Text(stringResource(m.label), maxLines = 1) }
                }
            }
            Group(stringResource(R.string.filter_labels)) {
                GroupRow(
                    stringResource(R.string.meta_tags),
                    names(q.tags) { md.tag(it)?.name }.let { if (q.tags.size > 1) it + "  ·  " + stringResource(if (q.tagsMatchAll) R.string.filter_tags_all else R.string.filter_tags_any) else it },
                    icon = Icons.Outlined.Sell,
                    onClick = { pick = FilterPick.TAGS },
                )
                if (q.tags.size > 1) {
                    GroupSwitch(stringResource(R.string.filter_tags_all_switch), checked = q.tagsMatchAll) { q = q.copy(tagsMatchAll = it) }
                }
                GroupRow(stringResource(R.string.filter_tags_exclude), names(q.tagsExcluded) { md.tag(it)?.name }.let { if (q.tagsExcluded.isEmpty()) stringResource(R.string.filter_none) else it }, icon = Icons.Outlined.Close, onClick = { pick = FilterPick.EXCLUDE })
                GroupRow(stringResource(R.string.meta_correspondent), names(q.correspondents) { md.correspondent(it)?.name }, icon = Icons.Outlined.Person, onClick = { pick = FilterPick.CORRESPONDENTS })
                GroupRow(stringResource(R.string.meta_type), names(q.documentTypes) { md.documentType(it)?.name }, icon = Icons.Outlined.Category, onClick = { pick = FilterPick.TYPES })
                GroupRow(stringResource(R.string.meta_storage_path), names(q.storagePaths) { md.storagePath(it)?.name }, icon = Icons.Outlined.Folder, onClick = { pick = FilterPick.PATHS })
                if (fields.isNotEmpty()) {
                    GroupRow(stringResource(R.string.filter_custom_fields), names(q.customFields) { id -> fields.firstOrNull { it.id == id }?.name }, icon = Icons.Outlined.EditNote, onClick = { pick = FilterPick.FIELDS })
                }
                if (users.size > 1) {
                    GroupRow(stringResource(R.string.filter_owner), names(q.owners) { id -> users.firstOrNull { it.id == id }?.displayName }, icon = Icons.Outlined.AccountCircle, onClick = { pick = FilterPick.OWNERS })
                }
            }
            Group(stringResource(R.string.filter_dates)) {
                DateRow(stringResource(R.string.filter_created_from), q.createdFrom, onPick = { date = DateField.CREATED_FROM }) { q = q.copy(createdFrom = null) }
                DateRow(stringResource(R.string.filter_created_to), q.createdTo, onPick = { date = DateField.CREATED_TO }) { q = q.copy(createdTo = null) }
                DateRow(stringResource(R.string.filter_added_from), q.addedFrom, onPick = { date = DateField.ADDED_FROM }) { q = q.copy(addedFrom = null) }
                DateRow(stringResource(R.string.filter_added_to), q.addedTo, onPick = { date = DateField.ADDED_TO }) { q = q.copy(addedTo = null) }
            }
            Group {
                GroupSwitch(stringResource(R.string.filter_inbox_only), checked = q.inboxOnly, icon = Icons.Outlined.Inbox) { q = q.copy(inboxOnly = it) }
            }
            if (q.extra.isNotEmpty()) {
                Group(footer = stringResource(R.string.filter_view_rules_footer)) {
                    GroupRow(stringResource(R.string.filter_view_rules), q.extra.keys.joinToString(", "), icon = Icons.Outlined.TextFields, trailing = {
                        IconButton(onClick = { q = q.copy(extra = emptyMap()) }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
                    })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CtrlSecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.filters_apply), onClick = { onApply(q) }, modifier = Modifier.weight(2f))
            }
        }
    }

    pick?.let { p ->
        val (title, options, current) = when (p) {
            FilterPick.TAGS -> Triple(stringResource(R.string.meta_tags), md.tags.map { PickOption(it.id, it.name, parseColor(it.color)) }, q.tags)
            FilterPick.EXCLUDE -> Triple(stringResource(R.string.filter_tags_exclude), md.tags.map { PickOption(it.id, it.name, parseColor(it.color)) }, q.tagsExcluded)
            FilterPick.CORRESPONDENTS -> Triple(stringResource(R.string.meta_correspondent), md.correspondents.map { PickOption(it.id, it.name) }, q.correspondents)
            FilterPick.TYPES -> Triple(stringResource(R.string.meta_type), md.documentTypes.map { PickOption(it.id, it.name) }, q.documentTypes)
            FilterPick.PATHS -> Triple(stringResource(R.string.meta_storage_path), md.storagePaths.map { PickOption(it.id, it.name, detail = it.path) }, q.storagePaths)
            FilterPick.OWNERS -> Triple(stringResource(R.string.filter_owner), users.map { PickOption(it.id, it.displayName, detail = it.username) }, q.owners)
            FilterPick.FIELDS -> Triple(stringResource(R.string.filter_custom_fields), fields.map { PickOption(it.id, it.name) }, q.customFields)
        }
        PickerSheet(title, options, current, multi = true, loading = false, onDismiss = { pick = null }) { ids ->
            pick = null
            q = when (p) {
                FilterPick.TAGS -> q.copy(tags = ids)
                FilterPick.EXCLUDE -> q.copy(tagsExcluded = ids)
                FilterPick.CORRESPONDENTS -> q.copy(correspondents = ids)
                FilterPick.TYPES -> q.copy(documentTypes = ids)
                FilterPick.PATHS -> q.copy(storagePaths = ids)
                FilterPick.OWNERS -> q.copy(owners = ids)
                FilterPick.FIELDS -> q.copy(customFields = ids)
            }
        }
    }

    date?.let { d ->
        val current = when (d) {
            DateField.CREATED_FROM -> q.createdFrom
            DateField.CREATED_TO -> q.createdTo
            DateField.ADDED_FROM -> q.addedFrom
            DateField.ADDED_TO -> q.addedTo
        }
        DatePickDialog(current, onDismiss = { date = null }) { iso ->
            date = null
            q = when (d) {
                DateField.CREATED_FROM -> q.copy(createdFrom = iso)
                DateField.CREATED_TO -> q.copy(createdTo = iso)
                DateField.ADDED_FROM -> q.copy(addedFrom = iso)
                DateField.ADDED_TO -> q.copy(addedTo = iso)
            }
        }
    }
}

@Composable
private fun DateRow(title: String, value: String?, onPick: () -> Unit, onClear: () -> Unit) {
    GroupRow(
        title,
        value?.let(::formatDate) ?: stringResource(R.string.filter_any),
        icon = Icons.Outlined.CalendarMonth,
        onClick = onPick,
        trailing = if (value != null) {
            { IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) } }
        } else {
            null
        },
    )
}

/** A date picker that returns yyyy-MM-dd. */
@Composable
fun DatePickDialog(initial: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}
