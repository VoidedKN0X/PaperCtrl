package com.vknxworks.paperctrl.ui.tabs

import com.vknxworks.paperctrl.ui.theme.Gutter
import com.vknxworks.paperctrl.ui.theme.CtrlType
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.paperctrl.ui.components.CtrlHeroCard
import com.vknxworks.paperctrl.ui.lock.AppLock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.queue.QueueStatus
import com.vknxworks.paperctrl.share.ShareActivity
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.LocalBottomClearance
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.TabTitle
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.QueueRoute
import com.vknxworks.paperctrl.ui.nav.ScanReviewRoute
import com.vknxworks.paperctrl.ui.nav.ScannerRoute

/** Getting paper and files into Paperless: the camera scanner, files from the phone, and the upload queue. */
@Composable
fun ScanTab() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val items by c.queue.items.collectAsStateWithLifecycle()
    val scanPages by c.scan.pages.collectAsStateWithLifecycle()
    val active = items.count { it.isActive }
    val failed = items.count { it.status == QueueStatus.FAILED }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) context.startActivity(ShareActivity.intent(context, uris))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_scan)) {
            IconButton(onClick = { nav.navigate(QueueRoute) }) {
                BadgedBox(badge = { if (active + failed > 0) Badge { Text("${active + failed}") } }) {
                    Icon(Icons.Outlined.CloudUpload, stringResource(R.string.queue_title))
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp + LocalBottomClearance.current),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 460.dp).padding(horizontal = Gutter, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hero: pages scanned and waiting to be uploaded; tapping it opens the scanner.
                CtrlHeroCard(
                    label = stringResource(R.string.scan_hero_label),
                    value = "%02d".format(scanPages.size),
                    trailingLabel = if (active > 0) stringResource(R.string.scan_hero_uploading, active) else stringResource(R.string.scan_hero_pages),
                    onClick = { nav.navigate(if (scanPages.isEmpty()) ScannerRoute() else ScanReviewRoute) },
                )
                Text(stringResource(R.string.scan_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text(
                    stringResource(R.string.scan_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                PillButton(
                    stringResource(if (scanPages.isEmpty()) R.string.scan_start else R.string.scan_continue),
                    onClick = { nav.navigate(if (scanPages.isEmpty()) ScannerRoute() else ScanReviewRoute) },
                    icon = Icons.Outlined.DocumentScanner,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (scanPages.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.scan_pages_waiting, scanPages.size, scanPages.size),
                        style = CtrlType.Mono,
                        color = CtrlColors.Accent,
                    )
                }
                CtrlSecondaryButton(
                    stringResource(R.string.scan_pick_files),
                    onClick = { AppLock.expectReturn(); pick.launch(ShareActivity.SUPPORTED_TYPES) },
                    icon = Icons.Outlined.UploadFile,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.scan_share_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.queue_title),
                    when {
                        active > 0 -> pluralStringResource(R.plurals.scan_queue_active, active, active)
                        failed > 0 -> pluralStringResource(R.plurals.scan_queue_failed, failed, failed)
                        else -> stringResource(R.string.scan_queue_idle)
                    },
                    icon = Icons.Outlined.CloudUpload,
                    showChevron = true,
                    onClick = { nav.navigate(QueueRoute) },
                )
            }
        }
    }
}
