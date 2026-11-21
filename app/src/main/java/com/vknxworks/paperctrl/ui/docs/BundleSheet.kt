package com.vknxworks.paperctrl.ui.docs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.createShareBundle
import com.vknxworks.paperctrl.data.api.shareUrl
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.ctrlSheet
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import com.vknxworks.paperctrl.ui.document.copyToClipboard
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

/**
 * One public link for the selected documents (a share-link bundle, Paperless-ngx 3). Paperless builds the zip
 * in the background; the link is copied right away and starts working when the zip is ready.
 */
@Composable
fun BundleSheet(selected: Set<Int>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf(7) }
    var original by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    ModalBottomSheet(modifier = Modifier.ctrlSheet(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CtrlColors.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(stringResource(R.string.bundles_share), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                pluralStringResource(R.plurals.bundles_share_body, selected.size, selected.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 7, 30, 0).forEach { d ->
                    FilterChip(
                        selected = days == d,
                        onClick = { days = d },
                        label = { Text(if (d == 0) stringResource(R.string.share_link_never_short) else pluralStringResource(R.plurals.share_link_days, d, d)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !original, onClick = { original = false }, label = { Text(stringResource(R.string.doc_archived)) })
                FilterChip(selected = original, onClick = { original = true }, label = { Text(stringResource(R.string.doc_original)) })
            }
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(color = CtrlColors.AccentText)
                } else {
                    PillButton(stringResource(R.string.bundles_create), modifier = Modifier.fillMaxWidth(), onClick = {
                        val cl = c.sessions.active.value?.client ?: return@PillButton
                        busy = true
                        scope.launch {
                            runCatching { cl.createShareBundle(selected, days.takeIf { it > 0 }, original) }
                                .onSuccess { bundle ->
                                    copyToClipboard(context, cl.shareUrl(bundle.slug))
                                    Toast.makeText(context, context.getString(R.string.bundles_created), Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                            busy = false
                        }
                    })
                }
            }
        }
    }
}
