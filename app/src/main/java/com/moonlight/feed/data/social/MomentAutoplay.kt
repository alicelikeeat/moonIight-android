package com.moonlight.feed.data.social

/** A laid-out timeline card (pixel offsets inside the list viewport). */
data class VisibleCard(val key: String, val offset: Int, val size: Int, val playable: Boolean)

object MomentAutoplay {
    /** Fraction of the card on screen; cards taller than the viewport count as fully visible when they fill it. */
    fun visibleFraction(c: VisibleCard, viewportStart: Int, viewportEnd: Int): Float {
        if (c.size <= 0) return 0f
        val shown = (minOf(c.offset + c.size, viewportEnd) - maxOf(c.offset, viewportStart)).coerceAtLeast(0)
        return shown.toFloat() / minOf(c.size, (viewportEnd - viewportStart).coerceAtLeast(1))
    }

    /** The single card whose video should autoplay: playable, ≥ [threshold] visible, most visible (ties → upper). */
    fun pick(cards: List<VisibleCard>, viewportStart: Int, viewportEnd: Int, threshold: Float = 0.6f): String? =
        cards.filter { it.playable }
            .map { it to visibleFraction(it, viewportStart, viewportEnd) }
            .filter { it.second >= threshold }
            .sortedWith(compareByDescending<Pair<VisibleCard, Float>> { it.second }.thenBy { it.first.offset })
            .firstOrNull()?.first?.key

    /** Wi‑Fi-only mode never streams over metered networks; local files always play. */
    fun allowed(mode: String, isLocal: Boolean, unmetered: Boolean): Boolean = when (mode) {
        "OFF" -> false
        "WIFI" -> isLocal || unmetered
        else -> true
    }

    // ------------------------------------------------------------------ v5.0: several inline windows at once
    /** 全部同时播放 cap: more than this and the extra tiles show their poster frame. */
    const val MAX_INLINE = 6
    /** A tile starts on its own once this much of its post is on screen. */
    const val MIN_VISIBLE = 0.3f

    /** Should [t] start by itself under [mode]? (Manual taps bypass this.) */
    fun eligible(mode: com.moonlight.feed.data.MomentsAutoplayMode, t: VideoTile, friends: Set<String>): Boolean = t.playable && when (mode) {
        com.moonlight.feed.data.MomentsAutoplayMode.ALL -> true
        com.moonlight.feed.data.MomentsAutoplayMode.PICKED -> t.marked || t.authorId in friends
        com.moonlight.feed.data.MomentsAutoplayMode.OFF -> false
    }

    /**
     * Which inline windows exist this frame. Priority: tiles the user tapped on → tiles the user paused (kept, frozen
     * on their frame) → tiles that start by themselves, most visible first (ties → upper). Everything off screen is
     * dropped (its player is released / pooled), and the total never exceeds [cap].
     */
    fun plan(
        mode: com.moonlight.feed.data.MomentsAutoplayMode, tiles: List<VideoTile>, friends: Set<String>,
        manualOn: Set<String>, manualOff: Set<String>, cap: Int = MAX_INLINE,
    ): InlinePlan {
        val order = compareByDescending<VideoTile> { it.visible }.thenBy { it.offset }.thenBy { it.index }
        val onScreen = tiles.filter { it.visible > 0f }
        val play = ArrayList<String>(); val held = ArrayList<String>()
        fun room() = play.size + held.size < cap
        onScreen.filter { it.attId in manualOn }.sortedWith(order).forEach { if (room()) play += it.attId }
        onScreen.filter { it.attId in manualOff && it.attId !in manualOn }.sortedWith(order).forEach { if (room()) held += it.attId }
        onScreen.filter { it.visible >= MIN_VISIBLE && it.attId !in manualOn && it.attId !in manualOff && eligible(mode, it, friends) }
            .sortedWith(order).forEach { if (room()) play += it.attId }
        return InlinePlan(play, held)
    }
}

/** One video tile of a laid-out post: [visible] = fraction of its post on screen, [index] = position in the grid. */
data class VideoTile(
    val attId: String, val momentId: String, val authorId: String, val marked: Boolean,
    val visible: Float, val offset: Int, val index: Int = 0, val playable: Boolean = true,
)

/** [play] run muted in their window; [held] keep a window but stay paused (the user paused them). */
data class InlinePlan(val play: List<String>, val held: List<String>) {
    val all: List<String> get() = play + held
    companion object { val EMPTY = InlinePlan(emptyList(), emptyList()) }
}
