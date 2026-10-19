package com.vknxworks.paperctrl.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Getting paper and files into Paperless. The camera scanner joins this tab in the next update. */
@Composable
fun ScanTab() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val items by c.queue.items.collectAsStateWithLifecycle()
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
                Modifier.widthIn(max = 420.dp).padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
                Text(stringResource(R.string.scan_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.scan_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PillButton(
                    stringResource(R.string.scan_pick_files),
                    onClick = { pick.launch(ShareActivity.SUPPORTED_TYPES) },
                    icon = Icons.Outlined.UploadFile,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
