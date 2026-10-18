package com.vknxworks.paperctrl.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vknxworks.paperctrl.BuildConfig
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupRow
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.SettingsRoute
import com.vknxworks.paperctrl.ui.theme.Gutter

private const val REPO_URL = "https://github.com/VoidedKN0X/PaperCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"

/** The You page, opened from your profile picture: your account, settings, and about the app. */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.you))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AccountCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.you_not_connected), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.you_not_connected_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Filled.Settings, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }

            Group {
                GroupRow(
                    stringResource(R.string.support_title),
                    stringResource(R.string.support_body),
                    icon = Icons.Filled.Favorite,
                    iconTint = MaterialTheme.colorScheme.error,
                    showChevron = true,
                    onClick = { context.openUrl(SPONSOR_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_code),
                    stringResource(R.string.source_code_body),
                    icon = Icons.Filled.Code,
                    showChevron = true,
                    onClick = { context.openUrl(REPO_URL) },
                )
                GroupRow(stringResource(R.string.report_bug), icon = Icons.Filled.BugReport, showChevron = true, onClick = { context.openUrl(ISSUES_URL) })
            }

            Text(
                stringResource(R.string.app_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.open_link_failed), Toast.LENGTH_SHORT).show()
    }
}
