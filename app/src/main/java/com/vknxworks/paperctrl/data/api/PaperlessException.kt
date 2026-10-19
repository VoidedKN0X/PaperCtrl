package com.vknxworks.paperctrl.data.api

import androidx.annotation.StringRes
import com.vknxworks.paperctrl.R
import java.io.IOException

/** What went wrong talking to a Paperless-ngx server, in terms the UI can explain. */
enum class ErrorKind(@StringRes val message: Int) {
    UNREACHABLE(R.string.error_unreachable),
    TLS(R.string.error_tls),
    CERT_CHANGED(R.string.error_cert_changed),
    BAD_CREDENTIALS(R.string.error_bad_credentials),
    BAD_TOKEN(R.string.error_bad_token),
    MFA_REQUIRED(R.string.error_mfa_required),
    MFA_INVALID(R.string.error_mfa_invalid),
    NOT_PAPERLESS(R.string.error_not_paperless),
    TOO_OLD(R.string.error_too_old),
    FORBIDDEN(R.string.error_forbidden),
    NOT_FOUND(R.string.error_not_found),
    THROTTLED(R.string.error_throttled),
    TOO_LARGE(R.string.error_too_large),
    SERVER(R.string.error_server),
    UNEXPECTED(R.string.error_unexpected),
    CLEARTEXT_BLOCKED(R.string.error_cleartext_blocked),
}

/**
 * An answer from (or about) the server that the app understands. [detail] is the server's own
 * message when it gave one (Paperless explains validation errors in plain English); it never holds
 * tokens, passwords or document contents.
 */
class PaperlessException(
    val kind: ErrorKind,
    val detail: String? = null,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : IOException(detail ?: kind.name, cause)
