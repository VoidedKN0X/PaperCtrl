package com.vknxworks.paperctrl.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * UnifiedPush, so Paperless can notify the phone through the user's own ntfy server, without
 * Google services. This speaks the UnifiedPush Android protocol directly (the same broadcasts the
 * official connector library sends); messages are plain JSON from a Paperless workflow webhook.
 */
object Push {
    private const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    private const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    private const val FEATURE_BYTES = "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"

    private const val PREFS = "unifiedpush"
    private const val K_TOKEN = "token"
    private const val K_ENDPOINT = "endpoint"
    private const val K_DISTRIBUTOR = "distributor"
    private const val K_FAILED = "failed"

    data class State(val distributor: String? = null, val endpoint: String? = null, val failed: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        _state.value = State(p.getString(K_DISTRIBUTOR, null), p.getString(K_ENDPOINT, null), p.getString(K_FAILED, null))
    }

    fun token(context: Context): String? = prefs(context).getString(K_TOKEN, null)

    /** Installed UnifiedPush distributors (ntfy, …), as package name and app name. */
    fun distributors(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return pm.queryBroadcastReceivers(Intent(ACTION_REGISTER), PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg -> pkg to (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString() }.getOrDefault(pkg)) }
    }

    fun register(context: Context, distributor: String) {
        val p = prefs(context)
        val token = p.getString(K_TOKEN, null) ?: UUID.randomUUID().toString()
        p.edit().putString(K_TOKEN, token).putString(K_DISTRIBUTOR, distributor).remove(K_FAILED).apply()
        load(context)
        // A PendingIntent lets the distributor see which app is registering.
        val pi = PendingIntent.getBroadcast(context, 0, Intent("org.unifiedpush.dummy_app").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        context.sendBroadcast(
            Intent(ACTION_REGISTER)
                .setPackage(distributor)
                .putExtra("token", token)
                .putExtra("features", arrayOf(FEATURE_BYTES))
                .putExtra("application", context.packageName)
                .putExtra("pi", pi)
                .putExtra("message", context.getString(R.string.app_name)),
        )
    }

    fun unregister(context: Context) {
        val p = prefs(context)
        val token = p.getString(K_TOKEN, null)
        val distributor = p.getString(K_DISTRIBUTOR, null)
        if (token != null && distributor != null) {
            context.sendBroadcast(Intent(ACTION_UNREGISTER).setPackage(distributor).putExtra("token", token))
        }
        p.edit().clear().apply()
        load(context)
    }

    internal fun saveEndpoint(context: Context, endpoint: String?, failed: String? = null) {
        prefs(context).edit().apply {
            if (endpoint != null) putString(K_ENDPOINT, endpoint) else remove(K_ENDPOINT)
            if (failed != null) putString(K_FAILED, failed) else remove(K_FAILED)
        }.apply()
        load(context)
    }

    internal fun ack(context: Context, token: String, id: String?) {
        val distributor = prefs(context).getString(K_DISTRIBUTOR, null) ?: return
        if (id == null) return
        context.sendBroadcast(Intent(ACTION_MESSAGE_ACK).setPackage(distributor).putExtra("token", token).putExtra("id", id))
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

/** Receives endpoint updates and messages from the UnifiedPush distributor. */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = Push.token(context) ?: return
        // Every message carries the random token PaperCtrl registered with; anything else isn't for us.
        if (intent.getStringExtra("token") != token) return
        when (intent.action) {
            Push.ACTION_NEW_ENDPOINT -> Push.saveEndpoint(context, intent.getStringExtra("endpoint"))
            Push.ACTION_REGISTRATION_FAILED -> Push.saveEndpoint(context, null, intent.getStringExtra("reason") ?: "failed")
            Push.ACTION_UNREGISTERED -> Push.saveEndpoint(context, null, "unregistered")
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
