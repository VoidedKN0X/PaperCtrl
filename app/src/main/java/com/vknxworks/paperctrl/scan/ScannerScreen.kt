package com.vknxworks.paperctrl.scan

import com.vknxworks.paperctrl.ui.lock.AppLock
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.ScanReviewRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/** What the live page detection currently sees, in upright, normalised coordinates. */
private data class LiveQuad(val corners: List<Corner>?, val frameW: Int, val frameH: Int, val steady: Float)

/**
 * The camera: outlines the page it finds, captures by itself once the page holds still (or with the
 * shutter button), and adds each capture as a page. [retakeId] replaces that page instead.
 */
@Composable
fun ScannerScreen(retakeId: String?) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val pages by c.scan.pages.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var busy by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            importImages(context, uris, settings.scanFilter)
            busy = false
            if (retakeId == null) nav.navigate(ScanReviewRoute)
        }
    }

    if (!granted) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            MessageView(
                Icons.Outlined.PhotoLibrary,
                stringResource(R.string.scanner_camera_needed),
                stringResource(R.string.scanner_camera_needed_body),
                actionLabel = stringResource(if (asked) R.string.scanner_open_settings else R.string.scanner_allow_camera),
                onAction = {
                    if (asked) {
                        AppLock.expectReturn()
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            IconButton(onClick = nav::back, modifier = Modifier.padding(8.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var live by remember { mutableStateOf(LiveQuad(null, 3, 4, 0f)) }
    var torch by remember { mutableStateOf(false) }
    var auto by remember(settings.autoCapture) { mutableStateOf(settings.autoCapture) }
    val flash = remember { Animatable(0f) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            val ratio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            previewResolutionSelector = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
            // About 12 MP: plenty for a page, and much faster than the sensor's full resolution.
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratio)
                .setResolutionStrategy(ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratio)
                .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    fun capture() {
        if (busy) return
        busy = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { flash.snapTo(0.8f); flash.animateTo(0f, tween(250)) }
        val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        runCatching { c.scan.addPhoto(file, settings.scanFilter, retakeId) }
                            .onFailure { Toast.makeText(context, context.getString(R.string.scanner_capture_failed), Toast.LENGTH_SHORT).show() }
                        withContext(Dispatchers.IO) { file.delete() }
                        busy = false
                        if (retakeId != null) nav.back()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    busy = false
                    Toast.makeText(context, context.getString(R.string.scanner_capture_failed), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    val autoNow by rememberUpdatedState(auto)
    val busyNow by rememberUpdatedState(busy)
    val captureNow by rememberUpdatedState(::capture)

    DisposableEffect(lifecycleOwner) {
        ImageOps.ensureLoaded()
        val tracker = SteadyTracker()
        controller.setImageAnalysisAnalyzer(analysisExecutor) { image ->
            try {
                val plane = image.planes[0]
                val gray = Mat(image.height, image.width, CvType.CV_8UC1, plane.buffer, plane.rowStride.toLong())
                val raw = ImageOps.detect(gray)
                gray.release()
                val rot = image.imageInfo.rotationDegrees
                val upright = raw?.let { ImageOps.rotateCorners(it, rot) }
                val (w, h) = if (rot % 180 == 0) image.width to image.height else image.height to image.width
                val steady = tracker.feed(upright)
                ContextCompat.getMainExecutor(context).execute {
                    live = LiveQuad(upright, w, h, steady)
                    if (autoNow && !busyNow && steady >= 1f) {
                        tracker.reset()
                        captureNow()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                image.close()
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        val accent = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            val corners = live.corners ?: return@Canvas
            // The preview is fitted (not cropped) in the view, with the same aspect ratio as the analysed frame.
            val frameAspect = live.frameW.toFloat() / live.frameH
            val viewAspect = size.width / size.height
            val (cw, ch) = if (viewAspect > frameAspect) size.height * frameAspect to size.height else size.width to size.width / frameAspect
            val ox = (size.width - cw) / 2
            val oy = (size.height - ch) / 2
            val path = Path().apply {
                corners.forEachIndexed { i, p ->
                    val x = ox + p.x * cw
                    val y = oy + p.y * ch
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, accent.copy(alpha = 0.18f + 0.22f * live.steady))
            drawPath(path, accent, style = Stroke(width = 3.dp.toPx()))
            corners.forEach { p -> drawCircle(accent, 6.dp.toPx(), Offset(ox + p.x * cw, oy + p.y * ch)) }
        }
        Box(Modifier.fillMaxSize().alpha(flash.value).background(Color.White))

        // Top: close, torch, auto-capture.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Filled.Close, stringResource(R.string.close)) { nav.back() }
            Box(Modifier.weight(1f))
            RoundIcon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, stringResource(R.string.scanner_torch), active = torch) {
                torch = !torch
                controller.enableTorch(torch)
            }
            RoundIcon(
                if (auto) Icons.Outlined.AutoMode else Icons.Outlined.TouchApp,
                stringResource(if (auto) R.string.scanner_auto_on else R.string.scanner_auto_off),
                active = auto,
            ) {
                auto = !auto
                c.appScope.launch { c.settings.update { it.copy(autoCapture = auto) } }
                Toast.makeText(context, context.getString(if (auto) R.string.scanner_auto_on else R.string.scanner_auto_off), Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom: hint, gallery, shutter, pages.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                Text(
                    stringResource(
                        when {
                            busy -> R.string.scanner_hint_saving
                            live.corners == null -> R.string.scanner_hint_searching
                            auto -> R.string.scanner_hint_hold
                            else -> R.string.scanner_hint_found
                        },
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    RoundIcon(Icons.Outlined.PhotoLibrary, stringResource(R.string.scanner_import), big = true) {
                        AppLock.expectReturn()
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Shutter(progress = if (auto) live.steady else 0f, busy = busy, onClick = ::capture)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (retakeId == null && pages.isNotEmpty()) {
                        val last = pages.last()
                        var thumb by remember { mutableStateOf<File?>(null) }
                        LaunchedEffect(last.id, last.version) { thumb = c.scan.previewFile(last, 300) }
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                .clickable { nav.navigate(ScanReviewRoute) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(model = thumb, contentDescription = stringResource(R.string.scanner_review), modifier = Modifier.fillMaxSize())
                            Surface(shape = CircleShape, color = accent, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                                Text(
                                    "${pages.size}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shutter(progress: Float, busy: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        if (progress > 0f) {
            CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.size(84.dp), color = Color.White, strokeWidth = 4.dp)
        }
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable(enabled = !busy, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean = false,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(if (big) 56.dp else 48.dp).clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White)
    }
}

/**
 * Counts how long the detected page has stayed in place. Returns 0..1, reaching 1 after about a
 * second of the corners moving less than 2% of the frame.
 */
private class SteadyTracker {
    private var last: List<Corner>? = null
    private var since = 0L

    fun reset() {
        last = null
        since = 0L
    }

    fun feed(corners: List<Corner>?): Float {
        val now = System.currentTimeMillis()
        val prev = last
        last = corners
        if (corners == null || prev == null) {
            since = now
            return 0f
        }
        val moved = corners.zip(prev).maxOf { (a, b) -> max(abs(a.x - b.x), abs(a.y - b.y)) }
        if (moved > 0.02f) since = now
        return ((now - since) / STEADY_MS.toFloat()).coerceIn(0f, 1f)
    }

    private companion object {
        const val STEADY_MS = 1100L
    }
}

/** Adds gallery images as pages. */
suspend fun importImages(context: Context, uris: List<Uri>, filter: ScanFilter) {
    val c = context.container
    for (uri in uris) {
        val tmp = File(context.cacheDir, "import-${UUID.randomUUID()}")
        runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            c.scan.addPhoto(tmp, filter)
        }
        withContext(Dispatchers.IO) { tmp.delete() }
    }
}
