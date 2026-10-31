package com.moonlight.feed.data

import com.moonlight.feed.EmbedHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGateTest {
    @Test fun resumesOnlyWhatWasPlaying() {
        val g = PlaybackGate()
        g.suspend(true, "a"); assertTrue(g.suspended)
        assertTrue(g.resume(true, "a"))
        g.suspend(false, "a")
        assertFalse("user had paused → stay paused", g.resume(true, "a"))
    }

    @Test fun settingCanForbidResume() {
        val g = PlaybackGate()
        g.suspend(true, "a")
        assertFalse(g.resume(false, "a"))
    }

    @Test fun pauseThenStopKeepsFirstAnswer() {
        val g = PlaybackGate()
        g.suspend(true, "a")     // ON_PAUSE while playing
        g.suspend(false, "a")    // ON_STOP after we paused it ourselves — must not overwrite
        assertTrue(g.resume(true, "a"))
    }

    @Test fun newItemWhileAwayAutoplaysNormally() {
        val g = PlaybackGate()
        g.suspend(false, "a")
        assertTrue("fresh capture pinned while camera was on top", g.resume(true, "b"))
        g.suspend(true, "a")
        assertFalse("nothing to play", g.resume(true, null))
    }

    @Test fun resumeWithoutSuspendIsNoop() = assertFalse(PlaybackGate().resume(true, "a"))

    @Test fun embedPauseScriptCoversEverything() {
        for (needle in listOf("pauseVideo", "querySelectorAll('video,audio')", "iframe", "\"method\":\"pause\"", "moonPaused"))
            assertTrue(needle, EmbedHost.PAUSE_JS.contains(needle))
        assertTrue(EmbedHost.RESUME_JS.contains("playVideo"))
        assertTrue("resume only restarts media we paused", EmbedHost.RESUME_JS.contains("dataset.moonPaused"))
    }

    @Test fun embedHardStopOnlyWithoutApiBridge() {
        assertTrue(EmbedHost.embedWasPlaying(true, -1))       // B站/Vimeo/…: unknown → assume playing
        assertTrue(EmbedHost.embedWasPlaying(true, 1))        // YT playing
        assertFalse(EmbedHost.embedWasPlaying(true, 2))       // YT paused
        assertFalse(EmbedHost.embedWasPlaying(false, 1))      // not attached
        assertTrue(EmbedHost.needsHardStop(true, -1))
        assertFalse(EmbedHost.needsHardStop(true, 1))         // YT API pause is reliable
        assertFalse(EmbedHost.needsHardStop(false, -1))
    }
}
