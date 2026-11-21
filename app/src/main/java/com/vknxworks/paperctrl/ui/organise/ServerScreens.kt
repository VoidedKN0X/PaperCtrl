package com.vknxworks.paperctrl.ui.organise

import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.FailedImport
import com.vknxworks.paperctrl.data.api.ShareBundle
import com.vknxworks.paperctrl.data.api.acknowledgeTasks
import com.vknxworks.paperctrl.data.api.deleteShareBundle
import com.vknxworks.paperctrl.data.api.failedImports
import com.vknxworks.paperctrl.data.api.rebuildShareBundle
import com.vknxworks.paperctrl.data.api.shareBundles
import com.vknxworks.paperctrl.data.api.shareUrl
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.CtrlCard
import com.vknxworks.paperctrl.ui.components.LoadingView
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.document.copyToClipboard
import com.vknxworks.paperctrl.ui.lock.AppLock
import com.vknxworks.paperctrl.ui.nav.DocumentRoute
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.Gutter
import com.vknxworks.paperctrl.ui.upload.formatDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Imports Paperless couldn't finish (failed consume tasks nobody dismissed yet), with Paperless' own
 * explanation. Dismissing one takes it off the list on the server too.
 */
@Composable
fun FailedImportsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FailedImport>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var dismissAll by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        val cl = c.sessions.active.value?.client
        if (cl != null) {
            runCatching { cl.failedImports() }
                .onSuccess { items = it; error = null }
                .onFailure { error = it.friendlyMessage(context) }
        }
        refreshing = false
    }
    fun dismiss(ids: List<Int>) {
        val cl = c.sessions.active.value?.client ?: return
        scope.launch {
            runCatching { cl.acknowledgeTasks(ids) }
                .onSuccess { items = items?.filterNot { it.id in ids } }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.imports_title)) {
                if (!items.isNullOrEmpty()) TextButton(onClick = { dismissAll = true }) { Text(stringResource(R.string.imports_dismiss_all)) }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.padding(padding).fillMaxSize()) {
            val list = items
            when {
                list == null && error != null -> MessageView(
                    Icons.Outlined.CloudOff, stringResource(R.string.load_failed), error,
                    actionLabel = stringResource(R.string.retry), onAction = { error = null; reload++ },
                )
                list == null -> LoadingView()
                list.isEmpty() -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.imports_none), stringResource(R.string.imports_none_body))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(stringResource(R.string.imports_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(list, key = { it.id }) { t ->
                        CtrlCard(padding = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    t.fileName ?: stringResource(R.string.imports_unnamed),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { dismiss(listOf(t.id)) }) { Icon(Icons.Outlined.Close, stringResource(R.string.imports_dismiss)) }
                            }
                            t.created?.let {
                                Text(formatDate(it.take(10)), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                            }
                            val why = t.message ?: t.duplicateOf?.let { stringResource(R.string.imports_duplicate, it) } ?: stringResource(R.string.imports_no_reason)
                            SelectionContainer {
                                Text(why, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, end = 12.dp))
                            }
                            t.duplicateOf?.toIntOrNull()?.let { dup ->
                                TextButton(onClick = { nav.navigate(DocumentRoute(dup)) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(stringResource(R.string.imports_open_duplicate))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (dismissAll) {
        ConfirmDialog(
            stringResource(R.string.imports_dismiss_all_title),
            stringResource(R.string.imports_dismiss_all_body),
            stringResource(R.string.imports_dismiss_all),
            onDismiss = { dismissAll = false },
            destructive = false,
        ) {
            dismissAll = false
            items?.map { it.id }?.takeIf { it.isNotEmpty() }?.let(::dismiss)
        }
    }
}

/**
 * Share-link bundles (Paperless-ngx 3): one public link to a zip of several documents. Paperless builds the
 * zip in the background, so a new bundle is "being prepared" for a moment; the list checks again by itself
 * until it's ready.
 */
@Composable
fun BundlesScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ShareBundle>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<ShareBundle?>(null) }

    LaunchedEffect(reload) {
        val cl = c.sessions.active.value?.client
        if (cl != null) {
            runCatching { cl.shareBundles() }
                .onSuccess { list -> items = list.sortedByDescending { it.created.orEmpty() }; error = null }
                .onFailure { error = it.friendlyMessage(context) }
        }
        refreshing = false
        // While Paperless is still building one, look again every few seconds (only while this screen is open).
        if (items?.any { it.isBuilding } == true) {
            delay(4000)
            reload++
        }
    }
    fun act(block: suspend (com.vknxworks.paperctrl.data.api.PaperlessClient) -> Unit) {
        val cl = c.sessions.active.value?.client ?: return
        scope.launch {
            runCatching { block(cl) }
                .onSuccess { reload++ }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.bundles_title)) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.padding(padding).fillMaxSize()) {
            val list = items
            val client = c.sessions.active.value?.client
            when {
                list == null && error != null -> MessageView(
                    Icons.Outlined.CloudOff, stringResource(R.string.load_failed), error,
                    actionLabel = stringResource(R.string.retry), onAction = { error = null; reload++ },
                )
                list == null -> LoadingView()
                list.isEmpty() -> MessageView(Icons.Outlined.Link, stringResource(R.string.bundles_none), stringResource(R.string.bundles_none_body))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(stringResource(R.string.bundles_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(list, key = { it.id }) { b ->
                        val count = if (b.documentCount > 0) b.documentCount else b.documents.size
                        val url = client?.shareUrl(b.slug)
                        CtrlCard(padding = PaddingValues(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
                            Text(pluralStringResource(R.plurals.bundles_documents, count, count), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    stringResource(
                                        when {
                                            b.isReady -> R.string.bundles_ready
                                            b.isFailed -> R.string.bundles_failed
                                            else -> R.string.bundles_building
                                        },
                                    ),
                                    b.sizeBytes?.takeIf { b.isReady }?.let { Formatter.formatShortFileSize(context, it) },
                                    stringResource(if (b.fileVersion == "original") R.string.share_link_original else R.string.share_link_archive),
                                    b.expiration?.let { stringResource(R.string.share_link_expires, formatDate(it.take(10))) } ?: stringResource(R.string.share_link_never),
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (b.isFailed) MaterialTheme.colorScheme.error else CtrlColors.Muted,
                                modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                            )
                            if (b.isFailed) {
                                val why = when (val e = b.lastError) {
                                    null, is JsonNull -> null
                                    is JsonPrimitive -> e.contentOrNull
                                    else -> e.toString()
                                }?.take(300)
                                if (!why.isNullOrBlank()) Text(why, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (url != null && !b.isFailed) {
                                    IconButton(onClick = { copyToClipboard(context, url) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
                                    IconButton(onClick = {
                                        AppLock.expectReturn()
                                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)) }
                                    }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                                }
                                if (!b.isBuilding) {
                                    IconButton(onClick = { act { it.rebuildShareBundle(b.id) } }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.bundles_rebuild)) }
                                }
                                IconButton(onClick = { deleting = b }) { Icon(Icons.Outlined.Delete, stringResource(R.string.share_link_revoke), tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { b ->
        ConfirmDialog(
            stringResource(R.string.bundles_delete_title),
            stringResource(R.string.bundles_delete_body),
            stringResource(R.string.delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            act { it.deleteShareBundle(b.id) }
        }
    }
}
