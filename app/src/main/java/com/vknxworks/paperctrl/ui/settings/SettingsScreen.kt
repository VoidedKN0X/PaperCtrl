package com.vknxworks.paperctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.settings.AppSettings
import com.vknxworks.paperctrl.data.settings.ThemeChoice
import com.vknxworks.paperctrl.data.settings.PdfQualityChoice
import com.vknxworks.paperctrl.notify.InboxDigestWorker
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.NtfyRoute
import com.vknxworks.paperctrl.ui.lock.AppLock
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupChoice
import com.vknxworks.paperctrl.ui.components.GroupSwitch
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.settings_scanning), footer = stringResource(R.string.settings_quality_footer)) {
                GroupChoice(
                    stringResource(R.string.settings_quality),
                    PdfQualityChoice.entries.map { it to stringResource(it.label) },
                    s.pdfQuality,
                ) { v -> update { it.copy(pdfQuality = v) } }
                GroupSwitch(stringResource(R.string.settings_auto_capture), stringResource(R.string.settings_auto_capture_body), s.autoCapture) { v ->
                    update { it.copy(autoCapture = v) }
                }
            }

            Group(stringResource(R.string.settings_notifications)) {
                GroupSwitch(stringResource(R.string.settings_notify_added), stringResource(R.string.settings_notify_added_body), s.notifyAdded) { v -> update { it.copy(notifyAdded = v) } }
                GroupSwitch(stringResource(R.string.settings_notify_failed), stringResource(R.string.settings_notify_failed_body), s.notifyFailed) { v -> update { it.copy(notifyFailed = v) } }
                GroupSwitch(stringResource(R.string.settings_digest), stringResource(R.string.settings_digest_body), s.inboxDigest) { v ->
                    update { it.copy(inboxDigest = v) }
                    InboxDigestWorker.schedule(context, v)
                }
                GroupRow(
                    stringResource(R.string.ntfy_title),
                    stringResource(if (s.pushEnabled) R.string.settings_on else R.string.settings_ntfy_body),
                    showChevron = true,
                    onClick = { nav.navigate(NtfyRoute) },
                )
            }

            Group(stringResource(R.string.settings_privacy), footer = stringResource(R.string.settings_privacy_footer)) {
                GroupSwitch(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_body), s.appLock) { v ->
                    if (!v || AppLock.canAuthenticate(context)) {
                        update { it.copy(appLock = v) }
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.lock_unavailable), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                GroupSwitch(stringResource(R.string.settings_secure), stringResource(R.string.settings_secure_body), s.secureScreens) { v -> update { it.copy(secureScreens = v) } }
            }

            Group(stringResource(R.string.settings_appearance)) {
                GroupChoice(
                    stringResource(R.string.settings_theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(
                    stringResource(R.string.settings_dynamic),
                    stringResource(R.string.settings_dynamic_body),
                    s.dynamicColor,
                ) { v -> update { it.copy(dynamicColor = v) } }
            }
        }
    }
}
