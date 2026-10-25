package com.vknxworks.paperctrl.ui.document

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.PageEditSupport
import com.vknxworks.paperctrl.data.api.PageOp
import com.vknxworks.paperctrl.data.files.LocalFile
import com.vknxworks.paperctrl.ui.components.CtrlProgressBar
import com.vknxworks.paperctrl.ui.components.CtrlSwitch
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.components.SectionLabel
import com.vknxworks.paperctrl.ui.components.ctrlDialog
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.theme.CtrlType
import com.vknxworks.paperctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One page in the page manager: [page] is its 1-based number in the document's current file. */
private data class EditPage(val page: Int, val rotation: Int = 0)

/**
 * Page manager, opened from a PDF document: thumbnails of every page, rendered on the phone from the
 * original file. Tap pages to select them, then rotate, delete or mark where a new document starts;
 * long-press and drag to move a page. Only what the server supports is offered ([support]).
 *
 * Shown inside the document screen's own window, so the app lock and "Hide in screenshots" cover it.
 */
@Composable
fun PageManagerScreen(model: DocumentModel, support: PageEditSupport, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val doc = model.doc ?: return
    BackHandler(onBack = onClose)

    // Page numbers refer to the original file, which is what Paperless edits.
    var file by remember { mutableStateOf<LocalFile?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(doc.id, doc.modified, attempt) {
        val cl = model.client ?: return@LaunchedEffect
        fileError = null
        try {
            file = c.files.fetch(cl, doc, original = true) { progress = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fileError = e.friendlyMessage(context)
        }
    }
    val pdf = remember(file) { file?.let { f -> runCatching { PdfDoc(f) }.getOrNull() } }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }

    val initial = remember(pdf) { pdf?.let { p -> List(p.pageCount) { EditPage(it + 1) } }.orEmpty() }
    var pages by remember(pdf) { mutableStateOf(initial) }
    var splits by remember(pdf) { mutableStateOf<Set<Int>>(emptySet()) }
    var selected by remember(pdf) { mutableStateOf<Set<Int>>(emptySet()) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var confirming by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Splits only count between pages that are still there, never after the last one.
    val activeSplits = pages.dropLast(1).map { it.page }.filter { it in splits }.toSet()
    val outputs = activeSplits.size + 1
    val changed = pages != initial || activeSplits.isNotEmpty()

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.pages_title), onBack = onClose) {
                if (changed) {
                    IconButton(onClick = { pages = initial; splits = emptySet(); selected = emptySet() }) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.pages_reset))
                    }
                }
            }
        },
        bottomBar = {
            if (pdf != null) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (selected.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (support.editPdf) {
                                PageTool(Icons.AutoMirrored.Outlined.RotateLeft, stringResource(R.string.page_rotate_left)) {
                                    pages = pages.map { if (it.page in selected) it.copy(rotation = (it.rotation + 270) % 360) else it }
                                }
                                PageTool(Icons.AutoMirrored.Outlined.RotateRight, stringResource(R.string.page_rotate_right)) {
                                    pages = pages.map { if (it.page in selected) it.copy(rotation = (it.rotation + 90) % 360) else it }
                                }
                            }
                            PageTool(Icons.Outlined.ContentCut, stringResource(R.string.pages_split_after)) {
                                val all = selected.all { it in splits }
                                splits = if (all) splits - selected else splits + selected
                                selected = emptySet()
                            }
                            PageTool(Icons.Outlined.Delete, stringResource(R.string.delete), danger = true) {
                                if (pages.all { it.page in selected }) {
                                    toast(context.getString(R.string.pages_keep_one))
                                } else {
                                    pages = pages.filterNot { it.page in selected }
                                    splits = splits - selected
                                    selected = emptySet()
                                }
                            }
                            TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.pages_clear_selection)) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            pluralStringResource(R.plurals.pages_count, pages.size, pages.size) +
                                if (outputs > 1) " · " + pluralStringResource(R.plurals.pages_documents, outputs, outputs) else "",
                            style = CtrlType.Mono,
                            color = CtrlColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            stringResource(R.string.save),
                            onClick = {
                                val deleted = initial.map { it.page }.filter { p -> pages.none { it.page == p } }
                                if (!support.editPdf && deleted.isNotEmpty() && activeSplits.isNotEmpty()) {
                                    toast(context.getString(R.string.pages_legacy_one_at_a_time))
                                } else {
                                    confirming = true
                                }
                            },
                            enabled = changed && !model.pageEditRunning,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                fileError != null -> MessageView(Icons.Outlined.Description, stringResource(R.string.load_failed), fileError, actionLabel = stringResource(R.string.retry), onAction = { attempt++ })
                file == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CtrlColors.AccentText)
                    if (progress > 0f) CtrlProgressBar(progress, Modifier.padding(24.dp))
                }
                pdf == null -> MessageView(Icons.Outlined.Description, stringResource(R.string.viewer_pdf_failed))
                else -> Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(if (support.editPdf) R.string.pages_hint else R.string.pages_hint_legacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    )
                    val dragModifier = if (!support.editPdf) Modifier else Modifier.pointerInput(pdf) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    pos.x >= item.offset.x && pos.x <= item.offset.x + item.size.width &&
                                        pos.y >= item.offset.y && pos.y <= item.offset.y + item.size.height
                                }
                                dragging = hit?.key as? Int
                                dragOffset = Offset.Zero
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val key = dragging ?: return@detectDragGesturesAfterLongPress
                                dragOffset += amount
                                val items = gridState.layoutInfo.visibleItemsInfo
                                val current = items.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                                val center = Offset(
                                    current.offset.x + dragOffset.x + current.size.width / 2f,
                                    current.offset.y + dragOffset.y + current.size.height / 2f,
                                )
                                val target = items.firstOrNull { item ->
                                    item.key != key &&
                                        center.x >= item.offset.x && center.x <= item.offset.x + item.size.width &&
                                        center.y >= item.offset.y && center.y <= item.offset.y + item.size.height
                                } ?: return@detectDragGesturesAfterLongPress
                                val list = pages.toMutableList()
                                val from = list.indexOfFirst { it.page == key }
                                val to = list.indexOfFirst { it.page == target.key as? Int }
                                if (from >= 0 && to >= 0) {
                                    list.add(to, list.removeAt(from))
                                    pages = list
                                    // The card jumps to its new slot; keep it under the finger.
                                    dragOffset += Offset(
                                        (current.offset.x - target.offset.x).toFloat(),
                                        (current.offset.y - target.offset.y).toFloat(),
                                    )
                                }
                            },
                            onDragEnd = { dragging = null; dragOffset = Offset.Zero },
                            onDragCancel = { dragging = null; dragOffset = Offset.Zero },
                        )
                    }
                    // Which output document each page lands in.
                    val docIndex = remember(pages, activeSplits) {
                        var d = 0
                        pages.associate { p -> (p.page to d).also { if (p.page in activeSplits) d++ } }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize().then(dragModifier),
                    ) {
                        itemsIndexed(pages, key = { _, p -> p.page }) { i, p ->
                            val isDragged = p.page == dragging
                            PageThumb(
                                pdf = pdf,
                                page = p,
                                position = i + 1,
                                selected = p.page in selected,
                                lifted = isDragged,
                                splitAfter = p.page in activeSplits,
                                docNumber = if (outputs > 1) (docIndex[p.page] ?: 0) + 1 else null,
                                onClick = { selected = if (p.page in selected) selected - p.page else selected + p.page },
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
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirming) {
        var deleteOriginal by remember { mutableStateOf(false) }
        val deleted = initial.map { it.page }.filter { p -> pages.none { it.page == p } }
        AlertDialog(
            onDismissRequest = { confirming = false },
            modifier = Modifier.ctrlDialog(),
            shape = CtrlShapes.Sheet,
            containerColor = CtrlColors.Surface,
            titleContentColor = CtrlColors.Text,
            textContentColor = CtrlColors.Muted,
            title = { Text(stringResource(if (outputs > 1) R.string.pages_confirm_split_title else R.string.pages_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (outputs > 1) {
                            pluralStringResource(R.plurals.pages_confirm_split_body, outputs, outputs)
                        } else {
                            stringResource(if (support.consumesEdits) R.string.pages_confirm_body_version else R.string.pages_confirm_body)
                        },
                    )
                    if (outputs > 1) {
                        Row(
                            Modifier.fillMaxWidth().clickable { deleteOriginal = !deleteOriginal },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.pages_delete_original), color = CtrlColors.Text, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            CtrlSwitch(deleteOriginal, onCheckedChange = { deleteOriginal = it })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    val plan = if (support.editPdf) {
                        val ops = pages.map { PageOp(page = it.page, rotate = it.rotation, doc = 0) }.let { list ->
                            var d = 0
                            list.map { op -> op.copy(doc = d).also { if (op.page in activeSplits) d++ } }
                        }
                        PagePlan.Edit(ops, outputs, deleteOriginal)
                    } else if (activeSplits.isNotEmpty()) {
                        val groups = mutableListOf<List<Int>>()
                        var cur = mutableListOf<Int>()
                        pages.forEach { p ->
                            cur += p.page
                            if (p.page in activeSplits) { groups += cur; cur = mutableListOf() }
                        }
                        if (cur.isNotEmpty()) groups += cur
                        PagePlan.Split(groups.joinToString(",") { g -> if (g.size == 1) "${g[0]}" else "${g.first()}-${g.last()}" }, groups.size, deleteOriginal)
                    } else {
                        PagePlan.DeletePages(deleted)
                    }
                    model.applyPageEdit(plan, support)
                    onClose()
                }) { Text(stringResource(R.string.save), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PageThumb(
    pdf: PdfDoc,
    page: EditPage,
    position: Int,
    selected: Boolean,
    lifted: Boolean,
    splitAfter: Boolean,
    docNumber: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = page.page - 1
    var bmp by remember(index) { mutableStateOf(pdf.cached(index, THUMB_WIDTH)) }
    LaunchedEffect(index) { if (bmp == null) bmp = pdf.render(index, THUMB_WIDTH) }
    val shown by androidx.compose.runtime.produceState<Bitmap?>(null, bmp, page.rotation) {
        val b = bmp
        value = if (b == null || page.rotation == 0) b else withContext(Dispatchers.Default) {
            runCatching { Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(page.rotation.toFloat()) }, true) }.getOrNull()
        }
    }
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(if (lifted) 12.dp else 0.dp, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(androidx.compose.ui.graphics.Color.White)
                .border(if (selected || lifted) 2.dp else 1.dp, if (selected || lifted) CtrlColors.AccentText else CtrlColors.Outline, MaterialTheme.shapes.medium)
                .clickable(enabled = !lifted, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val b = shown
            if (b == null) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = CtrlColors.AccentText)
            } else {
                Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            Surface(shape = CtrlShapes.Chip, color = CtrlColors.Accent, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                Text("%02d".format(position), color = CtrlColors.OnAccent, style = CtrlType.Status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, null, tint = CtrlColors.AccentText, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (docNumber != null) SectionLabel(stringResource(R.string.pages_doc_label, docNumber), Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (splitAfter) Icon(Icons.Outlined.ContentCut, stringResource(R.string.pages_split_here), tint = CtrlColors.AccentText, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PageTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else CtrlColors.AccentText)
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (danger) MaterialTheme.colorScheme.error else CtrlColors.Text)
        }
    }
}

private const val THUMB_WIDTH = 320
