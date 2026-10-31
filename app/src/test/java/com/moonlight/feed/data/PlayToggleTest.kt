package com.moonlight.feed.data

import com.moonlight.feed.ui.social.MomentVideoHandlers
import com.moonlight.feed.ui.social.momentVideoMenuSpec
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors ExoPlayer: play() only sets playWhenReady (no-op when already set, ENDED stays ended, IDLE stays idle). */
private class FakeExo(var state: Int = PlayToggle.READY, var pwr: Boolean = true, var supp: Boolean = false) : Playable {
    override val playWhenReady get() = pwr
    override val playbackState get() = state
    override val suppressed get() = supp
    override val hasMedia = true
    var focusRequests = 0
    override fun play() { if (!pwr) { pwr = true; focusRequests++; supp = false } }
    override fun pause() { pwr = false }
    override fun prepare() { if (state == PlayToggle.IDLE) state = PlayToggle.READY }
    override fun seekToStart() { if (state == PlayToggle.ENDED) state = PlayToggle.READY }
    /** What ExoPlayer.isPlaying reports. */
    val isPlaying get() = pwr && state == PlayToggle.READY && !supp
}

class PlayToggleTest {
    /** The v3.1 toggle, kept to prove the failure modes. */
    private fun oldToggle(p: FakeExo) { if (p.isPlaying) p.pause() else p.play() }

    @Test fun pauseThenTapPlays() {
        val p = FakeExo()
        assertFalse(PlayToggle.toggle(p))                      // ⏸ tapped
        assertFalse(p.isPlaying)
        // tap on the paused video surface → play/pause (not just "toggle chrome")
        assertEquals(FeedTap.TOGGLE_PLAY, FeedTapRule.onTap(isVideo = true, active = true))
        assertTrue(PlayToggle.toggle(p))
        assertTrue(p.isPlaying)
        // and again → paused, again → playing (single tap each time)
        assertFalse(PlayToggle.toggle(p)); assertTrue(PlayToggle.toggle(p)); assertTrue(p.isPlaying)
    }

    @Test fun centreIconAndSurfaceAgree() {
        val p = FakeExo(); PlayToggle.toggle(p)
        assertFalse(PlayToggle.showsPlaying(p))                // centre shows ▶
        PlayToggle.toggle(p)                                   // centre ▶ tapped
        assertTrue(PlayToggle.showsPlaying(p))
        assertEquals(FeedTap.TOGGLE_PLAY, FeedTapRule.onTap(true, true))  // v4.0: tap on a playing video pauses too
    }

    @Test fun suppressedPlaybackResumesWhereOldToggleWasANoOp() {
        // audio focus briefly taken: playWhenReady=true but silent → ▶ shown
        val old = FakeExo(supp = true); oldToggle(old); assertFalse("v3.1: tap did nothing", old.isPlaying)
        val p = FakeExo(supp = true)
        assertFalse(PlayToggle.showsPlaying(p))
        assertTrue(PlayToggle.toggle(p)); assertTrue(p.isPlaying); assertEquals(1, p.focusRequests)
    }

    @Test fun endedClipRestarts() {
        val old = FakeExo(state = PlayToggle.ENDED, pwr = true); oldToggle(old); assertFalse(old.isPlaying)
        val p = FakeExo(state = PlayToggle.ENDED, pwr = true)
        PlayToggle.toggle(p); assertTrue(p.isPlaying)
    }

    @Test fun idlePlayerIsPreparedOnResume() {
        val p = FakeExo(state = PlayToggle.IDLE, pwr = false)
        PlayToggle.toggle(p); assertTrue(p.isPlaying)
    }

    @Test fun bufferingTapPausesInsteadOfNoOp() {
        val old = FakeExo(state = PlayToggle.BUFFERING); oldToggle(old); assertTrue("v3.1: ⏸ during buffering kept playing", old.pwr)
        val p = FakeExo(state = PlayToggle.BUFFERING)
        assertTrue(PlayToggle.showsPlaying(p)); assertFalse(PlayToggle.toggle(p)); assertFalse(p.pwr)
    }

    @Test fun tapRulePerObject() {
        assertEquals(FeedTap.TOGGLE_PLAY, FeedTapRule.onTap(isVideo = true, active = true))
        assertEquals(FeedTap.TOGGLE_CHROME, FeedTapRule.onTap(isVideo = false, active = true))     // image
        assertEquals(FeedTap.TOGGLE_CHROME, FeedTapRule.onTap(isVideo = true, active = false))
        assertEquals(FeedTap.TOGGLE_CHROME, FeedTapRule.onTap(isVideo = true, active = true, hasError = true))
    }

    @Test fun nextSameSource() {
        val src = listOf("B站", "本地", "B站", "YouTube", "本地")
        assertEquals(2, FeedNav.nextSameSource(src, 0))
        assertEquals(4, FeedNav.nextSameSource(src, 1))
        assertEquals(1, FeedNav.nextSameSource(src, 4))   // wraps
        assertEquals(null, FeedNav.nextSameSource(src, 3))
    }

    // ---- 朋友圈 video ring
    private fun titles(playing: Boolean, muted: Boolean, inline: Boolean = true, lib: Boolean = true) =
        momentVideoMenuSpec(Rect.Zero, "小月", playing, muted, inline, lib, lib, MomentVideoHandlers()).radial!!.map { it.title }

    @Test fun momentRingItemsReflectState() {
        assertEquals(listOf("播放", "进入播放页面", "打开声音", "保存"), titles(playing = false, muted = true))
        assertEquals(listOf("暂停", "进入播放页面", "静音", "保存"), titles(playing = true, muted = false))
        assertEquals(listOf("进入播放页面", "保存"), titles(false, true, inline = false))
        assertEquals(listOf("播放", "打开声音"), titles(false, true, lib = false))
        assertFalse(momentVideoMenuSpec(Rect.Zero, null, false, true, true, true, true, MomentVideoHandlers()).list) // radial, not list
    }

    @Test fun momentRingActionsCallHandlers() {
        val hit = mutableListOf<String>()
        val spec = momentVideoMenuSpec(Rect.Zero, null, false, true, true, true, true,
            MomentVideoHandlers(toggle = { hit += "t" }, open = { hit += "o" }, sound = { hit += "s" }, save = { hit += "v" }))
        spec.radial!!.forEach { it.onClick() }
        assertEquals(listOf("t", "o", "s", "v"), hit)
    }
}
