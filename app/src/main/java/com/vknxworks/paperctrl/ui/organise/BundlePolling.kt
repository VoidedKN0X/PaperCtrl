package com.vknxworks.paperctrl.ui.organise

/**
 * How the share-link bundles list looks again while Paperless is still building a bundle: after 4 s, then
 * 8 s, 16 s and so on up to once a minute, and not at all any more after [BUNDLE_POLL_LIMIT_MS] (a bundle stuck
 * in "pending", for example while the Paperless worker is down, no longer means a request every few seconds).
 */
object BundlePolling {
    const val FIRST_MS = 4_000L
    const val MAX_MS = 60_000L
    const val BUNDLE_POLL_LIMIT_MS = 10 * 60_000L

    /** The wait before check number [round] (0 for the first). */
    fun delayFor(round: Int): Long {
        if (round <= 0) return FIRST_MS
        // 4 s doubled [round] times, without overflowing for large rounds.
        if (round >= 5) return MAX_MS
        return (FIRST_MS shl round).coerceAtMost(MAX_MS)
    }

    /** Whether to keep looking after [elapsedMs] of looking. */
    fun keepGoing(elapsedMs: Long): Boolean = elapsedMs < BUNDLE_POLL_LIMIT_MS
}
