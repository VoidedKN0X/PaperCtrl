package com.vknxworks.paperctrl.ui.upload

import com.vknxworks.paperctrl.ui.lock.AppLock
import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.queue.QueueItem
import com.vknxworks.paperctrl.data.queue.QueueStatus
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** Everything on its way to Paperless, newest first, with what's happening to each file. */
@Composable
fun QueueScreen() {
    val context = LocalContext.current
    val c = context.container
    val items by c.queue.items.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<QueueItem?>(null) }
    var removing by remember { mutableStateOf<QueueItem?>(null) }
    val sorted = remember(items) { items.sortedByDescending { it.createdAt } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.queue_title)) {
                if (items.any { it.status == QueueStatus.DONE }) {
                    TextButton(onClick = { c.appScope.launch { c.queue.clearFinished() } }) { Text(stringResource(R.string.queue_clear_done)) }
                }
            }
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                MessageView(Icons.Outlined.CloudUpload, stringResource(R.string.queue_empty_title), stringResource(R.string.queue_empty_body))
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sorted, key = { it.id }) { item ->
                val server = servers.firstOrNull { it.id == item.serverId }
                QueueCard(
                    item = item,
                    serverLabel = server?.host?.takeIf { servers.size > 1 },
                    onRetry = { c.appScope.launch { c.queue.retry(item.id) } },
                    onEdit = { editing = item },
                    onRemove = { if (item.status == QueueStatus.DONE) c.appScope.launch { c.queue.remove(item.id) } else removing = item },
                    onOpen = {
                        val docId = item.documentId ?: return@QueueCard
                        val url = (server?.url ?: return@QueueCard) + "documents/$docId/details"
                        AppLock.expectReturn()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.open_link_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }

    editing?.let { item ->
        EditSheet(item, onDismiss = { editing = null })
    }

    removing?.let { item ->
        ConfirmDialog(
            stringResource(R.string.queue_remove_title),
            stringResource(if (item.taskId != null) R.string.queue_remove_body_sent else R.string.queue_remove_body),
            stringResource(R.string.queue_remove),
            onDismiss = { removing = null },
        ) {
            removing = null
            c.appScope.launch { c.queue.remove(item.id) }
        }
    }
}

@Composable
private fun QueueCard(
    item: QueueItem,
    serverLabel: String?,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(item)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        item.documentTitle ?: item.meta.title?.takeIf { it.isNotBlank() } ?: item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.status == QueueStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(
                            Formatter.formatShortFileSize(context, item.sizeBytes),
                            serverLabel,
                            DateUtils.getRelativeTimeSpanString(item.createdAt).toString(),
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.status == QueueStatus.UPLOADING) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (item.status == QueueStatus.DONE && item.documentId != null) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.queue_open)) }
                }
                if (item.isEditable) TextButton(onClick = onEdit) { Text(stringResource(R.string.queue_edit)) }
                when (item.status) {
                    QueueStatus.FAILED -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    QueueStatus.WAITING -> TextButton(onClick = onRetry) { Text(stringResource(R.string.queue_try_now)) }
                    else -> Unit
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(if (item.status == QueueStatus.DONE) R.string.queue_dismiss else R.string.queue_remove))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(item: QueueItem) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when (item.status) {
            QueueStatus.WAITING -> Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            QueueStatus.UPLOADING -> CircularProgressIndicator(progress = { item.progress }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            QueueStatus.CONSUMING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            QueueStatus.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            QueueStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun statusText(item: QueueItem): String = when (item.status) {
    QueueStatus.WAITING -> item.message ?: stringResource(R.string.queue_status_waiting)
    QueueStatus.UPLOADING -> stringResource(R.string.queue_status_uploading, (item.progress * 100).toInt())
    QueueStatus.CONSUMING -> item.message ?: stringResource(R.string.queue_status_consuming)
    QueueStatus.DONE -> stringResource(R.string.queue_status_done)
    QueueStatus.FAILED -> item.message ?: stringResource(R.string.queue_status_failed)
}

/** Changes the details of a file that hasn't been sent yet. */
@Composable
private fun EditSheet(item: QueueItem, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    var meta by remember(item.id) { mutableStateOf(item.meta) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(
                item.fileName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            MetadataEditor(item.serverId, meta, onChange = { meta = it }, showTitle = true)
            PillButton(
                stringResource(R.string.save),
                onClick = {
                    c.appScope.launch { c.queue.updateMeta(item.id, meta) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
