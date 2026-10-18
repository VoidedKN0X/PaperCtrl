package com.vknxworks.paperctrl.ui.components

import android.content.Context
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.ErrorKind
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.api.isTlsProblem
import java.io.IOException

/**
 * A sentence the user can act on. Paperless' own explanation is added for errors where it helps
 * (validation messages, an unexpected answer); connection details stay out of the way.
 */
fun Throwable.friendlyMessage(context: Context): String = when (this) {
    is PaperlessException -> {
        val base = when (kind) {
            ErrorKind.TOO_OLD -> context.getString(R.string.error_too_old_version, detail ?: "?")
            else -> context.getString(kind.message)
        }
        val showDetail = detail != null && kind in setOf(
            ErrorKind.BAD_CREDENTIALS, ErrorKind.FORBIDDEN, ErrorKind.SERVER, ErrorKind.UNEXPECTED, ErrorKind.TLS,
        )
        if (showDetail) "$base\n\n$detail" else base
    }
    is IOException -> context.getString(if (isTlsProblem(this)) R.string.error_tls else R.string.error_unreachable)
    else -> context.getString(R.string.error_unexpected)
}
