package com.vknxworks.paperctrl.ui.document

import com.vknxworks.paperctrl.ui.components.ctrlSheet
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.files.LocalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shows a downloaded document: PDFs page by page, images, plain text, anything else via another app. */
@Composable
fun DocumentViewer(file: LocalFile, onOpenWith: () -> Unit, modifier: Modifier = Modifier) {
    when {
        file.mimeType == "application/pdf" -> PdfViewer(file, modifier)
        file.mimeType.startsWith("image/") -> ZoomBox(modifier) {
            AsyncImage(model = file.file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        file.mimeType.startsWith("text/") -> {
            var text by remember(file) { mutableStateOf<String?>(null) }
            LaunchedEffect(file) { text = withContext(Dispatchers.IO) { runCatching { file.file.readText().take(500_000) }.getOrNull() } }
            SelectionContainer(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.viewer_unsupported), style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = onOpenWith) {
                    Icon(Icons.Outlined.OpenInNew, null)
                    Text("  " + stringResource(R.string.viewer_open_with))
                }
            }
        }
    }
}

/** Pinch to zoom and drag to pan whatever's inside; double-tap to reset. */
@Composable
private fun ZoomBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
            .transformable(state)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    ) { content() }
}

/** Renders PDF pages with Android's own PdfRenderer, a few at a time, sharp at the current zoom. */
@Composable
private fun PdfViewer(file: LocalFile, modifier: Modifier) {
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.toInt()
    val pdf = remember(file) { runCatching { PdfDoc(file) }.getOrNull() }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }
    if (pdf == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.viewer_pdf_failed)) }
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var grid by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else Offset(offset.x + pan.x, offset.y)
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f; offset = Offset.Zero }) }
                .transformable(transform, canPan = { scale > 1f })
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x },
        ) {
            items(pdf.pageCount) { i ->
                PdfPage(pdf, i, (widthPx * 2).coerceAtMost(2400))
            }
        }
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            val current = listState.firstVisibleItemIndex + 1
            Text("$current / ${pdf.pageCount}", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        if (pdf.pageCount > 1) {
            SmallFloatingActionButton(onClick = { grid = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Outlined.GridView, stringResource(R.string.viewer_pages))
            }
        }
    }
    if (grid) {
        ModalBottomSheet(modifier = Modifier.ctrlSheet(), onDismissRequest = { grid = false }) {
            LazyVerticalGrid(GridCells.Adaptive(100.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pdf.pageCount) { i ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        grid = false
                        scope.launch { listState.scrollToItem(i) }
                    }) {
                        PdfPage(pdf, i, 300)
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(pdf: PdfDoc, index: Int, widthPx: Int) {
    var bmp by remember(index, widthPx) { mutableStateOf(pdf.cached(index, widthPx)) }
    LaunchedEffect(index, widthPx) { if (bmp == null) bmp = pdf.render(index, widthPx) }
    val aspect = pdf.aspect(index)
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** A PdfRenderer with a small bitmap cache; PdfRenderer allows one open page at a time, hence the lock. */
internal class PdfDoc(file: LocalFile) {
    private val fd = ParcelFileDescriptor.open(file.file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    private val aspects = FloatArray(renderer.pageCount) { -1f }
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    val pageCount: Int get() = renderer.pageCount

    fun aspect(i: Int): Float {
        if (aspects[i] < 0) {
            synchronized(renderer) {
                renderer.openPage(i).use { aspects[i] = it.width.toFloat() / it.height }
            }
        }
        return aspects[i].takeIf { it > 0 } ?: 0.707f
    }

    fun cached(i: Int, w: Int): Bitmap? = cache.get("$i@$w")

    suspend fun render(i: Int, w: Int): Bitmap? = withContext(Dispatchers.Default) {
        lock.withLock {
            runCatching {
                synchronized(renderer) {
                    renderer.openPage(i).use { page ->
                        val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cache.put("$i@$w", bmp)
                        bmp
                    }
                }
            }.getOrNull()
        }
    }

    fun close() {
        synchronized(renderer) { runCatching { renderer.close() } }
        runCatching { fd.close() }
    }
}
