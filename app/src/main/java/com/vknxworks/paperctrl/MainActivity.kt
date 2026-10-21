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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.data.settings.ThemeChoice
import com.vknxworks.paperctrl.notify.Notifications
import com.vknxworks.paperctrl.ui.nav.AppRoot
import com.vknxworks.paperctrl.ui.theme.PaperCtrlTheme
import com.vknxworks.paperctrl.ui.components.ScreenPrivacy
import com.vknxworks.paperctrl.ui.lock.lockCovering

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Document titles, file names and the sign-in screen never show in the recent-apps preview.
        setRecentsScreenshotEnabled(false)
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
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // "Hide in screenshots and recent apps", and always for signing in and the lock and PIN screens.
            val secure = settings.secureScreens || lockCovering(settings.appLock, c.vault) || ScreenPrivacy.forced > 0
            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
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
