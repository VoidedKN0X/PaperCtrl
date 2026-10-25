package com.vknxworks.paperctrl.share

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.vknxworks.paperctrl.MainActivity
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.UploadMeta
import com.vknxworks.paperctrl.data.queue.StagedFile
import com.vknxworks.paperctrl.data.queue.Staging
import com.vknxworks.paperctrl.ui.theme.PaperCtrlTheme
import com.vknxworks.paperctrl.ui.theme.CTRL_BAR_SCRIM
import com.vknxworks.paperctrl.ui.lock.AppLock
import com.vknxworks.paperctrl.ui.lock.AppLockGate
import com.vknxworks.paperctrl.notify.NotifyPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.vknxworks.paperctrl.ui.lock.lockCovering
import com.vknxworks.paperctrl.ui.components.WindowPrivacy

/**
 * "Share to PaperCtrl": receives one or more files (or plain text) from any app, copies them into
 * the app straight away, and queues them for Paperless with optional details. Also used for files
 * picked inside the app.
 */
class ShareActivity : ComponentActivity() {

    private var staging: File? = null
    private var files by mutableStateOf<List<StagedFile>?>(null)
    private var busy by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        askForNotifications()
        val dir = c.queue.newStagingDir().also { staging = it }
        lifecycleScope.launch {
            val uris = sharedUris(intent)
            val copied = if (uris.isNotEmpty()) {
                Staging.copyIn(this@ShareActivity, uris, dir).also { staged ->
                    // Some (not all) were refused: not readable, another app's link to PaperCtrl's own files, or too large.
                    val skipped = uris.size - staged.size
                    if (skipped > 0 && staged.isNotEmpty()) {
                        Toast.makeText(
                            this@ShareActivity,
                            resources.getQuantityString(R.plurals.share_skipped, skipped, skipped),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } else {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                    ?.let { listOf(Staging.fromText(dir, it, intent.getStringExtra(Intent.EXTRA_SUBJECT))) }
                    .orEmpty()
            }
            files = copied
        }

        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // Follow the device theme: light bar icons in dark mode, dark icons in light mode, over the
            // app background, edge to edge.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(CTRL_BAR_SCRIM) else SystemBarStyle.light(CTRL_BAR_SCRIM, CTRL_BAR_SCRIM)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val secure = settings.hideScreenshots || lockCovering(settings.appLock, c.vault)
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || settings.appLock || c.vault.enabled)
            PaperCtrlTheme(settings.theme, settings.dynamicColor) {
                // Shared files only reach the queue once the app lock is unlocked.
                AppLockGate { ShareScreen(
                    files = files,
                    busy = busy,
                    onCancel = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onUpload = ::upload,
                ) }
            }
        }
    }

    private fun upload(serverId: String, mergeImages: Boolean, meta: UploadMeta) {
        val all = files ?: return
        val dir = staging ?: return
        if (busy) return
        busy = true
        val c = container
        lifecycleScope.launch {
            try {
                val images = all.filter { it.isImage }
                val docs = if (mergeImages && images.size >= 2) {
                    listOf(Staging.mergeImages(dir, images, getString(R.string.share_merged_name))) + all.filterNot { it.isImage }
                } else {
                    all
                }
                // A title only makes sense for a single document.
                val perDoc = if (docs.size == 1) meta else meta.copy(title = null)
                docs.forEach { c.queue.enqueue(serverId, it.file, it.name, it.mimeType, perDoc) }
                Toast.makeText(
                    this@ShareActivity,
                    resources.getQuantityString(R.plurals.share_queued, docs.size, docs.size),
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                busy = false
                Toast.makeText(this@ShareActivity, getString(R.string.share_prepare_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Whatever wasn't queued (cancelled, or the merged images' originals) is removed.
        if (isFinishing) staging?.let { container.queue.discardStaging(it) }
    }

    private fun askForNotifications() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Counts as PaperCtrl's one automatic ask, so the main screens don't ask again right after.
            NotifyPermission.markAsked(this)
            AppLock.expectReturn()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> {
        val out = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(out::add)
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(out::addAll)
        }
        intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(out::add) }
        return out.distinct()
    }

    companion object {
        /** File types Paperless-ngx can consume (office formats need Tika on the server). */
        val SUPPORTED_TYPES = arrayOf(
            "application/pdf", "image/*", "text/plain", "text/csv", "text/html", "text/rtf", "message/rfc822",
            "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
        )

        /** Opens the share screen for files picked inside the app. */
        fun intent(context: Context, uris: List<Uri>): Intent =
            Intent(context, ShareActivity::class.java)
                .setAction(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
