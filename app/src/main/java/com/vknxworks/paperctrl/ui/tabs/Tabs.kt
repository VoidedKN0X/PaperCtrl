package com.vknxworks.paperctrl.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.ui.components.LocalBottomClearance
import com.vknxworks.paperctrl.ui.components.MessageView
import com.vknxworks.paperctrl.ui.components.TabTitle

// Each tab gets its real screen in its own build stage; until then it explains what's coming.

@Composable
fun InboxTab() = PlaceholderTab(R.string.tab_inbox, Icons.Outlined.Inbox, R.string.inbox_empty_title, R.string.inbox_empty_body)

@Composable
fun DocumentsTab() = PlaceholderTab(R.string.tab_documents, Icons.Outlined.Description, R.string.documents_empty_title, R.string.documents_empty_body)

@Composable
fun OrganiseTab() = PlaceholderTab(R.string.tab_organise, Icons.Outlined.Sell, R.string.organise_empty_title, R.string.organise_empty_body)

@Composable
private fun PlaceholderTab(title: Int, icon: ImageVector, heading: Int, body: Int) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(title))
        Box(Modifier.weight(1f).padding(bottom = LocalBottomClearance.current)) {
            MessageView(icon, stringResource(heading), stringResource(body) + "\n\n" + stringResource(R.string.coming_soon))
        }
    }
}
