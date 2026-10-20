package com.vknxworks.paperctrl

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.paperctrl.notify.InboxDigestWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.vknxworks.paperctrl.notify.Push
import com.vknxworks.paperctrl.notify.Shortcuts
import com.vknxworks.paperctrl.ui.lock.AppLock
import com.vknxworks.paperctrl.ui.lock.LockScreen
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

class MainActivity : FragmentActivity() {

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
            // Optional: keep the app's content out of screenshots and screen recordings.
            LaunchedEffect(settings.secureScreens) {
                if (settings.secureScreens) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val locked by AppLock.locked.collectAsStateWithLifecycle()
            PaperCtrlTheme(settings.theme, settings.dynamicColor) {
                if (settings.appLock && locked && AppLock.canAuthenticate(this)) LockScreen(this) else AppRoot()
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
