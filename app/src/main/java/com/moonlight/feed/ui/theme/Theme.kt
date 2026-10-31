package com.moonlight.feed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Moonlight palette — deep night + soft moonlight accent. */
object MoonPalette {
    val Night = Color(0xFF07080B)
    val Night2 = Color(0xFF0E1015)
    val Night3 = Color(0xFF171A21)
    val Moon = Color(0xFFE8E3C9)        // moonlight accent
    val MoonDeep = Color(0xFF8A825F)    // accent on light backgrounds
    val Paper = Color(0xFFF4F2EC)
    val Ink = Color(0xFF15161A)
    val Gray1 = Color(0xFF8D8F96)
    val Gray2 = Color(0xFF5B5E66)
    val Red = Color(0xFFFF5A52)
    val Heart = Color(0xFFFF4D6D)
}

/** 8pt grid spacing + radii. */
object Space {
    val xxs = 4.dp; val xs = 8.dp; val s = 12.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp; val xxl = 48.dp
}
object Radius {
    val s = 10.dp; val m = 16.dp; val l = 22.dp; val xl = 28.dp
}

@Immutable
data class MoonColors(
    val dark: Boolean,
    val accent: Color,
    val onAccent: Color,
    val glassTint: Color,       // translucent fill over blurred backdrop
    val glassFallback: Color,   // denser fill when no blur (< Android 12)
    val glassStroke: Color,     // highlight hairline
    val glassStrokeLow: Color,
    val hairline: Color,
    val text: Color,
    val textDim: Color,
    val danger: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val glow: Color,
)

private val DarkMoon = MoonColors(
    dark = true,
    accent = MoonPalette.Moon,
    onAccent = MoonPalette.Ink,
    glassTint = Color(0x521D2029),
    glassFallback = Color(0xE6191C23),
    glassStroke = Color(0x59FFFFFF),
    glassStrokeLow = Color(0x0AFFFFFF),
    hairline = Color(0x1AFFFFFF),
    text = Color(0xFFF5F4EF),
    textDim = Color(0x9EEBEBF5),     // vibrant secondary label (reads correctly on any glass)
    danger = MoonPalette.Red,
    bgTop = Color(0xFF0D0F15),
    bgBottom = MoonPalette.Night,
    glow = Color(0x26E8E3C9),
)

private val LightMoon = MoonColors(
    dark = false,
    accent = MoonPalette.MoonDeep,
    onAccent = Color.White,
    glassTint = Color(0x99FFFFFF),
    glassFallback = Color(0xEDFFFFFF),
    glassStroke = Color(0xE6FFFFFF),
    glassStrokeLow = Color(0x0F000000),
    hairline = Color(0x12000000),
    text = MoonPalette.Ink,
    textDim = Color(0x993C3C43),
    danger = Color(0xFFE5433A),
    bgTop = Color(0xFFF7F5EF),
    bgBottom = Color(0xFFECE9E0),
    glow = Color(0x33C9BF8E),
)

/** Glass on top of media (feed / player) is always dark. */
val MediaGlass = DarkMoon

val LocalMoonColors = staticCompositionLocalOf { DarkMoon }

enum class ThemeMode { SYSTEM, DARK, LIGHT }

private val Sans = FontFamily.SansSerif

val MoonTypography = Typography(
    // Large-title hierarchy with tight (negative) tracking at display sizes, airy tracking only on small labels.
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)

@Composable
fun MoonTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val mc = if (dark) DarkMoon else LightMoon
    val scheme = if (dark) darkColorScheme(
        primary = MoonPalette.Moon,
        onPrimary = MoonPalette.Ink,
        secondary = MoonPalette.Moon,
        background = MoonPalette.Night,
        surface = MoonPalette.Night3,
        surfaceVariant = Color(0xFF22252D),
        onBackground = mc.text,
        onSurface = mc.text,
        onSurfaceVariant = mc.textDim,
        outline = Color(0xFF2E3139),
        error = mc.danger,
    ) else lightColorScheme(
        primary = MoonPalette.Ink,
        onPrimary = Color.White,
        secondary = MoonPalette.MoonDeep,
        background = MoonPalette.Paper,
        surface = Color.White,
        surfaceVariant = Color(0xFFE6E3DA),
        onBackground = mc.text,
        onSurface = mc.text,
        onSurfaceVariant = mc.textDim,
        outline = Color(0xFFD5D2C8),
        error = mc.danger,
    )
    CompositionLocalProvider(LocalMoonColors provides mc) {
        MaterialTheme(colorScheme = scheme, typography = MoonTypography, content = content)
    }
}
