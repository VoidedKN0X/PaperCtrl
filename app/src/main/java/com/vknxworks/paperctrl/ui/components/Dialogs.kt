package com.vknxworks.paperctrl.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R

/** Yes/no question before something with consequences. [destructive] colours the confirm button red. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismiss: String? = null,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.ctrlDialog(),
        shape = CtrlShapes.Sheet,
        containerColor = CtrlColors.Surface,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss ?: stringResource(R.string.cancel)) } },
    )
}

/** Ctrl dialog edge: the 1dp card border around a 26dp-radius dialog. */
fun Modifier.ctrlDialog(): Modifier = border(1.dp, CtrlColors.Outline, CtrlShapes.Sheet)

/** The one main action on a screen: an accent [CtrlButton]. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = CtrlColors.Accent,
    content: Color = CtrlColors.OnAccent,
) {
    CtrlButton(text, onClick, modifier, icon = icon, enabled = enabled, container = container, content = content)
}
