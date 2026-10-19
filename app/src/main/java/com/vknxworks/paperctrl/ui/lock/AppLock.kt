package com.vknxworks.paperctrl.ui.lock

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.ui.components.PillButton
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional lock with the phone's fingerprint, face or screen lock: on start, and again after the
 * app has been in the background for a minute.
 */
object AppLock {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    private const val RELOCK_AFTER_MS = 60_000L

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundSince = 0L

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Watches the app going to the background, to lock it again after a while. */
    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundSince = SystemClock.elapsedRealtime()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > RELOCK_AFTER_MS) _locked.value = true
            }
        })
    }

    fun prompt(activity: FragmentActivity) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _locked.value = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.lock_title))
                .setSubtitle(activity.getString(R.string.lock_subtitle))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}

@Composable
fun LockScreen(activity: FragmentActivity) {
    LaunchedEffect(Unit) { AppLock.prompt(activity) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.lock_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            PillButton(stringResource(R.string.lock_unlock), onClick = { AppLock.prompt(activity) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
