package com.vknxworks.paperctrl.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.api.MAX_API_VERSION
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.appDataStore
import com.vknxworks.paperctrl.data.net.CleartextPolicy
import com.vknxworks.paperctrl.data.net.PinnedTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.UUID

/** A saved Paperless-ngx server and the account used on it. The token never leaves the Keystore-encrypted store. */
@Serializable
data class Server(
    val id: String,
    /** Main address, used anywhere (usually the HTTPS domain). Ends in "/". */
    val url: String,
    /** Optional home-network address. Only used while the user has picked it ([useLan]). Ends in "/". */
    val lanUrl: String? = null,
    /**
     * True when the user picked the home-network address. PaperCtrl never changes this by itself;
     * servers saved by older versions don't have it and stay on the main address.
     */
    val useLan: Boolean = false,
    val username: String,
    val displayName: String = username,
    val userId: Int = 0,
    val isSuperuser: Boolean = false,
    /** API token, encrypted with the Android Keystore. */
    val tokenEnc: String,
    /** Certificates the user confirmed by fingerprint, by origin ("https://host:port" → SHA-256). */
    val pins: Map<String, String> = emptyMap(),
    val apiVersion: Int = MAX_API_VERSION,
    val serverVersion: String? = null,
) {
    val host: String get() = url.toHttpUrlOrNull()?.host ?: url

    /** The address the user picked: the home-network one when chosen (and saved), otherwise the main one. */
    val activeUrl: String get() = lanUrl?.takeIf { useLan } ?: url
    val usesLan: Boolean get() = useLan && lanUrl != null
    fun isEncrypted(address: String = url) = address.startsWith("https://", ignoreCase = true)
}

/** The server in use and a client for the address the user picked for it. */
data class ActiveSession(val server: Server, val client: PaperlessClient, val viaLan: Boolean) {
    val address: String get() = client.baseUrl
}

/**
 * Saved servers and the active one. Each server is reached on the address the user picked (main or
 * home-network); the app never probes addresses or switches between them on its own.
 */
class SessionManager(
    private val context: Context,
    private val baseHttp: OkHttpClient,
    private val scope: CoroutineScope,
) {
    val connector = ServerConnector(baseHttp)

    private val serversKey = stringPreferencesKey("servers")
    private val activeKey = stringPreferencesKey("active_server")
    private val mutex = Mutex()

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** One OkHttp client per set of trust settings; they share the base client's connection pool. */
    private val clients = mutableMapOf<String?, OkHttpClient>()

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val prefs = context.appDataStore.data.first()
        val list = prefs[serversKey]?.let {
            runCatching { ApiJson.decodeFromString(ListSerializer(Server.serializer()), it) }.getOrNull()
        }.orEmpty()
        _servers.value = list
        val server = list.firstOrNull { it.id == prefs[activeKey] } ?: list.firstOrNull()
        if (server != null) activate(server)
        _ready.value = true
    }

    /** Waits until saved servers have been loaded (background work can start before the UI). */
    suspend fun awaitReady() {
        ready.first { it }
    }

    fun server(id: String): Server? = _servers.value.firstOrNull { it.id == id }

    /**
     * A client for [serverId] on the address the user picked for it: the active session's, or a new
     * one for another saved server. Null when the server was removed or its token can't be decrypted
     * any more.
     */
    fun clientFor(serverId: String): PaperlessClient? {
        _active.value?.let { if (it.server.id == serverId) return it.client }
        val s = server(serverId) ?: return null
        val token = KeystoreCrypto.decrypt(s.tokenEnc) ?: return null
        CleartextPolicy.allowServer(s.activeUrl)
        return client(s, s.activeUrl, token)
    }

    /**
     * A call for an image request (thumbnails). Requests to the active server's address go through
     * its own client (pinned certificate) with its token; anything else gets the base client and no
     * credentials.
     */
    fun imageCall(request: okhttp3.Request): okhttp3.Call {
        val s = _active.value
        if (s != null && request.url.toString().startsWith(s.client.baseUrl)) return s.client.http.newCall(s.client.authorize(request))
        return baseHttp.newCall(request)
    }

    /** OkHttp client that trusts the system's CAs plus the one certificate pinned for [origin] (if any). */
    fun httpFor(server: Server, address: String): OkHttpClient {
        val pin = server.pins[originOf(address)]
        return synchronized(clients) {
            clients.getOrPut(pin) {
                val trust = PinnedTrust(pin)
                baseHttp.newBuilder()
                    .sslSocketFactory(trust.sslSocketFactory, trust.trustManager)
                    .hostnameVerifier(trust.hostnameVerifier)
                    .build()
            }
        }
    }

    /**
     * Saves a verified sign-in (replacing an earlier one for the same address and user) and makes it
     * the active server. [lanUrl] and [pins] come from the setup screen, already verified.
     */
    suspend fun save(connected: Connected, lanUrl: String?, pins: Map<String, String>): Server = mutex.withLock {
        val user = connected.settings.user
        val existing = _servers.value.firstOrNull { it.url == connected.baseUrl && it.username == user.username }
        val relevantOrigins = listOfNotNull(connected.baseUrl, lanUrl).map(::originOf).toSet()
        val server = Server(
            id = existing?.id ?: UUID.randomUUID().toString(),
            url = connected.baseUrl,
            lanUrl = lanUrl,
            // A new sign-in starts on the main address; signing in again keeps the address the user picked.
            useLan = existing?.useLan == true && lanUrl != null && existing.lanUrl == lanUrl,
            username = user.username,
            displayName = user.displayName,
            userId = user.id,
            isSuperuser = user.isSuperuser,
            tokenEnc = KeystoreCrypto.encrypt(connected.token),
            pins = pins.filterKeys { it in relevantOrigins },
            apiVersion = connected.apiVersion,
            serverVersion = connected.serverVersion,
        )
        val updated = _servers.value.filterNot { it.id == server.id } + server
        persist(updated, server.id)
        activate(server)
        server
    }

    suspend fun switchTo(id: String) {
        val server = _servers.value.firstOrNull { it.id == id } ?: return
        context.appDataStore.edit { it[activeKey] = id }
        activate(server)
    }

    /**
     * Makes [useLan] the address used for server [id] (home-network address when true, main address
     * when false) and keeps it until the user picks again. Ignored for a server without a home address.
     */
    suspend fun useAddress(id: String, useLan: Boolean) {
        mutex.withLock {
            val current = _servers.value.firstOrNull { it.id == id } ?: return
            if (useLan && current.lanUrl == null) return
            if (current.useLan == useLan) return
            val updated = current.copy(useLan = useLan)
            val activeId = _active.value?.server?.id
            persist(_servers.value.map { if (it.id == id) updated else it }, activeId)
            if (activeId == id) activate(updated)
        }
    }

    /** Forgets a server and its token on this phone. The token itself stays valid on the server. */
    suspend fun remove(id: String) {
        mutex.withLock {
            val remaining = _servers.value.filterNot { it.id == id }
            val stillActive = _active.value?.server?.takeIf { it.id != id }
            val next = stillActive ?: remaining.firstOrNull()
            persist(remaining, next?.id)
            when {
                next == null -> _active.value = null
                stillActive == null -> activate(next)
                else -> Unit
            }
        }
    }

    /** Refreshes the saved name, rights and version after the server reports them again. */
    suspend fun refreshDetails(id: String, displayName: String, isSuperuser: Boolean, serverVersion: String?) {
        mutex.withLock {
            val current = _servers.value.firstOrNull { it.id == id } ?: return
            val updated = current.copy(
                displayName = displayName,
                isSuperuser = isSuperuser,
                serverVersion = serverVersion ?: current.serverVersion,
            )
            if (updated == current) return
            persist(_servers.value.map { if (it.id == id) updated else it }, _active.value?.server?.id)
            val session = _active.value
            if (session != null && session.server.id == id) _active.value = session.copy(server = updated)
        }
    }

    private suspend fun activate(server: Server) {
        val token = KeystoreCrypto.decrypt(server.tokenEnc)
        if (token == null) {
            // The Keystore key is gone (e.g. restored to a new phone): the user has to sign in again.
            _active.value = null
            return
        }
        // Reconnects always use the saved address and scheme; never switch an https server to http.
        val address = server.activeUrl
        CleartextPolicy.allowServer(address)
        _active.value = ActiveSession(server, client(server, address, token), viaLan = server.usesLan)
    }

    private fun client(server: Server, address: String, token: String) =
        PaperlessClient(address, token, server.apiVersion, httpFor(server, address))

    private suspend fun persist(list: List<Server>, activeId: String?) {
        _servers.value = list
        context.appDataStore.edit { prefs ->
            prefs[serversKey] = ApiJson.encodeToString(ListSerializer(Server.serializer()), list)
            if (activeId != null) prefs[activeKey] = activeId else prefs.remove(activeKey)
        }
    }
}
