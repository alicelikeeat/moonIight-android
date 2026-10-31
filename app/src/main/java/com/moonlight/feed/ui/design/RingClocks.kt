package com.moonlight.feed.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.RingMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the ring's per-button open clocks (and 月光's scene clock) to 1. The resting state never depends on an animation
 * finishing: each clock snaps to 1 when it ends, is cancelled or fails ([RingMotion.runSettled]), and a watchdog snaps
 * every clock to 1 at [RingMotion.settleDeadlineMs] even if the frame clock stalls. Pure suspend code so it is unit-tested
 * with fake / stalled frame clocks at animator scale 0.5 / 1 / 2.
 */
internal suspend fun driveRingClocks(
    style: RingAnim, anims: List<Animatable<Float, AnimationVector1D>>, scene: Animatable<Float, AnimationVector1D>,
    animatorScale: Float, starSpeed: Float = 1f, onLand: (Int) -> Unit = {},
) {
    val sp = starSpeed.coerceIn(0.5f, 2f)
    try { coroutineScope {
        val n = anims.size
        val jobs = ArrayList<Job>()
        if (style == RingAnim.MOON) jobs += launch { RingMotion.runSettled({ scene.animateTo(1f, tween(560, easing = LinearEasing)) }) { scene.snapTo(1f) } }
        anims.forEachIndexed { i, a -> jobs += launch {
            RingMotion.runSettled({
                when (style) {
                    RingAnim.SPIN -> { delay(i * 25L); a.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) }   // rotate along the arc
                    // linear clocks: the petal / flight / light curves are shaped in RingMotion.pose
                    RingAnim.LOTUS -> { delay(i * 38L); a.animateTo(1f, tween(520, easing = LinearEasing)) }
                    RingAnim.STAR -> { delay((i * RingMotion.starStaggerMs(n) / sp).toLong()); a.animateTo(1f, tween((RingMotion.STAR_FLIGHT_MS / sp).toInt(), easing = LinearEasing)) }
                    RingAnim.MOON -> { delay(150L + i * 60L); a.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
                    RingAnim.FADE -> a.animateTo(1f, tween(150))
                }
            }) { a.snapTo(1f) }
        } }
        // 星轨: a soft tick as each button arrives in its slot
        if (style == RingAnim.STAR && anims.any { it.value < 1f }) anims.indices.forEach { i -> launch {
            delay(((i * RingMotion.starStaggerMs(n) + RingMotion.STAR_FLIGHT_MS * RingMotion.STAR_LAND) / sp * animatorScale.coerceAtLeast(0f)).toLong()); onLand(i)
        } }
        // watchdog: if the frame clock stalls or the effect is starved, stop the clocks — each one's finally snaps it to 1
        val dog = launch { delay(RingMotion.settleDeadlineMs(style, n, animatorScale, sp)); jobs.forEach { it.cancel() } }
        jobs.joinAll(); dog.cancel()
    } } finally {
        // and whatever happened above (cancelled before a clock even started, failure…): rest at full size
        withContext(NonCancellable) { anims.forEach { if (it.value != 1f) it.snapTo(1f) }; if (scene.value != 1f) scene.snapTo(1f) }
    }
}
