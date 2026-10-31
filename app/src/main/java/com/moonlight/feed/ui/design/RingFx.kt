package com.moonlight.feed.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.moonlight.feed.data.RingMotion

/**
 * 长按动画 drawing kit. Everything is built ONCE in unit space (petal = base (0,0) → tip (0,-1), glows = unit discs)
 * and only transformed per frame, so animating allocates nothing but the frame itself.
 */
class RingFxKit(dark: Boolean) {
    // 荷花 ---------------------------------------------------------------------------------------------
    val petal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.46f, -0.16f, -0.40f, -0.70f, 0f, -1f)
        cubicTo(0.40f, -0.70f, 0.46f, -0.16f, 0f, 0f)
        close()
    }
    /** Glossy centre vein: a thin sliver up the middle of the petal. */
    val vein = Path().apply {
        moveTo(0f, -0.08f)
        cubicTo(-0.07f, -0.30f, -0.05f, -0.72f, 0f, -0.90f)
        cubicTo(0.05f, -0.72f, 0.07f, -0.30f, 0f, -0.08f)
        close()
    }
    val innerPetal = Brush.verticalGradient(0f to Color(0xFFF08FB2).copy(alpha = 0.92f), 0.55f to Color(0xFFF9C9DA).copy(alpha = 0.88f), 1f to Color(0xFFFFF7FA).copy(alpha = 0.96f), startY = 0f, endY = -1f)
    val outerPetal = Brush.verticalGradient(0f to Color(0xFFEE9CBB).copy(alpha = 0.70f), 0.6f to Color(0xFFF9D2E0).copy(alpha = 0.62f), 1f to Color(0xFFFFFFFF).copy(alpha = 0.72f), startY = 0f, endY = -1f)
    val veinBrush = Brush.verticalGradient(0f to Color.White.copy(alpha = 0f), 0.5f to Color.White.copy(alpha = 0.75f), 1f to Color.White.copy(alpha = 0.1f), startY = 0f, endY = -1f)
    val petalEdge = Color.White.copy(alpha = 0.85f)
    val petalStroke = Stroke(width = 0.028f)
    val lotusGlow = Brush.radialGradient(0f to Color(0xFFFFE4EE).copy(alpha = 0.85f), 0.45f to Color(0xFFF6B8CF).copy(alpha = 0.35f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)

    // 月光 ---------------------------------------------------------------------------------------------
    val moonLight = Color(0xFFEAF0FF)
    val moonHalo = Brush.radialGradient(0f to Color(0xFFF4F1E4).copy(alpha = 0.55f), 0.18f to Color(0xFFDCE4FF).copy(alpha = 0.22f), 0.5f to Color(0xFF9FB2E8).copy(alpha = 0.07f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    val moonDisc = Brush.radialGradient(0f to Color(0xFFFFFDF4), 0.72f to Color(0xFFF2EEDD), 0.93f to Color(0xFFE2E2D8), 1f to Color(0xFFD9DCE6).copy(alpha = 0f), center = Offset(-0.25f, -0.25f), radius = 1.25f)
    val crater = Color(0xFFCFCFC2).copy(alpha = 0.55f)
    val itemHalo = Brush.radialGradient(0f to Color(0xFFF2F5FF).copy(alpha = 0.70f), 0.35f to Color(0xFFBFD0FF).copy(alpha = 0.30f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    val itemCore = Brush.radialGradient(0f to Color.White.copy(alpha = 0.9f), 0.6f to Color(0xFFDDE6FF).copy(alpha = 0.35f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    /** Reused beam outline (reset per beam; never reallocated). */
    val beam = Path()
    /** Beam brush in unit space along the beam: 0 = at the moon, 1 = at the button. */
    val beamBrush = Brush.verticalGradient(0f to Color(0xFFEFF3FF).copy(alpha = 0.0f), 0.35f to Color(0xFFEFF3FF).copy(alpha = 0.10f), 1f to Color(0xFFEFF3FF).copy(alpha = 0.26f), startY = 0f, endY = 1f)

    // 星轨 ---------------------------------------------------------------------------------------------
    val trailCool = if (dark) Color(0xFF9FB8FF) else Color(0xFF8FA6E8)
    val skyStar = if (dark) Color(0xFFF4F1FF) else Color(0xFFB98A2E)
    val burst = if (dark) Color(0xFFFFF2CF) else Color(0xFFF0B44E)
    val shimmer = Color.White
    val trailWarm = if (dark) Color(0xFFFFF0D2) else Color(0xFFE7B45A)
    val trailCore = if (dark) Color.White else Color(0xFFFFF4DC)
    val sparkle = if (dark) Color(0xFFFFFBEF) else Color(0xFFF2C46B)
    val headGlow = Brush.radialGradient(0f to (if (dark) Color(0xFFFFF4DA) else Color(0xFFFFE2A6)).copy(alpha = 0.75f), 0.4f to (if (dark) Color(0xFFFFE6B8) else Color(0xFFF5C979)).copy(alpha = 0.25f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    val landHalo = Brush.radialGradient(0f to Color.Transparent, 0.55f to (if (dark) Color(0xFFFFF1D6) else Color(0xFFF2C46B)).copy(alpha = 0.0f), 0.75f to (if (dark) Color(0xFFFFF1D6) else Color(0xFFF2C46B)).copy(alpha = 0.55f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
}

@Composable
fun rememberRingFx(dark: Boolean) = remember(dark) { RingFxKit(dark) }

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** One lotus around a button centre. [b] bloom 0 = closed bud … 1 = open flower; [alpha] overall; [sink] px downward. */
fun DrawScope.drawLotus(k: RingFxKit, x: Float, y: Float, itemR: Float, b: Float, alpha: Float, sink: Float = 0f) {
    if (alpha <= 0.003f) return
    val e1 = RingMotion.lotusInner(b)
    val e2 = RingMotion.lotusOuter(b)
    val appear = RingMotion.smooth(0f, 0.16f, b)               // the bud itself swells in
    val a = alpha * appear
    val size = 0.62f + 0.38f * appear
    translate(x, y + sink) {
        scale(size, size, pivot = Offset.Zero) {
            // soft pink-moon glow under the flower
            scale(itemR * (1.2f + 1.5f * e2.coerceAtMost(1f)), pivot = Offset.Zero) { drawCircle(k.lotusGlow, 1f, Offset.Zero, alpha = (0.25f + 0.45f * b) * a) }
            rotate(-22f * (1f - e1), pivot = Offset.Zero) {
                // the closed bud sits centred; as it opens the petal bases move to the button centre
                translate(0f, itemR * 0.52f * (1f - e1).coerceAtLeast(0f)) {
                    for (i in 0 until 8) {   // outer layer, behind, paler and more translucent
                        val ang = lerp((i - 3.5f) * 3.6f, -157.5f + i * 45f, e2)
                        val len = itemR * lerp(1.0f, 1.8f, e2)
                        val w = lerp(0.62f, 0.86f, e2.coerceIn(0f, 1.05f))
                        petal(k, ang, len, w, k.outerPetal, a * (0.8f - 0.12f * e2.coerceIn(0f, 1f)))
                    }
                    for (i in 0 until 5) {   // inner layer, unfurls first
                        val ang = lerp((i - 2f) * 5f, -144f + i * 72f, e1)
                        val len = itemR * lerp(1.08f, 1.68f, e1)
                        val w = lerp(0.66f, 0.98f, e1.coerceIn(0f, 1.05f))
                        petal(k, ang, len, w, k.innerPetal, a)
                    }
                }
            }
        }
    }
}

private fun DrawScope.petal(k: RingFxKit, ang: Float, len: Float, w: Float, brush: Brush, a: Float) {
    withTransform({ rotate(ang, pivot = Offset.Zero); scale(len * w, len, pivot = Offset.Zero) }) {
        drawPath(k.petal, brush, alpha = a.coerceIn(0f, 1f))
        drawPath(k.vein, k.veinBrush, alpha = (a * 0.7f).coerceIn(0f, 1f))
        drawPath(k.petal, k.petalEdge, alpha = (a * 0.55f).coerceIn(0f, 1f), style = k.petalStroke)
    }
}

/**
 * 月光: the whole screen goes ~95% black, a moon glows near the top and soft beams fall on each button.
 * [dark] 0…1 (× 0.95 black), [glow] the moon, [lits] per-button light 0…1 at [xs]/[ys].
 */
fun DrawScope.drawMoonNight(k: RingFxKit, moon: Offset, moonR: Float, dark: Float, glow: Float, n: Int, xs: FloatArray, ys: FloatArray, lits: FloatArray, itemR: Float) {
    if (dark <= 0.002f) return
    drawRect(Color.Black, alpha = 0.95f * dark)
    if (glow > 0.003f) translate(moon.x, moon.y) {
        scale(moonR * 9f, pivot = Offset.Zero) { drawCircle(k.moonHalo, 1f, Offset.Zero, alpha = glow) }
        scale(moonR, pivot = Offset.Zero) {
            drawCircle(k.moonDisc, 1f, Offset.Zero, alpha = glow)
            drawCircle(k.crater, 0.17f, Offset(0.28f, -0.12f), alpha = glow * 0.5f)
            drawCircle(k.crater, 0.11f, Offset(-0.18f, 0.32f), alpha = glow * 0.45f)
            drawCircle(k.crater, 0.08f, Offset(0.05f, 0.05f), alpha = glow * 0.35f)
        }
    }
    for (i in 0 until n) {
        val lit = lits[i]
        if (lit <= 0.005f) continue
        val x = xs[i]; val y = ys[i]
        // beam: a thin wedge from the moon widening onto the button; drawn in its own unit frame so one brush serves all
        val dx = x - moon.x; val dy = y - moon.y
        val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() - 90f
        k.beam.reset()
        k.beam.moveTo(-0.010f, 0f); k.beam.lineTo(0.010f, 0f); k.beam.lineTo(itemR * 1.25f / d, 1f); k.beam.lineTo(-itemR * 1.25f / d, 1f); k.beam.close()
        withTransform({ translate(moon.x, moon.y); rotate(deg, pivot = Offset.Zero); scale(d, d, pivot = Offset.Zero) }) {
            drawPath(k.beam, k.beamBrush, alpha = (lit * glow).coerceIn(0f, 1f))
        }
        translate(x, y) {
            scale(itemR * 3.1f, pivot = Offset.Zero) { drawCircle(k.itemHalo, 1f, Offset.Zero, alpha = 0.85f * lit) }
            scale(itemR * 1.55f, pivot = Offset.Zero) { drawCircle(k.itemCore, 1f, Offset.Zero, alpha = 0.75f * lit) }
        }
    }
}

/**
 * 星轨 (v5.0): layered light trails along the spiral the button just flew — a wide cool haze, a warm streak and a white
 * core, sampled from the same pose curve via [at] — plus motion-blur ghosts of the head along its direction of travel and
 * twinkling sparkles scattered off the path. [depth] dims / thins far buttons (parallax). Nothing is allocated per frame.
 */
fun DrawScope.drawStarTrail(
    k: RingFxKit, i: Int, o: Float, cl: Float, closing: Boolean, itemR: Float, trail: Float, land: Float, alpha: Float,
    x: Float, y: Float, s: Float, scratch: RingMotion.Pose, depth: Float = 1f, at: (RingMotion.Pose) -> Offset,
) {
    if (alpha <= 0.003f && trail <= 0.003f) return
    if (trail <= 0.01f) return
    val dA = 0.6f + 0.4f * ((depth - 0.8f) / 0.4f).coerceIn(0f, 1f)
    var px = x; var py = y
    val steps = 22
    var hx = 0f; var hy = 0f   // direction of travel at the head
    for (j in 1..steps) {
        val q = if (!closing) RingMotion.pose(com.moonlight.feed.data.RingAnim.STAR, o - j * 0.018f, 1f, false, scratch, depth)
                else RingMotion.pose(com.moonlight.feed.data.RingAnim.STAR, 1f, (cl + j * 0.03f).coerceAtMost(1f), true, scratch, depth)
        if (!closing && o - j * 0.018f <= 0f) break
        val pt = at(q)
        if (j == 1) { hx = x - pt.x; hy = y - pt.y }
        val fade = 1f - j / (steps + 1f)
        val w = itemR * 0.62f * q.scale * fade
        val a = (trail * fade * fade * dA).coerceIn(0f, 1f)
        drawLine(k.trailCool, Offset(px, py), pt, strokeWidth = w * 4.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, alpha = a * 0.10f)
        drawLine(k.trailWarm, Offset(px, py), pt, strokeWidth = w * 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Butt, alpha = a * 0.34f)
        drawLine(k.trailCore, Offset(px, py), pt, strokeWidth = (w * 0.65f).coerceAtLeast(1.3f), cap = androidx.compose.ui.graphics.StrokeCap.Butt, alpha = (a * 0.95f).coerceAtMost(1f))
        // sparkles: deterministic scatter per button/sample, twinkling with the clock
        val h = kotlin.math.sin(i * 12.9898f + j * 78.233f) * 43758.547f
        val r = h - kotlin.math.floor(h)
        val h2 = kotlin.math.sin(i * 39.346f + j * 11.135f) * 24634.633f
        val r2 = h2 - kotlin.math.floor(h2)
        val dx = pt.x - px; val dy = pt.y - py
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val off = (r - 0.5f) * itemR * 1.6f * q.scale
        val tw = 0.55f + 0.45f * kotlin.math.sin(o * 46f + j * 1.7f + i)
        drawCircle(k.sparkle, radius = (1.2f + 2.4f * r2) * (0.6f + 0.6f * q.scale), center = Offset(pt.x - dy / len * off, pt.y + dx / len * off), alpha = (a * tw).coerceIn(0f, 1f))
        if (j % 2 == 0) drawCircle(k.sparkle, radius = (0.9f + 1.6f * r) * (0.6f + 0.6f * q.scale), center = Offset(pt.x + dy / len * off * 0.7f, pt.y - dx / len * off * 0.7f), alpha = (a * (1.1f - tw)).coerceIn(0f, 1f))
        if (j % 5 == 0 && r > 0.55f) drawStarGlint(k.sparkle, Offset(pt.x - dy / len * off * 1.6f, pt.y + dx / len * off * 1.6f), itemR * 0.22f * (0.5f + q.scale), (a * tw).coerceIn(0f, 1f))
        px = pt.x; py = pt.y
    }
    // motion blur: ghosts of the head stretched back along the direction of travel
    val hl = kotlin.math.sqrt(hx * hx + hy * hy)
    if (hl > 0.5f) {
        val ux = hx / hl; val uy = hy / hl
        for (g in 1..3) {
            val d = itemR * s * 0.42f * g
            drawCircle(k.trailCore, radius = itemR * s * (0.92f - g * 0.14f), center = Offset(x - ux * d, y - uy * d), alpha = (0.16f * trail * dA / g).coerceIn(0f, 1f))
        }
    }
    translate(x, y) { scale(itemR * 1.8f * s, pivot = Offset.Zero) { drawCircle(k.headGlow, 1f, Offset.Zero, alpha = (0.9f * trail * dA).coerceIn(0f, 1f)) } }
}

/** Four-point glint (✦) — two thin crossed strokes + a dot. */
fun DrawScope.drawStarGlint(c: Color, p: Offset, r: Float, a: Float) {
    if (a <= 0.01f || r <= 0.3f) return
    drawLine(c, Offset(p.x - r, p.y), Offset(p.x + r, p.y), strokeWidth = (r * 0.16f).coerceAtLeast(1f), alpha = a, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(c, Offset(p.x, p.y - r), Offset(p.x, p.y + r), strokeWidth = (r * 0.16f).coerceAtLeast(1f), alpha = a, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(c, r * 0.22f, p, alpha = a)
}

/**
 * Landing: an expanding glow ring + a burst of 8 sparks flung outward ([land] 0…1), then a shimmer — a glint orbiting
 * the button rim ([shim] 0…1…0). Both are 0 at rest.
 */
fun DrawScope.drawStarLanding(k: RingFxKit, i: Int, itemR: Float, land: Float, shim: Float, alpha: Float, x: Float, y: Float) {
    if (land > 0.01f && land < 0.999f) {
        val e = 1f - (1f - land) * (1f - land)
        val fade = (1f - land)
        translate(x, y) { scale(itemR * (1.3f + 1.2f * e), pivot = Offset.Zero) { drawCircle(k.landHalo, 1f, Offset.Zero, alpha = (0.75f * fade * alpha).coerceIn(0f, 1f)) } }
        drawCircle(k.burst, itemR * (1.05f + 1.25f * e), Offset(x, y), alpha = (0.65f * fade * alpha).coerceIn(0f, 1f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (itemR * 0.12f * fade).coerceAtLeast(1f)))
        for (j in 0 until 8) {
            val ang = (j * 45f + i * 17f) * (kotlin.math.PI.toFloat() / 180f)
            val cx = kotlin.math.cos(ang); val sy = kotlin.math.sin(ang)
            val r0 = itemR * (1.0f + 0.7f * e); val r1 = itemR * (1.25f + 1.5f * e)
            drawLine(k.burst, Offset(x + cx * r0, y + sy * r0), Offset(x + cx * r1, y + sy * r1), strokeWidth = (itemR * 0.09f * fade).coerceAtLeast(1f),
                alpha = (0.9f * fade * alpha).coerceIn(0f, 1f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (j % 2 == 0) drawCircle(k.sparkle, itemR * 0.07f + 1f, Offset(x + cx * r1 * 1.08f, y + sy * r1 * 1.08f), alpha = (fade * alpha).coerceIn(0f, 1f))
        }
    }
    if (shim > 0.01f) {
        val ang = (-120f + 300f * (1f - shim) + i * 35f) * (kotlin.math.PI.toFloat() / 180f)
        val rr = itemR * 1.02f
        drawStarGlint(k.shimmer, Offset(x + kotlin.math.cos(ang) * rr, y + kotlin.math.sin(ang) * rr), itemR * 0.42f, (0.95f * shim * alpha).coerceIn(0f, 1f))
        drawCircle(k.shimmer, itemR * 1.02f, Offset(x, y), alpha = (0.28f * shim * alpha).coerceIn(0f, 1f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f))
    }
}

/**
 * The sky behind 星轨: a field of twinkling stars fading in, and one shooting star sweeping across the whole sky while
 * the buttons fall (reversed on close). [os]/[cls] = the per-button clocks (their mean drives the scene).
 */
fun DrawScope.drawStarSky(k: RingFxKit, w: Float, h: Float, n: Int, os: FloatArray, cls: FloatArray, closing: Boolean, top: Float) {
    if (n == 0) return
    var m = 0f; for (i in 0 until n) m += if (closing) cls[i] else os[i]
    m /= n
    val sky = (if (closing) m else RingMotion.smooth(0f, 0.12f, m)) * (if (closing) 1f else 1f - 0.55f * RingMotion.smooth(0.8f, 1f, m))
    if (sky > 0.01f) for (j in 0 until 46) {
        val hx = kotlin.math.sin(j * 127.1f + 11.3f) * 43758.547f; val fx = hx - kotlin.math.floor(hx)
        val hy = kotlin.math.sin(j * 311.7f + 5.7f) * 24634.633f; val fy = hy - kotlin.math.floor(hy)
        val tw = 0.5f + 0.5f * kotlin.math.sin(m * 30f + j * 2.3f)
        val r = 1.3f + 2.2f * ((j * 7) % 5) / 4f
        val p = Offset(fx * w, top + fy * h * 0.62f)
        drawCircle(k.skyStar, r, p, alpha = (0.75f * sky * tw).coerceIn(0f, 1f))
        if (j % 9 == 0) drawStarGlint(k.skyStar, p, r * 3.2f, (0.45f * sky * tw).coerceIn(0f, 1f))
    }
    // shooting star: top-right → lower-left across the sky during the fall
    val g = if (closing) 1f - m else RingMotion.smooth(0.01f, 0.55f, m)
    if (g <= 0.001f || g >= 0.999f) return
    val sx = w * 1.15f; val sy = top + h * 0.04f; val ex = -w * 0.2f; val ey = top + h * 0.34f
    val hx = sx + (ex - sx) * g; val hy = sy + (ey - sy) * g
    val tail = 0.36f
    val fade = kotlin.math.sin(g * kotlin.math.PI.toFloat())
    var px = hx; var py = hy
    for (j in 1..14) {
        val t = (g - tail * j / 14f).coerceAtLeast(0f)
        val qx = sx + (ex - sx) * t; val qy = sy + (ey - sy) * t
        val f = 1f - j / 15f
        drawLine(k.trailCool, Offset(px, py), Offset(qx, qy), strokeWidth = 9f * f, alpha = (0.10f * fade * f).coerceIn(0f, 1f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(k.trailCore, Offset(px, py), Offset(qx, qy), strokeWidth = 2.6f * f + 0.6f, alpha = (0.85f * fade * f * f).coerceIn(0f, 1f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        px = qx; py = qy
    }
    translate(hx, hy) { scale(22f, pivot = Offset.Zero) { drawCircle(k.headGlow, 1f, Offset.Zero, alpha = (0.95f * fade).coerceIn(0f, 1f)) } }
    drawStarGlint(k.trailCore, Offset(hx, hy), 14f, fade.coerceIn(0f, 1f))
}
