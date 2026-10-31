package com.moonlight.feed.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.moonlight.feed.data.DeleteAnim
import com.moonlight.feed.data.V5

/**
 * 碎纸机 — delete / restore animation over everything. The overlay composes its OWN copy of the deleted thing
 * ([Run.content]: the image, a live looping video, the post card), records it into a GraphicsLayer every frame and
 * draws that layer as vertical strips (optionally cross-cut) feeding down through a slot. Because the copy is live,
 * videos keep playing inside the strips. Restore plays the same timeline backwards.
 */
object Shredder {
    class Run(val id: String, val rect: Rect, val reverse: Boolean, val anim: DeleteAnim, val crossCut: Boolean, val content: @Composable () -> Unit) {
        val key = System.nanoTime()
    }
    val runs = mutableStateListOf<Run>()
    /** Last on-screen bounds of every shreddable thing (root px), by id. Not state: read only when a delete starts. */
    val anchors = HashMap<String, Rect>()
    /** Tests: freeze every run at this progress (0 = intact … 1 = gone). */
    var debugProgress by mutableStateOf<Float?>(null)
    const val MS = 950
    const val MAX_RUNS = 6

    /** Plays the delete (or [reverse] = restore) of [id] at its last known bounds (or [at]). No-op when 关 / unknown. */
    fun play(id: String, reverse: Boolean = false, at: Rect? = null, content: @Composable () -> Unit) {
        val anim = V5.prefs.deleteAnim
        if (anim == DeleteAnim.NONE) return
        val r = at ?: anchors[id] ?: return
        if (r.width < 4f || r.height < 4f || runs.size >= MAX_RUNS) return
        runs += Run(id, r, reverse, anim, crossCut = r.width * r.height > 160_000f, content = content)
    }
}

/** Remember where [id] is drawn so a delete / restore can shred it in place. */
@Suppress("FunctionName") fun ShredAnchorMod(id: String): Modifier = Modifier.shredAnchor(id)
fun Modifier.shredAnchor(id: String): Modifier = onGloballyPositioned { Shredder.anchors[id] = it.boundsInRoot() }

@Composable
internal fun ShredLayer() {
    for (run in Shredder.runs) key(run.key) { ShredRunView(run) }
}

@Composable
private fun ShredRunView(run: Shredder.Run) {
    val inspect = androidx.compose.ui.platform.LocalInspectionMode.current
    val p = remember { Animatable(if (run.reverse) 1f else 0f) }
    LaunchedEffect(run.key) {
        if (!inspect) {
            p.animateTo(if (run.reverse) 0f else 1f, tween(Shredder.MS, easing = LinearEasing))
            Shredder.runs.remove(run)
        }
    }
    val t = Shredder.debugProgress ?: p.value
    val d = LocalDensity.current
    val r = run.rect
    val boxMod = Modifier.offset { IntOffset(r.left.toInt(), r.top.toInt()) }.size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
    if (run.anim == DeleteAnim.FADE) {
        Box(boxMod.graphicsLayer { alpha = 1f - t; val s = 1f - 0.12f * t; scaleX = s; scaleY = s; translationY = 24f * t }) { run.content() }
        return
    }
    val layer = rememberGraphicsLayer()
    // the live copy: recorded, never drawn directly (the strips below draw it)
    Box(boxMod.drawWithContent { layer.record { this@drawWithContent.drawContent() } }) { run.content() }
    val mouth = with(d) { androidx.compose.ui.unit.Dp(10f).toPx() }
    Canvas(Modifier.fillMaxSize()) { drawShred(layer, r, t, run.crossCut, mouth) }
}

/**
 * Pure-ish strip maths (only draw calls; no allocation): the sheet slides down into a slot under its bottom edge;
 * what has passed the slot is cut into vertical strips that spread, sway and fall away, fading out.
 */
internal fun DrawScope.drawShred(layer: GraphicsLayer, r: Rect, t: Float, crossCut: Boolean, mouthH: Float) {
    if (t >= 0.999f) return
    val w = r.width; val h = r.height
    val mouthY = r.bottom
    val feed = ShredMath.feed(t) * (h * 1.05f)          // how far the sheet has moved into the slot
    val cut = mouthY - r.top - feed                      // content rows above this (layer coords) are still intact
    // intact part above the slot
    if (cut > 0f) clipRect(r.left - w, r.top - h, r.right + w, mouthY) { translate(r.left, r.top + feed) { clipRect(0f, 0f, w, cut) { drawLayer(layer) } } }
    // strips below the slot
    val n = ShredMath.strips(w)
    val sw = w / n
    val pieces = if (crossCut) 7 else 12
    val ph = h / pieces
    for (k in 0 until n) for (c in 0 until pieces) {
        val y0 = c * ph; val y1 = y0 + ph
        if (y1 <= cut) continue
        val top = maxOf(y0, cut)
        val depth = r.top + feed + (top + y1) / 2f - mouthY    // how far this piece is past the slot (px)
        if (depth <= 0f) continue
        val o = ShredMath.piece(k, c, n, depth, h, crossCut)
        if (o.alpha <= 0.01f) continue
        translate(r.left + o.dx, r.top + feed + o.dy) {
            clipRect(k * sw + 0.6f, top, (k + 1) * sw - 0.6f, y1) {
                // per-strip fade: the layer is drawn by reference (RenderNode), so its own alpha can't vary per strip
                if (o.alpha >= 0.995f) drawLayer(layer) else {
                    val nc = drawContext.canvas.nativeCanvas
                    val save = nc.saveLayerAlpha(k * sw, top, (k + 1) * sw, y1, (o.alpha * 255f).toInt())
                    drawLayer(layer)
                    nc.restoreToCount(save)
                }
            }
        }
    }
    // the slot: a dark glossy mouth a little wider than the sheet, with a highlight lip
    val ma = ShredMath.mouthAlpha(t)
    if (ma > 0.01f) {
        val ext = w * 0.06f
        drawRoundRect(Color(0xFF15161B), Offset(r.left - ext, mouthY - mouthH / 2), Size(w + ext * 2, mouthH), CornerRadius(mouthH / 2), alpha = 0.92f * ma)
        drawRoundRect(Color.White, Offset(r.left - ext + mouthH, mouthY - mouthH / 2 + 1f), Size(w + ext * 2 - mouthH * 2, 1.6f), CornerRadius(1f), alpha = 0.45f * ma)
        drawRoundRect(Color(0xFFFFD27A), Offset(r.left - ext + mouthH * 0.6f, mouthY + mouthH / 2 - 2f), Size(w + ext * 2 - mouthH * 1.2f, 1.2f), CornerRadius(1f), alpha = 0.35f * ma)
    }
}

/** The shredder's numbers (pure; unit-tested). */
object ShredMath {
    class Piece { var dx = 0f; var dy = 0f; var alpha = 1f }
    private val scratch = Piece()
    /** Sheet travel 0…1 from the timeline (eases in like paper being grabbed, done by 85%). */
    fun feed(t: Float): Float { val x = (t / 0.85f).coerceIn(0f, 1f); return x * x * (3f - 2f * x) * 0.35f + x * 0.65f }
    fun strips(widthPx: Float): Int = (widthPx / 26f).toInt().coerceIn(8, 22)
    fun mouthAlpha(t: Float): Float = (t / 0.06f).coerceIn(0f, 1f) * (1f - ((t - 0.86f) / 0.12f).coerceIn(0f, 1f))
    /** Offset of strip [k], piece [c] once [depth] px past the slot. Reuses one scratch object (single-threaded draw). */
    fun piece(k: Int, c: Int, n: Int, depth: Float, h: Float, cross: Boolean): Piece {
        val q = depth / h
        val hr = kotlin.math.sin(k * 12.9898f + (if (cross) c * 78.233f else 0f)) * 43758.547f
        val rnd = hr - kotlin.math.floor(hr)
        val spread = (k - (n - 1) / 2f) / n
        scratch.dx = spread * depth * 0.22f + kotlin.math.sin(k * 1.7f + q * 6f) * depth * 0.06f * (0.5f + rnd)
        scratch.dy = depth * q * (0.55f + 0.5f * rnd) * (if (cross) 1.25f else 1f)
        scratch.alpha = 1f - ((q - 0.45f) / 0.75f).coerceIn(0f, 1f)
        return scratch
    }
}
