package com.vknxworks.paperctrl.notify

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.UUID

/**
 * Push from Paperless through the user's own ntfy server and the ntfy app, and only the ntfy app.
 * This speaks the UnifiedPush Android protocol (https://unifiedpush.org/developers/spec/android/)
 * directly instead of using a library; messages are plain JSON from a Paperless workflow webhook.
 */
object Push {
    /** The ntfy app, the only push app PaperCtrl registers with. */
    const val NTFY = "io.heckel.ntfy"

    /** Where to get ntfy. */
    const val NTFY_DOWNLOAD = "https://f-droid.org/packages/io.heckel.ntfy/"

    private const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    private const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val ACTION_TEMP_UNAVAILABLE = "org.unifiedpush.android.connector.TEMP_UNAVAILABLE"
    private const val FEATURE_BYTES = "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"

    private const val PREFS = "unifiedpush"
    private const val K_TOKEN = "token"
    private const val K_ENDPOINT = "endpoint"
    private const val K_DISTRIBUTOR = "distributor"
    private const val K_FAILED = "failed"
    private const val K_TEMP_UNAVAILABLE = "temp_unavailable"

    /** [tempUnavailable]: ntfy can't reach the ntfy server for now; the address stays valid. */
    data class State(
        val distributor: String? = null,
        val endpoint: String? = null,
        val failed: String? = null,
        val tempUnavailable: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Reads the saved registration. A registration with another push app (possible before PaperCtrl
     * became ntfy-only; it only exists while push is on) is cancelled there, and PaperCtrl registers
     * with ntfy instead if it's installed. Otherwise push stays off until ntfy is installed.
     */
    fun load(context: Context) {
        val p = prefs(context)
        val distributor = p.getString(K_DISTRIBUTOR, null)
        if (distributor != null && distributor != NTFY) {
            p.getString(K_TOKEN, null)?.let { sendUnregister(context, distributor, it) }
            p.edit().clear().apply()
            if (ntfyInstalled(context)) {
                register(context)
                return
            }
        }
        publish(context)
    }

    private fun publish(context: Context) {
        val p = prefs(context)
        _state.value = State(
            p.getString(K_DISTRIBUTOR, null),
            p.getString(K_ENDPOINT, null),
            p.getString(K_FAILED, null),
            p.getBoolean(K_TEMP_UNAVAILABLE, false),
        )
    }

    fun token(context: Context): String? = prefs(context).getString(K_TOKEN, null)

    /** Whether the ntfy app is installed and can hand out push addresses. */
    fun ntfyInstalled(context: Context): Boolean =
        context.packageManager.queryBroadcastReceivers(Intent(ACTION_REGISTER).setPackage(NTFY), PackageManager.ResolveInfoFlags.of(0)).isNotEmpty()

    /** Asks ntfy for a push address. The answer arrives in [PushReceiver]. */
    fun register(context: Context) {
        val p = prefs(context)
        val token = p.getString(K_TOKEN, null) ?: UUID.randomUUID().toString()
        p.edit().putString(K_TOKEN, token).putString(K_DISTRIBUTOR, NTFY).remove(K_FAILED).remove(K_TEMP_UNAVAILABLE).apply()
        publish(context)
        sendToDistributor(
            context,
            NTFY,
            Intent(ACTION_REGISTER)
                .putExtra("token", token)
                .putExtra("features", arrayOf(FEATURE_BYTES))
                .putExtra("application", context.packageName)
                .putExtra("message", context.getString(R.string.app_name)),
        )
    }

    fun unregister(context: Context) {
        val p = prefs(context)
        val token = p.getString(K_TOKEN, null)
        val distributor = p.getString(K_DISTRIBUTOR, null)
        if (token != null && distributor != null) sendUnregister(context, distributor, token)
        p.edit().clear().apply()
        publish(context)
    }

    private fun sendUnregister(context: Context, distributor: String, token: String) {
        runCatching { sendToDistributor(context, distributor, Intent(ACTION_UNREGISTER).putExtra("token", token)) }
    }

    /**
     * Sends [intent] to [distributor] with PaperCtrl's identity: shared with the broadcast from
     * Android 14, through a pending intent before that.
     */
    private fun sendToDistributor(context: Context, distributor: String, intent: Intent) {
        intent.setPackage(distributor)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            context.sendBroadcast(intent, null, options.toBundle())
        } else {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent("org.unifiedpush.dummy_app").setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            context.sendBroadcast(intent.putExtra("pi", pi))
        }
    }

    internal fun onEndpoint(context: Context, endpoint: String) {
        prefs(context).edit().putString(K_ENDPOINT, endpoint).remove(K_FAILED).remove(K_TEMP_UNAVAILABLE).apply()
        publish(context)
    }

    internal fun onFailed(context: Context, reason: String) {
        val p = prefs(context)
        // Ignored once ntfy has given an address for this token.
        if (p.getString(K_ENDPOINT, null) != null) return
        // A new token for the next try.
        p.edit().remove(K_TOKEN).putString(K_FAILED, reason).apply()
        publish(context)
    }

    internal fun onUnregistered(context: Context) {
        prefs(context).edit().remove(K_TOKEN).remove(K_ENDPOINT).remove(K_TEMP_UNAVAILABLE).putString(K_FAILED, "unregistered").apply()
        publish(context)
    }

    internal fun onTempUnavailable(context: Context) {
        prefs(context).edit().putBoolean(K_TEMP_UNAVAILABLE, true).apply()
        publish(context)
    }

    internal fun ack(context: Context, token: String, id: String?) {
        if (id == null) return
        runCatching {
            context.sendBroadcast(Intent(ACTION_MESSAGE_ACK).setPackage(NTFY).putExtra("token", token).putExtra("id", id))
        }
    }

    /** The body to paste into a Paperless workflow's webhook action (sent as JSON). */
    const val WEBHOOK_BODY = """{"event": "added", "id": "{{doc_id}}", "title": "{{doc_title}}", "correspondent": "{{correspondent}}"}"""

    /** Turns a pushed message into a notification. */
    internal fun show(context: Context, bytes: ByteArray) {
        val text = bytes.toString(Charsets.UTF_8).trim()
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json == null) {
            Notifications.event(context, context.getString(R.string.push_generic_title), text.take(300), null)
            return
        }
        val id = json.optString("id").toIntOrNull()
        val title = json.optString("title").takeIf { it.isNotBlank() && !it.contains("{{") }
        val correspondent = json.optString("correspondent").takeIf { it.isNotBlank() && it != "None" && !it.contains("{{") }
        val heading = when (json.optString("event")) {
            "added", "" -> context.getString(R.string.push_added_title)
            "updated" -> context.getString(R.string.push_updated_title)
            else -> json.optString("event").replaceFirstChar { it.uppercase() }
        }
        Notifications.event(context, heading, listOfNotNull(title, correspondent).joinToString(" · ").ifEmpty { null }, id)
    }
}

/**
 * Receives endpoint updates and messages from ntfy. Exported (ntfy is another app), so every broadcast
 * must carry PaperCtrl's token or it's ignored: the token is what keeps other apps out. From Android 14 a
 * broadcast from a sender that identifies itself as an app other than ntfy is dropped too, but a sender can
 * stay anonymous, so that check only adds to the token.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            // Only rejects senders that identify as another app; anonymous ones get through to the token check.
            val from = sentFromPackage
            if (from != null && from != Push.NTFY) return
        }
        val token = Push.token(context) ?: return
        // Every message carries the random token PaperCtrl registered with; anything else isn't for us.
        if (intent.getStringExtra("token") != token) return
        when (intent.action) {
            Push.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra("endpoint") ?: return
                Push.onEndpoint(context, endpoint)
                Push.ack(context, token, intent.getStringExtra("id"))
            }
            Push.ACTION_REGISTRATION_FAILED -> Push.onFailed(context, intent.getStringExtra("reason") ?: "failed")
            Push.ACTION_UNREGISTERED -> Push.onUnregistered(context)
            Push.ACTION_TEMP_UNAVAILABLE -> Push.onTempUnavailable(context)
            Push.ACTION_MESSAGE -> {
                val bytes = intent.getByteArrayExtra("bytesMessage") ?: intent.getStringExtra("message")?.toByteArray() ?: return
                Push.ack(context, token, intent.getStringExtra("id"))
                // The app may have been started just for this message, before settings were read.
                val enabled = runBlocking { context.container.settings.stored.first().pushEnabled }
                if (enabled) Push.show(context, bytes)
            }
        }
    }
}
