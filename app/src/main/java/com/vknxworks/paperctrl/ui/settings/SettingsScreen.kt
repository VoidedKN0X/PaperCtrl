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
import com.vknxworks.paperctrl.ui.lock.PinRequest
import com.vknxworks.paperctrl.ui.lock.findActivity
import com.vknxworks.paperctrl.data.lock.LockVault
import com.vknxworks.paperctrl.data.settings.LockTimeout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.remember
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
    val bioAvailable = remember { AppLock.biometricAvailable(context) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.settings_appearance)) {
                GroupChoice(
                    stringResource(R.string.settings_theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                    icon = Icons.Outlined.DarkMode,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(
                    stringResource(R.string.settings_dynamic),
                    stringResource(R.string.settings_dynamic_body),
                    s.dynamicColor,
                    icon = Icons.Outlined.Palette,
                ) { v -> update { it.copy(dynamicColor = v) } }
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

            val lockOn = c.vault.enabled
            Group(
                stringResource(R.string.settings_privacy),
                footer = stringResource(R.string.settings_privacy_footer) +
                    (if (lockOn) "\n\n" + stringResource(R.string.settings_lock_background_note) else ""),
            ) {
                GroupSwitch(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_body), lockOn, icon = Icons.Outlined.Lock) { v ->
                    AppLock.request = if (v) PinRequest.ENABLE else PinRequest.DISABLE
                }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.settings_change_pin),
                        stringResource(R.string.settings_change_pin_body, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { AppLock.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.settings_fingerprint),
                        stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.settings_fingerprint_body else R.string.settings_fingerprint_unavailable),
                        c.vault.biometricEnabled,
                        icon = Icons.Outlined.Fingerprint,
                    ) { on ->
                        if (!on) {
                            c.vault.disableBiometric()
                            return@GroupSwitch
                        }
                        val failed = context.getString(R.string.settings_fingerprint_failed)
                        val activity = context.findActivity()
                        val cipher = if (bioAvailable) c.vault.biometricEnrolCipher() else null
                        if (activity == null || cipher == null) {
                            android.widget.Toast.makeText(
                                context,
                                if (bioAvailable) failed else context.getString(R.string.settings_fingerprint_unavailable),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@GroupSwitch
                        }
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enrol_title),
                            context.getString(R.string.lock_fingerprint_enrol_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.settings_lock_again),
                        LockTimeout.entries.map { it to stringResource(it.label) },
                        s.lockTimeout,
                        icon = Icons.Outlined.Timer,
                    ) { v -> update { it.copy(lockTimeout = v) } }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    s.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { v -> update { it.copy(hideScreenshots = v) } }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    s.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { v -> update { it.copy(hideRecents = v) } }
            }

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
        }
    }
}
