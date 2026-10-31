package com.moonlight.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.RingAnimPick
import com.moonlight.feed.data.RingMotion
import com.moonlight.feed.ui.design.driveRingClocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v4.2: ring buttons always come to rest at full size (the v4.1 坠落 "tiny buttons" bug) + 星轨 flight. */
class V42LogicTest {
    private val styles = RingAnim.values().toList()
    private val scales = listOf(0.5f, 1f, 2f)

    /** Frame clock that ticks 16 ms per frame as fast as the test can run. */
    private class FastClock : MonotonicFrameClock {
        var t = 0L; var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R { yield(); t += 16_000_000L; frames++; return onFrame(t) }
    }
    /** A frame clock that never delivers a frame (device starved / effect blocked). */
    private object StalledClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = awaitCancellation()
    }
    private class Scale(override val scaleFactor: Float) : MotionDurationScale

    private fun assertRest(style: RingAnim, values: List<Float>, ctx: String) {
        values.forEachIndexed { i, v ->
            assertEquals("$ctx: clock $i", 1f, v, 0f)
            val q = RingMotion.pose(style, v, 1f, false)
            assertTrue("$ctx: button $i rests at full size (scale=${q.scale} alpha=${q.alpha} label=${q.label} rf=${q.rf})", q.isRest)
        }
    }

    // ------------------------------------------------------------------ pose invariants
    @Test fun restingPoseIsExactlyFullSizeForEveryStyle() {
        for (st in styles) {
            val q = RingMotion.pose(st, 1f, 1f, false)
            assertTrue("$st rest", q.isRest)
            assertEquals("$st scale", 1f, q.scale, 0f); assertEquals("$st alpha", 1f, q.alpha, 0f); assertEquals("$st label", 1f, q.label, 0f)
            // not closing → the close argument is ignored
            assertTrue("$st rest ignores cl", RingMotion.pose(st, 1f, 0.3f, false).isRest)
            // clocks that overshoot / are snapped past 1 still rest at full size (SPIN keeps its spring bounce until exactly 1)
            if (st != RingAnim.SPIN) assertTrue("$st o>1", RingMotion.pose(st, 1.02f, 1f, false).isRest)
        }
    }

    @Test fun fullyClosedIsInvisibleForEveryStyle() {
        for (st in styles) assertEquals("$st", 0f, RingMotion.pose(st, 1f, 0f, true).alpha, 1e-4f)
    }

    @Test fun scaleNeverTiedToAlphaReachingOne() {
        // the v4.1 symptom: 坠落 alpha hit 1 at o≈0.16 while scale was ≈0.24. Whatever alpha does, the final pose is full size.
        for (st in styles) for (k in 0..100) {
            val q = RingMotion.pose(st, k / 100f, 1f, false)
            assertTrue("$st @${k}% scale in range", q.scale in 0.05f..1.25f)
            assertTrue("$st @${k}% alpha in range", q.alpha in 0f..1f && q.label in 0f..1f)
        }
    }

    // ------------------------------------------------------------------ the production clock driver
    @Test fun clocksEndAtOneUnderAnimatorScales() = runBlocking {
        for (st in styles) for (s in scales) {
            val anims = List(4) { Animatable(0f) }; val scene = Animatable(0f); var landed = 0
            withTimeout(10_000) {
                kotlinx.coroutines.withContext(FastClock() + Scale(s)) { driveRingClocks(st, anims, scene, s) { landed++ } }
            }
            assertRest(st, anims.map { it.value }, "$st ×$s")
            assertEquals("$st ×$s scene", 1f, scene.value, 0f)
            if (st == RingAnim.STAR) assertEquals("one tick per landing", 4, landed)
        }
    }

    @Test fun interruptedAtAnyPointStillRestsAtFullSize() = runBlocking {
        for (st in styles) for (cutMs in listOf(1L, 20L, 60L, 140L)) {
            val anims = List(4) { Animatable(0f) }; val scene = Animatable(0f)
            val job = CoroutineScope(coroutineContext + StalledClock + Scale(1f)).launch { driveRingClocks(st, anims, scene, 1f) }
            yield(); delay(cutMs)
            job.cancelAndJoin()                           // e.g. the effect restarts / leaves composition mid-flight
            assertRest(st, anims.map { it.value }, "$st cut@$cutMs")
            assertEquals(1f, scene.value, 0f)
        }
    }

    @Test fun watchdogSnapsToFullSizeWhenFramesStall() = runBlocking {
        for (st in styles) for (s in scales) {
            val anims = List(4) { Animatable(0f) }; val scene = Animatable(0f)
            val t0 = System.nanoTime()
            withTimeout(RingMotion.settleDeadlineMs(st, 4, s) + 2_000) {
                kotlinx.coroutines.withContext(StalledClock + Scale(s)) { driveRingClocks(st, anims, scene, s) }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertRest(st, anims.map { it.value }, "$st stalled ×$s")
            assertTrue("$st ×$s settled by the deadline ($ms ms)", ms <= RingMotion.settleDeadlineMs(st, 4, s) + 400)
        }
    }

    @Test fun settleDeadlineFollowsAnimatorScale() {
        for (st in styles) for (n in 1..6) {
            val d = scales.map { RingMotion.settleDeadlineMs(st, n, it) }
            assertTrue("$st n=$n grows with scale", d.zipWithNext().all { (a, b) -> b > a })
            scales.forEachIndexed { k, s -> assertTrue("$st ×$s after the animation", d[k] >= RingMotion.openMs(st, n) * s) }
            assertTrue("$st off", RingMotion.settleDeadlineMs(st, n, 0f) <= 200)
        }
    }

    @Test fun runSettledSettlesOnCancelAndFailure() = runBlocking {
        var v = 0f
        val j = launch { RingMotion.runSettled({ v = 0.2f; awaitCancellation() }) { v = 1f } }
        yield(); j.cancelAndJoin(); assertEquals(1f, v, 0f)
        v = 0f
        runCatching { RingMotion.runSettled({ v = 0.3f; error("boom") }) { v = 1f } }
        assertEquals(1f, v, 0f)
    }

    /** Guard against the root cause: deferred layer / layout blocks must read only captured plain values, never shared arrays. */
    @Test fun noArrayReadsInsideDeferredLayerOrLayoutBlocks() {
        val src = listOf("src/main/java/com/moonlight/feed/ui/design/Radial.kt", "app/src/main/java/com/moonlight/feed/ui/design/Radial.kt").map(::File).first { it.exists() }.readText()
        val arr = Regex("""\b(xs|ys|ss|al|la|ex|ex2|os|cls)\[""")
        var found = 0
        for (m in Regex("""(graphicsLayer|\.layout)\s*\{""").findAll(src)) {
            var depth = 0; var i = m.range.last; val start = i
            while (i < src.length) { when (src[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) break } }; i++ }
            val body = src.substring(start, i)
            assertFalse("array read inside deferred block: ${body.take(160)}", arr.containsMatchIn(body))
            found++
        }
        assertTrue("found the button layer/layout blocks", found >= 4)
    }

    // ------------------------------------------------------------------ 星轨
    @Test fun starButtonsFlyInFromFarAlongTheArc() {
        val first = RingMotion.pose(RingAnim.STAR, 0f, 1f, false)
        assertTrue("starts tiny", first.scale <= 0.15f); assertEquals("far up", 1f, first.lift, 1e-4f)
        assertEquals("a long sweep still to go", RingMotion.STAR_SWEEP, first.ang, 1e-3f); assertTrue("wide orbit", first.rf > 1.5f)
        val landAt = (RingMotion.STAR_LAND * 100).toInt()   // v5.0: lands at 72 %, then overshoot + settle
        val flight = (0..landAt).map { RingMotion.pose(RingAnim.STAR, it / 100f, 1f, false).let { q -> Triple(q.scale, q.ang, q.lift) } }
        assertTrue("grows continuously", flight.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue("sweeps toward its slot", flight.zipWithNext().all { (a, b) -> b.second <= a.second })
        assertTrue("approaches", flight.zipWithNext().all { (a, b) -> b.third <= a.third })
        assertEquals("overshoots at landing", RingMotion.STAR_OVERSHOOT, RingMotion.starScale(RingMotion.STAR_LAND), 1e-3f)
        val settle = (landAt + 1..100).map { RingMotion.pose(RingAnim.STAR, it / 100f, 1f, false).scale }
        assertTrue("settles down to 1", settle.zipWithNext().all { (a, b) -> b <= a } && settle.last() == 1f)
        assertTrue("trail while flying, gone at rest", RingMotion.pose(RingAnim.STAR, 0.3f, 1f, false).fx > 0.3f && RingMotion.pose(RingAnim.STAR, 1f, 1f, false).fx == 0f)
    }

    @Test fun starCloseReversesOutAlongTheArc() {
        val gone = RingMotion.pose(RingAnim.STAR, 1f, 0f, true)
        assertTrue(gone.scale <= 0.15f); assertEquals(RingMotion.STAR_SWEEP, gone.ang, 1e-3f); assertEquals(1f, gone.lift, 1e-4f)
        val path = (100 downTo 0).map { RingMotion.pose(RingAnim.STAR, 1f, it / 100f, true).scale }
        assertTrue("shrinks as it leaves", path.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test fun starTimingFeelsLikeAnIntro() {
        // v5.0: the showcase intro is longer (sky → spiral → landing → shimmer), still under ~1.2 s at 标准
        for (n in 2..6) assertTrue("open ${RingMotion.openMs(RingAnim.STAR, n)}ms", RingMotion.openMs(RingAnim.STAR, n) in 850L..1200L)
        assertEquals(RingMotion.openMs(RingAnim.STAR, 4), RingMotion.STAR_FLIGHT_MS + 3 * RingMotion.starStaggerMs(4))
    }

    @Test fun fallAndOlderValuesMigrateToStar() {
        assertEquals("STAR", RingAnimPick.normalize("FALL")); assertEquals("STAR", RingAnimPick.normalize("BLUR"))
        assertEquals("LOTUS,STAR", RingAnimPick.encodeSet(RingAnimPick.parseSet("FALL,SHATTER")))
        assertEquals("星轨", RingAnim.STAR.short)
    }
}
