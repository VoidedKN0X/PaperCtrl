package com.vknxworks.paperctrl.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vknxworks.paperctrl.ui.lock.AppLock
import com.vknxworks.paperctrl.ui.lock.findActivity

/**
 * Android 13's notification permission, which upload progress, results, the inbox reminder and ntfy
 * pushes all need. PaperCtrl asks for it once by itself; after that only when you turn something on.
 */
object NotifyPermission {
    private const val PREFS = "notify_permission"
    private const val KEY_ASKED = "asked"

    /** PaperCtrl may show notifications: the permission is granted and they aren't turned off. */
    fun allowed(context: Context): Boolean =
        !permissionMissing(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The Android 13+ runtime permission hasn't been granted. */
    fun permissionMissing(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Android's dialog was shown once, so PaperCtrl never shows it again on its own. */
    fun asked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** Android still shows its dialog: the first time, and once more after a "Don't allow". */
    fun dialogAvailable(context: Context): Boolean =
        permissionMissing(context) && (
            !asked(context) ||
                context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
            )

    /** PaperCtrl's page in Android's notification settings. */
    fun openSettings(context: Context) {
        AppLock.expectReturn()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

/**
 * Asks for notifications: Android's dialog while it still shows, otherwise PaperCtrl's notification
 * settings. [onResult] gets whether notifications can be shown now (not called after a trip to Settings;
 * screens check again on resume with [rememberNotificationsAllowed]).
 */
@Composable
fun rememberNotifyPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        latest(NotifyPermission.allowed(context))
    }
    return remember(launcher) {
        {
            if (NotifyPermission.allowed(context)) {
                latest(true)
            } else if (NotifyPermission.dialogAvailable(context)) {
                NotifyPermission.markAsked(context)
                AppLock.expectReturn()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotifyPermission.openSettings(context)
            }
        }
    }
}

/** Whether notifications are allowed, checked again every time the screen comes back (e.g. from Settings). */
@Composable
fun rememberNotificationsAllowed(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotifyPermission.allowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = NotifyPermission.allowed(context)
        onPauseOrDispose { }
    }
    return allowed to { v: Boolean -> allowed = v }
}
