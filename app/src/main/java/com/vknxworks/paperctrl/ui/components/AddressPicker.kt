package com.vknxworks.paperctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.auth.Server
import com.vknxworks.paperctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** "Main address" or "Home-network address", for the address in use. */
@Composable
fun addressKind(lan: Boolean): String = stringResource(if (lan) R.string.address_home else R.string.address_main)

/**
 * Lets the user pick which of a server's two addresses PaperCtrl uses. The choice is kept until the
 * user changes it here again; PaperCtrl never switches on its own.
 */
@Composable
fun AddressPickerSheet(server: Server, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val lan = server.lanUrl ?: return
    val options = listOf(
        false to stringResource(R.string.address_option, addressKind(false), server.url.removeSuffix("/")),
        true to stringResource(R.string.address_option, addressKind(true), lan.removeSuffix("/")),
    )
    ChoiceSheet(
        stringResource(R.string.address_pick_title, server.host),
        options,
        server.usesLan,
        onDismiss = onDismiss,
    ) { useLan ->
        onDismiss()
        c.appScope.launch { c.sessions.useAddress(server.id, useLan) }
    }
}

/**
 * Shown when the active server can't be reached and it has a second address: says which address
 * failed and offers to use the other one. Only switches when the user taps the button.
 */
@Composable
fun SwitchAddressBar(onSwitched: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    if (s.server.lanUrl == null) return
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = Gutter, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (s.viaLan) R.string.address_unreachable_home else R.string.address_unreachable_main),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            scope.launch {
                c.sessions.useAddress(s.server.id, !s.viaLan)
                onSwitched()
            }
        }) {
            Text(stringResource(if (s.viaLan) R.string.address_use_main else R.string.address_use_home))
        }
    }
}
