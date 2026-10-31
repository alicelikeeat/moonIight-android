package com.moonlight.feed.data

/**
 * The few player facts the play/pause rules need — implemented over ExoPlayer in the UI
 * (see ui/design/PlayerControl.kt) and by a fake in tests.
 */
interface Playable {
    val playWhenReady: Boolean
    /** Player.STATE_*: 1 idle, 2 buffering, 3 ready, 4 ended. */
    val playbackState: Int
    /** playbackSuppressionReason != NONE (e.g. transient audio-focus loss): wants to play but is held silent. */
    val suppressed: Boolean
    val hasMedia: Boolean
    fun play()
    fun pause()
    fun prepare()
    fun seekToStart()
}

/**
 * One rule set for every inline player (home feed, 朋友圈 cards).
 *
 * v3.1 bug: the toggle asked `player.isPlaying` and then called `play()`. `isPlaying` is false while
 * buffering, while playback is suppressed (audio focus briefly taken) and after the clip ENDED, and in
 * all three cases `play()` is a no-op (playWhenReady is already true / ended needs a seek / idle needs
 * prepare). The ▶ showed, the tap "did nothing". Decide on intent (playWhenReady + state) instead and
 * make resume do whatever the current state needs.
 */
object PlayToggle {
    const val IDLE = 1
    const val BUFFERING = 2
    const val READY = 3
    const val ENDED = 4

    /** What the UI should present as playing (⏸ icon, 暂停 label). */
    fun showsPlaying(p: Playable): Boolean =
        p.playWhenReady && !p.suppressed && (p.playbackState == READY || p.playbackState == BUFFERING)

    /** Toggle; returns true when the player is now (meant to be) playing. */
    fun toggle(p: Playable): Boolean = if (showsPlaying(p)) { p.pause(); false } else { resume(p); true }

    fun resume(p: Playable) {
        when (p.playbackState) {
            IDLE -> if (p.hasMedia) p.prepare()
            ENDED -> p.seekToStart()
        }
        // already "playing" but held silent → pause+play re-requests audio focus instead of a no-op
        if (p.playWhenReady && p.suppressed) p.pause()
        p.play()
    }
}

/** What a single tap on the home-feed media surface does. */
enum class FeedTap { TOGGLE_PLAY, TOGGLE_CHROME }

/**
 * v4.0 interaction language: TAP = the object's one obvious primary action.
 * Video → play/pause (anywhere on the picture, not just the centre ▶); image / embed cover → show/hide chrome.
 * (v3.1 bug: a tap on a paused video only toggled the near-invisible controls, so "tap didn't resume".)
 */
object FeedTapRule {
    fun onTap(isVideo: Boolean, active: Boolean, hasError: Boolean = false): FeedTap =
        if (isVideo && active && !hasError) FeedTap.TOGGLE_PLAY else FeedTap.TOGGLE_CHROME
}

/** Swipe-left info card: derived tags (no tag field in the library). */
object MediaInfo {
    fun tags(e: MediaEntry): List<String> = buildList {
        add(when (e.kind) { MediaKind.VIDEO -> "视频"; MediaKind.IMAGE -> "图片"; else -> "在线播放" })
        add(if (e.localPath != null) "本地" else "在线")
        e.platform?.takeIf { it.isNotBlank() && it != "local" }?.let { add(e.platformLabel()) }
        if (e.favorite) add("已收藏")
        if (e.hidden) add("已隐藏")
        if (e.source == "camera") add("拍摄")
    }
}

/** Feed navigation helpers (pure). */
object FeedNav {
    /** Next index after [from] whose source label equals the current one (wraps once around); null when none. */
    fun nextSameSource(sources: List<String>, from: Int): Int? {
        val src = sources.getOrNull(from) ?: return null
        val n = sources.size
        for (k in 1 until n) { val i = (from + k) % n; if (sources[i] == src) return i }
        return null
    }
}
