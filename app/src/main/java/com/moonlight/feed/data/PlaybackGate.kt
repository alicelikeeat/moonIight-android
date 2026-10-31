package com.moonlight.feed.data

/**
 * Pause-on-leave bookkeeping shared by the feed and the swipe player (pure; unit-tested).
 *
 * - [suspend] on ON_PAUSE (home, recents, another app, screen off, another activity on top, audio-focus loss):
 *   remembers whether the current item was playing. Idempotent — ON_PAUSE then ON_STOP keeps the first answer.
 * - [resume] on ON_RESUME: true only if that same item was playing before and the user allows resuming.
 *   If the current item changed while we were away (e.g. a fresh capture was pinned to 首页), it is a new item → normal autoplay.
 */
class PlaybackGate {
    var suspended = false
        private set
    var wasPlaying = false
        private set
    private var itemAtSuspend: String? = null

    fun suspend(isPlayingNow: Boolean, itemId: String?) {
        if (suspended) return
        suspended = true
        wasPlaying = isPlayingNow
        itemAtSuspend = itemId
    }

    fun resume(allow: Boolean, itemId: String?): Boolean {
        if (!suspended) return false
        suspended = false
        val changed = itemId != itemAtSuspend
        val r = if (changed) itemId != null else wasPlaying && allow
        wasPlaying = false; itemAtSuspend = null
        return r
    }
}
