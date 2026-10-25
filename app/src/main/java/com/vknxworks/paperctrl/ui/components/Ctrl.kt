package com.vknxworks.paperctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlShapes
import com.vknxworks.paperctrl.ui.theme.CtrlType

// Ctrl / Signal building blocks shared by every screen.

/** "PAPER/CTRL", with the slash in the accent colour. */
@Composable
fun CtrlWordmark(modifier: Modifier = Modifier) {
    val left = stringResource(R.string.wordmark_name)
    val right = stringResource(R.string.wordmark_ctrl)
    Text(
        buildAnnotatedString {
            append(left)
            withStyle(SpanStyle(color = CtrlColors.Accent)) { append("/") }
            append(right)
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier.semantics { contentDescription = left + right },
    )
}

/** Mono, upper-case section label in the muted colour. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** An 8dp square status dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CtrlShapes.Dot).background(color))
}

/** 44dp square icon button with the Ctrl border: back, close and other bar actions. */
@Composable
fun CtrlSquareButton(icon: ImageVector, description: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) CtrlColors.Text else CtrlColors.Disabled)
    }
}

/** Secondary card: surface fill, hairline border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    shape: Shape = CtrlShapes.Card,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.Outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The one most important thing on a main screen: accent fill, a mono label row on top and a
 * giant number below. [below] sits under the number.
 */
@Composable
fun CtrlHeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    unit: String? = null,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtrlShapes.Hero)
            .background(CtrlColors.Accent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(label, Modifier.weight(1f), color = CtrlColors.OnAccent)
            if (trailingLabel != null) SectionLabel(trailingLabel, color = CtrlColors.OnAccent)
        }
        Text(
            buildAnnotatedString {
                append(value)
                if (unit != null) withStyle(SpanStyle(fontSize = CtrlType.HeroNumber.fontSize * 0.44f)) { append(unit) }
            },
            style = CtrlType.HeroNumber,
            color = CtrlColors.OnAccent,
            maxLines = 1,
        )
        below?.invoke(this)
    }
}

/**
 * Rounded-rect switch, 52x32dp. On: accent track, dark knob. Off: grey track and knob.
 * Announced as a switch to TalkBack.
 */
@Composable
fun CtrlSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.Disabled, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-knob-x")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Box(
        modifier
            .width(52.dp)
            .height(32.dp)
            .clip(shape)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                .background(if (enabled) knob else knob.copy(alpha = 0.5f)),
        )
    }
}

/** Primary action: accent fill, dark text, 18dp corners, 56dp tall. */
@Composable
fun CtrlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = CtrlColors.Accent,
    content: Color = CtrlColors.OnAccent,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CtrlShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Disabled,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: surface fill with the control border. */
@Composable
fun CtrlSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CtrlShapes.Button,
        border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.ControlOutline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CtrlColors.Surface, contentColor = CtrlColors.Text),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Text field colours: surface fill, control border, accent border and cursor when focused. */
@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.Accent,
    unfocusedBorderColor = CtrlColors.ControlOutline,
    disabledBorderColor = CtrlColors.Outline,
    cursorColor = CtrlColors.Accent,
    focusedLabelColor = CtrlColors.Accent,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedPlaceholderColor = CtrlColors.Muted,
    unfocusedPlaceholderColor = CtrlColors.Muted,
    focusedLeadingIconColor = CtrlColors.Muted,
    unfocusedLeadingIconColor = CtrlColors.Muted,
    focusedTrailingIconColor = CtrlColors.Muted,
    unfocusedTrailingIconColor = CtrlColors.Muted,
)

/** The Ctrl text field: an outlined field with surface fill and 16dp corners. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = CtrlShapes.Field,
    colors: TextFieldColors = ctrlFieldColors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = colors,
    )
}

/** 6dp progress bar with a 3dp radius. [accent] for the primary metric, otherwise off-white. */
@Composable
fun CtrlProgressBar(progress: Float, modifier: Modifier = Modifier, accent: Boolean = true) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
    Box(modifier.fillMaxWidth().height(6.dp).clip(shape).background(CtrlColors.Track)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(if (accent) CtrlColors.Accent else CtrlColors.Text),
        )
    }
}

/** Draws the 1dp list divider along the top edge, as between plain list rows. */
fun Modifier.topDivider(color: Color = CtrlColors.Divider, width: Dp = 1.dp): Modifier = drawBehind {
    val h = width.toPx()
    drawRect(color, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, h))
}

data class CtrlTab(val label: String, val icon: ImageVector)

/**
 * Floating bottom navigation: 64dp tall, 22dp corners, translucent surface with the control border.
 * The selected tab is an accent block with icon and label; the others are icon only.
 */
@Composable
fun CtrlBottomBar(items: List<CtrlTab>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Card)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .height(48.dp)
                    .then(if (selected) Modifier else Modifier.width(48.dp))
                    .clip(shape)
                    .background(if (selected) CtrlColors.Accent else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .semantics { contentDescription = item.label }
                    .padding(horizontal = if (selected) 18.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, Modifier.size(if (selected) 18.dp else 20.dp), tint = if (selected) CtrlColors.OnAccent else CtrlColors.Muted)
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.titleSmall, color = CtrlColors.OnAccent, maxLines = 1)
                }
            }
        }
    }
}

/** Ctrl bottom sheets: the 1dp card border along the 26dp top corners. */
fun Modifier.ctrlSheet(): Modifier = border(
    1.dp,
    CtrlColors.Outline,
    androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
)
