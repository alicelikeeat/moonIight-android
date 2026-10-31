package com.moonlight.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaInfo
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.RingAnimPick
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.RING_MAX
import com.moonlight.feed.data.RingMotion
import com.moonlight.feed.ui.design.ring
import com.moonlight.feed.ui.design.sheetGroups
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.library.LibraryHandlers
import com.moonlight.feed.ui.library.libraryMenuSpec
import com.moonlight.feed.ui.player.PlayerActions
import com.moonlight.feed.ui.player.playerMediaMenu
import com.moonlight.feed.ui.settings.ringAnimPreview
import com.moonlight.feed.ui.social.MomentVideoHandlers
import com.moonlight.feed.ui.social.momentVideoMenuSpec
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** v4.0 interaction language + 长按动画. */
class V40LogicTest {
    private val anchor = Rect(60f, 2190f, 280f, 2330f)
    private fun e(kind: MediaKind) = MediaEntry(kind = kind, title = "t", localPath = if (kind == MediaKind.VIDEO || kind == MediaKind.IMAGE) "/f" else null,
        embedUrl = if (kind == MediaKind.EMBED) "https://e" else null, biliBvid = if (kind == MediaKind.BILI) "BV1" else null)

    /** Every ring context we can build without Android: name → spec. */
    private fun contexts(): Map<String, MenuSpec> = buildMap {
        for (k in MediaKind.entries) for (playing in listOf(true, false)) put("feed $k $playing", feedMenuSpec(e(k), false, true, 1f, true, playing, FeedMenuHandlers(), 5, FeedScope.ALL))
        put("home ring", homeRing(anchor, FeedScope.ALL, false, false, HomeRingHandlers()))
        for (i in 0..6) put("tab $i", tabMenu(i, anchor, ThemeMode.DARK, false, {}, {}))
        put("capture", captureMenu(anchor, true, {}, {}, {}, {}))
        for (k in MediaKind.entries) put("library $k", libraryMenuSpec(e(k), anchor, LibraryHandlers()))
        for (embed in listOf(true, false)) put("player $embed", playerMediaMenu(Offset(500f, 900f), e(MediaKind.VIDEO), false, embed, PlayerActions()))
        put("moments video", momentVideoMenuSpec(anchor, "x", true, true, true, true, true, MomentVideoHandlers()))
        for (st in RingAnim.CHOICES) put("preview $st", ringAnimPreview(st))
    }

    @Test fun everyRingHasAtMostFourAndNoDelete() {
        for ((name, spec) in contexts()) {
            val r = spec.ring()
            assertTrue("$name: ${r.size} items", r.size in 1..RING_MAX)
            assertTrue("$name: destructive on ring", r.none { it.destructive })
            assertTrue("$name: 删除 on ring", r.none { it.title.contains("删除") })
        }
    }

    @Test fun deleteOnlyInTheSheetsDangerSection() {
        for ((name, spec) in contexts()) {
            val groups = spec.sheetGroups()
            val danger = groups.filter { g -> g.second.any { it.destructive } }
            if (danger.isNotEmpty()) {
                assertEquals("$name: one 危险 section", 1, danger.size)
                assertEquals("$name: 危险 is last", groups.last(), danger.single())
                assertEquals("危险", danger.single().first)
            }
        }
        // the actions that used to sit on rings still exist — behind ···
        val lib = libraryMenuSpec(e(MediaKind.VIDEO), anchor, LibraryHandlers()).sheetGroups().flatMap { it.second }.map { it.title }
        assertTrue(lib.containsAll(listOf("播放", "从首页隐藏", "删除")))
    }

    @Test fun suggestedRings() {
        fun t(s: MenuSpec) = s.ring().map { it.title }
        assertEquals(listOf("拍照", "录像", "导入", "发朋友圈"), t(captureMenu(anchor, true, {}, {}, {}, {})))
        assertEquals(listOf("收藏", "发朋友圈", "分享·保存", "多选"), t(libraryMenuSpec(e(MediaKind.IMAGE), anchor, LibraryHandlers())))
        assertEquals(listOf("暂停", "进入播放页面", "打开声音", "保存"), t(momentVideoMenuSpec(anchor, null, true, true, true, true, true, MomentVideoHandlers())))
        assertEquals(listOf("收藏", "静音", "分享·保存", "随机一条"), t(playerMediaMenu(Offset.Zero, e(MediaKind.VIDEO), false, false, PlayerActions())))
    }

    // ---- 长按动画 (v4.1 styles)
    @Test fun ringAnimSettingPersistsAndMigrates() {
        assertEquals("SPIN", com.moonlight.feed.data.SettingsRepository.Snapshot().ringAnim)
        assertEquals(listOf("旋转", "荷花", "星轨", "月光", "随机"), com.moonlight.feed.ui.settings.RING_ANIM_LABELS)
        for (st in RingAnim.CHOICES) assertEquals(st.name, RingAnimPick.normalize(st.name))   // round-trips
        assertEquals("RANDOM", RingAnimPick.normalize("RANDOM"))
        // v4.0 values migrate: 模糊 → 星轨, 破碎 → 荷花, 月光表盘 → 月光; v4.1 坠落 → 星轨
        assertEquals("STAR", RingAnimPick.normalize("BLUR"))
        assertEquals("STAR", RingAnimPick.normalize("FALL"))
        assertEquals("LOTUS", RingAnimPick.normalize("SHATTER"))
        assertEquals("MOON", RingAnimPick.normalize("MOON"))
        assertEquals("SPIN", RingAnimPick.normalize("FADE"))          // reduce-motion style is not storable
        assertEquals("SPIN", RingAnimPick.normalize("nonsense"))
        assertEquals("SPIN", RingAnimPick.normalize(null))
        assertEquals(RingAnim.CHOICES.toSet(), RingAnimPick.parseSet(""))
        assertEquals(setOf(RingAnim.STAR, RingAnim.LOTUS, RingAnim.MOON), RingAnimPick.parseSet("BLUR,SHATTER,MOON"))
        assertEquals("LOTUS,STAR,MOON", RingAnimPick.encodeSet(RingAnimPick.parseSet("BLUR, SHATTER ,MOON")))
        val set = setOf(RingAnim.MOON, RingAnim.LOTUS)
        assertEquals(set, RingAnimPick.parseSet(RingAnimPick.encodeSet(set)))
    }

    @Test fun randomPicksOnlyAmongEnabledStyles() {
        val enabled = setOf(RingAnim.LOTUS, RingAnim.MOON)
        val rnd = Random(42)
        assertEquals(enabled, (1..400).map { RingAnimPick.pick("RANDOM", enabled, 1f, rnd) }.toSet())
        assertEquals(RingAnim.CHOICES.toSet(), (1..400).map { RingAnimPick.pick("RANDOM", RingAnim.CHOICES.toSet(), 1f, rnd) }.toSet())
        assertEquals(RingAnim.STAR, RingAnimPick.pick("STAR", enabled, 1f, rnd))     // fixed style ignores the pool
        assertEquals(RingAnim.STAR, RingAnimPick.pick("FALL", enabled, 1f, rnd))     // legacy value still resolves
        // the 试一试 row offers every style plus 随机
        assertEquals(listOf("旋转", "荷花", "星轨", "月光", "随机"), com.moonlight.feed.ui.settings.ringAnimPreviewChips().map { it.first })
        assertEquals(null, com.moonlight.feed.ui.settings.ringAnimPreviewChips().last().second)
    }

    @Test fun reduceMotionFallsBackToFade() {
        for (v in listOf("SPIN", "LOTUS", "STAR", "FALL", "MOON", "RANDOM", "BLUR")) assertEquals(RingAnim.FADE, RingAnimPick.pick(v, RingAnim.CHOICES.toSet(), 0f))
        assertEquals(RingAnim.MOON, RingAnimPick.pick("MOON", RingAnim.CHOICES.toSet(), 0.5f))
    }

    @Test fun previewForcesItsStyle() {
        for (st in RingAnim.CHOICES) assertEquals(st, ringAnimPreview(st).anim)
    }

    @Test fun lotusUnfurlsInnerThenOuterAndRevealsTheButton() {
        assertEquals(0f, RingMotion.lotusReveal(0f), 1e-4f); assertEquals(1f, RingMotion.lotusReveal(1f), 1e-4f)
        assertEquals(0f, RingMotion.lotusReveal(0.2f), 1e-4f)                                        // still a bud
        assertTrue(RingMotion.lotusInner(0.3f) > RingMotion.lotusOuter(0.3f))                       // inner layer first
        assertEquals(1f, RingMotion.lotusInner(1f), 1e-4f); assertEquals(1f, RingMotion.lotusOuter(1f), 1e-4f)
        assertTrue("petals overshoot a little", (0..100).maxOf { RingMotion.lotusInner(it / 100f) } in 1.005f..1.12f)
        assertEquals(1f, RingMotion.lotusSpread(0.5f), 1e-4f)                                       // blooms out of the finger first
    }

    @Test fun moonDarkensBeforeTheLightArrives() {
        assertEquals(0f, RingMotion.moonDark(0f), 1e-4f); assertEquals(1f, RingMotion.moonDark(0.6f), 1e-4f)
        assertTrue(RingMotion.moonDark(0.2f) > RingMotion.moonGlow(0.2f))
        assertEquals(1f, RingMotion.moonGlow(1f), 1e-4f)
    }

    @Test fun infoCardTags() {
        assertEquals(listOf("视频", "本地"), MediaInfo.tags(e(MediaKind.VIDEO)))
        assertTrue(MediaInfo.tags(e(MediaKind.IMAGE).copy(favorite = true)).contains("已收藏"))
        assertFalse(MediaInfo.tags(e(MediaKind.EMBED)).contains("本地"))
    }
}
