package com.moonlight.feed.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.settings.GESTURE_GUIDE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3.2: long-press only opens a menu; the release never selects; a separate tap does. */
class RingTapTest {
    private val calls = mutableListOf<String>()

    @org.junit.Before fun tapMode() { MenuStyle.select = com.moonlight.feed.data.MenuSelect.TAP }
    @org.junit.After fun reset() { MenuStyle.select = com.moonlight.feed.data.MenuSelect.TAP }
    private fun a(t: String, children: List<MenuAction>? = null) = MenuAction(t, children = children) { calls += t }

    /** Long-press open: the finger is down when the menu is shown (root tracker already saw the down). */
    private fun openByLongPress(spec: MenuSpec): MoonOverlayState = MoonOverlayState().apply {
        pressOrigin = Offset(540f, 1200f); finger = pressOrigin; fingerDown = true
        assertFalse("the opening down itself isn't consumed (nothing held yet)", onPointer(true))
        showMenu(spec)
    }

    /** What the root tracker does on the up: fingerDown first, then the gate. */
    private fun MoonOverlayState.release(): Boolean { fingerDown = false; return onPointer(false) }

    private fun ring(vararg items: MenuAction) = MenuSpec(anchor = Rect.Zero, actions = emptyList(), radial = items.toList())

    @Test fun releaseAfterOpenSelectsNothingAndRingStaysOpen() {
        val spec = ring(a("收藏"), a("保存"), a("删除"))
        val s = openByLongPress(spec)
        assertTrue(s.holdingOpen)
        // finger slides over items while held: every move is swallowed (page underneath gets nothing)
        repeat(5) { assertTrue(s.onPointer(anyPressed = true)) }
        // a tap attempt while the opening finger is still down is ignored
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.IGNORED, s.tapRingItem(spec, items, rest, 1))
        // release (finger over 保存): the up itself is consumed, nothing runs, the ring stays
        assertTrue("the release must be consumed", s.release())
        assertFalse(s.holdingOpen)
        assertTrue(calls.isEmpty())
        assertEquals(spec, s.menu)
        // later events are not consumed any more (taps reach the ring)
        assertFalse(s.onPointer(true)); assertFalse(s.release())
    }

    @Test fun tapAfterReleaseSelectsAndCloses() {
        val spec = ring(a("收藏"), a("保存"), a("删除"))
        val s = openByLongPress(spec)
        s.release()
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.SELECTED, s.tapRingItem(spec, items, rest, 1))
        assertEquals(listOf("保存"), calls)
        assertNull(s.menu)
        // a second tap on a closing ring does nothing
        assertEquals(RingTap.IGNORED, s.tapRingItem(spec, items, rest, 0))
        assertEquals(listOf("保存"), calls)
    }

    @Test fun tapOutsideClosesButNotDuringTheHold() {
        val spec = ring(a("收藏"), a("保存"))
        val s = openByLongPress(spec)
        assertFalse(s.tapOutside())                 // still holding: the release isn't a "tap outside" either
        assertNotNull(s.menu)
        s.release()
        assertTrue(s.tapOutside())
        assertNull(s.menu)
        assertTrue(calls.isEmpty())
    }

    @Test fun menuOpenedByTapIsLiveImmediately() {
        val spec = ring(a("一"), a("二"))
        val s = MoonOverlayState().apply { fingerDown = false; showMenu(spec) }   // e.g. ⋯ button click (fires on up)
        assertFalse(s.holdingOpen)
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.SELECTED, s.tapRingItem(spec, items, rest, 0))
        assertEquals(listOf("一"), calls)
    }

    @Test fun subRingFlowOnRealHomeRing() {
        var picked: FeedScope? = null
        val spec = homeRing(Rect(500f, 1200f, 580f, 1280f), FeedScope.ALL, muted = false, shuffle = false, h = HomeRingHandlers(scope = { picked = it }))
        val s = openByLongPress(spec)
        s.release()                           // release over nothing / anything: no-op
        assertNull(picked)
        val (items, rest) = spec.ringItems()
        val filter = items.indexOfFirst { it.title.startsWith("筛选") }
        assertTrue(filter >= 0)
        // tap 筛选 → its choices fan out on the secondary arc (same ring stays up, nothing chosen yet)
        assertEquals(RingTap.SUBMENU, s.tapRingItem(spec, items, rest, filter))
        assertEquals(spec, s.menu)
        assertEquals(filter, s.subIndex)
        val choices = items[filter].children!!
        assertTrue(choices.size >= 3)
        assertNull(picked)
        // tap a chip → selected + closed
        val local = choices.first { it.title.contains("本地") }
        assertEquals(RingTap.SELECTED, s.tapSubItem(local))
        assertEquals(FeedScope.LOCAL, picked)
        assertNull(s.menu)
    }

    @Test fun subMenuOpenedByTapIsNotHeld() {
        val spec = ring(a("倍速", children = listOf(a("1.0x"), a("2.0x"))), a("保存"))
        val s = openByLongPress(spec)
        s.release()
        // even if a finger were reported down, a tap-opened sub-menu never holds
        s.fingerDown = true
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.SUBMENU, s.tapRingItem(spec, items, rest, 0))
        assertFalse(s.holdingOpen)
        assertEquals(0, s.subIndex)
        // tapping the parent again folds the arc
        assertEquals(RingTap.SUBMENU, s.tapRingItem(spec, items, rest, 0)); assertNull(s.subIndex)
        s.tapRingItem(spec, items, rest, 0)
        assertEquals(RingTap.SELECTED, s.tapSubItem(items[0].children!!.first { it.title == "2.0x" }))
        assertEquals(listOf("2.0x"), calls)
    }

    @Test fun longChildListsStillOpenAsAList() {
        val spec = ring(a("改发言人", children = (1..9).map { a("人$it") }))
        val s = openByLongPress(spec); s.release()
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.SUBMENU, s.tapRingItem(spec, items, rest, 0))
        assertTrue(s.menu!!.list)
        assertEquals(RingTap.SELECTED, s.tapMenuAction(s.menu!!.actions.flatten().last()))
        assertEquals(listOf("人9"), calls)
    }

    @Test fun ringIsAtMostFourAndTheRestIsInTheSheet() {
        val spec = MenuSpec(anchor = Rect.Zero, actions = listOf((1..10).map { a("项$it") } + MenuAction("删除", destructive = true) { calls += "删除" }))
        val s = openByLongPress(spec)
        s.release()
        val (items, rest) = spec.ringItems()
        assertEquals(listOf("项1", "项2", "项3", "项4"), items.map { it.title })
        assertEquals(listOf("项5", "项6", "项7", "项8", "项9", "项10"), spec.sheetGroups().first().second.map { it.title })
        assertEquals("危险" to listOf("删除"), spec.sheetGroups().last().let { it.first to it.second.map { x -> x.title } })
        assertTrue(rest.isNotEmpty())
        // ··· during the hold is ignored; afterwards it swaps the ring for the sheet
        val held = openByLongPress(spec)
        assertEquals(RingTap.IGNORED, held.openSheet(spec))
        assertEquals(RingTap.SHEET, s.openSheet(spec))
        assertNull(s.menu); assertNotNull(s.sheet)
        assertTrue(calls.isEmpty())
    }

    @Test fun deleteNeverOnTheRingEvenIfListedFirst() {
        val spec = ring(MenuAction("删除", destructive = true), a("复制"))
        assertEquals(listOf("复制"), spec.ring().map { it.title })
        assertEquals(listOf("危险"), spec.sheetGroups().map { it.first })
    }

    @Test fun listMenusOpenedByLongPressAlsoSwallowTheRelease() {
        val spec = MenuSpec(anchor = Rect(0f, 0f, 100f, 100f), actions = listOf(listOf(a("复制"), a("删除"))), list = true)
        val s = openByLongPress(spec)
        assertTrue(s.holdingOpen)
        assertEquals(RingTap.IGNORED, s.tapMenuAction(spec.actions[0][1]))
        assertTrue(s.release())
        assertEquals(RingTap.SELECTED, s.tapMenuAction(spec.actions[0][0]))
        assertEquals(listOf("复制"), calls)
    }

    // ---------------------------------------------------------------- 拖动 (v3.1 hold-and-slide), kept as an option
    @Test fun dragModeReleaseOnItemSelects() {
        MenuStyle.select = com.moonlight.feed.data.MenuSelect.DRAG
        val spec = ring(a("收藏"), a("保存"), a("删除"))
        val s = openByLongPress(spec)
        assertTrue(s.onPointer(true))                    // slide: still swallowed by the page
        assertTrue(s.release())                          // the up is consumed …
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.SELECTED, s.releaseRingItem(spec, items, rest, 1))   // … and released over 保存 → selects
        assertEquals(listOf("保存"), calls)
        assertNull(s.menu)
    }
    @Test fun dragModeReleaseOverNothingKeepsRingForTap() {
        MenuStyle.select = com.moonlight.feed.data.MenuSelect.DRAG
        val spec = ring(a("收藏"), a("保存"))
        val s = openByLongPress(spec)
        s.release()
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.IGNORED, s.releaseRingItem(spec, items, rest, null))
        assertEquals(spec, s.menu)
        assertEquals(RingTap.SELECTED, s.tapRingItem(spec, items, rest, 0))
        assertEquals(listOf("收藏"), calls)
    }
    @Test fun tapModeReleaseOverItemIsIgnored() {
        val spec = ring(a("收藏"), a("保存"))
        val s = openByLongPress(spec)
        s.release()
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.IGNORED, s.releaseRingItem(spec, items, rest, 1))
        assertTrue(calls.isEmpty()); assertEquals(spec, s.menu)
    }
    @Test fun dragModeSubRing() {
        MenuStyle.select = com.moonlight.feed.data.MenuSelect.DRAG
        val spec = ring(a("倍速", children = listOf(a("1.0x"), a("2.0x"))), a("保存"))
        val s = openByLongPress(spec)
        s.release()
        val (items, rest) = spec.ringItems()
        assertEquals(RingTap.SUBMENU, s.releaseRingItem(spec, items, rest, 0))
        assertFalse(s.holdingOpen)
        assertTrue(calls.isEmpty())
        assertEquals(0, s.subIndex)
        assertEquals(RingTap.SELECTED, s.tapSubItem(items[0].children!!.first { it.title == "1.0x" }))
        assertEquals(listOf("1.0x"), calls)
    }
    @Test fun guideFollowsMode() {
        val tap = com.moonlight.feed.ui.settings.gestureGuide(com.moonlight.feed.data.MenuSelect.TAP).joinToString { it.first + it.second }
        val drag = com.moonlight.feed.ui.settings.gestureGuide(com.moonlight.feed.data.MenuSelect.DRAG).joinToString { it.first + it.second }
        assertTrue(drag.contains("按住滑动") && drag.contains("松开即执行") && drag.contains("拖动（当前）"))
        assertFalse(tap.contains("按住滑动")); assertTrue(tap.contains("点击（当前）"))
        assertTrue(tap.contains("点按") && drag.contains("双击"))   // shared rows remain
    }
    @Test fun selectModeDefaultsAndParsing() {
        assertEquals(com.moonlight.feed.data.MenuSelect.TAP, com.moonlight.feed.data.MenuSelect.of(null))
        assertEquals(com.moonlight.feed.data.MenuSelect.DRAG, com.moonlight.feed.data.MenuSelect.of("DRAG"))
        assertEquals(listOf("点击", "拖动"), com.moonlight.feed.data.MenuSelect.entries.map { it.label })
        assertEquals("TAP", com.moonlight.feed.data.SettingsRepository.Snapshot().menuSelect)
    }

    @Test fun gestureGuideDescribesTapToSelect() {
        val text = GESTURE_GUIDE.joinToString("\n") { "${it.first}: ${it.second}" }
        assertFalse(text.contains("按住滑动")); assertFalse(text.contains("后松开即执行"))
        assertTrue(text.contains("松开手指不会选中"))
        assertTrue(text.contains("点按圆环里的按钮执行"))
        assertTrue(text.contains("点空白处或按返回键关闭"))
    }
}
