package com.vknxworks.paperctrl.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.vknxworks.paperctrl.R
import java.util.concurrent.Executors

/**
 * Reads an archive serial number from a barcode or QR label (Paperless' default labels say "ASN00042").
 * Calls [onResult] with the number found.
 */
@Composable
fun AsnScanDialog(onDismiss: () -> Unit, onResult: (Int) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onDismiss()
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    val result by rememberUpdatedState(onResult)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                val owner = LocalLifecycleOwner.current
                val executor = remember { Executors.newSingleThreadExecutor() }
                val controller = remember {
                    LifecycleCameraController(context).apply {
                        setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                        imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    }
                }
                DisposableEffect(owner) {
                    val reader = MultiFormatReader().apply {
                        setHints(
                            mapOf(
                                DecodeHintType.POSSIBLE_FORMATS to listOf(
                                    BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
                                    BarcodeFormat.DATA_MATRIX, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.ITF,
                                ),
                                DecodeHintType.TRY_HARDER to true,
                            ),
                        )
                    }
                    var done = false
                    controller.setImageAnalysisAnalyzer(executor) { image ->
                        try {
                            if (done) return@setImageAnalysisAnalyzer
                            val plane = image.planes[0]
                            val stride = plane.rowStride
                            val data = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                            val h = image.height
                            val w = image.width
                            val text = decode(reader, data, stride, w, h)
                                ?: decode(reader, rotate(data, stride, w, h), h, h, w)
                            val asn = text?.let(::parseAsn)
                            if (asn != null) {
                                done = true
                                ContextCompat.getMainExecutor(context).execute { result(asn) }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            image.close()
                        }
                    }
                    controller.bindToLifecycle(owner)
                    onDispose {
                        controller.clearImageAnalysisAnalyzer()
                        controller.unbind()
                        executor.shutdown()
                    }
                }
                AndroidView(
                    factory = { PreviewView(it).apply { this.controller = controller } },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.75f).height(160.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
                )
            }
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White) }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.asn_scan_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private fun decode(reader: MultiFormatReader, data: ByteArray, dataWidth: Int, w: Int, h: Int): String? = runCatching {
    val source = PlanarYUVLuminanceSource(data, dataWidth, h, 0, 0, w, h, false)
    reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
}.getOrNull().also { reader.reset() }

/** Rotates the luminance plane 90° clockwise, so barcodes held either way round can be read. */
private fun rotate(data: ByteArray, stride: Int, w: Int, h: Int): ByteArray {
    val out = ByteArray(w * h)
    for (y in 0 until h) {
        val row = y * stride
        for (x in 0 until w) out[x * h + (h - 1 - y)] = data[row + x]
    }
    return out
}

/** "ASN00042", "asn 42", "42" → 42. Anything without digits isn't an ASN. */
fun parseAsn(text: String): Int? {
    val t = text.trim()
    val digits = Regex("(\\d{1,9})").findAll(t).lastOrNull()?.value ?: return null
    if (t.length > 20 && !t.startsWith("ASN", ignoreCase = true)) return null
    return digits.toIntOrNull()
}
