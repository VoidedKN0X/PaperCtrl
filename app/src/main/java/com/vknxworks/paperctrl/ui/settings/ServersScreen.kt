package com.vknxworks.paperctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.auth.Server
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.nav.AddServerRoute
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import kotlinx.coroutines.launch

/** Saved servers: switch between them, add one, or remove one from this phone. */
@Composable
fun ServersScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Server?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.servers_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Group(footer = stringResource(R.string.servers_footer)) {
                servers.forEach { s ->
                    val isActive = s.id == active?.server?.id
                    GroupRow(
                        stringResource(R.string.servers_row_title, s.displayName, s.host),
                        serverSubtitle(s),
                        onClick = if (isActive) null else { { scope.launch { c.sessions.switchTo(s.id) }; Unit } },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) Icon(Icons.Filled.CheckCircle, stringResource(R.string.servers_active), tint = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { removing = s }) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.servers_remove))
                                }
                            }
                        },
                    )
                }
            }
            Group {
                GroupRow(stringResource(R.string.servers_add), icon = Icons.Filled.Add, showChevron = true, onClick = { nav.navigate(AddServerRoute) })
            }
        }
    }

    removing?.let { s ->
        ConfirmDialog(
            stringResource(R.string.servers_remove_title, s.host),
            stringResource(R.string.servers_remove_body),
            stringResource(R.string.servers_remove),
            onDismiss = { removing = null },
        ) {
            removing = null
            c.appScope.launch { c.removeServer(s.id) }
        }
    }
}

@Composable
private fun serverSubtitle(s: Server): String {
    val parts = mutableListOf(s.url.removeSuffix("/"))
    s.lanUrl?.let { parts += stringResource(R.string.servers_home_address, it.removeSuffix("/")) }
    s.serverVersion?.let { parts += stringResource(R.string.servers_version, it) }
    if (!s.isEncrypted()) parts += stringResource(R.string.not_encrypted)
    s.lanUrl?.let { if (!s.isEncrypted(it)) parts += stringResource(R.string.servers_home_not_encrypted) }
    return parts.joinToString("  ·  ")
}
