package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.ButtonSkinPrefs
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.SkinKind
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.ButtonSkins
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.ScreenHeader
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.me.MeContent
import com.moonlight.feed.ui.me.MeHandlers
import com.moonlight.feed.ui.me.MeStats
import com.moonlight.feed.ui.settings.ButtonSkinEditor
import com.moonlight.feed.ui.settings.ButtonSkinList
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** v4.3: menus follow one palette rule; 按键背景 per-button pictures. Procedural `art:N` stands in for photos / video frames. */
class V43Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot("v43-$name") { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

    private val now = 1_760_000_000_000L
    private val social = DemoSocial.seed(SocialState.fresh(), now)
    private val stats = MeStats(posts = 0, friends = 2, library = 1, favorites = 0, unread = 0)
    private val tabHome = Rect(70f, 2190f, 300f, 2330f)
    private val tabMoments = Rect(640f, 2190f, 840f, 2330f)

    @Composable private fun MePage(menu: com.moonlight.feed.ui.design.MenuSpec?) {
        MoonShell(remember { MoonOverlayState(initialMenu = menu) }, tab = 6, tabBarVisible = true) { MeContent(social.self, stats, MeHandlers()) }
    }

    // ---------------------------------------------------------------- the reported bug: 首页 ring vs 朋友圈 ring on the light 我 page
    @Test fun homeRingOnMeLight() = shot("ring-home-tab-over-me-light", ThemeMode.LIGHT) { MePage(tabMenu(0, tabHome, ThemeMode.LIGHT, false, {}, {})) }
    @Test fun momentsRingOnMeLight() = shot("ring-moments-tab-over-me-light", ThemeMode.LIGHT) { MePage(tabMenu(4, tabMoments, ThemeMode.LIGHT, false, {}, {})) }
    @Test fun homeRingOnMeDark() = shot("ring-home-tab-over-me-dark") { MePage(tabMenu(0, tabHome, ThemeMode.DARK, false, {}, {})) }
    @Test fun momentsRingOnMeDark() = shot("ring-moments-tab-over-me-dark") { MePage(tabMenu(4, tabMoments, ThemeMode.DARK, false, {}, {})) }
    @Test fun homeRingOverFeed() = shot("ring-home-tab-over-feed-light-theme", ThemeMode.LIGHT) { Feed(remember { MoonOverlayState(initialMenu = tabMenu(0, tabHome, ThemeMode.LIGHT, true, {}, {})) }) }

    // ---------------------------------------------------------------- 按键背景
    private val video = MediaEntry(id = "e6", kind = MediaKind.VIDEO, title = "山脊上的月出", localPath = "/x/6", coverUrl = "art:6", author = "我", platform = "local", createdAt = 1_759_900_000_000L)
    @Composable private fun Feed(overlay: MoonOverlayState, play: Boolean = false) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                FeedItem(video, chromeVisible = true, muted = false, playing = false, showPlayButton = play) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(6, Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = true, index = 1, total = 22)
            }
        }
    }

    /** Distinct pictures on different tab-bar and player buttons (each its own skin). */
    private val distinct: ButtonSkinPrefs = listOf(
        "tab.home" to "art:1", "tab.discover" to "art:2", "tab.capture" to "art:3", "tab.moments" to "art:4", "tab.me" to "art:5",
        "player.like" to "art:7", "player.mute" to "art:8", "player.more" to "art:9", "player.play" to "art:10", "player.counter" to "art:11",
    ).fold(ButtonSkinPrefs()) { p, (b, f) -> p.pickPrivate(b, SkinKind.PHOTO, f) }

    @Test fun tabAndPlayerDistinctDark() = shot("skins-tabbar-player-distinct-dark") { ButtonSkins.prefs = distinct; Feed(remember { MoonOverlayState() }, play = true) }
    @Test fun tabAndPlayerDistinctLight() = shot("skins-tabbar-player-distinct-light", ThemeMode.LIGHT) { ButtonSkins.prefs = distinct; MePage(null) }
    @Test fun tabAndPlayerSharedDark() = shot("skins-tabbar-player-shared-image-dark") {
        ButtonSkins.prefs = ButtonSkinPrefs().pickShared(listOf("tab.bar", "tab.capture", "player.like", "player.mute", "player.more", "player.counter"), SkinKind.PHOTO, "art:2")
        Feed(remember { MoonOverlayState() })
    }

    /** Ring with mixed skins: buttons + title have pictures, ··· and the sub-arc follow the theme. */
    private val mixed = ButtonSkinPrefs().pickPrivate("ring.button", SkinKind.PHOTO, "art:4").pickPrivate("ring.title", SkinKind.PHOTO, "art:9")
    private val press = Offset(540f, 1250f)
    @Composable private fun RingOnMe() {
        val spec = tabMenu(4, tabMoments, ThemeMode.LIGHT, false, {}, {}).copy(title = "朋友圈", origin = press, centerHorizontally = false)
        MoonShell(remember { MoonOverlayState(initialMenu = spec).apply { pressOrigin = press; finger = press; fingerDown = false } }, tab = 6, tabBarVisible = true) { MeContent(social.self, stats, MeHandlers()) }
    }
    @Test fun ringMixedLight() = shot("skins-ring-mixed-light", ThemeMode.LIGHT) { ButtonSkins.prefs = mixed; MenuStyle.debugProgress = 1f; RingOnMe() }
    @Test fun ringMixedDark() = shot("skins-ring-mixed-dark") { ButtonSkins.prefs = mixed; MenuStyle.debugProgress = 1f; RingOnMe() }
    @Test fun ringMixedStarMid() = shot("skins-ring-mixed-star-mid-dark") {
        ButtonSkins.prefs = mixed; MenuStyle.debugProgress = 0.5f
        val spec = com.moonlight.feed.ui.settings.ringAnimPreview(com.moonlight.feed.data.RingAnim.STAR, press)
        MoonShell(remember { MoonOverlayState(initialMenu = spec).apply { pressOrigin = press; finger = press; fingerDown = false } }, tab = 6, tabBarVisible = true) { MeContent(social.self, stats, MeHandlers()) }
    }

    // ---------------------------------------------------------------- settings
    private val listPrefs = distinct.pickShared(listOf("ring.button", "moments.compose"), SkinKind.PHOTO, "art:6")
    @Composable private fun SettingsPage(content: @Composable () -> Unit) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ScreenHeader("按键背景")
                Box(Modifier.glassStatic(RoundedCornerShape(24.dp)).padding(14.dp)) { content() }
            }
        }
    }
    @Test fun settingsListLight() = shot("settings-button-skins-list-light", ThemeMode.LIGHT) {
        SettingsPage { ButtonSkinList(listPrefs, setOf("tab.home", "tab.discover", "player.like", "ring.button", "ring.more"), {}, {}, {}, {}, {}) }
    }
    @Test fun settingsListDark() = shot("settings-button-skins-list-dark") {
        SettingsPage { ButtonSkinList(listPrefs, com.moonlight.feed.data.ButtonRegistry.inGroup(com.moonlight.feed.data.ButtonRegistry.G_TAB).map { it.id }.toSet(), {}, {}, {}, {}, {}) }
    }
    @Test fun editorDark() = shot("settings-button-skin-editor-dark") {
        SettingsPage { ButtonSkinEditor("ring.button", listPrefs.withScrim(listPrefs.skinFor("ring.button")!!.id, 0.65f), {}, {}, {}, {}, {}, {}, {}) }
    }
    @Test fun editorLight() = shot("settings-button-skin-editor-light", ThemeMode.LIGHT) {
        SettingsPage { ButtonSkinEditor("tab.home", listPrefs, {}, {}, {}, {}, {}, {}, {}) }
    }

    @After fun reset() { ButtonSkins.prefs = ButtonSkinPrefs(); MenuStyle.debugProgress = null }
}
