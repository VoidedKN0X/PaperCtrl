package com.vknxworks.paperctrl

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Document titles, file names and the sign-in screen never show in the recent-apps preview.
        setRecentsScreenshotEnabled(false)
        val c = container
        if (savedInstanceState == null) c.telemetry.appOpened()
        handleOpen(intent)
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
            PaperCtrlTheme(settings.theme, settings.dynamicColor) {
                AppRoot()
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
