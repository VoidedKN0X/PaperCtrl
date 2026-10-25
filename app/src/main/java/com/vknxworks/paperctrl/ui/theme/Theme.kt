package com.vknxworks.paperctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/** One full set of Ctrl / Signal colours: [CtrlColors.Dark] or [CtrlColors.Light]. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val outline: Color,
    val controlOutline: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    val disabled: Color,
    val accentText: Color,
    val error: Color,
    val onError: Color,
    val track: Color,
    val progressFill: Color,
    val switchOff: Color,
    val switchOffKnob: Color,
    val pinEmpty: Color,
)

/** The palette in use: dark or light, following the device's theme. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/**
 * "Ctrl / Signal": the colours shared by every Ctrl app, in a dark and a light palette that follow
 * the device theme. The accent lives here only: change [Accent] (fills) and the two accent-text
 * colours to recolour the whole app. Reading a colour here gives the current palette's value.
 */
object CtrlColors {
    /** The accent fill: hero cards, primary buttons, the open tab, switch tracks, PIN dots. Same in both palettes. */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons on top of the accent fill. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        disabled = Color(0xFF8C8C94),
        accentText = Accent,
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        track = Color(0xFF26262C),
        progressFill = Color(0xFFF4F4F2),
        switchOff = Color(0xFF2A2A30),
        switchOffKnob = Color(0xFF8C8C94),
        pinEmpty = Color(0xFF3A3A41),
    )

    /** "Signal Light". */
    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        disabled = Color(0xFF74747C),
        // #B48CFF is too pale for text on white.
        accentText = Color(0xFF6A3BDB),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        track = Color(0xFFE2E2DC),
        progressFill = Color(0xFF0B0B0D),
        switchOff = Color(0xFFDCDCD5),
        switchOffKnob = Color(0xFF74747C),
        pinEmpty = Color(0xFFC9C9C1),
    )

    private val current: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val Background: Color @Composable @ReadOnlyComposable get() = current.background
    /** Cards, sheets, dialogs. */
    val Surface: Color @Composable @ReadOnlyComposable get() = current.surface
    /** Raised elements on a surface. */
    val Raised: Color @Composable @ReadOnlyComposable get() = current.raised
    /** Card borders and dividers inside cards. */
    val Outline: Color @Composable @ReadOnlyComposable get() = current.outline
    /** Borders on controls: buttons, fields, the profile picture, the bottom bar. */
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = current.controlOutline
    /** Dividers between plain list rows. */
    val Divider: Color @Composable @ReadOnlyComposable get() = current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = current.muted
    /** Disabled and stopped states. */
    val Disabled: Color @Composable @ReadOnlyComposable get() = current.disabled
    /** The accent used as text, icons, outlines and spinners on the background. */
    val AccentText: Color @Composable @ReadOnlyComposable get() = current.accentText
    val Error: Color @Composable @ReadOnlyComposable get() = current.error
    /** Progress bar track. */
    val Track: Color @Composable @ReadOnlyComposable get() = current.track
    /** Progress bar fill for secondary metrics. */
    val ProgressFill: Color @Composable @ReadOnlyComposable get() = current.progressFill
    /** Switch track when off. */
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = current.switchOff
    /** Switch knob when off. */
    val SwitchOffKnob: Color @Composable @ReadOnlyComposable get() = current.switchOffKnob
    /** Empty PIN cell border. */
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = current.pinEmpty
}

/** Kept for older call sites: the accent fill. */
val PaperAccent = CtrlColors.Accent

private fun schemeFor(p: CtrlPalette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    // Material's own text buttons, tabs, checkboxes and icon tints read "primary": the readable accent.
    primary = p.accentText,
    onPrimary = if (p.isDark) CtrlColors.OnAccent else Color.White,
    // Fills use the accent with dark content in both palettes.
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = p.text,
    onSecondary = p.background,
    // Selected chips, segmented buttons and the tablet rail indicator are accent-filled.
    secondaryContainer = CtrlColors.Accent,
    onSecondaryContainer = CtrlColors.OnAccent,
    tertiary = p.accentText,
    onTertiary = if (p.isDark) CtrlColors.OnAccent else Color.White,
    tertiaryContainer = p.raised,
    onTertiaryContainer = p.text,
    background = p.background,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.raised,
    onSurfaceVariant = p.muted,
    surfaceTint = Color.Transparent,
    surfaceDim = p.background,
    surfaceBright = p.surface,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.raised,
    surfaceContainerHighest = p.raised,
    inverseSurface = p.text,
    inverseOnSurface = p.background,
    outline = p.controlOutline,
    outlineVariant = p.outline,
    error = p.error,
    onError = p.onError,
    errorContainer = if (p.isDark) Color(0xFF2A1517) else Color(0xFFFBE3E3),
    onErrorContainer = p.error,
    scrim = Color.Black,
)

private val DarkScheme = schemeFor(CtrlColors.Dark)
private val LightScheme = schemeFor(CtrlColors.Light)

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
 * The app theme. Follows the device's dark / light setting; there's no in-app theme choice and no
 * wallpaper colours. The parameters are kept only so stored settings keep loading.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PaperCtrlTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, dynamic: Boolean = false, content: @Composable () -> Unit) {
    CtrlTheme(dark = isSystemInDarkTheme(), content = content)
}

/** Ctrl theme with an explicit palette. */
@Composable
fun CtrlTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Camera views stay dark in both themes: the controls sit on top of the live picture. */
@Composable
fun CtrlForceDark(content: @Composable () -> Unit) = CtrlTheme(dark = true, content = content)

/**
 * System bar colour for edge-to-edge: fully transparent, so the status and navigation bars show
 * the app background behind them.
 */
const val CTRL_BAR_SCRIM: Int = 0x00000000
