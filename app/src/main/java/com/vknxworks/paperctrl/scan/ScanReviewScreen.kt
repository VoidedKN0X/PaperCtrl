package com.vknxworks.paperctrl.scan

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.UploadMeta
import com.vknxworks.paperctrl.data.queue.Staging
import com.vknxworks.paperctrl.data.telemetry.Telemetry
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.PageEditRoute
import com.vknxworks.paperctrl.ui.nav.ScanRoute
import com.vknxworks.paperctrl.ui.nav.ScannerRoute
import com.vknxworks.paperctrl.ui.upload.MetadataEditor
import kotlinx.coroutines.launch
import java.io.File

/** The pages scanned so far: reorder by long-pressing and dragging, tap to edit, then upload. */
@Composable
fun ScanReviewScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val pages by c.scan.pages.collectAsStateWithLifecycle()
    var order by remember { mutableStateOf<List<String>?>(null) }
    val shown = order?.let { ids -> ids.mapNotNull { id -> pages.firstOrNull { it.id == id } } } ?: pages
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var discard by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(pluralTitle(pages.size)) {
                if (pages.isNotEmpty()) IconButton(onClick = { discard = true }) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.scan_discard)) }
            }
        },
        bottomBar = {
            if (pages.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { nav.navigate(ScannerRoute()) }, shape = CircleShape, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Outlined.AddAPhoto, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.scan_add_pages))
                    }
                    PillButton(stringResource(R.string.scan_upload), onClick = { uploading = true }, modifier = Modifier.weight(1f))
                }
            }
        },
    ) { padding ->
        if (pages.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                MessageView(
                    Icons.Outlined.DocumentScanner,
                    stringResource(R.string.scan_no_pages),
                    stringResource(R.string.scan_no_pages_body),
                    actionLabel = stringResource(R.string.scan_start),
                    onAction = { nav.navigate(ScannerRoute()) },
                )
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.scan_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                pos.x >= item.offset.x && pos.x <= item.offset.x + item.size.width &&
                                    pos.y >= item.offset.y && pos.y <= item.offset.y + item.size.height
                            }
                            dragging = hit?.key as? String
                            dragOffset = Offset.Zero
                            order = c.scan.pages.value.map { it.id }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val id = dragging ?: return@detectDragGesturesAfterLongPress
                            dragOffset += amount
                            val items = gridState.layoutInfo.visibleItemsInfo
                            val current = items.firstOrNull { it.key == id } ?: return@detectDragGesturesAfterLongPress
                            val center = Offset(
                                current.offset.x + dragOffset.x + current.size.width / 2f,
                                current.offset.y + dragOffset.y + current.size.height / 2f,
                            )
                            val target = items.firstOrNull { item ->
                                item.key != id &&
                                    center.x >= item.offset.x && center.x <= item.offset.x + item.size.width &&
                                    center.y >= item.offset.y && center.y <= item.offset.y + item.size.height
                            }
                            if (target != null) {
                                val list = order?.toMutableList() ?: return@detectDragGesturesAfterLongPress
                                val from = list.indexOf(id)
                                val to = list.indexOf(target.key as String)
                                if (from >= 0 && to >= 0) {
                                    list.add(to, list.removeAt(from))
                                    order = list
                                    // The card jumps to its new slot; keep it under the finger.
                                    dragOffset += Offset(
                                        (current.offset.x - target.offset.x).toFloat(),
                                        (current.offset.y - target.offset.y).toFloat(),
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            val final = order
                            dragging = null
                            dragOffset = Offset.Zero
                            if (final != null) c.appScope.launch { c.scan.reorder(final) }
                            order = null
                        },
                        onDragCancel = {
                            dragging = null
                            dragOffset = Offset.Zero
                            order = null
                        },
                    )
                },
            ) {
                itemsIndexed(shown, key = { _, p -> p.id }) { i, page ->
                    val isDragged = page.id == dragging
                    PageCard(
                        page = page,
                        number = i + 1,
                        modifier = Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                }
                            },
                        lifted = isDragged,
                        onClick = { nav.navigate(PageEditRoute(page.id)) },
                    )
                }
            }
        }
    }

    if (discard) {
        ConfirmDialog(
            stringResource(R.string.scan_discard_title),
            stringResource(R.string.scan_discard_body),
            stringResource(R.string.scan_discard),
            onDismiss = { discard = false },
        ) {
            discard = false
            c.appScope.launch { c.scan.clear() }
            nav.back()
        }
    }

    if (uploading) UploadSheet(pageCount = pages.size, onDismiss = { uploading = false })
}

@Composable
private fun pluralTitle(n: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.scan_pages_title, n, n)

@Composable
private fun PageCard(page: ScanPage, number: Int, modifier: Modifier, lifted: Boolean, onClick: () -> Unit) {
    val c = LocalContext.current.container
    var file by remember(page.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(page.id, page.version) { file = c.scan.previewFile(page, 600) }
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(if (lifted) 12.dp else 2.dp, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(if (lifted) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                .clickable(enabled = !lifted, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (file == null) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    "$number",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Turns the pages into one PDF and queues it, with optional details. */
@Composable
private fun UploadSheet(pageCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var meta by remember { mutableStateOf(UploadMeta()) }
    var details by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val serverId = active?.server?.id

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(
                stringResource(R.string.scan_upload_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Group(footer = stringResource(R.string.scan_upload_footer, stringResource(settings.pdfQuality.label))) {
                GroupRow(
                    stringResource(R.string.share_details),
                    stringResource(if (details) R.string.share_details_on else R.string.share_details_off),
                    icon = Icons.Outlined.Tune,
                    onClick = { details = !details },
                    trailing = { Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                )
            }
            AnimatedVisibility(details && serverId != null) {
                if (serverId != null) MetadataEditor(serverId, meta, onChange = { meta = it }, showTitle = true)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                } else {
                    PillButton(
                        stringResource(if (details && !meta.isEmpty) R.string.share_upload_details else R.string.share_upload_quick),
                        enabled = serverId != null,
                        onClick = {
                            val id = serverId ?: return@PillButton
                            busy = true
                            scope.launch {
                                val ok = runCatching {
                                    val dir = c.queue.newStagingDir()
                                    val pdf = File(dir, "scan.pdf")
                                    c.scan.writePdf(pdf, settings.pdfQuality.quality)
                                    c.queue.enqueue(id, pdf, "Scan ${Staging.timestamp()}.pdf", "application/pdf", if (details) meta else UploadMeta())
                                    c.queue.discardStaging(dir)
                                    c.telemetry.track(
                                        "document_uploaded",
                                        mapOf("source" to "scan", "pages" to Telemetry.pageBucket(pageCount), "kind" to "scan"),
                                    )
                                }.isSuccess
                                if (ok) {
                                    c.scan.clear()
                                    Toast.makeText(context, context.getString(R.string.scan_queued), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    nav.nav.popBackStack(ScanRoute, inclusive = false)
                                } else {
                                    busy = false
                                    Toast.makeText(context, context.getString(R.string.share_prepare_failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
