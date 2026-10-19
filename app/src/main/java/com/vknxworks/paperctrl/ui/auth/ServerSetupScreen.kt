package com.vknxworks.paperctrl.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.ErrorKind
import com.vknxworks.paperctrl.data.api.PaperlessException
import com.vknxworks.paperctrl.data.auth.Connected
import com.vknxworks.paperctrl.data.auth.Credentials
import com.vknxworks.paperctrl.data.auth.HttpFallbackNeeded
import com.vknxworks.paperctrl.data.auth.NeedsTrust
import com.vknxworks.paperctrl.data.auth.originOf
import com.vknxworks.paperctrl.ui.components.ConfirmDialog
import com.vknxworks.paperctrl.ui.components.PillButton
import com.vknxworks.paperctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat

private enum class Method { PASSWORD, TOKEN }

/**
 * Adds a Paperless-ngx server: address (plus an optional home-network address), then a username
 * and password (with a 2FA code when the account has one) or a pasted API token.
 */
@Composable
fun ServerSetupScreen(onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    // Passwords and tokens can be shown in clear text here. The whole app is kept out of the recent-apps
    // preview (MainActivity), so this screen needs nothing extra.
    val scope = rememberCoroutineScope()

    var address by rememberSaveable { mutableStateOf("") }
    var lanAddress by rememberSaveable { mutableStateOf("") }
    var showLan by rememberSaveable { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf(Method.PASSWORD) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var showCode by rememberSaveable { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pins = remember { mutableStateMapOf<String, String>() }
    var pendingTrust by remember { mutableStateOf<NeedsTrust?>(null) }
    var offerHttp by remember { mutableStateOf<String?>(null) }
    var lanProblem by remember { mutableStateOf<Pair<Connected, String>?>(null) }

    val credentialsReady = when (method) {
        Method.PASSWORD -> username.isNotBlank() && password.isNotEmpty() && (!showCode || code.isNotBlank())
        Method.TOKEN -> token.isNotBlank()
    }
    val canSubmit = address.isNotBlank() && credentialsReady && !busy

    fun finish(connected: Connected, lan: String?) {
        scope.launch {
            c.sessions.save(connected, lan, pins.toMap())
            onDone()
        }
    }

    fun attempt(target: String) {
        busy = true
        error = null
        scope.launch {
            try {
                val credentials = when (method) {
                    Method.PASSWORD -> Credentials.Password(username, password, code.takeIf { showCode && it.isNotBlank() })
                    Method.TOKEN -> Credentials.Token(token)
                }
                val connected = c.sessions.connector.connect(target, credentials, pins.toMap())
                val lanTyped = lanAddress.trim().takeIf { showLan && it.isNotEmpty() }
                val lan = if (lanTyped == null) {
                    null
                } else {
                    try {
                        c.sessions.connector.verifyAlternate(lanTyped, connected.token, pins.toMap(), connected)
                    } catch (e: NeedsTrust) {
                        throw e
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The home address didn't work (often: not at home right now). Ask before dropping it.
                        lanProblem = connected to e.friendlyMessage(context)
                        return@launch
                    }
                }
                c.sessions.save(connected, lan, pins.toMap())
                onDone()
            } catch (e: NeedsTrust) {
                pendingTrust = e
            } catch (e: HttpFallbackNeeded) {
                offerHttp = e.httpUrl
            } catch (e: PaperlessException) {
                if (e.kind == ErrorKind.MFA_REQUIRED || e.kind == ErrorKind.MFA_INVALID) showCode = true
                error = e.friendlyMessage(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.friendlyMessage(context)
            } finally {
                busy = false
            }
        }
    }

    fun submit() {
        if (canSubmit) attempt(address)
    }

    pendingTrust?.let { trust ->
        TrustDialog(
            trust,
            onDismiss = {
                pendingTrust = null
                error = context.getString(R.string.error_tls)
            },
            onTrust = {
                pendingTrust = null
                pins[originOf(trust.address)] = trust.cert.sha256
                attempt(address)
            },
        )
    }

    offerHttp?.let { httpUrl ->
        ConfirmDialog(
            stringResource(R.string.setup_http_title),
            stringResource(R.string.setup_http_body),
            stringResource(R.string.setup_http_confirm),
            onDismiss = {
                offerHttp = null
                error = context.getString(R.string.error_https_failed)
            },
        ) {
            offerHttp = null
            address = httpUrl
            attempt(httpUrl)
        }
    }

    lanProblem?.let { (connected, message) ->
        ConfirmDialog(
            stringResource(R.string.setup_lan_failed_title),
            stringResource(R.string.setup_lan_failed_body, message),
            stringResource(R.string.setup_lan_failed_save),
            destructive = false,
            onDismiss = {
                lanProblem = null
                error = message
            },
        ) {
            lanProblem = null
            finish(connected, null)
        }
    }

    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.height(48.dp)) {
                if (onCancel != null) IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            }
            Text(
                stringResource(if (onCancel == null) R.string.app_name else R.string.setup_add_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Field(
                address, { address = it },
                label = stringResource(R.string.setup_address),
                placeholder = stringResource(R.string.setup_address_hint),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                enabled = !busy,
            )
            if (address.trim().startsWith("http://", ignoreCase = true)) Notice(Icons.Outlined.LockOpen, stringResource(R.string.setup_http_notice), warn = true)

            if (showLan) {
                Field(
                    lanAddress, { lanAddress = it },
                    label = stringResource(R.string.setup_lan_address),
                    placeholder = stringResource(R.string.setup_lan_address_hint),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    enabled = !busy,
                )
                if (lanAddress.trim().startsWith("http://", ignoreCase = true)) {
                    Notice(Icons.Outlined.LockOpen, stringResource(R.string.setup_lan_http_notice), warn = true)
                }
                Notice(Icons.Outlined.Home, stringResource(R.string.setup_lan_notice))
            } else {
                TextButton(onClick = { showLan = true }, enabled = !busy) { Text(stringResource(R.string.setup_lan_add)) }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Method.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = method == m,
                        onClick = { method = m; error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, Method.entries.size),
                        enabled = !busy,
                    ) {
                        Text(stringResource(if (m == Method.PASSWORD) R.string.setup_method_password else R.string.setup_method_token))
                    }
                }
            }

            when (method) {
                Method.PASSWORD -> {
                    Field(
                        username, { username = it },
                        label = stringResource(R.string.setup_username),
                        keyboard = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        enabled = !busy,
                    )
                    Field(
                        password, { password = it },
                        label = stringResource(R.string.setup_password),
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (showCode) ImeAction.Next else ImeAction.Done),
                        actions = KeyboardActions(onDone = { submit() }),
                        enabled = !busy,
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    stringResource(if (showPassword) R.string.setup_hide_password else R.string.setup_show_password),
                                )
                            }
                        },
                    )
                    if (showCode) {
                        Field(
                            code, { code = it.filter(Char::isDigit).take(8) },
                            label = stringResource(R.string.setup_mfa_code),
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            actions = KeyboardActions(onDone = { submit() }),
                            enabled = !busy,
                        )
                    } else {
                        TextButton(onClick = { showCode = true }, enabled = !busy) { Text(stringResource(R.string.setup_mfa_add)) }
                    }
                }
                Method.TOKEN -> {
                    Field(
                        token, { token = it.trim() },
                        label = stringResource(R.string.setup_token),
                        visual = PasswordVisualTransformation(),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        actions = KeyboardActions(onDone = { submit() }),
                        enabled = !busy,
                    )
                    Text(
                        stringResource(R.string.setup_token_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (busy) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            } else {
                PillButton(stringResource(R.string.setup_connect), onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(R.string.setup_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shows a certificate the system doesn't trust, so the user can compare its fingerprint with the server's. */
@Composable
private fun TrustDialog(trust: NeedsTrust, onDismiss: () -> Unit, onTrust: () -> Unit) {
    val cert = trust.cert
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (trust.changed) R.string.trust_changed_title else R.string.trust_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (trust.changed) R.string.trust_changed_body else R.string.trust_body, trust.address))
                CertLine(stringResource(R.string.trust_issued_to), cert.subject)
                CertLine(
                    stringResource(R.string.trust_issued_by),
                    if (cert.selfSigned) stringResource(R.string.trust_self_signed) else cert.issuer,
                )
                CertLine(stringResource(R.string.trust_valid), "${df.format(cert.notBefore)} – ${df.format(cert.notAfter)}")
                CertLine(stringResource(R.string.trust_fingerprint), cert.sha256Pretty, mono = true)
                Text(
                    stringResource(R.string.trust_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) {
                Text(stringResource(R.string.trust_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CertLine(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, warn: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboard,
        keyboardActions = actions,
        visualTransformation = visual,
        trailingIcon = trailing,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
