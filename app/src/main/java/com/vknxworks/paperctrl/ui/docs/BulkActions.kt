package com.vknxworks.paperctrl.ui.docs

import com.vknxworks.paperctrl.ui.theme.CtrlColors
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.api.body
import com.vknxworks.paperctrl.data.api.bulkEdit
import com.vknxworks.paperctrl.data.api.documentOperation
import com.vknxworks.paperctrl.data.api.errorFor
import com.vknxworks.paperctrl.data.files.LocalFile
import com.vknxworks.paperctrl.data.metadata.ServerMetadata
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.upload.PickOption
import com.vknxworks.paperctrl.ui.upload.PickerSheet
import com.vknxworks.paperctrl.ui.upload.parseColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

private enum class BulkPick { CORRESPONDENT, TYPE, PATH, ADD_TAGS, REMOVE_TAGS }

/**
 * The action bar shown while documents are selected. [inboxTags] adds "Mark done" (removes them);
 * [extended] adds download, merge and rotate. [onChanged] runs after the server accepted a change.
 */
@Composable
fun BulkBar(
    selected: Set<Int>,
    md: ServerMetadata,
    onClear: () -> Unit,
    onChanged: (removed: Boolean) -> Unit,
    inboxTags: List<Int> = emptyList(),
    extended: Boolean = false,
) {
    val context = LocalContext.current
    val c = context.container
    var pick by remember { mutableStateOf<BulkPick?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmMerge by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    fun run(removed: Boolean = false, block: suspend (PaperlessClient) -> Unit) {
        val client = c.sessions.active.value?.client ?: return
        working = true
        c.appScope.launch {
            val r = runCatching { block(client) }
            withContext(Dispatchers.Main) {
                working = false
                r.onSuccess { onChanged(removed) }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            }
        }
    }

    Surface(
        color = CtrlColors.Surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.ControlOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.navigationBarsPadding()) {
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CtrlColors.AccentText, trackColor = CtrlColors.Track)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.bulk_clear)) }
                Text(
                    pluralStringResource(R.plurals.bulk_selected, selected.size, selected.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (inboxTags.isNotEmpty()) {
                    IconButton(onClick = {
                        run(removed = true) { it.bulkEdit(selected, "modify_tags", tagChange(emptyList(), inboxTags)) }
                    }) { Icon(Icons.Outlined.DoneAll, stringResource(R.string.inbox_mark_done)) }
                }
                IconButton(onClick = { pick = BulkPick.CORRESPONDENT }) { Icon(Icons.Outlined.Person, stringResource(R.string.meta_correspondent)) }
                IconButton(onClick = { pick = BulkPick.TYPE }) { Icon(Icons.Outlined.Category, stringResource(R.string.meta_type)) }
                IconButton(onClick = { pick = BulkPick.ADD_TAGS }) { Icon(Icons.Outlined.Sell, stringResource(R.string.bulk_add_tags)) }
                Row {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bulk_remove_tags)) },
                            leadingIcon = { Icon(Icons.Outlined.Sell, null) },
                            onClick = { menu = false; pick = BulkPick.REMOVE_TAGS },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.meta_storage_path)) },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                            onClick = { menu = false; pick = BulkPick.PATH },
                        )
                        if (extended) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bulk_download)) },
                                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                onClick = {
                                    menu = false
                                    run { client ->
                                        val zip = downloadZip(client, selected, File(context.cacheDir, "docs").apply { mkdirs() })
                                        val name = c.files.saveToDownloads(LocalFile(zip, "Paperless documents.zip", "application/zip"))
                                        zip.delete()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.download_saved, name), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bulk_rotate)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, null) },
                                onClick = {
                                    menu = false
                                    run { it.documentOperation("rotate", selected, mapOf("degrees" to JsonPrimitive(90))) }
                                    Toast.makeText(context, context.getString(R.string.bulk_rotate_started), Toast.LENGTH_LONG).show()
                                },
                            )
                            if (selected.size > 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bulk_merge)) },
                                    leadingIcon = { Icon(Icons.Outlined.MergeType, null) },
                                    onClick = { menu = false; confirmMerge = true },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        }
    }

    pick?.let { p ->
        val (title, options, multi) = when (p) {
            BulkPick.CORRESPONDENT -> Triple(stringResource(R.string.meta_correspondent), md.correspondents.map { PickOption(it.id, it.name) }, false)
            BulkPick.TYPE -> Triple(stringResource(R.string.meta_type), md.documentTypes.map { PickOption(it.id, it.name) }, false)
            BulkPick.PATH -> Triple(stringResource(R.string.meta_storage_path), md.storagePaths.map { PickOption(it.id, it.name, detail = it.path) }, false)
            BulkPick.ADD_TAGS -> Triple(stringResource(R.string.bulk_add_tags), md.tags.map { PickOption(it.id, it.name, parseColor(it.color)) }, true)
            BulkPick.REMOVE_TAGS -> Triple(stringResource(R.string.bulk_remove_tags), md.tags.map { PickOption(it.id, it.name, parseColor(it.color)) }, true)
        }
        PickerSheet(
            title = title,
            options = options,
            selected = emptySet(),
            multi = multi,
            loading = false,
            noneLabel = if (multi) null else stringResource(R.string.bulk_set_none),
            onDismiss = { pick = null },
        ) { ids ->
            pick = null
            val one = ids.firstOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
            when (p) {
                BulkPick.CORRESPONDENT -> run { it.bulkEdit(selected, "set_correspondent", buildJsonObject { put("correspondent", one) }) }
                BulkPick.TYPE -> run { it.bulkEdit(selected, "set_document_type", buildJsonObject { put("document_type", one) }) }
                BulkPick.PATH -> run { it.bulkEdit(selected, "set_storage_path", buildJsonObject { put("storage_path", one) }) }
                BulkPick.ADD_TAGS -> if (ids.isNotEmpty()) run { it.bulkEdit(selected, "modify_tags", tagChange(ids.toList(), emptyList())) }
                BulkPick.REMOVE_TAGS -> if (ids.isNotEmpty()) run { it.bulkEdit(selected, "modify_tags", tagChange(emptyList(), ids.toList())) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            pluralStringResource(R.plurals.delete_documents_title, selected.size, selected.size),
            stringResource(R.string.delete_documents_body),
            stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            run(removed = true) { it.documentOperation("delete", selected) }
        }
    }

    if (confirmMerge) {
        ConfirmDialog(
            stringResource(R.string.bulk_merge_title),
            stringResource(R.string.bulk_merge_body),
            stringResource(R.string.bulk_merge),
            destructive = false,
            onDismiss = { confirmMerge = false },
        ) {
            confirmMerge = false
            run { it.documentOperation("merge", selected) }
            Toast.makeText(context, context.getString(R.string.bulk_merge_started), Toast.LENGTH_LONG).show()
        }
    }
}

fun tagChange(adding: List<Int>, removing: List<Int>) = buildJsonObject {
    put("add_tags", buildJsonArray { adding.forEach { add(JsonPrimitive(it)) } })
    put("remove_tags", buildJsonArray { removing.forEach { add(JsonPrimitive(it)) } })
}

/** documents/bulk_download/: a zip with the archived versions (originals where there's no archive). */
private suspend fun downloadZip(client: PaperlessClient, ids: Set<Int>, dir: File): File = withContext(Dispatchers.IO) {
    val body = buildJsonObject {
        put("documents", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
        put("content", "archive")
        put("compression", "deflated")
    }.body()
    val target = File(dir, "bulk-${System.currentTimeMillis()}.zip")
    client.http.newCall(client.request(client.url("documents/bulk_download/"), accept = "*/*").post(body).build()).execute().use { resp ->
        if (!resp.isSuccessful) throw errorFor(resp.code, resp.body?.string().orEmpty())
        resp.body?.byteStream()?.use { input -> target.outputStream().use { input.copyTo(it) } }
    }
    target
}
