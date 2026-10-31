package com.moonlight.feed.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure easing/motion curves for the 长按动画 styles (no Compose → unit-tested).
 * Every function takes a linear progress 0…1 and returns a shaped value; Radial.kt only maps them to pixels.
 */
object RingMotion {
    fun clamp01(x: Float) = x.coerceIn(0f, 1f)
    fun smooth(e0: Float, e1: Float, x: Float): Float { val t = clamp01((x - e0) / (e1 - e0)); return t * t * (3f - 2f * t) }
    fun easeOutCubic(x: Float): Float { val t = 1f - clamp01(x); return 1f - t * t * t }
    /** Overshoots past 1 then settles (s ≈ 1.2 → ~6% overshoot). */
    fun backOut(x: Float, s: Float = 1.2f): Float { val t = clamp01(x) - 1f; return 1f + (s + 1f) * t * t * t + s * t * t }

    // ---------------------------------------------------------------- 星轨 (v5.0)
    /**
     * Share of the open timeline spent falling; the rest is the landing (overshoot, glow burst, shimmer).
     * Each button starts high above the top of the screen — tiny, spinning — and spirals down around the finger,
     * accelerating as it falls and growing to full size.
     */
    const val STAR_LAND = 0.72f
    /** Degrees each button still has to travel around the finger when it starts (more than a full turn). */
    const val STAR_SWEEP = 400f
    /** Spin while falling (degrees still to unwind at the start). */
    const val STAR_SPIN = 540f
    /** Scale at touchdown, before it settles to 1. */
    const val STAR_OVERSHOOT = 1.16f
    /** One button's flight at 星轨速度 1× (linear clock; curves below). */
    const val STAR_FLIGHT_MS = 900L
    /** Buttons launch one after another. */
    fun starStaggerMs(n: Int): Long = if (n <= 1) 0L else minOf(70L, 240L / (n - 1))
    /** Path progress 0 (high in the sky) … 1 (in its slot): starts drifting, then accelerates into the slot. */
    fun starTravel(o: Float): Float { val x = clamp01(o / STAR_LAND); return 0.15f * x + 0.85f * pow(x, 2.2f) }
    /** Normalised speed along the path (0…1) — drives trail length / streak brightness. */
    fun starSpeed(o: Float): Float { val x = clamp01(o / STAR_LAND); return if (o >= STAR_LAND) 0f else (0.15f + 1.87f * pow(x, 1.2f)) / 2.02f }
    /** Perspective: tiny far away, growing continuously to [STAR_OVERSHOOT] at touchdown, then settling to 1. */
    fun starScale(o: Float): Float =
        if (o < STAR_LAND) 0.06f + (STAR_OVERSHOOT - 0.06f) * pow(starTravel(o), 0.9f)
        else STAR_OVERSHOOT - (STAR_OVERSHOOT - 1f) * smooth(0f, 1f, (o - STAR_LAND) / (1f - STAR_LAND))
    /** Landing progress 0…1 (0 while falling). */
    fun starLanding(o: Float): Float = if (o <= STAR_LAND) 0f else clamp01((o - STAR_LAND) / (1f - STAR_LAND))
    /** Per-button depth 0.8…1.2 (parallax: far ones start smaller / dimmer / higher). Deterministic per index. */
    fun starDepth(i: Int): Float { val h = kotlin.math.sin(i * 91.7f + 3.1f) * 43758.547f; return 0.8f + 0.4f * (h - kotlin.math.floor(h)) }
    private fun pow(a: Float, b: Float) = Math.pow(a.toDouble(), b.toDouble()).toFloat()

    // ---------------------------------------------------------------- 荷花
    /** Inner petals unfurl first (with a little overshoot), outer layer follows. b = bloom 0 (bud) … 1 (open). */
    fun lotusInner(b: Float): Float = unfurl(clamp01(b / 0.70f))
    fun lotusOuter(b: Float): Float = unfurl(clamp01((b - 0.25f) / 0.75f))
    /** Petal opening: slow out of the bud, fast through the middle, ~4% overshoot just before it rests. */
    fun unfurl(x: Float): Float { val t = clamp01(x); return t * t * (3f - 2f * t) + 0.9f * t * t * (1f - t) }
    /** The button shows through once the petals part. */
    fun lotusReveal(b: Float): Float = smooth(0.34f, 0.86f, b)
    /** The whole ring blooms out of the finger point during the first part. */
    fun lotusSpread(b: Float): Float = easeOutCubic(clamp01(b / 0.5f))

    // ---------------------------------------------------------------- 月光
    /** Screen darkness (× 0.95 black) from the scene progress — falls fast, before the light arrives. */
    fun moonDark(scene: Float): Float = easeOutCubic(clamp01(scene * 1.8f))
    /** The moon itself rises/brightens after the dark settles. */
    fun moonGlow(scene: Float): Float = smooth(0.25f, 1f, scene)

    // ---------------------------------------------------------------- one button's pose (all styles)
    /**
     * Where/how one ring button is drawn this frame. [rf] radius fraction from the ring centre, [ang] degrees still to sweep
     * along the arc, [lift] 0…1 of the "high above" offset, [sink] 0…1 of the sink offset, [fx]/[fx2] style extras
     * (bloom, light, trail, landing glow). The resting pose (o = 1, not closing) is exactly full size — tested for every style.
     */
    class Pose {
        var rf = 1f; var ang = 0f; var lift = 0f; var sink = 0f
        var scale = 1f; var alpha = 1f; var label = 1f; var fx = 0f; var fx2 = 0f
        /** v5.0: button rotation (degrees) and a third effect channel (星轨 shimmer). */
        var rot = 0f; var fx3 = 0f
        val isRest get() = rf == 1f && ang == 0f && lift == 0f && sink == 0f && scale == 1f && alpha == 1f && label == 1f && rot == 0f
    }

    /** [o] open clock 0…1 (snapped to 1 when it ends or is interrupted); [cl] close progress 1 → 0 (1 while open). */
    fun pose(style: com.moonlight.feed.data.RingAnim, o0: Float, cl0: Float, closing: Boolean, out: Pose = Pose(), depth: Float = 1f): Pose {
        // SPIN's spring may overshoot past 1 (kept for the bounce); every other clock is clamped
        val o = if (style == com.moonlight.feed.data.RingAnim.SPIN) o0.coerceIn(0f, 1.25f) else clamp01(o0); val cl = if (closing) clamp01(cl0) else 1f
        out.rf = 1f; out.ang = 0f; out.lift = 0f; out.sink = 0f; out.fx = 0f; out.fx2 = 0f; out.rot = 0f; out.fx3 = 0f
        when (style) {
            com.moonlight.feed.data.RingAnim.SPIN -> {
                val t = o * cl
                out.rf = 0.18f + 0.82f * t; out.ang = -62f * (1f - t)
                out.scale = 0.35f + 0.65f * t; out.alpha = clamp01(t); out.label = clamp01(t) * clamp01(t)
            }
            com.moonlight.feed.data.RingAnim.LOTUS -> {
                val b = if (closing) cl else o
                out.rf = if (closing) 1f else lotusSpread(b)
                out.sink = if (closing) (1f - cl) * (1f - cl) else 0f
                val rv = lotusReveal(b)
                out.scale = 0.42f + 0.58f * backOut(rv, 1.5f); out.alpha = rv; out.label = rv * rv
                out.fx = b; out.fx2 = if (closing) smooth(0f, 0.6f, cl) else 1f
            }
            com.moonlight.feed.data.RingAnim.STAR -> if (!closing) {
                val u = starTravel(o); val land = starLanding(o)
                starOrbit(u, out)
                // touchdown: a small dive past the slot and back (positional overshoot), unwinding the last of the spin
                if (land > 0f) { out.rf = 1f - 0.07f * kotlin.math.sin(land * PI.toFloat()) * (1f - land); out.rot = -14f * kotlin.math.sin(land * PI.toFloat() * 1.5f) * (1f - land) }
                else out.rot = STAR_SPIN * pow(1f - u, 1.5f)
                val near = clamp01(u)
                out.scale = starScale(o) * (depth + (1f - depth) * near)
                out.lift *= (1.15f - 0.15f * depth)   // far (small depth) starts higher
                out.alpha = smooth(0f, 0.10f, o) * (0.55f + 0.45f * clamp01((depth - 0.8f) / 0.4f) + (0.45f - 0.45f * clamp01((depth - 0.8f) / 0.4f)) * near)
                out.label = smooth(0.80f, 1f, o)
                out.fx = smooth(0f, 0.06f, o) * (0.35f + 0.65f * starSpeed(o)) * (1f - smooth(0.92f, 1f, u))      // light trail ∝ speed
                out.fx2 = land                                                                                      // glow burst
                out.fx3 = if (land < 0.3f) 0f else kotlin.math.sin(clamp01((land - 0.3f) / 0.7f) * PI.toFloat())   // shimmer
            } else {
                // close: spin back up into the sky — slow lift-off, accelerating away along the spiral
                val r = (1f - cl) * (1f - cl)
                starOrbit(1f - r, out)
                out.rot = -STAR_SPIN * pow(r, 1.2f)
                out.scale = 0.06f + 0.94f * (1f - r) * (depth + (1f - depth) * (1f - r)); out.alpha = smooth(0f, 0.35f, cl); out.label = cl * cl * cl
                out.fx = (1f - cl) * smooth(0f, 0.3f, cl)
            }
            com.moonlight.feed.data.RingAnim.MOON -> {
                val lit = o * cl
                out.scale = 0.94f + 0.06f * easeOutCubic(lit); out.alpha = (0.14f + 0.86f * lit) * cl; out.label = lit
                out.fx = lit
            }
            com.moonlight.feed.data.RingAnim.FADE -> { val t = o * cl; out.scale = 1f; out.alpha = t; out.label = t }
        }
        // resting state: exactly full size, in place, opaque — no float drift from the curves
        if (!closing && o0 >= 1f && (style != com.moonlight.feed.data.RingAnim.SPIN || o0 == 1f)) {
            out.rf = 1f; out.ang = 0f; out.lift = 0f; out.sink = 0f; out.scale = 1f; out.alpha = 1f; out.label = 1f
            out.fx2 = if (style == com.moonlight.feed.data.RingAnim.STAR) 0f else out.fx2
            out.rot = 0f; out.fx3 = 0f
        }
        return out
    }

    /**
     * 星轨 path at travel [u] 0…1: an inward spiral around the finger — the button starts high and far out (top-left of its
     * slot, [STAR_SWEEP]° still to go), swings wide around the finger and tightens into its ring slot.
     */
    fun starOrbit(u: Float, out: Pose) {
        val r = 1f - clamp01(u)
        out.rf = 1f + 0.9f * pow(r, 1.1f); out.ang = STAR_SWEEP * pow(r, 1.15f); out.lift = pow(r, 2.6f)
    }

    /** Wall-clock length of one opening (ms) at animator scale 1: longest clock + stagger. Used by the settle watchdog. */
    fun openMs(style: com.moonlight.feed.data.RingAnim, n: Int, starSpeed: Float = 1f): Long = when (style) {
        com.moonlight.feed.data.RingAnim.SPIN -> 600L + 25L * n
        com.moonlight.feed.data.RingAnim.LOTUS -> 520L + 38L * n
        com.moonlight.feed.data.RingAnim.STAR -> ((STAR_FLIGHT_MS + starStaggerMs(n) * (n - 1).coerceAtLeast(0)) / starSpeed.coerceIn(0.5f, 2f)).toLong()
        com.moonlight.feed.data.RingAnim.MOON -> 150L + 360L + 60L * n
        com.moonlight.feed.data.RingAnim.FADE -> 150L
    }
    /** When the watchdog snaps any unfinished clock to 1: generous margin, scaled like system animations. */
    fun settleDeadlineMs(style: com.moonlight.feed.data.RingAnim, n: Int, animatorScale: Float, starSpeed: Float = 1f): Long =
        ((openMs(style, n, starSpeed) + 250L) * animatorScale.coerceIn(0f, 10f)).toLong().coerceAtLeast(0L) + 120L

    /** Runs [animate]; whatever happens (end, cancel, exception) [settle] runs afterwards, uncancellable. */
    suspend fun runSettled(animate: suspend () -> Unit, settle: suspend () -> Unit) {
        try { animate() } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { settle() } }
    }
}
