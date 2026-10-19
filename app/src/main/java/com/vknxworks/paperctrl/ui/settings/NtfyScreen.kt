package com.vknxworks.paperctrl.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.notify.Push
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupChoice
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.GroupSwitch
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.document.copyToClipboard
import com.vknxworks.paperctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Notifications from Paperless itself (documents added by email or the consume folder) through
 * UnifiedPush and the user's own ntfy server, with the workflow setup spelled out.
 */
@Composable
fun NtfyScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val push by Push.state.collectAsStateWithLifecycle()
    var distributors by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        Push.load(context)
        distributors = Push.distributors(context)
    }

    fun enable(on: Boolean, pkg: String? = null) {
        c.appScope.launch { c.settings.update { it.copy(pushEnabled = on) } }
        if (on) {
            val target = pkg ?: push.distributor ?: distributors.firstOrNull()?.first
            if (target != null) Push.register(context, target)
        } else {
            Push.unregister(context)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.ntfy_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.ntfy_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )
            if (distributors.isEmpty()) {
                Group(footer = stringResource(R.string.ntfy_no_distributor_body)) {
                    GroupRow(stringResource(R.string.ntfy_no_distributor), icon = Icons.Outlined.NotificationsActive)
                }
                return@Column
            }
            Group {
                GroupSwitch(stringResource(R.string.ntfy_enable), stringResource(R.string.ntfy_enable_body), settings.pushEnabled, icon = Icons.Outlined.NotificationsActive) { enable(it) }
                if (distributors.size > 1) {
                    GroupChoice(stringResource(R.string.ntfy_distributor), distributors, push.distributor ?: distributors.first().first) { pkg ->
                        if (settings.pushEnabled) {
                            Push.unregister(context)
                            enable(true, pkg)
                        }
                    }
                }
            }
            if (settings.pushEnabled) {
                val endpoint = push.endpoint
                Group(stringResource(R.string.ntfy_endpoint), footer = push.failed?.let { stringResource(R.string.ntfy_failed, it) }) {
                    if (endpoint == null) {
                        GroupRow(stringResource(R.string.ntfy_waiting), stringResource(R.string.ntfy_waiting_body))
                    } else {
                        GroupRow(endpoint, null, trailing = {
                            IconButton(onClick = { copyToClipboard(context, endpoint) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
                        })
                        GroupRow(stringResource(R.string.ntfy_test), stringResource(R.string.ntfy_test_body), icon = Icons.Outlined.Send, onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val body = """{"event": "test", "title": "${context.getString(R.string.ntfy_test_message)}"}"""
                                        OkHttpClient().newCall(Request.Builder().url(endpoint).post(body.toRequestBody("application/json".toMediaType())).build()).execute().use { it.isSuccessful }
                                    }.getOrDefault(false)
                                }
                                if (!ok) Toast.makeText(context, context.getString(R.string.ntfy_test_failed), Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                }
                Group(stringResource(R.string.ntfy_setup)) {
                    Text(
                        stringResource(R.string.ntfy_steps),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    GroupRow(stringResource(R.string.ntfy_body), null, trailing = {
                        IconButton(onClick = { copyToClipboard(context, Push.WEBHOOK_BODY) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
                    })
                    SelectionContainer(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(Push.WEBHOOK_BODY, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                    Text(
                        stringResource(R.string.ntfy_internal_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
