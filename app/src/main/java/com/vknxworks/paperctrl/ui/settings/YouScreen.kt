package com.vknxworks.paperctrl.ui.settings

import com.vknxworks.paperctrl.ui.lock.AppLock
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.text.style.TextOverflow
import com.vknxworks.paperctrl.ui.components.CtrlButton
import com.vknxworks.paperctrl.ui.components.CtrlSquareButton
import com.vknxworks.paperctrl.ui.components.SectionLabel
import com.vknxworks.paperctrl.ui.components.initials
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.BuildConfig
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.ui.components.AddressPickerSheet
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.ServersRoute
import com.vknxworks.paperctrl.ui.nav.SettingsRoute
import com.vknxworks.paperctrl.ui.theme.Gutter

private const val REPO_URL = "https://github.com/VoidedKN0X/PaperCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The You page, opened from your profile picture: your account, settings, and about the app. */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    val server = s.server
    var picking by remember { mutableStateOf(false) }

    // Keep the saved name, admin rights and server version current.
    LaunchedEffect(server.id) {
        runCatching { s.client.uiSettings() }.getOrNull()?.let { ui ->
            c.sessions.refreshDetails(server.id, ui.user.displayName, ui.user.isSuperuser, null)
        }
    }

    if (picking) AddressPickerSheet(server, onDismiss = { picking = false })

    Column(Modifier.fillMaxSize()) {
        // Close button on the left, mono "PROFILE" label centred.
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gutter, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = nav::back, modifier = Modifier.align(Alignment.CenterStart))
            SectionLabel(stringResource(R.string.you_profile))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials(server.displayName), style = MaterialTheme.typography.headlineSmall, color = CtrlColors.OnAccent)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.displayName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.address.removeSuffix("/"), style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val extra = listOfNotNull(
                        server.username.takeIf { it != server.displayName },
                        server.serverVersion?.let { stringResource(R.string.you_paperless_version, it) },
                        stringResource(R.string.you_admin).takeIf { server.isSuperuser },
                    ).joinToString(" · ")
                    if (extra.isNotEmpty()) Text(extra.uppercase(), style = CtrlType.Status, color = CtrlColors.Muted, maxLines = 2)
                }
            }

            Group(stringResource(R.string.you_server)) {
                GroupRow(
                    s.address.removeSuffix("/"),
                    listOfNotNull(
                        stringResource(if (s.viaLan) R.string.you_via_home else R.string.you_via_main).takeIf { server.lanUrl != null },
                        stringResource(R.string.not_encrypted).takeIf { !server.isEncrypted(s.address) },
                    ).joinToString("  ·  ").ifEmpty { null },
                    icon = if (s.viaLan) Icons.Outlined.Home else Icons.Outlined.Dns,
                    showChevron = server.lanUrl != null,
                    onClick = if (server.lanUrl != null) { { picking = true } } else null,
                )
                GroupRow(stringResource(R.string.servers_title), icon = Icons.Outlined.SwapHoriz, showChevron = true, onClick = { nav.navigate(ServersRoute) })
            }

            Group(stringResource(R.string.you_app)) {
                GroupRow(stringResource(R.string.settings), icon = Icons.Outlined.Tune, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Outlined.BugReport,
                    showChevron = true,
                    onClick = { context.openUrl(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Outlined.Code,
                    showChevron = true,
                    onClick = { context.openUrl(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Outlined.Gavel,
                    showChevron = true,
                    onClick = { context.openUrl(LICENCE_URL) },
                )
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlButton(
                    stringResource(R.string.support_title),
                    onClick = { context.openUrl(SPONSOR_URL) },
                    icon = Icons.Outlined.FavoriteBorder,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                stringResource(R.string.ctrl_footer),
                style = CtrlType.Status,
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME).uppercase(),
                style = CtrlType.Status,
                color = CtrlColors.Disabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Context.openUrl(url: String) {
    AppLock.expectReturn()
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.open_link_failed), Toast.LENGTH_SHORT).show()
    }
}
