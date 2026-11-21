package com.vknxworks.paperctrl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** How often an open list or document looks at the server again. */
const val LIVE_UPDATE_INTERVAL_MS = 30_000L

/**
 * "Live updates": while the calling screen is the one on show (and [enabled]), runs [check] every
 * [LIVE_UPDATE_INTERVAL_MS]. It stops as soon as the screen is covered, left or the app goes to the
 * background, so nothing is checked while nobody is looking.
 *
 * Paperless has a live channel of its own (a WebSocket), but it only accepts a browser login session, not the
 * API token PaperCtrl holds, so the app asks the server again instead.
 */
@Composable
fun LiveUpdates(enabled: Boolean, check: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(check)
    LaunchedEffect(enabled, owner) {
        if (!enabled) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(LIVE_UPDATE_INTERVAL_MS)
                current()
            }
        }
    }
}
