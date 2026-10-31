package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.ring
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.settings.GestureGuide
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.social.MomentHandlers
import com.moonlight.feed.ui.social.MomentVideoHandlers
import com.moonlight.feed.ui.social.MomentsContent
import com.moonlight.feed.ui.social.momentVideoMenuSpec
import org.junit.Rule
import org.junit.Test

/** v3.2: rings stay open after the long-press is released; selection is a separate tap. Fake procedural art only. */
class V32Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

    private val video = MediaEntry(id = "v1", kind = MediaKind.VIDEO, title = "示例视频", localPath = "/x/v1.mp4", coverUrl = "art:6", author = "我", platform = "local")
    private val press = Offset(540f, 1250f)

    /** The state after: long-press → ring opens → finger lifted (released over an item, nothing selected). */
    private fun released(spec: MenuSpec) = MoonOverlayState(initialMenu = spec).apply {
        pressOrigin = press; finger = press; fingerDown = false
    }

    @Composable
    private fun Feed(overlay: MoonOverlayState) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                FeedItem(video, chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(6, Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = true, index = 1, total = 22)
            }
        }
    }

    private val itemSpec = feedMenuSpec(video, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.ALL).copy(origin = press)

    @Test fun itemRingAfterRelease() = shot("ring-open-after-release-item-dark") { Feed(remember { released(itemSpec) }) }
    @Test fun homeRingAfterReleaseLight() = shot("ring-open-after-release-home-light", ThemeMode.LIGHT) {
        Feed(remember { released(homeRing(Rect(press, press), FeedScope.ALL, false, false, HomeRingHandlers(), origin = press)) })
    }
    @Test fun subChoicesAfterTap() = shot("ring-tap-speed-subchoices-dark") {
        // v4.0: tap 倍速 on the released ring → its choices fan out on a secondary arc; a second tap picks one
        MenuStyle.debugProgress = 1f   // chips spring in; capture them settled
        val i = itemSpec.ring().indexOfFirst { it.title == "倍速" }
        check(i >= 0)
        Feed(remember { released(itemSpec).apply { subIndex = i } })
    }
    @Test fun guide() = shot("gesture-guide-tap-to-select-dark") {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color(0xFF0B0D14)).statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) { GestureGuide() }
    }

    // ---------------------------------------------------------------- settings: 长按菜单选择方式 / 应用图标 / 首页背景
    @Test fun settingsAppearance() = shot("settings-menu-select-app-icon-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            com.moonlight.feed.ui.settings.SettingsContent(com.moonlight.feed.data.SettingsRepository.Snapshot(themeMode = "DARK", menuSelect = "TAP"), com.moonlight.feed.ui.settings.SettingsActions())
        }
    }
    @Test fun settingsAppearanceDragLight() = shot("settings-menu-select-drag-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            com.moonlight.feed.ui.settings.SettingsContent(com.moonlight.feed.data.SettingsRepository.Snapshot(themeMode = "LIGHT", menuSelect = "DRAG"), com.moonlight.feed.ui.settings.SettingsActions(), appIcon = com.moonlight.feed.data.AppIcon.CLASSIC)
        }
    }
    private val savedBg = com.moonlight.feed.data.SettingsRepository.Snapshot(
        themeMode = "DARK", homeBg = "THEME", homeBgPhoto = "art:5", homeBgVideo = "art:2", homeBgPhotoFocus = "0.5,0.3,1.2",
    )
    @Test fun homeBgSavedDark() = shot("settings-home-bg-saved-thumbnails-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState())) {
                com.moonlight.feed.ui.design.ScreenHeader("设置")
                com.moonlight.feed.ui.settings.HomeBackgroundGroup(savedBg, com.moonlight.feed.ui.settings.SettingsActions())
                com.moonlight.feed.ui.settings.HomeBackgroundGroup(savedBg.copy(homeBg = "PHOTO", homeBgPhotoDim = false), com.moonlight.feed.ui.settings.SettingsActions())
            }
        }
    }
    @Test fun homeBgSavedLight() = shot("settings-home-bg-saved-thumbnails-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState())) {
                com.moonlight.feed.ui.design.ScreenHeader("设置")
                com.moonlight.feed.ui.settings.HomeBackgroundGroup(savedBg.copy(themeMode = "LIGHT", homeBg = "VIDEO", homeBgPhoto = null), com.moonlight.feed.ui.settings.SettingsActions())
            }
        }
    }
    @Test fun dragGuide() = shot("gesture-guide-drag-mode-dark") {
        MenuStyle.select = com.moonlight.feed.data.MenuSelect.DRAG
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color(0xFF0B0D14)).statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) { GestureGuide() }
    }
    // ---- 朋友圈 video long-press ring (tap mode, after release)
    private val now = 1_760_000_000_000L
    private val social: SocialState = DemoSocial.seed(SocialState.fresh(), now).let { st ->
        val (v, rest) = st.moments.partition { m -> m.attachments.size == 1 && m.attachments[0].kind == AttachmentKind.VIDEO }
        st.copy(moments = v.take(1).map { it.copy(createdAt = now - 60_000) } + rest)
    }
    @Composable private fun MomentsRing(mode: ThemeMode) {
        val m = social.moments.first()
        val origin = Offset(516f, 1230f) // over the single video tile of the top card
        val spec = momentVideoMenuSpec(Rect(origin, 1f), social.contact(m.authorId)?.name, playing = true, muted = true,
            canPlayInline = true, canOpen = true, canSave = true, h = MomentVideoHandlers()).copy(origin = origin)
        val overlay = remember { MoonOverlayState(initialMenu = spec).apply { pressOrigin = origin; finger = origin; fingerDown = false } }
        MoonShell(overlay, tab = 4, tabBarVisible = true) { MomentsContent(social, social.selfId, MomentHandlers(), now) }
    }
    @Test fun momentsVideoRing() = shot("moments-video-ring-dark") { MomentsRing(ThemeMode.DARK) }
    @Test fun momentsVideoRingLight() = shot("moments-video-ring-light", ThemeMode.LIGHT) { MomentsRing(ThemeMode.LIGHT) }
    @Test fun momentsVideoNoRing() = shot("moments-video-card-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) { MomentsContent(social, social.selfId, MomentHandlers(), now) }
    }

    @org.junit.After fun resetMode() { MenuStyle.select = com.moonlight.feed.data.MenuSelect.TAP; MenuStyle.debugProgress = null }
}
