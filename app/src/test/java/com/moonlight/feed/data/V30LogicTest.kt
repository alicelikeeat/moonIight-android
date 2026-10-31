package com.moonlight.feed.data

import com.moonlight.feed.data.social.MomentAutoplay
import com.moonlight.feed.data.social.VisibleCard
import com.moonlight.feed.ui.design.TAB_SLOTS
import com.moonlight.feed.ui.design.tabBack
import com.moonlight.feed.ui.design.tabOwner
import com.moonlight.feed.ui.design.tabSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V30LogicTest {
    // ---- D: tab bar
    @Test fun fiveSlotsBalancedAroundCapture() {
        assertEquals(listOf(0, 2, -1, 4, 6), TAB_SLOTS)
        assertEquals(2, TAB_SLOTS.indexOf(-1))
    }
    @Test fun subPagesLightUpTheirParent() {
        assertEquals(6, tabOwner(1)); assertEquals(6, tabOwner(3)); assertEquals(4, tabOwner(5))
        assertEquals(tabSlot(6), tabSlot(1)); assertEquals(tabSlot(4), tabSlot(5))
    }
    @Test fun saneBack() {
        assertNull(tabBack(0)); assertEquals(6, tabBack(1)); assertEquals(6, tabBack(3)); assertEquals(4, tabBack(5))
        assertEquals(0, tabBack(2)); assertEquals(0, tabBack(4)); assertEquals(0, tabBack(6))
    }

    // ---- B: moments autoplay
    @Test fun mostVisiblePlayableCardWins() {
        val cards = listOf(
            VisibleCard("a", -300, 500, true),   // 40% visible
            VisibleCard("b", 200, 600, true),    // fully visible
            VisibleCard("c", 820, 600, true),    // partly
            VisibleCard("d", 0, 100, false),     // no video
        )
        assertEquals("b", MomentAutoplay.pick(cards, 0, 1600))
    }
    @Test fun nothingMostlyVisibleNothingPlays() {
        assertNull(MomentAutoplay.pick(listOf(VisibleCard("a", 1300, 600, true)), 0, 1600))   // 50%
        assertEquals("a", MomentAutoplay.pick(listOf(VisibleCard("a", 1200, 600, true)), 0, 1600)) // 66%
    }
    @Test fun tallCardsCountAgainstViewport() {
        assertEquals(1f, MomentAutoplay.visibleFraction(VisibleCard("a", -100, 3000, true), 0, 1600), 0.001f)
    }
    @Test fun tieGoesToUpperCard() {
        assertEquals("x", MomentAutoplay.pick(listOf(VisibleCard("y", 900, 400, true), VisibleCard("x", 100, 400, true)), 0, 1600))
    }
    @Test fun wifiOnlyMode() {
        assertTrue(MomentAutoplay.allowed("ALWAYS", false, false))
        assertTrue(MomentAutoplay.allowed("WIFI", true, false))    // local file, mobile data → fine
        assertFalse(MomentAutoplay.allowed("WIFI", false, false))  // stream on mobile data → no
        assertTrue(MomentAutoplay.allowed("WIFI", false, true))
        assertFalse(MomentAutoplay.allowed("OFF", true, true))
    }

    // ---- C: decorative video budget
    @Test fun budgetCapsConcurrentLoops() {
        val b = VideoBudget(2)
        val k1 = Any(); val k2 = Any(); val k3 = Any()
        assertTrue(b.acquire(k1)); assertTrue(b.acquire(k2)); assertFalse("3rd shows first frame", b.acquire(k3))
        assertTrue("re-acquire is idempotent", b.acquire(k1))
        b.release(k1); assertTrue(b.acquire(k3)); assertEquals(2, b.active)
    }

    // ---- E: home background
    @Test fun homeBgResolves() {
        val ok: (String) -> Boolean = { true }
        assertEquals(HomeBgKind.THEME, HomeBgSpec.resolve(null, null, true, false, ok).kind)       // default
        assertEquals(HomeBgKind.BLACK, HomeBgSpec.resolve("BLACK", "/x", true, false, ok).kind)
        assertNull(HomeBgSpec.resolve("BLACK", "/x", true, false, ok).path)
        assertEquals("/v.mp4", HomeBgSpec.resolve("VIDEO", "/v.mp4", true, false, ok).path)
        assertEquals("deleted file → theme", HomeBgKind.THEME, HomeBgSpec.resolve("PHOTO", "/gone.jpg", true, false) { false }.kind)
        assertEquals(HomeBgKind.THEME, HomeBgSpec.resolve("VIDEO", null, true, false, ok).kind)
        assertEquals(HomeBgKind.THEME, HomeBgSpec.resolve("???", null, true, false, ok).kind)
    }
    @Test fun otherPagesOnlyWhenOptedInAndNotTheme() {
        assertFalse(HomeBgSpec(HomeBgKind.THEME, null, true, true).appliesToOtherPages())
        assertFalse(HomeBgSpec(HomeBgKind.BLACK, null, true, false).appliesToOtherPages())
        assertTrue(HomeBgSpec(HomeBgKind.VIDEO, "/v", true, true).appliesToOtherPages())
    }
    @Test fun settingsDefaults() {
        val s = SettingsRepository.Snapshot()
        assertEquals("THEME", s.homeBg); assertFalse(s.homeBgAllPages)
        assertEquals("ALWAYS", s.momentsAutoplay); assertTrue(s.loopVideos); assertTrue(s.resumeOnReturn)
    }
}
