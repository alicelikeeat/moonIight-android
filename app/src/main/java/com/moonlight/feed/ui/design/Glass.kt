package com.moonlight.feed.ui.design

import androidx.compose.ui.draw.drawBehind
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MoonColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlin.random.Random

/** Root backdrop recorded by Haze; glass surfaces blur whatever is behind them. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** True when real backdrop blur (RenderEffect) is available: Android 12+. */
val canRealBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private var noiseCache: ImageBitmap? = null

/** Tiny tiled film-grain bitmap; gives the translucent fallback a frosted feel. */
fun noiseBitmap(): ImageBitmap {
    noiseCache?.let { return it }
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val r = Random(7)
    val px = IntArray(size * size) {
        val v = r.nextInt(256)
        val a = 10 + r.nextInt(18)
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(px, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap().also { noiseCache = it }
}

fun Modifier.noise(alpha: Float = 1f): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(noiseBitmap(), TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = alpha)
    }
}

/**
 * Frosted glass surface.
 *  - Android 12+: real backdrop blur via Haze (RenderEffect) + translucent tint + grain.
 *  - Below 12 (or no backdrop): denser translucent fill + grain.
 * Always: hairline highlight border (bright top → faint bottom) and a soft drop shadow.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    colors: MoonColors = LocalMoonColors.current,
    blur: Boolean = true,
    blurRadius: Dp = 28.dp,
    elevation: Dp = 0.dp,
    tint: Color? = null,
    fallbackFill: Color? = null,
): Modifier {
    val haze = if (blur) LocalHazeState.current else null
    val inspect = LocalInspectionMode.current
    // 0.5dp hairline: bright at the top edge, gone by the middle, a whisper at the bottom (light from above).
    val stroke = remember(colors) {
        Brush.verticalGradient(0f to colors.glassStroke, 0.45f to colors.glassStrokeLow, 1f to colors.glassStrokeLow.copy(alpha = colors.glassStrokeLow.alpha * 1.6f))
    }
    val specular = remember(colors) {
        Brush.verticalGradient(0f to Color.White.copy(alpha = if (colors.dark) 0.075f else 0.32f), 0.38f to Color.Transparent)
    }
    var m = this
    if (elevation > 0.dp) {
        // Layered shadow: wide soft ambient + tight contact shadow.
        m = m.shadow(elevation * 1.5f, shape, clip = false, ambientColor = Color.Black.copy(0.10f), spotColor = Color.Black.copy(if (colors.dark) 0.30f else 0.14f))
            .shadow(elevation * 0.3f, shape, clip = false, ambientColor = Color.Black.copy(0.12f), spotColor = Color.Black.copy(if (colors.dark) 0.30f else 0.10f))
    }
    m = m.clip(shape)
    val fill = tint ?: colors.glassTint
    m = if (haze != null && (canRealBlur || inspect)) {
        m.hazeChild(
            state = haze,
            style = HazeStyle(
                backgroundColor = if (colors.dark) Color(0xFF07080B) else Color(0xFFF4F2EC),
                tint = HazeTint(fill),
                blurRadius = blurRadius,
                noiseFactor = 0.08f,
                fallbackTint = HazeTint(colors.glassFallback),
            ),
        )
    } else {
        // Translucent fallback: denser fill + grain so it still reads as frosted.
        m.background(fallbackFill ?: colors.glassFallback).noise(0.9f)
    }
    // Specular sheen on the upper third, under the content.
    return m.drawBehind { drawRect(specular) }.border(0.5.dp, stroke, shape)
}

/** Static glass (no backdrop sampling) for long scrolling lists — cheap, same look. */
@Composable
fun Modifier.glassStatic(shape: Shape, colors: MoonColors = LocalMoonColors.current, elevation: Dp = 0.dp): Modifier {
    val fill = if (colors.dark) Color(0x8C1A1D25) else Color(0xB8FFFFFF)
    return this.glass(shape, colors, blur = false, elevation = elevation, fallbackFill = fill)
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape,
    colors: MoonColors = LocalMoonColors.current,
    blur: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(shape, colors, blur), content = content)
}
