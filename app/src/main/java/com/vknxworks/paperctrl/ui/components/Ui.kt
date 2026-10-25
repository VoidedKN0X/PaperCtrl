package com.vknxworks.paperctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.ui.nav.LocalNavigator
import com.vknxworks.paperctrl.ui.nav.YouRoute
import com.vknxworks.paperctrl.ui.theme.Gutter

// ---------- Top bars ----------

/**
 * Bar for pushed screens: a 44dp square back button and the title in Space Grotesk.
 * No Material colour fill: it sits straight on the background.
 */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .heightIn(min = 68.dp)
            .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = nav::back)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Top bar of the main tabs: the PAPER/CTRL wordmark, optional actions and your profile picture. */
@Composable
fun CtrlTopBar(
    modifier: Modifier = Modifier,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark()
        Spacer(Modifier.weight(1f))
        trailing()
        if (showProfile) {
            Spacer(Modifier.width(6.dp))
            ProfileButton()
        }
    }
}

/** The top bar and a big left-aligned title for the main tabs. */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        CtrlTopBar(showProfile = showProfile, trailing = trailing)
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Gutter, end = Gutter, top = 6.dp, bottom = 10.dp),
        )
    }
}

/**
 * The signed-in user's picture: a 44dp square with their initial and a small accent square while
 * connected. Tapping it opens the You page with the account and settings.
 */
@Composable
fun ProfileButton() {
    val nav = LocalNavigator.current
    val session by LocalContext.current.container.sessions.active.collectAsStateWithLifecycle()
    val name = session?.server?.displayName.orEmpty()
    val you = stringResource(R.string.you)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
                .clickable(role = Role.Button) { nav.navigate(YouRoute) }
                .semantics { contentDescription = you },
            contentAlignment = Alignment.Center,
        ) {
            if (name.isEmpty()) {
                Icon(Icons.Outlined.Person, null, Modifier.size(20.dp), tint = CtrlColors.Text)
            } else {
                Text(initials(name), style = MaterialTheme.typography.titleSmall, color = CtrlColors.Text)
            }
        }
        if (session != null) {
            // Connected: a small accent square on the bottom-right corner.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

/** One or two letters for a profile picture. */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(2)
        else -> parts[0].take(1) + parts[1].take(1)
    }.uppercase()
}

// ---------- Grouped lists (settings, profile) ----------

/** A card of related rows, with an optional mono label above it. Rows are separated by 1dp dividers. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        if (title != null) {
            SectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        CtrlCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp), content = content)
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GroupRowLayout(
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        title, subtitle, icon, iconTint, titleColor, showChevron, trailing,
    )
}

@Composable
private fun GroupRowLayout(
    action: Modifier,
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    iconTint: Color,
    titleColor: Color,
    showChevron: Boolean,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // The first row's divider lies on the card's own border, so only the lines between rows show.
            .topDivider(CtrlColors.Outline)
            .heightIn(min = 60.dp)
            .then(action)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(CtrlColors.Raised),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(20.dp), tint = iconTint) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row with a [CtrlSwitch]; the whole row toggles and reads as one switch to TalkBack. */
@Composable
fun GroupSwitch(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    GroupRowLayout(
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        title, subtitle, icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, false,
    ) { CtrlSwitch(checked, onCheckedChange = null) }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    ModalBottomSheet(modifier = Modifier.ctrlSheet(), onDismissRequest = onDismiss, containerColor = CtrlColors.Surface) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Filled.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- States ----------

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText, trackColor = CtrlColors.Track) }
}

/** Centred icon, title and explanation, with an optional action. Used for empty and error states. */
@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                CtrlSecondaryButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
