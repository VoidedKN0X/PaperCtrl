package com.vknxworks.paperctrl.ui.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.DocumentVersion
import com.vknxworks.paperctrl.data.api.emailDocuments
import com.vknxworks.paperctrl.data.api.emailEnabled
import com.vknxworks.paperctrl.data.queue.Staging
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.CtrlTextField
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.ShieldScreen
import com.vknxworks.paperctrl.ui.components.ctrlDialog
import com.vknxworks.paperctrl.ui.components.ctrlSheet
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.lock.AppLock
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.upload.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The name a version goes by: its label, or its place in the list ("Version 3"). */
@Composable
fun versionName(versions: List<DocumentVersion>, v: DocumentVersion): String =
    v.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.versions_number, versions.size - versions.indexOf(v))

/**
 * Every version of the document's file (Paperless-ngx 3), newest first: look at an older one, label or delete
 * one, or upload a new file as the next version.
 */
@Composable
fun VersionsSheet(model: DocumentModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = model.doc ?: return
    val versions = doc.versions
    var newLabel by remember { mutableStateOf("") }
    var labelling by remember { mutableStateOf<DocumentVersion?>(null) }
    var deleting by remember { mutableStateOf<DocumentVersion?>(null) }
    var copying by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copying = true
        scope.launch {
            val staged = runCatching { stageForUpload(context, uri) }
            copying = false
            staged.onSuccess { (file, name, mime) ->
                model.addVersion(file, name, mime, newLabel)
                onDismiss()
            }.onFailure { Toast.makeText(context, context.getString(R.string.versions_file_failed), Toast.LENGTH_LONG).show() }
        }
    }

    ModalBottomSheet(modifier = Modifier.ctrlSheet(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CtrlColors.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Text(stringResource(R.string.versions_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(R.string.versions_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            if (versions.isNotEmpty()) {
                Group {
                    versions.forEachIndexed { i, v ->
                        val shown = if (model.version == null) i == 0 else model.version == v.id
                        GroupRow(
                            versionName(versions, v),
                            listOfNotNull(
                                v.added?.let { formatDate(it.take(10)) },
                                stringResource(R.string.versions_current).takeIf { i == 0 },
                                stringResource(R.string.versions_first).takeIf { v.isRoot && versions.size > 1 },
                                stringResource(R.string.versions_shown).takeIf { shown && versions.size > 1 },
                            ).joinToString("  ·  "),
                            onClick = {
                                model.showVersion(v.id)
                                onDismiss()
                            },
                            trailing = if (doc.userCanChange) {
                                {
                                    Row {
                                        IconButton(onClick = { labelling = v }) { Icon(Icons.Outlined.Edit, stringResource(R.string.versions_label)) }
                                        if (!v.isRoot) {
                                            IconButton(onClick = { deleting = v }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            if (doc.userCanChange) {
                Text(stringResource(R.string.versions_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
                CtrlTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it.take(64) },
                    label = { Text(stringResource(R.string.versions_label_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                )
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    if (copying) {
                        CircularProgressIndicator(color = CtrlColors.AccentText)
                    } else {
                        PillButton(stringResource(R.string.versions_choose_file), enabled = !model.pageEditRunning, modifier = Modifier.fillMaxWidth(), onClick = {
                            AppLock.expectReturn()
                            runCatching { picker.launch(arrayOf("*/*")) }
                                .onFailure { Toast.makeText(context, context.getString(R.string.open_link_failed), Toast.LENGTH_SHORT).show() }
                        })
                    }
                }
            }
        }
    }

    labelling?.let { v ->
        var text by remember(v.id) { mutableStateOf(v.label.orEmpty()) }
        AlertDialog(
            onDismissRequest = { labelling = null },
            modifier = Modifier.ctrlDialog(),
            shape = CtrlShapes.Sheet,
            containerColor = CtrlColors.Surface,
            titleContentColor = CtrlColors.Text,
            title = { Text(stringResource(R.string.versions_label)) },
            text = {
                CtrlTextField(value = text, onValueChange = { text = it.take(64) }, singleLine = true, placeholder = { Text(stringResource(R.string.versions_label_hint)) }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    labelling = null
                    model.labelVersion(v.id, text)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { labelling = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { v ->
        ConfirmDialog(
            stringResource(R.string.versions_delete_title),
            stringResource(R.string.versions_delete_body, versionName(versions, v)),
            stringResource(R.string.delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            model.deleteVersion(v.id)
        }
    }
}

/** Copies the picked file into the app's cache so it can be uploaded: the copy, its name and its type. */
private suspend fun stageForUpload(context: Context, uri: Uri): Triple<File, String, String> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val raw = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "document"
    val dir = File(context.cacheDir, "docs").apply { mkdirs() }
    val file = File(dir, "new-version-${System.currentTimeMillis()}")
    try {
        (resolver.openInputStream(uri) ?: throw java.io.IOException("can't open the file")).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    Triple(file, Staging.safeName(raw, mime), mime)
}

/** Asks for the PDF's password, which Paperless needs to save an unprotected copy as a new version. */
@Composable
fun RemovePasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    // A password is typed here: no other app's floating window over it, and nothing for screen-reading apps.
    ShieldScreen()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.ctrlDialog(),
        shape = CtrlShapes.Sheet,
        containerColor = CtrlColors.Surface,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        title = { Text(stringResource(R.string.password_remove_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.password_remove_body))
                CtrlTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_remove_field)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password) }) { Text(stringResource(R.string.password_remove_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Sends the document as an attachment from the Paperless server's own mail account. */
@Composable
fun EmailSheet(model: DocumentModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = model.doc ?: return
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(doc.title) }
    var message by remember { mutableStateOf("") }
    var original by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    /** What the server says about its mail set-up; null until it answered (or when it doesn't say). */
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { enabled = runCatching { model.client?.emailEnabled() }.getOrNull() }
    val addresses = to.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    val valid = addresses.isNotEmpty() && addresses.all { a -> a.indexOf('@') in 1 until a.length - 1 } &&
        subject.isNotBlank() && message.isNotBlank()

    ModalBottomSheet(modifier = Modifier.ctrlSheet(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CtrlColors.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Text(stringResource(R.string.email_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(if (enabled == false) R.string.email_not_set_up else R.string.email_body),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            CtrlTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(stringResource(R.string.email_to)) },
                supportingText = { Text(stringResource(R.string.email_to_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            CtrlTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.email_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            CtrlTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.email_message)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (doc.hasArchive) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !original, onClick = { original = false }, label = { Text(stringResource(R.string.doc_archived)) })
                    FilterChip(selected = original, onClick = { original = true }, label = { Text(stringResource(R.string.doc_original)) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(color = CtrlColors.AccentText)
                } else {
                    PillButton(stringResource(R.string.email_send), enabled = valid && enabled != false && model.client != null, modifier = Modifier.fillMaxWidth(), onClick = {
                        val cl = model.client ?: return@PillButton
                        busy = true
                        scope.launch {
                            runCatching { cl.emailDocuments(listOf(doc.id), addresses.joinToString(","), subject.trim(), message.trim(), archive = !original) }
                                .onSuccess {
                                    Toast.makeText(context, context.getString(R.string.email_sent), Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                                .onFailure { e ->
                                    val code = (e as? com.vknxworks.paperctrl.data.api.PaperlessException)?.httpCode
                                    // Paperless only says "check the logs" when its mail server refuses.
                                    val msg = if (code != null && code >= 500) context.getString(R.string.email_failed) else e.friendlyMessage(context)
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            busy = false
                        }
                    })
                }
            }
        }
    }
}
