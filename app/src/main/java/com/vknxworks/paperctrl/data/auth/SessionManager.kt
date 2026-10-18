package com.vknxworks.paperctrl.data.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.paperctrl.data.api.ApiJson
import com.vknxworks.paperctrl.data.api.MAX_API_VERSION
import com.vknxworks.paperctrl.data.api.PaperlessClient
import com.vknxworks.paperctrl.data.appDataStore
import com.vknxworks.paperctrl.data.net.CleartextPolicy
import com.vknxworks.paperctrl.data.net.PinnedTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Optional home-network address, preferred whenever it answers. Ends in "/". */
    val lanUrl: String? = null,
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
    fun isEncrypted(address: String = url) = address.startsWith("https://", ignoreCase = true)
}

/** The server in use and a client for whichever of its addresses is reachable right now. */
data class ActiveSession(val server: Server, val client: PaperlessClient, val viaLan: Boolean) {
    val address: String get() = client.baseUrl
}

/** Saved servers, the active one, and switching between its home-network and main address. */
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

    // Declared before init: the network callback registered there can fire straight away.
    @Volatile private var rerouteJob: Job? = null

    init {
        scope.launch { load() }
        watchNetwork()
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
        // Reconnects always use the saved addresses and schemes; never switch an https server to http.
        CleartextPolicy.allowServer(server.url)
        server.lanUrl?.let(CleartextPolicy::allowServer)

        // Start on the main address straight away, then move to the home address if it answers.
        _active.value = ActiveSession(server, client(server, server.url, token), viaLan = false)
        if (server.lanUrl != null) scope.launch { rerouteNow(server, token) }
    }

    private fun client(server: Server, address: String, token: String) =
        PaperlessClient(address, token, server.apiVersion, httpFor(server, address))

    private suspend fun rerouteNow(server: Server, token: String) {
        val lan = server.lanUrl ?: return
        val reachable = connector.isReachable(lan, server.pins[originOf(lan)])
        val current = _active.value ?: return
        if (current.server.id != server.id) return
        if (reachable != current.viaLan) {
            val address = if (reachable) lan else server.url
            _active.value = ActiveSession(current.server, client(current.server, address, token), viaLan = reachable)
        }
    }

    /** On every network change (Wi-Fi on/off, VPN), re-check whether the home address is reachable. */
    private fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = reroute()
            override fun onLost(network: Network) = reroute()
        })
    }

    private fun reroute() {
        rerouteJob?.cancel()
        rerouteJob = scope.launch {
            delay(1500) // let the new network settle (DHCP, VPN) before testing
            val session = _active.value ?: return@launch
            val token = KeystoreCrypto.decrypt(session.server.tokenEnc) ?: return@launch
            rerouteNow(session.server, token)
        }
    }

    private suspend fun persist(list: List<Server>, activeId: String?) {
        _servers.value = list
        context.appDataStore.edit { prefs ->
            prefs[serversKey] = ApiJson.encodeToString(ListSerializer(Server.serializer()), list)
            if (activeId != null) prefs[activeKey] = activeId else prefs.remove(activeKey)
        }
    }
}
