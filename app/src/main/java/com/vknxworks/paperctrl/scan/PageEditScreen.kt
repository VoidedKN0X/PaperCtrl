package com.vknxworks.paperctrl.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.ScannerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

/** Edits one page: drag its corners, rotate it, pick a filter, retake or delete it. Changes save as you go. */
@Composable
fun PageEditScreen(pageId: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val pages by c.scan.pages.collectAsStateWithLifecycle()
    val page = pages.firstOrNull { it.id == pageId }
    var photo by remember(pageId) { mutableStateOf<Bitmap?>(null) }
    var corners by remember(pageId) { mutableStateOf(page?.corners ?: ImageOps.FULL_PAGE) }
    var showResult by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(pageId, page?.version) {
        photo = withContext(Dispatchers.Default) { runCatching { ImageOps.decode(c.scan.photo(pageId), 1600) }.getOrNull() }
        page?.let { corners = it.corners }
    }
    LaunchedEffect(page?.version, showResult) {
        if (showResult && page != null) result = c.scan.previewFile(page, 1400)
    }

    if (page == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }

    fun save(p: ScanPage) {
        c.appScope.launch { c.scan.update(p) }
    }

    val pageNumber = pages.indexOfFirst { it.id == pageId } + 1
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.page_title, pageNumber, pages.size)) {
                IconButton(onClick = { showResult = !showResult }) {
                    Icon(
                        if (showResult) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        stringResource(if (showResult) R.string.page_show_crop else R.string.page_show_result),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val bmp = photo
                if (showResult) {
                    if (result == null) CircularProgressIndicator() else AsyncImage(model = result, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else if (bmp == null) {
                    CircularProgressIndicator()
                } else {
                    val density = LocalDensity.current
                    val boxW = with(density) { maxWidth.toPx() }
                    val boxH = with(density) { maxHeight.toPx() }
                    val aspect = bmp.width.toFloat() / bmp.height
                    val (w, h) = if (boxW / boxH > aspect) boxH * aspect to boxH else boxW to boxW / aspect
                    val ox = (boxW - w) / 2
                    val oy = (boxH - h) / 2
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Image(image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    val accent = MaterialTheme.colorScheme.primary
                    val handleRadius = with(density) { 14.dp.toPx() }
                    var active by remember { mutableStateOf(-1) }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(w, h) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    active = corners.withIndex().minByOrNull { (_, p) -> hypot(ox + p.x * w - pos.x, oy + p.y * h - pos.y) }
                                        ?.takeIf { (_, p) -> hypot(ox + p.x * w - pos.x, oy + p.y * h - pos.y) < 48.dp.toPx() }?.index ?: -1
                                },
                                onDrag = { change, amount ->
                                    if (active >= 0) {
                                        change.consume()
                                        corners = corners.toMutableList().also { list ->
                                            val p = list[active]
                                            list[active] = Corner((p.x + amount.x / w).coerceIn(0f, 1f), (p.y + amount.y / h).coerceIn(0f, 1f))
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (active >= 0) save(page.copy(corners = corners))
                                    active = -1
                                },
                                onDragCancel = { active = -1 },
                            )
                        },
                    ) {
                        val pts = corners.map { Offset(ox + it.x * w, oy + it.y * h) }
                        // Darken everything outside the page.
                        val outline = Path().apply {
                            pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                            close()
                        }
                        drawPath(outline, accent.copy(alpha = 0.15f))
                        drawPath(outline, accent, style = Stroke(width = 2.5.dp.toPx()))
                        pts.forEachIndexed { i, p ->
                            drawCircle(Color.White, handleRadius + 2.dp.toPx(), p)
                            drawCircle(if (i == active) accent else accent.copy(alpha = 0.85f), handleRadius, p)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScanFilter.entries.forEach { f ->
                    FilterChip(
                        selected = page.filter == f,
                        onClick = {
                            save(page.copy(filter = f))
                            // Remember the last filter for new pages.
                            c.appScope.launch { c.settings.update { it.copy(scanFilter = f) } }
                        },
                        label = { Text(stringResource(f.label)) },
                    )
                }
                TextButton(onClick = { c.appScope.launch { c.scan.applyFilterToAll(page.filter) } }) {
                    Text(stringResource(R.string.page_filter_all))
                }
            }

            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolButton(Icons.AutoMirrored.Outlined.RotateLeft, stringResource(R.string.page_rotate_left)) {
                    save(page.copy(rotation = (page.rotation + 270) % 360))
                }
                ToolButton(Icons.AutoMirrored.Outlined.RotateRight, stringResource(R.string.page_rotate_right)) {
                    save(page.copy(rotation = (page.rotation + 90) % 360))
                }
                ToolButton(Icons.Outlined.AutoFixHigh, stringResource(R.string.page_detect)) {
                    scope.launch {
                        val found = photo?.let { b -> withContext(Dispatchers.Default) { ImageOps.detect(b) } }
                        corners = found ?: ImageOps.FULL_PAGE
                        save(page.copy(corners = corners))
                    }
                }
                ToolButton(Icons.Outlined.CropFree, stringResource(R.string.page_full)) {
                    corners = ImageOps.FULL_PAGE
                    save(page.copy(corners = corners))
                }
                ToolButton(Icons.Outlined.CameraAlt, stringResource(R.string.page_retake)) { nav.navigate(ScannerRoute(retakeId = pageId)) }
                ToolButton(Icons.Outlined.Delete, stringResource(R.string.page_delete), danger = true) { deleting = true }
            }
        }
    }

    if (deleting) {
        ConfirmDialog(
            stringResource(R.string.page_delete_title),
            stringResource(R.string.page_delete_body),
            stringResource(R.string.page_delete),
            onDismiss = { deleting = false },
        ) {
            deleting = false
            c.appScope.launch { c.scan.remove(pageId) }
            nav.back()
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
