package com.moonlight.feed.data

/**
 * 定时长按 — the third gesture tier, as a pure state machine (no Android; unit-tested).
 *
 *   tap      up before [HoldConfig.longMs]
 *   long     the normal long-press (rings open as before, at the usual time)
 *   timed    still holding at [HoldConfig.timedMs] on something that opened a ring → the ring's assigned option fires
 *            (or the ring just stays up / pops when nothing is assigned or 到时自动触发 is off)
 *   center   still holding at [HoldConfig.centerMs] with the press inside the centre circle → 全部按键 panel
 *
 * One target per gesture: a press inside the centre circle (with 全部按键 on) only ever leads to the panel, never to a
 * timed auto-fire, so the two can't race. Moving beyond [HoldConfig.slopPx] from the press point cancels both timed tiers
 * (the normal long-press / 拖动 selection carry on untouched).
 */
data class HoldConfig(
    val longMs: Long = 500L,
    val timedOn: Boolean = false,
    val timedMs: Long = 5000L,
    val autoFire: Boolean = true,
    val centerOn: Boolean = true,
    val centerMs: Long = 5000L,
    /** Diameter of the centre circle as a fraction of the short screen side. */
    val centerFrac: Float = 0.30f,
    val slopPx: Float = 24f,
) {
    companion object {
        fun of(p: V5Prefs, slopPx: Float, longMs: Long = 500L) = HoldConfig(
            longMs = longMs, timedOn = p.timedOn, timedMs = p.timedMs.coerceAtLeast(longMs + 500L), autoFire = p.timedAutoFire,
            centerOn = p.centerPanel, slopPx = slopPx,
        )
    }
}

enum class HoldTier { NONE, TIMED, CENTER }
enum class HoldOutcome { TAP, LONG, TIMED, CENTER, CANCELLED }

sealed interface HoldEvent {
    /** The normal long-press time passed (readout may appear). */
    data object Long : HoldEvent
    /** Timed threshold reached on [context]; [fireKey] non-null → fire that (assignable, non-destructive) action. */
    data class Timed(val context: HoldContext?, val fireKey: String?, val deferred: Boolean) : HoldEvent
    /** Centre hold reached → open 全部按键. */
    data object Center : HoldEvent
    /** Moved beyond slop: the timed tiers are off for the rest of this press. */
    data object Cancelled : HoldEvent
}

class HoldMachine(var cfg: HoldConfig = HoldConfig()) {
    enum class Phase { IDLE, PRESSED, LONG, FIRED, CANCELLED }
    var phase = Phase.IDLE; private set
    private var downAt = 0L
    private var ox = 0f; private var oy = 0f
    private var w = 0f; private var h = 0f
    /** Context of the ring this press opened (or the deferred hold registered for it); null = nothing timed to do. */
    var context: HoldContext? = null; private set
    private var assigned: String? = null
    /** The ring isn't open yet: it opens at the threshold (朋友圈 video: long-press = full-screen player on release). */
    var deferred = false; private set
    var firedTier = HoldTier.NONE; private set

    fun down(t: Long, x: Float, y: Float, width: Float, height: Float) {
        phase = Phase.PRESSED; downAt = t; ox = x; oy = y; w = width; h = height
        context = null; assigned = null; deferred = false; firedTier = HoldTier.NONE
    }

    /** This press opened a ring of [ctx] (with [assignedKey] from the settings), or registered a deferred one. */
    fun ring(ctx: HoldContext?, assignedKey: String?, deferred: Boolean = false) {
        if (phase != Phase.PRESSED && phase != Phase.LONG) return
        if (context != null && !deferred && this.deferred) return   // the deferred hold already owns this press
        context = ctx
        assigned = assignedKey?.takeIf { ctx != null && TimedActions.assignable(ctx, it) }   // never anything destructive
        this.deferred = deferred
    }

    val inCenter: Boolean get() {
        val r = minOf(w, h) * cfg.centerFrac / 2f
        val dx = ox - w / 2f; val dy = oy - h / 2f
        return w > 0f && h > 0f && dx * dx + dy * dy <= r * r
    }

    /** Which timed tier this press is heading for (decided by where it started + what it opened). */
    fun tier(): HoldTier = when {
        cfg.centerOn && inCenter -> HoldTier.CENTER
        cfg.timedOn && context != null -> HoldTier.TIMED
        else -> HoldTier.NONE
    }
    fun targetMs(): Long = when (tier()) { HoldTier.CENTER -> cfg.centerMs; HoldTier.TIMED -> cfg.timedMs; HoldTier.NONE -> Long.MAX_VALUE }
    fun elapsed(t: Long): Long = if (phase == Phase.IDLE) 0L else (t - downAt).coerceAtLeast(0L)
    /** Countdown fill 0…1 from the normal long-press to the threshold. */
    fun progress(t: Long): Float {
        val tg = targetMs(); if (tg == Long.MAX_VALUE) return 0f
        return ((elapsed(t) - cfg.longMs).toFloat() / (tg - cfg.longMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    }
    /** Readout / countdown are on screen. */
    fun showing(): Boolean = phase == Phase.LONG && tier() != HoldTier.NONE

    fun move(x: Float, y: Float): HoldEvent? {
        if (phase != Phase.PRESSED && phase != Phase.LONG) return null
        val dx = x - ox; val dy = y - oy
        if (dx * dx + dy * dy > cfg.slopPx * cfg.slopPx) { phase = Phase.CANCELLED; return HoldEvent.Cancelled }
        return null
    }

    fun tick(t: Long): HoldEvent? {
        val e = elapsed(t)
        if (phase == Phase.PRESSED && e >= cfg.longMs) { phase = Phase.LONG; return HoldEvent.Long }
        if (phase != Phase.LONG) return null
        return when (tier()) {
            HoldTier.CENTER -> if (e >= cfg.centerMs) { phase = Phase.FIRED; firedTier = HoldTier.CENTER; HoldEvent.Center } else null
            HoldTier.TIMED -> if (e >= cfg.timedMs) {
                phase = Phase.FIRED; firedTier = HoldTier.TIMED
                HoldEvent.Timed(context, if (cfg.autoFire) assigned else null, deferred)
            } else null
            HoldTier.NONE -> null
        }
    }

    fun up(t: Long): HoldOutcome {
        tick(t)
        val out = when (phase) {
            Phase.IDLE -> HoldOutcome.TAP
            Phase.PRESSED -> HoldOutcome.TAP
            Phase.LONG -> HoldOutcome.LONG
            Phase.FIRED -> if (firedTier == HoldTier.CENTER) HoldOutcome.CENTER else HoldOutcome.TIMED
            Phase.CANCELLED -> HoldOutcome.CANCELLED
        }
        phase = Phase.IDLE
        return out
    }
}
