package com.vknxworks.paperctrl.ui.document

import com.vknxworks.paperctrl.ui.lock.AppLock
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.Group
import com.vknxworks.paperctrl.data.api.Permissions
import com.vknxworks.paperctrl.data.api.User
import com.vknxworks.paperctrl.data.api.createShareLink
import com.vknxworks.paperctrl.data.api.deleteShareLink
import com.vknxworks.paperctrl.data.api.documents
import com.vknxworks.paperctrl.data.api.groups
import com.vknxworks.paperctrl.data.api.shareUrl
import com.vknxworks.paperctrl.data.api.updateDocument
import com.vknxworks.paperctrl.data.api.users
import com.vknxworks.paperctrl.ui.components.Group as SettingsGroup
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.docs.DocumentRow
import com.vknxworks.paperctrl.ui.docs.rememberMetadata
import com.vknxworks.paperctrl.ui.upload.PickOption
import com.vknxworks.paperctrl.ui.upload.PickerSheet
import com.vknxworks.paperctrl.ui.upload.formatDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.ZoneOffset
import com.vknxworks.paperctrl.ui.components.copySensitive

fun copyToClipboard(context: Context, text: String) {
    // Marked sensitive: share links and document text stay out of the clipboard preview and keyboards' history.
    copySensitive(context, context.getString(R.string.app_name), text)
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

/** Public links to the document: copy, share or revoke existing ones, create new ones with an expiry. */
@Composable
fun ShareLinksSheet(model: DocumentModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf(7) }
    var original by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val client = model.client
    val doc = model.doc ?: return

    fun share(url: String) {
        AppLock.expectReturn()
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, doc.title), null),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(stringResource(R.string.share_links), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(R.string.share_links_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            if (model.links.isNotEmpty() && client != null) {
                SettingsGroup {
                    model.links.forEach { link ->
                        val url = client.shareUrl(link.slug)
                        GroupRow(
                            stringResource(if (link.fileVersion == "original") R.string.share_link_original else R.string.share_link_archive),
                            link.expiration?.let { stringResource(R.string.share_link_expires, formatDate(it.take(10))) } ?: stringResource(R.string.share_link_never),
                            trailing = {
                                Row {
                                    IconButton(onClick = { copyToClipboard(context, url) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
                                    IconButton(onClick = { share(url) }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                                    IconButton(onClick = {
                                        scope.launch {
                                            runCatching { client.deleteShareLink(link.id) }
                                                .onSuccess { model.refreshLinks() }
                                                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                                        }
                                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.share_link_revoke)) }
                                }
                            },
                        )
                    }
                }
            }
            Text(stringResource(R.string.share_link_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 7, 30, 0).forEach { d ->
                    FilterChip(
                        selected = days == d,
                        onClick = { days = d },
                        label = { Text(if (d == 0) stringResource(R.string.share_link_never_short) else androidx.compose.ui.res.pluralStringResource(R.plurals.share_link_days, d, d)) },
                    )
                }
            }
            if (doc.hasArchive) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !original, onClick = { original = false }, label = { Text(stringResource(R.string.doc_archived)) })
                    FilterChip(selected = original, onClick = { original = true }, label = { Text(stringResource(R.string.doc_original)) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator()
                } else {
                    PillButton(stringResource(R.string.share_link_create), enabled = client != null, modifier = Modifier.fillMaxWidth(), onClick = {
                        val cl = client ?: return@PillButton
                        busy = true
                        scope.launch {
                            val expiry = if (days == 0) null else LocalDate.now().plusDays(days.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toString()
                            runCatching { cl.createShareLink(doc.id, expiry, original || !doc.hasArchive) }
                                .onSuccess { link ->
                                    model.refreshLinks()
                                    val url = cl.shareUrl(link.slug)
                                    copyToClipboard(context, url)
                                    share(url)
                                }
                                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                            busy = false
                        }
                    })
                }
            }
        }
    }
}

private enum class PermPick { OWNER, VIEW_USERS, VIEW_GROUPS, CHANGE_USERS, CHANGE_GROUPS }

/** Owner and who else may view or change the document (only for its owner or an administrator). */
@Composable
fun PermissionsSheet(model: DocumentModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = model.doc ?: return
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var owner by remember { mutableStateOf(doc.owner) }
    var perms by remember { mutableStateOf(Permissions.from(doc.permissions)) }
    var pick by remember { mutableStateOf<PermPick?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cl = model.client ?: return@LaunchedEffect
        users = cl.users()
        groups = cl.groups()
    }
    fun userNames(ids: Set<Int>) = ids.map { id -> users.firstOrNull { it.id == id }?.displayName ?: "#$id" }.joinToString(", ")
    fun groupNames(ids: Set<Int>) = ids.map { id -> groups.firstOrNull { it.id == id }?.name ?: "#$id" }.joinToString(", ")
    val nobody = stringResource(R.string.perm_nobody)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(stringResource(R.string.doc_permissions), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            SettingsGroup {
                GroupRow(stringResource(R.string.perm_owner), owner?.let { userNames(setOf(it)) } ?: stringResource(R.string.perm_no_owner), icon = Icons.Outlined.AccountCircle, onClick = { pick = PermPick.OWNER })
            }
            SettingsGroup(stringResource(R.string.perm_view)) {
                GroupRow(stringResource(R.string.perm_users), userNames(perms.viewUsers).ifEmpty { nobody }, icon = Icons.Outlined.Visibility, onClick = { pick = PermPick.VIEW_USERS })
                GroupRow(stringResource(R.string.perm_groups), groupNames(perms.viewGroups).ifEmpty { nobody }, icon = Icons.Outlined.Group, onClick = { pick = PermPick.VIEW_GROUPS })
            }
            SettingsGroup(stringResource(R.string.perm_change), footer = stringResource(R.string.perm_footer)) {
                GroupRow(stringResource(R.string.perm_users), userNames(perms.changeUsers).ifEmpty { nobody }, icon = Icons.Outlined.Edit, onClick = { pick = PermPick.CHANGE_USERS })
                GroupRow(stringResource(R.string.perm_groups), groupNames(perms.changeGroups).ifEmpty { nobody }, icon = Icons.Outlined.Group, onClick = { pick = PermPick.CHANGE_GROUPS })
            }
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator()
                } else {
                    PillButton(stringResource(R.string.save), modifier = Modifier.fillMaxWidth(), onClick = {
                        val cl = model.client ?: return@PillButton
                        busy = true
                        scope.launch {
                            runCatching {
                                cl.updateDocument(doc.id, buildJsonObject {
                                    put("owner", owner?.let { JsonPrimitive(it) } ?: JsonNull)
                                    put("set_permissions", perms.toJson())
                                })
                            }.onSuccess {
                                model.load()
                                onDismiss()
                            }.onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                            busy = false
                        }
                    })
                }
            }
        }
    }

    pick?.let { p ->
        val userOptions = users.map { PickOption(it.id, it.displayName, detail = it.username) }
        val groupOptions = groups.map { PickOption(it.id, it.name) }
        when (p) {
            PermPick.OWNER -> PickerSheet(stringResource(R.string.perm_owner), userOptions, setOfNotNull(owner), multi = false, loading = users.isEmpty(), onDismiss = { pick = null }, noneLabel = stringResource(R.string.perm_no_owner)) {
                pick = null
                owner = it.firstOrNull()
            }
            PermPick.VIEW_USERS -> PickerSheet(stringResource(R.string.perm_users), userOptions, perms.viewUsers, multi = true, loading = false, onDismiss = { pick = null }) { pick = null; perms = perms.copy(viewUsers = it) }
            PermPick.VIEW_GROUPS -> PickerSheet(stringResource(R.string.perm_groups), groupOptions, perms.viewGroups, multi = true, loading = false, onDismiss = { pick = null }) { pick = null; perms = perms.copy(viewGroups = it) }
            PermPick.CHANGE_USERS -> PickerSheet(stringResource(R.string.perm_users), userOptions, perms.changeUsers, multi = true, loading = false, onDismiss = { pick = null }) { pick = null; perms = perms.copy(changeUsers = it) }
            PermPick.CHANGE_GROUPS -> PickerSheet(stringResource(R.string.perm_groups), groupOptions, perms.changeGroups, multi = true, loading = false, onDismiss = { pick = null }) { pick = null; perms = perms.copy(changeGroups = it) }
        }
    }
}

/** Finds a document by its title, for document-link custom fields. */
@Composable
fun DocumentSearchSheet(excludeId: Int, onDismiss: () -> Unit, onPick: (Document) -> Unit) {
    val c = LocalContext.current.container
    val md = rememberMetadata()
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Document>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        delay(300)
        val cl = c.sessions.active.value?.client ?: return@LaunchedEffect
        loading = true
        results = runCatching {
            cl.documents(if (text.isBlank()) mapOf("ordering" to "-added") else mapOf("title_content" to text.trim()), 1, 30).results.filter { it.id != excludeId }
        }.getOrDefault(emptyList())
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (loading && results.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LazyColumn(Modifier.heightIn(max = 520.dp).navigationBarsPadding()) {
            items(results, key = { it.id }) { d ->
                DocumentRow(d, md, selected = false, selecting = false, onClick = { onPick(d) }, onLongClick = { onPick(d) })
            }
        }
    }
}
