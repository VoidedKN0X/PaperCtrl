package com.vknxworks.paperctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.settings.ThemeChoice

/**
 * "Ctrl / Signal": the colours shared by every Ctrl app. Dark only.
 * Change [Accent] to recolour the whole app.
 */
object CtrlColors {
    /** The one accent colour. */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons on top of the accent. */
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    /** Cards, sheets, dialogs. */
    val Surface = Color(0xFF141417)
    /** Raised elements on a surface. */
    val Raised = Color(0xFF1C1C21)
    /** Card borders and dividers inside cards. */
    val Outline = Color(0xFF232328)
    /** Borders on controls: buttons, fields, the profile picture, the bottom bar. */
    val ControlOutline = Color(0xFF2A2A2F)
    /** Dividers between plain list rows. */
    val Divider = Color(0xFF1E1E23)

    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled and stopped states. */
    val Disabled = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)

    /** Progress bar track. */
    val Track = Color(0xFF26262C)
    /** Switch track when off. */
    val SwitchOff = Color(0xFF2A2A30)
    /** Empty PIN cell border. */
    val PinEmpty = Color(0xFF3A3A41)
}

/** Kept for older call sites: the accent. */
val PaperAccent = CtrlColors.Accent

private val Scheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    // Selected chips, segmented buttons and the tablet rail indicator are accent-filled.
    secondaryContainer = CtrlColors.Accent,
    onSecondaryContainer = CtrlColors.OnAccent,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceDim = CtrlColors.Background,
    surfaceBright = CtrlColors.Raised,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Raised,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.OnAccent,
    outline = CtrlColors.ControlOutline,
    outlineVariant = CtrlColors.Outline,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF2A1517),
    onErrorContainer = CtrlColors.Error,
    scrim = Color.Black,
)

/** Space Grotesk: all UI text and headings (SIL OFL 1.1, bundled). */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** JetBrains Mono: numbers, data readouts, section labels and the wordmark (SIL OFL 1.1, bundled). */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 56, FontWeight.Bold, -0.04),
    displayMedium = style(48, 50, FontWeight.Bold, -0.04),
    displaySmall = style(40, 42, FontWeight.Bold, -0.035),
    headlineLarge = style(40, 42, FontWeight.Bold, -0.035),
    headlineMedium = style(28, 32, FontWeight.Bold, -0.025),
    headlineSmall = style(24, 28, FontWeight.Bold, -0.02),
    titleLarge = style(18, 24, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)

/** Text styles outside the Material type scale. */
object CtrlType {
    /** Section labels: use with an UPPERCASE string and the muted colour. */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** "PAPER/CTRL" in the top bar. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Small data readouts: IDs, dates, sizes, addresses. */
    val Mono = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Trailing status text on list rows. */
    val Status = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
    /** The giant number on a hero card. */
    val HeroNumber = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** PIN pad digits. */
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
}

/** Corner radii shared by the Ctrl apps. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Sheet = RoundedCornerShape(26.dp)
    val Button = RoundedCornerShape(18.dp)
    val Control = RoundedCornerShape(14.dp)
    val Field = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * The app theme. Ctrl / Signal is dark only: the saved theme choice and "wallpaper colours" are
 * ignored, so the parameters are kept only so stored settings keep loading.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PaperCtrlTheme(choice: ThemeChoice = ThemeChoice.DARK, dynamic: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

/**
 * System bar colour for edge-to-edge: the background colour, fully transparent, so the status and
 * navigation bars show the #08080A app background behind light icons.
 */
const val CTRL_BAR_SCRIM: Int = 0x0008080A
