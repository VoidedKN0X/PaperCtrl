package com.vknxworks.paperctrl.ui.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.CustomField
import com.vknxworks.paperctrl.data.api.CustomFieldValue
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.metadata.ServerMetadata
import com.vknxworks.paperctrl.scan.AsnScanDialog
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.docs.DatePickDialog
import com.vknxworks.paperctrl.ui.docs.TagChip
import com.vknxworks.paperctrl.ui.upload.PickOption
import com.vknxworks.paperctrl.ui.upload.PickerSheet
import com.vknxworks.paperctrl.ui.upload.formatDate
import com.vknxworks.paperctrl.ui.upload.parseColor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The editable fields of a document. */
data class DocEdit(
    val title: String,
    val created: String?,
    val correspondent: Int?,
    val documentType: Int?,
    val storagePath: Int?,
    val tags: List<Int>,
    val asn: Int?,
    val customFields: List<CustomFieldValue>,
) {
    companion object {
        fun of(d: Document) = DocEdit(d.title, d.createdDate, d.correspondent, d.documentType, d.storagePath, d.tags, d.asn, d.customFields)
    }
}

private enum class DetailPick { CORRESPONDENT, TYPE, PATH, TAGS, ADD_FIELD }

@Composable
fun DetailsEditor(
    edit: DocEdit,
    md: ServerMetadata,
    fields: List<CustomField>,
    canEdit: Boolean,
    linkTitles: Map<Int, String>,
    onChange: (DocEdit) -> Unit,
    onPickDocument: (onPicked: (Int) -> Unit) -> Unit,
) {
    var pick by remember { mutableStateOf<DetailPick?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var scanAsn by remember { mutableStateOf(false) }
    var asnText by remember(edit.asn) { mutableStateOf(edit.asn?.toString().orEmpty()) }
    val none = stringResource(R.string.detail_none)

    Column {
        TextField(
            value = edit.title,
            onValueChange = { onChange(edit.copy(title = it)) },
            label = { Text(stringResource(R.string.meta_title)) },
            enabled = canEdit,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Group {
            GroupRow(stringResource(R.string.meta_created), edit.created?.let(::formatDate) ?: none, icon = Icons.Outlined.CalendarMonth, onClick = if (canEdit) { { pickDate = true } } else null)
            GroupRow(stringResource(R.string.meta_correspondent), md.correspondent(edit.correspondent)?.name ?: none, icon = Icons.Outlined.Person, onClick = if (canEdit) { { pick = DetailPick.CORRESPONDENT } } else null)
            GroupRow(stringResource(R.string.meta_type), md.documentType(edit.documentType)?.name ?: none, icon = Icons.Outlined.Category, onClick = if (canEdit) { { pick = DetailPick.TYPE } } else null)
            GroupRow(stringResource(R.string.meta_storage_path), md.storagePath(edit.storagePath)?.name ?: none, icon = Icons.Outlined.Folder, onClick = if (canEdit) { { pick = DetailPick.PATH } } else null)
            GroupRow(stringResource(R.string.meta_tags), if (edit.tags.isEmpty()) none else null, icon = Icons.Outlined.Sell, onClick = if (canEdit) { { pick = DetailPick.TAGS } } else null)
            if (edit.tags.isNotEmpty()) {
                FlowRow(Modifier.padding(start = 66.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    edit.tags.mapNotNull { md.tag(it) }.forEach { TagChip(it) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Numbers, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp, start = 8.dp))
                TextField(
                    value = asnText,
                    onValueChange = { v -> val d = v.filter(Char::isDigit).take(9); asnText = d; onChange(edit.copy(asn = d.toIntOrNull())) },
                    label = { Text(stringResource(R.string.meta_asn)) },
                    enabled = canEdit,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) IconButton(onClick = { scanAsn = true }) { Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.asn_scan)) }
            }
        }

        if (fields.isNotEmpty() || edit.customFields.isNotEmpty()) {
            Group(stringResource(R.string.detail_custom_fields)) {
                edit.customFields.forEachIndexed { i, cf ->
                    val def = fields.firstOrNull { it.id == cf.field }
                    CustomFieldInput(
                        def = def,
                        value = cf.value,
                        enabled = canEdit,
                        linkTitles = linkTitles,
                        onPickDocument = onPickDocument,
                        onChange = { v -> onChange(edit.copy(customFields = edit.customFields.toMutableList().also { it[i] = cf.copy(value = v) })) },
                        onRemove = { onChange(edit.copy(customFields = edit.customFields.filterIndexed { j, _ -> j != i })) },
                    )
                }
                if (canEdit && fields.any { f -> edit.customFields.none { it.field == f.id } }) {
                    TextButton(onClick = { pick = DetailPick.ADD_FIELD }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Outlined.Add, null)
                        Text("  " + stringResource(R.string.detail_add_field))
                    }
                }
            }
        }
    }

    if (pickDate) {
        DatePickDialog(edit.created, onDismiss = { pickDate = false }) { pickDate = false; onChange(edit.copy(created = it)) }
    }
    if (scanAsn) {
        AsnScanDialog(onDismiss = { scanAsn = false }) { scanAsn = false; asnText = "$it"; onChange(edit.copy(asn = it)) }
    }
    pick?.let { p ->
        val (title, options, current) = when (p) {
            DetailPick.CORRESPONDENT -> Triple(stringResource(R.string.meta_correspondent), md.correspondents.map { PickOption(it.id, it.name) }, setOfNotNull(edit.correspondent))
            DetailPick.TYPE -> Triple(stringResource(R.string.meta_type), md.documentTypes.map { PickOption(it.id, it.name) }, setOfNotNull(edit.documentType))
            DetailPick.PATH -> Triple(stringResource(R.string.meta_storage_path), md.storagePaths.map { PickOption(it.id, it.name, detail = it.path) }, setOfNotNull(edit.storagePath))
            DetailPick.TAGS -> Triple(stringResource(R.string.meta_tags), md.tags.map { PickOption(it.id, it.name, parseColor(it.color)) }, edit.tags.toSet())
            DetailPick.ADD_FIELD -> Triple(
                stringResource(R.string.detail_add_field),
                fields.filter { f -> edit.customFields.none { it.field == f.id } }.map { PickOption(it.id, it.name, detail = fieldTypeLabel(it.dataType)) },
                emptySet(),
            )
        }
        PickerSheet(
            title, options, current,
            multi = p == DetailPick.TAGS,
            loading = false,
            onDismiss = { pick = null },
            noneLabel = if (p == DetailPick.ADD_FIELD) null else stringResource(R.string.detail_none),
        ) { ids ->
            pick = null
            val one = ids.firstOrNull()
            onChange(
                when (p) {
                    DetailPick.CORRESPONDENT -> edit.copy(correspondent = one)
                    DetailPick.TYPE -> edit.copy(documentType = one)
                    DetailPick.PATH -> edit.copy(storagePath = one)
                    DetailPick.TAGS -> edit.copy(tags = ids.toList())
                    DetailPick.ADD_FIELD -> if (one == null) edit else edit.copy(customFields = edit.customFields + CustomFieldValue(one, JsonNull))
                },
            )
        }
    }
}

@Composable
private fun fieldTypeLabel(type: String): String = stringResource(
    when (type) {
        "url" -> R.string.field_url
        "date" -> R.string.field_date
        "boolean" -> R.string.field_boolean
        "integer" -> R.string.field_integer
        "float" -> R.string.field_float
        "monetary" -> R.string.field_monetary
        "documentlink" -> R.string.field_documentlink
        "select" -> R.string.field_select
        "longtext" -> R.string.field_longtext
        else -> R.string.field_string
    },
)

/** The right input for each custom field type. */
@Composable
private fun CustomFieldInput(
    def: CustomField?,
    value: JsonElement?,
    enabled: Boolean,
    linkTitles: Map<Int, String>,
    onPickDocument: (onPicked: (Int) -> Unit) -> Unit,
    onChange: (JsonElement) -> Unit,
    onRemove: () -> Unit,
) {
    val name = def?.name ?: "#"
    val type = def?.dataType ?: "string"
    val str = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    var pickDate by remember { mutableStateOf(false) }
    var pickSelect by remember { mutableStateOf(false) }
    val remove: @Composable () -> Unit = { if (enabled) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.detail_remove_field)) } }

    when (type) {
        "boolean" -> GroupRow(name, null, onClick = if (enabled) { { onChange(JsonPrimitive(!((value as? JsonPrimitive)?.booleanOrNull ?: false))) } } else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch((value as? JsonPrimitive)?.booleanOrNull ?: false, onCheckedChange = { onChange(JsonPrimitive(it)) }, enabled = enabled)
                remove()
            }
        }
        "date" -> GroupRow(name, str?.let(::formatDate) ?: stringResource(R.string.detail_none), icon = Icons.Outlined.CalendarMonth, onClick = if (enabled) { { pickDate = true } } else null, trailing = remove)
        "select" -> {
            val options = def?.selectOptions.orEmpty()
            GroupRow(name, options.firstOrNull { it.first == str }?.second ?: stringResource(R.string.detail_none), onClick = if (enabled) { { pickSelect = true } } else null, trailing = remove)
        }
        "documentlink" -> {
            val ids = (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.orEmpty()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  $name", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    remove()
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ids.forEach { id ->
                        InputChip(
                            selected = false,
                            onClick = { onChange(buildJsonArray { ids.filter { it != id }.forEach { add(JsonPrimitive(it)) } }) },
                            label = { Text(linkTitles[id] ?: "#$id") },
                            trailingIcon = if (enabled) {
                                { Icon(Icons.Outlined.Close, null, Modifier.width(16.dp)) }
                            } else {
                                null
                            },
                            enabled = enabled,
                            modifier = Modifier.padding(end = 0.dp),
                        )
                    }
                    if (enabled) {
                        TextButton(onClick = { onPickDocument { picked -> onChange(buildJsonArray { (ids + picked).distinct().forEach { add(JsonPrimitive(it)) } }) } }) {
                            Text(stringResource(R.string.detail_link_document))
                        }
                    }
                }
            }
        }
        else -> {
            val keyboard = when (type) {
                "integer" -> KeyboardType.Number
                "float", "monetary" -> KeyboardType.Decimal
                "url" -> KeyboardType.Uri
                else -> KeyboardType.Text
            }
            var text by remember(value) { mutableStateOf(str.orEmpty()) }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { v ->
                        text = v
                        onChange(
                            when (type) {
                                "integer" -> v.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
                                "float" -> v.replace(',', '.').toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
                                else -> if (v.isEmpty()) JsonNull else JsonPrimitive(v)
                            },
                        )
                    },
                    label = { Text(name) },
                    placeholder = if (type == "monetary") { { Text(stringResource(R.string.field_monetary_hint, def?.defaultCurrency ?: "EUR")) } } else null,
                    enabled = enabled,
                    singleLine = type != "longtext",
                    minLines = if (type == "longtext") 3 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
                remove()
            }
        }
    }

    if (pickDate) DatePickDialog(str, onDismiss = { pickDate = false }) { pickDate = false; onChange(JsonPrimitive(it)) }
    if (pickSelect) {
        val options = def?.selectOptions.orEmpty()
        // Picker ids are numbers; select option ids are strings, so map by position.
        PickerSheet(
            name,
            options.mapIndexed { i, o -> PickOption(i, o.second) },
            setOfNotNull(options.indexOfFirst { it.first == str }.takeIf { it >= 0 }),
            multi = false,
            loading = false,
            onDismiss = { pickSelect = false },
            noneLabel = stringResource(R.string.detail_none),
        ) { ids ->
            pickSelect = false
            onChange(ids.firstOrNull()?.let { JsonPrimitive(options[it].first) } ?: JsonNull)
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
