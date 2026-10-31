package com.moonlight.feed

import androidx.compose.ui.geometry.Rect
import com.moonlight.feed.data.ButtonRegistry
import com.moonlight.feed.data.ButtonSkinPrefs
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.MediaFocus
import com.moonlight.feed.data.RingInk
import com.moonlight.feed.data.SkinKind
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.settings.ringAnimPreview
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v4.3: one palette rule for menus (over media → MediaGlass, else theme) + 按键背景 (per-button picture / video). */
class V43LogicTest {
    private val anchor = Rect(70f, 2190f, 300f, 2330f)

    // ------------------------------------------------------------------ forceDark rule
    @Test fun homeTabRingFollowsThemeOffTheFeed() {
        assertFalse("首页 tab ring from 我 / 朋友圈", tabMenu(0, anchor, ThemeMode.LIGHT, onFeed = false, {}, {}).forceDark)
        assertTrue("首页 tab ring on the feed", tabMenu(0, anchor, ThemeMode.LIGHT, onFeed = true, {}, {}).forceDark)
        for (i in listOf(2, 4, 6)) {
            assertFalse("tab $i off the feed", tabMenu(i, anchor, ThemeMode.LIGHT, false, {}, {}).forceDark)
            assertTrue("tab $i on the feed", tabMenu(i, anchor, ThemeMode.LIGHT, true, {}, {}).forceDark)
        }
        assertFalse(captureMenu(anchor, false, {}, {}, {}).forceDark); assertTrue(captureMenu(anchor, true, {}, {}, {}).forceDark)
    }

    @Test fun mediaRingsStayDarkAndThemePreviewsDont() {
        assertTrue("feed counter / empty-space ring over the feed", homeRing(anchor, FeedScope.ALL, false, false, HomeRingHandlers()).forceDark)
        assertFalse("empty feed on a theme background", homeRing(anchor, FeedScope.ALL, false, false, HomeRingHandlers(), overMedia = false).forceDark)
        assertTrue("per-item feed ring", com.moonlight.feed.ui.feed.feedMenuSpec(
            com.moonlight.feed.data.MediaEntry(id = "x", kind = com.moonlight.feed.data.MediaKind.VIDEO, title = "t", localPath = "/x", coverUrl = "art:1", author = "", platform = "local"),
            false, true, 1f, true, true, com.moonlight.feed.ui.feed.FeedMenuHandlers(), 5, FeedScope.ALL).forceDark)
        assertFalse("设置 › 试一试 preview", ringAnimPreview(com.moonlight.feed.data.RingAnim.SPIN).forceDark)
        val p = com.moonlight.feed.ui.player.PlayerActions()
        assertTrue("full-screen player", com.moonlight.feed.ui.player.playerHomeMenu(anchor, p).forceDark)
    }

    @Test fun sourceHasNoOtherHardcodedForceDark() {
        // every forceDark = true left in the app is a media surface (feed item ring, full-screen player)
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val hits = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if ("forceDark = true" in l) "${f.name}:${i + 1}" else null } }.toList()
        assertTrue("unexpected: $hits", hits.all { it.startsWith("PlayerChrome.kt") || it.startsWith("FeedScreen.kt") })
        assertEquals("FeedScreen: only the per-item ring", 1, hits.count { it.startsWith("FeedScreen.kt") })
    }

    // ------------------------------------------------------------------ 按键背景
    @Test fun registryIdsAreUniqueAndAllWired() {
        val ids = ButtonRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val src = root.walkTopDown().filter { it.extension == "kt" && it.name != "ButtonSkins.kt" }.joinToString("\n") { it.readText() }
        val used = Regex("""(?:skinId = |rememberButtonSkin\(|to )"([a-z]+\.[a-zA-Z]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        assertEquals("every registered button is wired to the shared skin path", emptySet<String>(), ids.toSet() - used)
        assertEquals("no skin id outside the registry", emptySet<String>(), used.filter { it.substringBefore('.') in setOf("ring", "tab", "player", "moments", "chat", "friends", "page", "discover", "dialog") }.toSet() - ids.toSet())
    }

    @Test fun persistenceRoundTripDropsUnknownButtons() {
        val p = ButtonSkinPrefs()
            .pickPrivate("tab.home", SkinKind.PHOTO, "/s/a.jpg")
            .pickShared(listOf("player.like", "player.more"), SkinKind.VIDEO, "/s/b.mp4")
            .let { it.withFocus(it.skinFor("tab.home")!!.id, MediaFocus(0.3f, 0.6f, 1.4f)).withScrim(it.skinFor("player.like")!!.id, 0.8f) }
        val back = ButtonSkinPrefs.decode(p.encode())
        assertEquals(p, back)
        // a button removed in a later version + a dangling skin id + garbage are ignored
        val raw = org.json.JSONObject(p.encode()).apply {
            getJSONObject("assign").put("old.removed", "s1").put("tab.me", "s99")
            getJSONArray("skins").put(org.json.JSONObject().put("id", "bad"))
        }.toString()
        val d = ButtonSkinPrefs.decode(raw)
        assertEquals(p.assign, d.assign); assertEquals(p.skins, d.skins)
        assertEquals(ButtonSkinPrefs(), ButtonSkinPrefs.decode("not json")); assertEquals(ButtonSkinPrefs(), ButtonSkinPrefs.decode(null))
        assertEquals("ids for unknown buttons are refused on write too", p, p.assign(listOf("nope.nope"), "s1"))
    }

    @Test fun threeButtonsEachWithTheirOwnMedia() {
        var p = ButtonSkinPrefs()
        val files = mapOf("tab.home" to "/s/moon.jpg", "player.like" to "/s/sea.mp4", "ring.button" to "/s/sky.jpg")
        files.forEach { (b, f) -> p = p.pickPrivate(b, if (f.endsWith("mp4")) SkinKind.VIDEO else SkinKind.PHOTO, f) }
        assertEquals(3, p.skins.size)
        files.forEach { (b, f) -> assertEquals(f, p.skinFor(b)!!.path); assertEquals(b, p.skinFor(b)!!.owner) }
        assertEquals(3, files.keys.map { p.skinFor(it)!!.id }.toSet().size)
        assertNull("untouched buttons follow the theme", p.skinFor("tab.me"))
        // changing one never touches the others
        val q = p.pickPrivate("tab.home", SkinKind.PHOTO, "/s/new.jpg")
        assertEquals("/s/new.jpg", q.skinFor("tab.home")!!.path)
        assertEquals("/s/sea.mp4", q.skinFor("player.like")!!.path); assertEquals("/s/sky.jpg", q.skinFor("ring.button")!!.path)
    }

    @Test fun batchSharedVsEach() {
        val ids = listOf("tab.home", "tab.discover", "tab.me")
        val shared = ButtonSkinPrefs().pickShared(ids, SkinKind.PHOTO, "/s/one.jpg")
        assertEquals(1, shared.skins.size); assertEquals(ids.toSet(), shared.usersOf(shared.skins[0].id))
        var each = ButtonSkinPrefs(); ids.forEachIndexed { i, b -> each = each.pickPrivate(b, SkinKind.PHOTO, "/s/$i.jpg") }
        assertEquals(3, each.skins.size); assertEquals(3, ids.map { each.skinFor(it)!!.path }.toSet().size)
    }

    @Test fun resettingOrDeletingNeverDeletesOtherMedia() {
        var p = ButtonSkinPrefs().pickShared(listOf("tab.home", "tab.me"), SkinKind.PHOTO, "/s/a.jpg").pickPrivate("chat.send", SkinKind.VIDEO, "/s/v.mp4")
        val a = p.skinFor("tab.home")!!.id
        p = p.assign(listOf("tab.home"), null)                      // back to theme
        assertEquals(2, p.skins.size); assertEquals("/s/a.jpg", p.skinFor("tab.me")!!.path)
        p = p.deleteSkin(a)                                         // forget the shared one
        assertNull(p.skinFor("tab.me")); assertEquals("/s/v.mp4", p.skinFor("chat.send")!!.path)
        // files are only *offered* for cleanup; the one still used is never among them
        assertEquals(listOf("/s/a.jpg", "/s/x.jpg"), p.unusedFiles(listOf("/s/a.jpg", "/s/v.mp4", "/s/x.jpg")))
    }

    @Test fun atMostFourVideoSkinsPlay() {
        val order = (1..6).map { "/v$it.mp4" }
        val users = order.associateWith { 1 } + ("/v2.mp4" to 0)
        assertEquals(listOf("/v1.mp4", "/v3.mp4", "/v4.mp4", "/v5.mp4"), ButtonSkinPrefs.playingVideos(order, users))
        assertEquals("a slot frees up → the next waiting one plays", listOf("/v3.mp4", "/v4.mp4", "/v5.mp4", "/v6.mp4"),
            ButtonSkinPrefs.playingVideos(order, users + ("/v1.mp4" to 0)))
        assertEquals(4, ButtonSkinPrefs.MAX_PLAYING_VIDEOS)
    }

    @Test fun inkFollowsLuminance() {
        assertTrue(RingInk.forLuminance(0.8f).lightMedia); assertFalse(RingInk.forLuminance(0.2f).lightMedia)
        assertEquals(1f, RingInk.average(intArrayOf(-1)), 1e-3f); assertEquals(0f, RingInk.average(intArrayOf(0xFF000000.toInt())), 1e-3f)
    }
}
