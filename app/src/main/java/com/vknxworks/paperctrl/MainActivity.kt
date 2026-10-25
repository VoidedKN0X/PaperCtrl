package com.vknxworks.paperctrl

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.paperctrl.notify.InboxDigestWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.vknxworks.paperctrl.notify.Push
import com.vknxworks.paperctrl.notify.Shortcuts
import com.vknxworks.paperctrl.ui.lock.AppLockGate
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.notify.Notifications
import com.vknxworks.paperctrl.ui.nav.AppRoot
import com.vknxworks.paperctrl.ui.theme.PaperCtrlTheme
import com.vknxworks.paperctrl.ui.theme.CTRL_BAR_SCRIM
import com.vknxworks.paperctrl.ui.components.ScreenPrivacy
import com.vknxworks.paperctrl.ui.components.WindowPrivacy
import com.vknxworks.paperctrl.ui.lock.lockCovering

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        handleOpen(intent)
        if (savedInstanceState == null) {
            Shortcuts.publish(this)
            Push.load(this)
            // Read the stored setting: the in-memory one may not be loaded this early.
            c.appScope.launch { InboxDigestWorker.schedule(applicationContext, c.settings.stored.first().inboxDigest) }
        }
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // Ctrl / Signal is dark only: light bar icons over the app background, edge to edge.
            DisposableEffect(Unit) {
                val style = SystemBarStyle.dark(CTRL_BAR_SCRIM)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // "Hide in screenshots and recent apps", and always for signing in and the lock and PIN screens.
            // "Hide in recent apps" (off by default); with the app lock on, Recents always shows a blank
            // card, since a preview there would show documents without the PIN.
            val secure = settings.hideScreenshots || lockCovering(settings.appLock, c.vault) || ScreenPrivacy.forced > 0
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || settings.appLock || c.vault.enabled)
            PaperCtrlTheme(settings.theme, settings.dynamicColor) {
                AppLockGate { AppRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** A notification asked for a particular screen (the upload queue). */
    private fun handleOpen(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_OPEN)?.let { container.pendingOpen.value = it }
    }
}
