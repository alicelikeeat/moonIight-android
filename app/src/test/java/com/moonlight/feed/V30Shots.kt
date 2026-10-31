package com.moonlight.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.HomeBackdrop
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.ScreenHeader
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.me.MeContent
import com.moonlight.feed.ui.me.MeHandlers
import com.moonlight.feed.ui.me.MeStats
import com.moonlight.feed.ui.settings.HomeBackgroundGroup
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.social.ChatListContent
import com.moonlight.feed.ui.social.ChatListHandlers
import com.moonlight.feed.ui.social.MomentHandlers
import com.moonlight.feed.ui.social.MomentsContent
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.ThemeMode
import dev.chrisbanes.haze.haze
import org.junit.Rule
import org.junit.Test

/** v3.0 screenshots. All data is fake (示例) with procedural art only. */
class V30Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, bg: HomeBgSpec = HomeBgSpec(HomeBgKind.THEME, null, true, false), content: @Composable () -> Unit) {
        paparazzi.snapshot(name) { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides bg) { content() } } }
    }

    private val now = 1_760_000_000_000L
    private val social: SocialState = DemoSocial.seed(SocialState.fresh(), now).let { s ->
        s.copy(contacts = s.contacts.map { if (it.isSelf) it.copy(bio = "夜里拍月亮的人 · 示例资料") else it })
    }
    private val stats = MeStats(posts = 1, friends = 5, library = 42, favorites = 7, unread = social.totalUnread)

    // tab-bar geometry on a Pixel 6 (px): bar ≈ y 2190..2330
    private val tabHome = Rect(70f, 2190f, 300f, 2330f)
    private val tabMoments = Rect(640f, 2190f, 840f, 2330f)
    private val tabMe = Rect(840f, 2190f, 1010f, 2330f)

    // ---------------------------------------------------------------- D. tab bar + 我
    @Test fun meDark() = shot("me-tabbar-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 6, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { MeContent(social.self, stats, MeHandlers()) }
    }
    @Test fun meLight() = shot("me-tabbar-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 6, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { MeContent(social.self, stats, MeHandlers()) }
    }
    @Test fun libraryUnderMe() = shot("tabbar-library-under-me-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 1, tabBarVisible = true, badges = mapOf(4 to stats.unread)) {
            com.moonlight.feed.ui.library.LibraryContent(items = sample(15), filter = "全部", onFilter = {}, selectionMode = false, selectedIds = emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> })
        }
    }

    // ---------------------------------------------------------------- A. 朋友圈 with 踩 + B. chat badge
    @Test fun momentsTopLight() = shot("moments-chat-badge-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { MomentsContent(social, social.selfId, MomentHandlers(), now) }
    }
    @Test fun momentsDislikeDark() = shot("moments-dislike-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true, badges = mapOf(4 to stats.unread)) {
            MomentsContent(social, social.selfId, MomentHandlers(), now, listState = rememberLazyListState(2))
        }
    }
    @Test fun momentsDislikeLight() = shot("moments-dislike-light", ThemeMode.LIGHT) {
        val mine = social.copy(moments = social.moments.map { m -> if (m.text.startsWith("夜跑")) m.copy(dislikes = m.dislikes + social.selfId) else m })
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true, badges = mapOf(4 to stats.unread)) {
            MomentsContent(mine, mine.selfId, MomentHandlers(), now, listState = rememberLazyListState(2))
        }
    }
    @Test fun dislikeWho() = shot("moments-dislike-who-dark") {
        val m = social.moments.first { it.dislikes.isNotEmpty() }
        val people = listOf(social.self) + social.friends
        val spec = MenuSpec(anchor = Rect(700f, 900f, 820f, 960f), title = "谁踩了", list = true, actions = listOf(people.map { p ->
            MenuAction(if (p.isSelf) "我" else p.name, Icons.Outlined.ThumbDown, checked = p.id in m.dislikes, subtitle = if (p.id in m.likes) "已赞 · 踩会取消赞" else null)
        }))
        MoonShell(remember { MoonOverlayState(initialMenu = spec) }, tab = 4, tabBarVisible = true) {
            MomentsContent(social, social.selfId, MomentHandlers(), now, listState = rememberLazyListState(2))
        }
    }

    // ---------------------------------------------------------------- 朋友圈 ↔ 消息
    @Test fun swipeToChats() = shot("moments-to-chats-swipe-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true, badges = mapOf(4 to stats.unread)) {
            val pager = rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0.42f) { 2 }
            HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
                if (p == 0) MomentsContent(social, social.selfId, MomentHandlers(), now)
                else ChatListContent(social, ChatListHandlers(onBack = {}), now)
            }
        }
    }
    @Test fun chatsLight() = shot("chats-unread-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { ChatListContent(social, ChatListHandlers(onBack = {}), now) }
    }
    @Test fun chatsDark() = shot("chats-unread-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true, badges = mapOf(4 to stats.unread)) { ChatListContent(social, ChatListHandlers(onBack = {}), now) }
    }

    // ---------------------------------------------------------------- E. home background
    @Test fun homeBgSettingsDark() = shot("settings-home-bg-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ScreenHeader("设置")
                HomeBackgroundGroup(SettingsRepository.Snapshot(themeMode = "DARK", homeBg = "VIDEO", homeBgPath = "/data/user/0/com.moonlight.feed/files/avatars/moon-loop.mp4", homeBgDim = true), SettingsActions())
                com.moonlight.feed.ui.settings.SocialSettingsGroups(SettingsRepository.Snapshot(themeMode = "DARK"), SettingsActions(), social)
            }
        }
    }
    @Test fun homeBgSettingsLight() = shot("settings-home-bg-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ScreenHeader("设置")
                HomeBackgroundGroup(SettingsRepository.Snapshot(themeMode = "LIGHT"), SettingsActions())
            }
        }
    }

    private val wide = MediaEntry(id = "w1", kind = MediaKind.VIDEO, title = "湖边延时 · 示例", localPath = "/x/w.mp4", coverUrl = "art:3", author = "我", platform = "local")

    @Composable
    private fun Feed(overlay: MoonOverlayState, scopeLabel: String? = null) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            val fh = remember { dev.chrisbanes.haze.HazeState() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().haze(fh)) {
                    FeedItem(wide, chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProceduralArt(3, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                    }
                }
                FeedTopChrome(visible = overlay.liftKey == null, index = 3, total = 12, scopeLabel = scopeLabel, haze = fh)
            }
        }
    }
    @Test fun homeBgPhoto() = shot("home-bg-photo-letterbox-dark", bg = HomeBgSpec(HomeBgKind.PHOTO, "art:5", true, false)) { Feed(remember { MoonOverlayState() }) }
    @Test fun homeBgTheme() = shot("home-bg-theme-letterbox-light", ThemeMode.LIGHT) { Feed(remember { MoonOverlayState() }) }
    @Test fun homeBgEmptyTheme() = shot("home-bg-theme-empty-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MoonEmptyState("没有在线内容", "嵌入播放（B 站、YouTube 等）与网络视频会出现在这里", action = "显示全部", onAction = {}) }
        }
    }

    // ---------------------------------------------------------------- rings (bug 1 + tab long-press)
    @Test fun ringTabHome() = shot("ring-tab-home-filter-dark") {
        Feed(remember { MoonOverlayState(initialMenu = tabMenu(0, tabHome, ThemeMode.DARK, true, {}, {}, HomeTabState(scope = FeedScope.LOCAL, shuffle = true))) }, "本地")
    }
    @Test fun ringTabHomeLight() = shot("ring-tab-home-filter-light", ThemeMode.LIGHT) {
        Feed(remember { MoonOverlayState(initialMenu = tabMenu(0, tabHome, ThemeMode.LIGHT, true, {}, {}, HomeTabState())) })
    }
    @Test fun ringCounter() = shot("ring-counter-filter-dark") {
        Feed(remember { MoonOverlayState(initialMenu = com.moonlight.feed.ui.feed.counterRing(homeRing(Rect(820f, 150f, 1040f, 250f), FeedScope.ONLINE, true, false, HomeRingHandlers()))) }, "在线")
    }
    @Test fun ringEmpty() = shot("ring-empty-space-filter-dark") {
        val o = Offset(540f, 1300f)
        MoonShell(remember { MoonOverlayState(initialMenu = homeRing(Rect(o, o), FeedScope.LOCAL, false, false, HomeRingHandlers())) }, tab = 0, tabBarVisible = true) {
            CompositionLocalProvider(LocalMoonColors provides MediaGlass) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MoonEmptyState("没有本地内容", "拍摄、导入或下载的图片与视频会出现在这里", action = "显示全部", onAction = {}) }
            }
        }
    }
    @Test fun ringTabMe() = shot("ring-tab-me-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState(initialMenu = tabMenu(6, tabMe, ThemeMode.LIGHT, false, {}, {})) }, tab = 6, tabBarVisible = true, badges = mapOf(4 to 2)) {
            MeContent(social.self, stats, MeHandlers())
        }
    }
    @Test fun ringTabMoments() = shot("ring-tab-moments-dark") {
        MoonShell(remember { MoonOverlayState(initialMenu = tabMenu(4, tabMoments, ThemeMode.DARK, false, {}, {}, unread = 2)) }, tab = 4, tabBarVisible = true, badges = mapOf(4 to 2)) {
            MomentsContent(social, social.selfId, MomentHandlers(), now)
        }
    }
}
