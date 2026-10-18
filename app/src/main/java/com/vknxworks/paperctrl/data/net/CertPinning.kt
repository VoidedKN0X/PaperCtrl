package com.vknxworks.paperctrl.data.net

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** What the user sees before trusting a certificate the system doesn't. */
data class CertInfo(
    /** SHA-256 of the DER-encoded certificate, upper-case hex. */
    val sha256: String,
    val subject: String,
    val issuer: String,
    val notBefore: Date,
    val notAfter: Date,
) {
    val selfSigned: Boolean get() = subject == issuer

    /** The fingerprint in pairs ("AB:CD:…"), the way Paperless' reverse proxies and browsers show it. */
    val sha256Pretty: String get() = sha256.chunked(2).joinToString(":")

    companion object {
        fun of(cert: X509Certificate) = CertInfo(
            sha256 = sha256(cert),
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            notBefore = cert.notBefore,
            notAfter = cert.notAfter,
        )
    }
}

fun sha256(cert: X509Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }

/**
 * Trust for one server address. A certificate the system (or a user-installed CA) trusts always
 * works; on top of that, exactly one certificate the user confirmed by its SHA-256 fingerprint
 * ([pinnedSha256]) is accepted, for self-signed servers. There is no "trust everything".
 *
 * The certificate that was rejected last is kept in [rejected], so the setup screen can show it
 * to the user for a decision.
 */
class PinnedTrust(private val pinnedSha256: String?) {
    @Volatile var rejected: X509Certificate? = null
        private set

    private val system: X509TrustManager = run {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun isPinned(cert: X509Certificate?): Boolean =
        cert != null && pinnedSha256 != null && sha256(cert).equals(pinnedSha256, ignoreCase = true)

    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                val leaf = chain?.firstOrNull()
                if (isPinned(leaf)) return
                rejected = leaf
                throw e
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    /**
     * The normal hostname check, except for the pinned certificate: a self-signed certificate on
     * a LAN address rarely names that address, and its exact fingerprint already identifies it.
     */
    val hostnameVerifier: HostnameVerifier = HostnameVerifier { host: String, session: SSLSession ->
        if (HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) return@HostnameVerifier true
        val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
        if (isPinned(leaf)) return@HostnameVerifier true
        rejected = leaf
        false
    }

    val sslSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), null)
    }.socketFactory
}
