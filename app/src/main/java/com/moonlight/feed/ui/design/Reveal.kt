package com.moonlight.feed.ui.design

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** 模糊渐显 speed. */
enum class RevealSpeed(val ms: Int, val label: String) { FAST(380, "快"), STANDARD(520, "标准"), SLOW(720, "慢") }

data class RevealConfig(val enabled: Boolean = true, val ms: Int = RevealSpeed.STANDARD.ms) {
    companion object { val Off = RevealConfig(false, 0) }
}

/** Provided once at the root from settings + the system "remove animations" switch. */
val LocalReveal = compositionLocalOf { RevealConfig() }

/** Ease-out (fast start, long soft landing). */
val RevealEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** True when the user turned animations off (Developer options / Accessibility → 移除动画). */
fun systemAnimationsOff(ctx: Context): Boolean = runCatching {
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

fun revealConfig(ctx: Context, on: Boolean, speed: String): RevealConfig {
    if (!on || systemAnimationsOff(ctx)) return RevealConfig.Off
    val s = runCatching { RevealSpeed.valueOf(speed) }.getOrDefault(RevealSpeed.STANDARD)
    return RevealConfig(true, s.ms)
}

/** Keys that already revealed once (library thumbnails reveal on first load only). */
object RevealMemory {
    private val seen = java.util.Collections.synchronizedSet(HashSet<String>())
    fun first(key: String) = seen.add(key)
    fun has(key: String) = key in seen
}

/** Blur → sharp, 1.04 → 1, dim → full. p in 0..1. Real blur on Android 12+ (RenderEffect). */
fun Modifier.revealLayer(p: Float, maxBlur: Dp = 32.dp): Modifier =
    if (p >= 0.999f) this else graphicsLayer {
        val k = p.coerceIn(0f, 1f)
        val s = 1.04f - 0.04f * k
        scaleX = s; scaleY = s
        alpha = 0.4f + 0.6f * k
        if (Build.VERSION.SDK_INT >= 31) {
            val r = maxBlur.toPx() * (1f - k)
            if (r > 0.5f) { renderEffect = BlurEffect(r, r, TileMode.Clamp); clip = true }
        }
    }

/** Progress of a reveal keyed by [key]; plays when [play] turns true. */
@Composable
fun rememberReveal(key: Any, play: Boolean, cfg: RevealConfig = LocalReveal.current, initial: Float? = null): Float {
    val a = remember(key) { Animatable(initial ?: if (cfg.enabled) 0f else 1f) }
    LaunchedEffect(key, play, cfg) {
        if (!cfg.enabled) a.snapTo(1f)
        else if (play && a.value < 1f) a.animateTo(1f, tween(cfg.ms, easing = RevealEasing))
    }
    return a.value
}

/**
 * Wraps media so it resolves from blur to sharp when it becomes active. The content (e.g. a playing video) renders
 * underneath the whole time, so there is no added start delay. Below Android 12 a downscaled copy of [fallback]
 * (cover / first frame, decoded at ~24 px so it is naturally soft) is cross-faded away instead.
 */
@Composable
fun RevealMedia(key: Any, play: Boolean, fallback: Any?, modifier: Modifier = Modifier, progress: Float? = null, content: @Composable BoxScope.() -> Unit) {
    val p = progress ?: rememberReveal(key, play)
    Box(modifier) {
        Box(Modifier.fillMaxSize().revealLayer(p), content = content)
        if (Build.VERSION.SDK_INT < 31 && p < 0.999f && fallback != null) {
            val ctx = LocalContext.current
            AsyncImage(
                model = remember(fallback) { ImageRequest.Builder(ctx).data(fallback).size(24, 24).build() },
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - p },
            )
        }
    }
}
