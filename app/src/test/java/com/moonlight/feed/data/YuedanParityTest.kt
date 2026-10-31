package com.moonlight.feed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuedanParityTest {
    private val slop = 18f

    @Test fun tapAndLongPressAreExclusive() {
        val held = YuedanGesture.route(TouchOrigin.CONTENT, 0f, 0f, 480L, slop, listBusy = false)
        assertEquals(PointerDecision.LONG_PRESS, held)
        assertEquals(PointerDecision.LONG_PRESS, YuedanGesture.onUp(held))
        val pending = YuedanGesture.route(TouchOrigin.CONTENT, 2f, 1f, 120L, slop, listBusy = false)
        assertEquals(PointerDecision.PENDING, pending)
        assertEquals(PointerDecision.TAP, YuedanGesture.onUp(pending))
    }

    @Test fun quickMoveStaysAScrollAndCancelsTheHold() {
        val flick = YuedanGesture.route(TouchOrigin.CONTENT, 4f, 40f, 90L, slop, listBusy = false)
        assertEquals(PointerDecision.SCROLL, flick)
        assertEquals(PointerDecision.SCROLL, YuedanGesture.onUp(flick))
    }

    @Test fun busyListNeverStartsAHold() {
        val still = YuedanGesture.route(TouchOrigin.CONTENT, 0f, 0f, 900L, slop, listBusy = true)
        assertEquals(PointerDecision.SCROLL, still)
    }

    @Test fun touchOnTheBarDragsHorizontallyAndLongPressesWhenStill() {
        val drag = YuedanGesture.route(TouchOrigin.TAB_BAR, 36f, 4f, 80L, slop, listBusy = false)
        assertEquals(PointerDecision.BAR_DRAG, drag)
        val hold = YuedanGesture.route(TouchOrigin.TAB_BAR, 2f, 2f, 500L, slop, listBusy = false)
        assertEquals(PointerDecision.LONG_PRESS, hold)
        val vertical = YuedanGesture.route(TouchOrigin.TAB_BAR, 3f, 40f, 100L, slop, listBusy = false)
        assertEquals(PointerDecision.SCROLL, vertical)
    }

    @Test fun dragSwitchUsesTheSlotUnderTheFinger() {
        assertEquals(0, YuedanGesture.tabIndexAt(10f, 500f, 5))
        assertEquals(2, YuedanGesture.tabIndexAt(250f, 500f, 5))
        assertEquals(4, YuedanGesture.tabIndexAt(499f, 500f, 5))
        assertEquals(0, YuedanGesture.tabIndexAt(-20f, 500f, 5))
        assertEquals(4, YuedanGesture.tabIndexAt(900f, 500f, 5))
    }

    @Test fun dropletIsOptInAndOffByDefault() {
        assertEquals(HoldPresent.POPOVER, HoldPresent.of(null))
        assertEquals(HoldPresent.POPOVER, HoldPresent.of("nope"))
        assertEquals(HoldPresent.DROPLET, HoldPresent.of("DROPLET"))
        assertNotEquals(HoldPresent.DEFAULT, HoldPresent.DROPLET.name)
        assertEquals(HoldPresent.POPOVER, HoldPresent.of(SettingsRepository.Snapshot().holdPresent))
        assertFalse(SettingsRepository.Snapshot().virtualFriends)
        assertTrue(SettingsRepository.Snapshot().portraitPlayback)
    }

    @Test fun discoverLayoutAndRandomCounts() {
        assertEquals(2, DiscoverLayout.columns(0))
        assertEquals(1, DiscoverLayout.toggle(2))
        assertEquals(2, DiscoverLayout.toggle(1))
        assertEquals(100, DiscoverLayout.percent(1f))
        assertEquals(75, DiscoverLayout.percent(0.1f))
        assertEquals(135, DiscoverLayout.percent(9f))
        assertEquals(220f, DiscoverLayout.cardHeightDp(1, 1f), 0.01f)
        assertEquals(155f * 1.2f, DiscoverLayout.cardHeightDp(2, 1.2f), 0.01f)
        assertEquals(listOf(10, 15, 20), RandomBatch.CHOICES)
        assertEquals(10, RandomBatch.normalize(1))
        assertEquals(15, RandomBatch.normalize(15))
        assertEquals(20, RandomBatch.normalize(20))
        assertEquals(3, RandomBatch.pick(listOf("a", "b", "c"), 10).size)
        assertEquals(10, RandomBatch.pick((1..40).toList(), 10).size)
    }

    @Test fun bubbleStartAvoidsTheNearEdge() {
        assertEquals(0f to -48f, HoldPresent.bubbleStart(200f, 800f, 400f, 900f))
        assertEquals(-48f to 0f, HoldPresent.bubbleStart(320f, 200f, 400f, 900f))
        assertEquals(48f to 0f, HoldPresent.bubbleStart(40f, 200f, 400f, 900f))
    }

    @Test fun virtualIntervalHasAFloorAndCopyDoesNotRequireAKey() {
        assertEquals(15, VirtualCopy.intervalMinutes(0))
        assertEquals(15, VirtualCopy.intervalMinutes(5))
        assertEquals(30, VirtualCopy.intervalMinutes(30))
        val line = VirtualCopy.local("海边", "北岛")
        assertTrue(line.contains("海边"))
        assertTrue(line.contains("北岛"))
        assertTrue(VirtualCopy.local("", "").isNotBlank())
    }

    @Test fun circlesAreTheDefaultDock() {
        assertEquals(TabDock.CIRCLES, TabDock.of(null))
        assertEquals(TabDock.SLIDER, TabDock.of("SLIDER"))
        assertEquals(TabDock.CIRCLES, TabDock.of(SettingsRepository.Snapshot().tabDock))
        assertEquals(2, SettingsRepository.Snapshot().discoverColumns)
    }
}
