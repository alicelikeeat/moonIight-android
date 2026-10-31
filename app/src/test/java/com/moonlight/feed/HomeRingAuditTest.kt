package com.moonlight.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.ring
import com.moonlight.feed.ui.design.sheetGroups
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.0 bug 1: the 首页 tab long-press ring had only 回到顶部/随机一条/重新洗牌 — no 筛选.
 * Audits EVERY home long-press ring: tab bar, counter pill, empty space, and the per-item ring for every media kind.
 */
class HomeRingAuditTest {
    private val tabAnchor = Rect(60f, 2190f, 280f, 2330f)

    private fun items(spec: MenuSpec): List<MenuAction> = spec.ring()
    private fun sheet(spec: MenuSpec): List<MenuAction> = spec.sheetGroups().flatMap { it.second }

    private fun assertHasFilter(name: String, spec: MenuSpec, scope: FeedScope) {
        val f = items(spec).singleOrNull { it.title == "筛选" }
        assertNotNull("$name must contain 筛选", f)
        assertEquals("$name 筛选 choices", listOf("全部", "本地", "在线"), f!!.children!!.map { it.title })
        assertEquals("$name marks current", listOf(scope.label), f.children!!.filter { it.checked }.map { it.title })
    }

    private fun tab0(scope: FeedScope) = tabMenu(0, tabAnchor, ThemeMode.DARK, true, {}, {}, HomeTabState(scope = scope))

    /** v4.0: 筛选 / 随机 / 洗牌 / 回到顶部 are view-level → on the tab / counter / empty rings, never on a per-item ring. */
    @Test fun everyViewRingHasFilterAndItemRingsDont() {
        val viewLevel = setOf("筛选", "随机一条", "重新洗牌", "回到顶部")
        for (scope in FeedScope.entries) {
            assertHasFilter("首页 tab ring", tab0(scope), scope)
            assertHasFilter("counter ring", homeRing(Rect(900f, 120f, 1040f, 200f), scope, false, false, HomeRingHandlers()), scope)
            assertHasFilter("empty-space ring", homeRing(Rect(Offset(540f, 1200f), Offset(540f, 1200f)), scope, true, true, HomeRingHandlers()), scope)
            for (kind in MediaKind.entries) {
                val e = MediaEntry(kind = kind, title = "t", localPath = if (kind == MediaKind.VIDEO || kind == MediaKind.IMAGE) "/f" else null,
                    embedUrl = if (kind == MediaKind.EMBED) "https://e" else null, biliBvid = if (kind == MediaKind.BILI) "BV1xx" else null)
                for (playing in listOf(true, false)) {
                    val spec = feedMenuSpec(e, false, true, 1f, true, playing, FeedMenuHandlers(), 5, scope)
                    val all = (items(spec) + sheet(spec)).map { it.title }
                    assertTrue("item ring $kind has no view-level actions: $all", all.none { it in viewLevel })
                }
            }
        }
    }

    @Test fun tabRingIsFourAndLiftedAboveTheBar() {
        val spec = tab0(FeedScope.ALL)
        assertEquals(listOf("筛选", "随机一条", "重新洗牌", "回到顶部"), items(spec).map { it.title })
        val o = spec.origin!!
        assertTrue("ring centre sits above the tab bar", o.y < tabAnchor.top - tabAnchor.height)
        assertTrue("balanced around the ● button", spec.centerHorizontally)
    }

    @Test fun choosingFromTabRingRoutesToFeed() {
        var went = -1
        var mute = false
        val spec = tabMenu(0, tabAnchor, ThemeMode.DARK, false, { went = it }, {}, HomeTabState(toggleMute = { mute = true }))
        com.moonlight.feed.ui.AppBus.feedScopeRequest.value = null
        items(spec).first { it.title == "筛选" }.children!!.first { it.title == "在线" }.onClick()
        assertEquals(0, went)
        assertEquals(FeedScope.ONLINE, com.moonlight.feed.ui.AppBus.feedScopeRequest.value)
        sheet(spec).first { it.title == "静音" }.onClick()   // 声音 moved behind ···
        assertTrue(mute)
        com.moonlight.feed.ui.AppBus.feedScopeRequest.value = null
    }

    @Test fun otherTabRingsAreBalancedAndLifted() {
        for (i in listOf(2, 4, 6)) {
            val spec = tabMenu(i, tabAnchor, ThemeMode.LIGHT, false, {}, {})
            val n = items(spec).size
            assertTrue("tab $i ring has 2..4 items (got $n)", n in 2..4)
            assertTrue("tab $i lifted", spec.origin!!.y < tabAnchor.top)
            assertTrue("tab $i centred", spec.centerHorizontally)
        }
        val me = items(tabMenu(6, tabAnchor, ThemeMode.LIGHT, false, {}, {})).map { it.title }
        assertTrue(me.containsAll(listOf("库", "设置", "朋友")))
        val moments = items(tabMenu(4, tabAnchor, ThemeMode.LIGHT, false, {}, {}, unread = 3))
        assertEquals("3 未读", moments.first { it.title == "消息" }.subtitle)
        assertTrue(moments.any { it.title == "发动态" })
    }
}
