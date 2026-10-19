package com.vknxworks.paperctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.settings.AppSettings
import com.vknxworks.paperctrl.data.settings.ThemeChoice
import com.vknxworks.paperctrl.ui.components.Group
import com.vknxworks.paperctrl.ui.components.GroupChoice
import com.vknxworks.paperctrl.ui.components.GroupSwitch
import com.vknxworks.paperctrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (c.telemetry.available) {
                Group(stringResource(R.string.settings_usage_stats), footer = stringResource(R.string.usage_stats_description)) {
                    GroupSwitch(
                        stringResource(R.string.settings_usage_stats_switch),
                        stringResource(R.string.settings_usage_stats_body),
                        s.usageStats,
                    ) { v -> update { it.copy(usageStats = v, usageStatsAsked = true) } }
                }
            }

            Group(stringResource(R.string.settings_appearance)) {
                GroupChoice(
                    stringResource(R.string.settings_theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(
                    stringResource(R.string.settings_dynamic),
                    stringResource(R.string.settings_dynamic_body),
                    s.dynamicColor,
                ) { v -> update { it.copy(dynamicColor = v) } }
            }
        }
    }
}
