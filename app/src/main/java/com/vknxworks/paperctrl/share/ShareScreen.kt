package com.vknxworks.paperctrl.share

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.UploadMeta
import com.vknxworks.paperctrl.data.queue.StagedFile
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupChoice
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.GroupSwitch
import com.vknxworks.paperctrl.ui.components.LoadingView
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.upload.MetadataEditor

/**
 * What's about to be uploaded, where to, and (optionally) the details Paperless should use.
 * With the details folded away, one tap uploads and lets Paperless match everything itself.
 */
@Composable
fun ShareScreen(
    files: List<StagedFile>?,
    busy: Boolean,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit,
    onUpload: (serverId: String, mergeImages: Boolean, meta: UploadMeta) -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val ready by c.sessions.ready.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    var serverId by rememberSaveable { mutableStateOf<String?>(null) }
    var merge by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var meta by rememberSaveable(stateSaver = MetaSaver) { mutableStateOf(UploadMeta()) }
    val target = serverId?.takeIf { id -> servers.any { it.id == id } } ?: active?.server?.id ?: servers.firstOrNull()?.id

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        when {
            !ready || files == null -> LoadingView()
            servers.isEmpty() -> MessageView(
                Icons.Outlined.Dns,
                stringResource(R.string.share_no_server_title),
                stringResource(R.string.share_no_server_body),
                actionLabel = stringResource(R.string.share_open_app),
                onAction = onOpenApp,
            )
            files.isEmpty() -> MessageView(
                Icons.Outlined.Description,
                stringResource(R.string.share_nothing_title),
                stringResource(R.string.share_nothing_body),
                actionLabel = stringResource(R.string.close),
                onAction = onCancel,
            )
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                val images = files.count { it.isImage }
                val docCount = if (merge && images >= 2) files.size - images + 1 else files.size
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Group(pluralStringResource(R.plurals.share_files, files.size, files.size)) {
                        files.forEach { f -> FileRow(f) }
                    }
                    if (images >= 2) {
                        Group(footer = stringResource(R.string.share_merge_footer)) {
                            GroupSwitch(
                                stringResource(R.string.share_merge),
                                pluralStringResource(R.plurals.share_merge_body, images, images),
                                merge,
                            ) { merge = it }
                        }
                    }
                    if (servers.size > 1 && target != null) {
                        Group {
                            GroupChoice(
                                stringResource(R.string.share_server),
                                servers.map { it.id to "${it.displayName} · ${it.host}" },
                                target,
                                icon = Icons.Outlined.Dns,
                            ) { serverId = it }
                        }
                    }
                    Group {
                        GroupRow(
                            stringResource(R.string.share_details),
                            stringResource(if (showDetails) R.string.share_details_on else R.string.share_details_off),
                            icon = Icons.Outlined.Tune,
                            onClick = { showDetails = !showDetails },
                            trailing = { Icon(if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                        )
                    }
                    AnimatedVisibility(showDetails && target != null) {
                        if (target != null) MetadataEditor(target, meta, onChange = { meta = it }, showTitle = docCount == 1)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (busy) {
                        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    } else {
                        val details = showDetails && !meta.isEmpty
                        PillButton(
                            stringResource(if (details) R.string.share_upload_details else R.string.share_upload_quick),
                            onClick = { target?.let { onUpload(it, merge, if (showDetails) meta else UploadMeta()) } },
                            enabled = target != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(f: StagedFile) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    f.isPdf -> Icons.Outlined.PictureAsPdf
                    f.isImage -> Icons.Outlined.Image
                    else -> Icons.Outlined.Description
                },
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Formatter.formatShortFileSize(context, f.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Keeps the entered details through rotation and process death. */
private val MetaSaver = androidx.compose.runtime.saveable.Saver<UploadMeta, String>(
    save = { com.vknxworks.paperctrl.data.api.ApiJson.encodeToString(UploadMeta.serializer(), it) },
    restore = { runCatching { com.vknxworks.paperctrl.data.api.ApiJson.decodeFromString(UploadMeta.serializer(), it) }.getOrNull() },
)
