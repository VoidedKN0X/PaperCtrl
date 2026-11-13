package com.vknxworks.paperctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.appDataStore
import com.vknxworks.paperctrl.data.queue.PdfQuality
import com.vknxworks.paperctrl.scan.ScanFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/** How large and sharp scanned pages are in the PDF. */
enum class PdfQualityChoice(@StringRes val label: Int, val quality: PdfQuality) {
    COMPACT(R.string.quality_compact, PdfQuality(maxSidePx = 1754, jpegQuality = 72, dpi = 150)),
    STANDARD(R.string.quality_standard, PdfQuality(maxSidePx = 2339, jpegQuality = 82, dpi = 200)),
    HIGH(R.string.quality_high, PdfQuality(maxSidePx = 3508, jpegQuality = 88, dpi = 300)),
}

/** When the app lock asks again after you leave the app. */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Last filter picked in the scanner, used for new pages. */
    val scanFilter: ScanFilter = ScanFilter.ENHANCED,
    val autoCapture: Boolean = true,
    val pdfQuality: PdfQualityChoice = PdfQualityChoice.STANDARD,
    val documentsGrid: Boolean = false,
    val notifyAdded: Boolean = true,
    val notifyFailed: Boolean = true,
    val inboxDigest: Boolean = false,
    /** Notifications from Paperless through UnifiedPush (ntfy). */
    val pushEnabled: Boolean = false,
    val appLock: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** "Hide in screenshots": FLAG_SECURE on every window (also blanks the recent-apps preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the recent-apps view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        /** Left over from the optional stats removed in 0.3.3; deleted at start and on every save. */
        val legacy = listOf(booleanPreferencesKey("usage_stats"), booleanPreferencesKey("usage_stats_asked"))
        val scanFilter = stringPreferencesKey("scan_filter")
        val autoCapture = booleanPreferencesKey("auto_capture")
        val pdfQuality = stringPreferencesKey("pdf_quality")
        val documentsGrid = booleanPreferencesKey("documents_grid")
        val notifyAdded = booleanPreferencesKey("notify_added")
        val notifyFailed = booleanPreferencesKey("notify_failed")
        val inboxDigest = booleanPreferencesKey("inbox_digest")
        val pushEnabled = booleanPreferencesKey("push_enabled")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        /** The old single "Block screenshots" switch: turned on, it turns both switches on. */
        val legacySecureScreens = booleanPreferencesKey("secure_screens")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    init {
        // Delete what the removed optional stats stored, so nothing of them stays on the phone.
        scope.launch { context.appDataStore.edit { p -> K.legacy.forEach { p.remove(it) } } }
    }

    /** The stored settings as they're read and changed, without [settings]' placeholder defaults at start-up. */
    val stored: Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        scanFilter = enumOr(this[K.scanFilter], ScanFilter.ENHANCED),
        autoCapture = this[K.autoCapture] ?: true,
        pdfQuality = enumOr(this[K.pdfQuality], PdfQualityChoice.STANDARD),
        documentsGrid = this[K.documentsGrid] ?: false,
        notifyAdded = this[K.notifyAdded] ?: true,
        notifyFailed = this[K.notifyFailed] ?: true,
        inboxDigest = this[K.inboxDigest] ?: false,
        pushEnabled = this[K.pushEnabled] ?: false,
        appLock = this[K.appLock] ?: false,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: this[K.legacySecureScreens] ?: false,
        hideRecents = this[K.hideRecents] ?: this[K.legacySecureScreens] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            K.legacy.forEach { p.remove(it) }
            p[K.scanFilter] = s.scanFilter.name
            p[K.autoCapture] = s.autoCapture
            p[K.pdfQuality] = s.pdfQuality.name
            p[K.documentsGrid] = s.documentsGrid
            p[K.notifyAdded] = s.notifyAdded
            p[K.notifyFailed] = s.notifyFailed
            p[K.inboxDigest] = s.inboxDigest
            p[K.pushEnabled] = s.pushEnabled
            p[K.appLock] = s.appLock
            p[K.lockTimeout] = s.lockTimeout.name
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p.remove(K.legacySecureScreens)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
