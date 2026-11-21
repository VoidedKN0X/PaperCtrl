package com.vknxworks.paperctrl.data.settings

import androidx.annotation.StringRes
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.scan.ScanFilter
import kotlinx.serialization.Serializable

/**
 * PaperCtrl's own settings as a file, for moving them to another phone or keeping a copy.
 *
 * Only settings go in. **Never** in the file, and never read from one: servers and their addresses, API
 * tokens, confirmed certificates, the app lock, its PIN, fingerprint unlock or when it locks again, the ntfy
 * set-up, encryption keys, the upload queue, scans or kept documents.
 *
 * An import can therefore never sign in anywhere, trust a certificate, allow an unencrypted address or
 * weaken the app lock. The two privacy switches (hide in screenshots, hide in recent apps) are only ever
 * turned **on** by an import, never off. Every field is optional, so a file from another version still reads.
 */
@Serializable
data class SettingsBackup(
    val app: String = APP,
    val format: Int = FORMAT,
    val scanFilter: String? = null,
    val autoCapture: Boolean? = null,
    val pdfQuality: String? = null,
    val documentsGrid: Boolean? = null,
    val notifyAdded: Boolean? = null,
    val notifyFailed: Boolean? = null,
    val inboxDigest: Boolean? = null,
    val liveUpdates: Boolean? = null,
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
) {
    companion object {
        const val APP = "PaperCtrl"
        const val FORMAT = 1
        /** Larger files aren't read at all: a settings file is a few hundred bytes. */
        const val MAX_BYTES = 64 * 1024

        fun of(s: AppSettings) = SettingsBackup(
            scanFilter = s.scanFilter.name,
            autoCapture = s.autoCapture,
            pdfQuality = s.pdfQuality.name,
            documentsGrid = s.documentsGrid,
            notifyAdded = s.notifyAdded,
            notifyFailed = s.notifyFailed,
            inboxDigest = s.inboxDigest,
            liveUpdates = s.liveUpdates,
            hideScreenshots = s.hideScreenshots,
            hideRecents = s.hideRecents,
        )

        fun encode(s: AppSettings): String = ApiJson.encodeToString(serializer(), of(s))

        /** The backup in [text], or null when it isn't a PaperCtrl settings file this version understands. */
        fun decode(text: String): SettingsBackup? {
            val b = runCatching { ApiJson.decodeFromString(serializer(), text) }.getOrNull() ?: return null
            return b.takeIf { it.app == APP && it.format in 1..FORMAT }
        }
    }

    /** [current] with this file's settings applied. Unknown values are skipped; privacy switches only go on. */
    fun applyTo(current: AppSettings): AppSettings = current.copy(
        scanFilter = ScanFilter.entries.firstOrNull { it.name == scanFilter } ?: current.scanFilter,
        autoCapture = autoCapture ?: current.autoCapture,
        pdfQuality = PdfQualityChoice.entries.firstOrNull { it.name == pdfQuality } ?: current.pdfQuality,
        documentsGrid = documentsGrid ?: current.documentsGrid,
        notifyAdded = notifyAdded ?: current.notifyAdded,
        notifyFailed = notifyFailed ?: current.notifyFailed,
        inboxDigest = inboxDigest ?: current.inboxDigest,
        liveUpdates = liveUpdates ?: current.liveUpdates,
        hideScreenshots = current.hideScreenshots || hideScreenshots == true,
        hideRecents = current.hideRecents || hideRecents == true,
    )
}

/** One setting an import would change. Switches carry [from]/[to]; choices carry the labels to show. */
data class SettingChange(
    @StringRes val title: Int,
    val from: Boolean? = null,
    val to: Boolean? = null,
    @StringRes val fromLabel: Int? = null,
    @StringRes val toLabel: Int? = null,
)

/** What importing would replace: every setting whose value differs between [before] and [after]. */
fun settingChanges(before: AppSettings, after: AppSettings): List<SettingChange> = buildList {
    fun switch(title: Int, a: Boolean, b: Boolean) {
        if (a != b) add(SettingChange(title, from = a, to = b))
    }
    switch(R.string.settings_notify_added, before.notifyAdded, after.notifyAdded)
    switch(R.string.settings_notify_failed, before.notifyFailed, after.notifyFailed)
    switch(R.string.settings_digest, before.inboxDigest, after.inboxDigest)
    switch(R.string.settings_live_updates, before.liveUpdates, after.liveUpdates)
    switch(R.string.hide_screenshots, before.hideScreenshots, after.hideScreenshots)
    switch(R.string.hide_recents, before.hideRecents, after.hideRecents)
    if (before.pdfQuality != after.pdfQuality) {
        add(SettingChange(R.string.settings_quality, fromLabel = before.pdfQuality.label, toLabel = after.pdfQuality.label))
    }
    switch(R.string.settings_auto_capture, before.autoCapture, after.autoCapture)
    if (before.scanFilter != after.scanFilter) {
        add(SettingChange(R.string.backup_scan_filter, fromLabel = before.scanFilter.label, toLabel = after.scanFilter.label))
    }
    if (before.documentsGrid != after.documentsGrid) {
        add(
            SettingChange(
                R.string.backup_documents_view,
                fromLabel = if (before.documentsGrid) R.string.backup_view_grid else R.string.backup_view_list,
                toLabel = if (after.documentsGrid) R.string.backup_view_grid else R.string.backup_view_list,
            ),
        )
    }
}
