package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaFocus
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.MenuBackdrop
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.FocusEditorContent
import com.moonlight.feed.ui.design.FocusFrames
import com.moonlight.feed.ui.design.FocusRequest
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.me.MeContent
import com.moonlight.feed.ui.me.MeHandlers
import com.moonlight.feed.ui.me.MeStats
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.settings.SettingsContent
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import dev.chrisbanes.haze.haze
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** v3.1 screenshots. All data is fake (示例) with procedural art only — no photos of real people. */
class V31Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    @After fun resetStyle() { MenuStyle.backdrop = MenuBackdrop.BLUR }

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, backdrop: MenuBackdrop = MenuBackdrop.BLUR, content: @Composable () -> Unit) {
        MenuStyle.backdrop = backdrop
        paparazzi.snapshot(name) {
            MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } }
        }
    }

    private val now = 1_760_000_000_000L
    private val social: SocialState = DemoSocial.seed(SocialState.fresh(), now)
    private val stats = MeStats(posts = 1, friends = 5, library = 42, favorites = 7, unread = social.totalUnread)

    private fun entry(id: String, title: String) = MediaEntry(id = id, kind = MediaKind.VIDEO, title = title, localPath = "/x/$id.mp4", coverUrl = "art:3", author = "我", platform = "local")

    /** aspect = w/h of the (fake) video; null = fills the screen (near-screen-ratio portrait under 智能). */
    @Composable
    private fun Feed(overlay: MoonOverlayState, aspect: Float?, seed: Int = 3, favorite: Boolean = false) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            val fh = remember { dev.chrisbanes.haze.HazeState() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().haze(fh)) {
                    FeedItem(entry("v$seed", "示例视频").copy(favorite = favorite), chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            if (aspect == null) ProceduralArt(seed, Modifier.fillMaxSize())
                            else ProceduralArt(seed, Modifier.fillMaxWidth().aspectRatio(aspect))
                        }
                    }
                }
                FeedTopChrome(visible = overlay.liftKey == null, index = 1, total = 22, haze = fh)
            }
        }
    }

    // ---------------------------------------------------------------- 2. home: transparent controls (智能 fit)
    @Test fun homeLandscape() = shot("home-clear-controls-landscape-dark") { Feed(remember { MoonOverlayState() }, 16f / 9f) }
    @Test fun homeLandscapeLight() = shot("home-clear-controls-landscape-light", ThemeMode.LIGHT) { Feed(remember { MoonOverlayState() }, 16f / 9f, seed = 5) }
    @Test fun homePortraitFill() = shot("home-clear-controls-portrait-fill-dark") { Feed(remember { MoonOverlayState() }, null, seed = 6, favorite = true) }
    @Test fun homePortrait916() = shot("home-clear-controls-portrait-9x16-fit-dark") { Feed(remember { MoonOverlayState() }, 9f / 16f, seed = 2) }

    // ---------------------------------------------------------------- 1a. reposition editor
    @Test fun coverEditor() = shot("cover-reposition-editor-dark") {
        FocusEditorContent(FocusRequest("art:7", false, FocusFrames.ME_COVER, "调整封面位置", MediaFocus(0.5f, 0.22f, 1.15f)) { }, IntSize(900, 1200), {}, {})
    }
    @Test fun avatarEditor() = shot("avatar-reposition-editor-dark") {
        FocusEditorContent(FocusRequest("art:2", false, FocusFrames.AVATAR, "调整头像位置", MediaFocus(0.5f, 0.3f, 1.6f)) { }, IntSize(900, 1200), {}, {})
    }

    // ---------------------------------------------------------------- 1a. 我 with a positioned cover
    private val framedSelf = social.self.copy(
        bio = "夜里拍月亮的人 · 示例资料",
        coverPath = "art:7", coverFocus = MediaFocus(0.5f, 0.22f, 1.15f),
        avatarPath = "art:2", avatarFocus = MediaFocus(0.5f, 0.3f, 1.6f),
    )
    @Test fun meCoverDark() = shot("me-positioned-cover-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 6, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { MeContent(framedSelf, stats, MeHandlers()) }
    }
    @Test fun meCoverLight() = shot("me-positioned-cover-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 6, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { MeContent(framedSelf, stats, MeHandlers()) }
    }

    // ---------------------------------------------------------------- 3. 菜单背景 + 视频显示 settings
    @Test fun settingsDark() = shot("settings-menu-backdrop-video-display-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            SettingsContent(SettingsRepository.Snapshot(themeMode = "DARK", menuBackdrop = "CLEAR", videoDisplay = "SMART"), SettingsActions())
        }
    }
    @Test fun settingsLight() = shot("settings-menu-backdrop-video-display-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            SettingsContent(SettingsRepository.Snapshot(themeMode = "LIGHT", menuBackdrop = "LIGHT", videoDisplay = "FIT"), SettingsActions())
        }
    }

    // ---------------------------------------------------------------- 3. rings by backdrop
    private val press = Offset(540f, 1250f)
    @Composable private fun RingOver(seed: Int = 6) =
        Feed(remember { MoonOverlayState(initialMenu = homeRing(Rect(press, press), FeedScope.ALL, true, false, HomeRingHandlers())) }, null, seed)

    @Test fun ringClear() = shot("ring-backdrop-transparent-dark", backdrop = MenuBackdrop.CLEAR) { RingOver() }
    @Test fun ringClearLight() = shot("ring-backdrop-transparent-light", ThemeMode.LIGHT, backdrop = MenuBackdrop.CLEAR) { RingOver(5) }
    @Test fun ringLight() = shot("ring-backdrop-light-blur-dark", backdrop = MenuBackdrop.LIGHT) { RingOver() }
    @Test fun ringBlur() = shot("ring-backdrop-blur-dark", backdrop = MenuBackdrop.BLUR) { RingOver() }
}
