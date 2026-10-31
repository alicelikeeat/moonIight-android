package com.moonlight.feed.ui.design

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.moonlight.feed.data.VideoBudget
import com.moonlight.feed.data.FocusCrop
import com.moonlight.feed.data.MediaFocus
import java.io.File

/** false → every decorative video shows its first frame (setting off, low-RAM device or battery saver). */
val LocalLoopVideos = compositionLocalOf { true }

/**
 * Muted looping video for avatars / covers / backgrounds. The first frame (Coil video decoder) is always drawn
 * underneath, so it degrades to a still when animation is off, the [VideoBudget] is full, in previews, or while loading.
 * No audio track is decoded and no audio focus is requested, so it never interrupts the user's music.
 * Pauses with the lifecycle (app backgrounded / covered).
 */
@Composable
fun LoopVideo(path: String, modifier: Modifier = Modifier, budget: VideoBudget = VideoBudget.Profile, focus: MediaFocus? = null) {
    val allow = LocalLoopVideos.current && !LocalInspectionMode.current && File(path).exists()
    Box(modifier) {
        // first frame, cropped exactly like the live video
        if (focus != null) MoonImage(File(path), Modifier.fillMaxSize(), FocusContentScale(focus.scale), alignment = focusAlignment(focus))
        else MoonImage(File(path), Modifier.fillMaxSize(), ContentScale.Crop)
        if (allow) {
            val key = remember { Any() }
            var granted by remember { mutableStateOf(false) }
            DisposableEffect(path, budget) {
                granted = budget.acquire(key)
                onDispose { budget.release(key); granted = false }
            }
            if (granted) LoopSurface(path, Modifier.fillMaxSize(), focus)
        }
    }
}

@Composable
private fun LoopSurface(path: String, modifier: Modifier, focus: MediaFocus?) {
    val ctx = LocalContext.current
    var shown by remember(path) { mutableStateOf(false) }
    var size by remember(path) { mutableStateOf(VideoSize.UNKNOWN) }
    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { shown = true }
                override fun onVideoSizeChanged(v: VideoSize) { size = v }
            })
            prepare(); playWhenReady = true
        }
    }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); player.release() }
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "loop")
    AndroidView(
        factory = { c -> TextureView(c).also { player.setVideoTextureView(it) } },
        update = { tv -> if (focus != null) focusCrop(tv, size, focus) else centerCrop(tv, size) },
        modifier = modifier.alpha(a),
    )
}

/** TextureView stretches by default; scale it so the video covers the view (like ContentScale.Crop). */
internal fun centerCrop(tv: TextureView, v: VideoSize) {
    val vw = tv.width.toFloat(); val vh = tv.height.toFloat()
    if (v.width <= 0 || v.height <= 0 || vw <= 0f || vh <= 0f) {
        if (vw <= 0f) tv.post { if (tv.width > 0) centerCrop(tv, v) }
        return
    }
    val videoAspect = v.width * v.pixelWidthHeightRatio / v.height
    val viewAspect = vw / vh
    val sx = if (videoAspect > viewAspect) videoAspect / viewAspect else 1f
    val sy = if (videoAspect > viewAspect) 1f else viewAspect / videoAspect
    tv.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
}

/** TextureView placement from [FocusCrop] (same geometry as images). */
internal fun focusCrop(tv: TextureView, v: VideoSize, f: MediaFocus) {
    val vw = tv.width.toFloat(); val vh = tv.height.toFloat()
    if (v.width <= 0 || v.height <= 0 || vw <= 0f || vh <= 0f) {
        if (vw <= 0f) tv.post { if (tv.width > 0) focusCrop(tv, v, f) }
        return
    }
    val p = FocusCrop.place(v.width * v.pixelWidthHeightRatio, v.height.toFloat(), vw, vh, f)
    // TextureView stretches the frame to the view; rescale to the placed size, then shift
    tv.setTransform(Matrix().apply { setScale(p.width / vw, p.height / vh); postTranslate(p.left, p.top) })
}

/** "Cover" × user zoom; pair with [focusAlignment] so images crop exactly like [FocusCrop.place]. */
class FocusContentScale(private val zoom: Float) : ContentScale {
    override fun computeScaleFactor(srcSize: androidx.compose.ui.geometry.Size, dstSize: androidx.compose.ui.geometry.Size): androidx.compose.ui.layout.ScaleFactor {
        val k = FocusCrop.coverScale(srcSize.width, srcSize.height, dstSize.width, dstSize.height) * zoom.coerceIn(1f, MediaFocus.MAX_SCALE)
        return androidx.compose.ui.layout.ScaleFactor(k, k)
    }
}

fun focusAlignment(f: MediaFocus) = androidx.compose.ui.BiasAlignment(f.x.coerceIn(0f, 1f) * 2f - 1f, f.y.coerceIn(0f, 1f) * 2f - 1f)

/**
 * A picked photo or video (copied into app storage), or [fallback] when none.
 * Cropped by [focus] (reposition editor); unset → upper-third bias so heads aren't cut.
 */
@Composable
fun ProfileMedia(path: String?, video: Boolean, modifier: Modifier = Modifier, focus: MediaFocus? = null, fallback: @Composable () -> Unit) {
    val f = focus ?: MediaFocus.DEFAULT
    when {
        path == null -> Box(modifier) { fallback() }
        path.startsWith("art:") -> FocusedArt(path.removePrefix("art:").toIntOrNull() ?: 0, f, modifier)
        video -> LoopVideo(path, modifier, focus = f)
        else -> MoonImage(File(path), modifier, FocusContentScale(f.scale), alignment = focusAlignment(f))
    }
}


/** Demo/preview art (`art:N`) is treated as a 3:4 portrait source so focus/zoom behave exactly like a real photo. */
const val ART_SRC_W = 900f
const val ART_SRC_H = 1200f

@Composable
fun FocusedArt(seed: Int, focus: MediaFocus, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        ProceduralArt(
            seed,
            Modifier.layout { m, c ->
                val dw = c.maxWidth.toFloat(); val dh = c.maxHeight.toFloat()
                val p = FocusCrop.place(ART_SRC_W, ART_SRC_H, dw, dh, focus)
                val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(p.width.toInt().coerceAtLeast(1), p.height.toInt().coerceAtLeast(1)))
                layout(c.maxWidth, c.maxHeight) { pl.place(p.left.toInt(), p.top.toInt()) }
            },
        )
    }
}
