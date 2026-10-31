package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.ActionSheetContent
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.SheetSpec
import com.moonlight.feed.ui.design.ring
import com.moonlight.feed.ui.design.sheetGroups
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.JumpSheet
import com.moonlight.feed.ui.feed.MediaInfoCard
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.library.LibraryContent
import com.moonlight.feed.ui.library.LibraryHandlers
import com.moonlight.feed.ui.library.libraryMenuSpec
import com.moonlight.feed.ui.player.PlayerActions
import com.moonlight.feed.ui.player.playerMediaMenu
import com.moonlight.feed.ui.settings.GestureGuide
import com.moonlight.feed.ui.settings.RingAnimRows
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.settings.ringAnimPreview
import com.moonlight.feed.ui.social.ChatContent
import com.moonlight.feed.ui.social.ChatHandlers
import com.moonlight.feed.ui.social.FriendRingHandlers
import com.moonlight.feed.ui.social.MomentHandlers
import com.moonlight.feed.ui.social.MomentsContent
import com.moonlight.feed.ui.social.PostRingHandlers
import com.moonlight.feed.ui.social.friendMenuSpec
import com.moonlight.feed.ui.social.messageMenu
import com.moonlight.feed.ui.social.momentPostMenuSpec
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** v4.0 interaction language: ≤4-item rings + ··· sheet, swipe-left info card, 长按动画 mid-frames. Fake procedural art only. */
class V40Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot("v40-$name") { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

    private val press = Offset(540f, 1250f)
    private fun entry(kind: MediaKind, art: Int) = MediaEntry(id = "e$art", kind = kind, title = when (kind) { MediaKind.VIDEO -> "山脊上的月出"; MediaKind.IMAGE -> "湖面长曝光"; else -> "在线 · 夜航" },
        localPath = if (kind == MediaKind.VIDEO || kind == MediaKind.IMAGE) "/x/$art" else null, coverUrl = "art:$art", author = "我",
        platform = if (kind == MediaKind.BILI) "bilibili" else if (kind == MediaKind.EMBED) "youtube" else "local",
        embedUrl = if (kind == MediaKind.EMBED) "https://example.com/v" else null, biliBvid = if (kind == MediaKind.BILI) "BV1xx" else null,
        favorite = art == 3, createdAt = 1_759_900_000_000L)

    private fun released(spec: MenuSpec, at: Offset = press) = MoonOverlayState(initialMenu = spec.copy(origin = at)).apply { pressOrigin = at; finger = at; fingerDown = false }

    @Composable private fun Feed(e: MediaEntry, overlay: MoonOverlayState) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                FeedItem(e, chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(e.coverUrl!!.removePrefix("art:").toInt(), Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = true, index = 4, total = 22)
            }
        }
    }
    private fun feedSpec(e: MediaEntry) = feedMenuSpec(e, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.ALL)

    // ---------------------------------------------------------------- rings per context
    @Test fun ringFeedVideo() = shot("ring-feed-video-dark") { val e = entry(MediaKind.VIDEO, 6); Feed(e, remember { released(feedSpec(e)) }) }
    @Test fun ringFeedImage() = shot("ring-feed-image-light", ThemeMode.LIGHT) { val e = entry(MediaKind.IMAGE, 3); Feed(e, remember { released(feedSpec(e)) }) }
    @Test fun ringFeedOnline() = shot("ring-feed-online-dark") { val e = entry(MediaKind.BILI, 8); Feed(e, remember { released(feedSpec(e)) }) }
    @Test fun ringSubArc() = shot("ring-sub-arc-share-save-dark") {
        MenuStyle.debugProgress = 1f   // chips spring in; capture them settled
        val e = entry(MediaKind.VIDEO, 6); val spec = feedSpec(e)
        Feed(e, remember { released(spec).apply { subIndex = spec.ring().indexOfFirst { it.title == "分享·保存" } } })
    }
    @Test fun ringSubArcSpeed() = shot("ring-sub-arc-speed-dark") {
        MenuStyle.debugProgress = 1f
        val e = entry(MediaKind.VIDEO, 2); val spec = feedSpec(e)
        Feed(e, remember { released(spec).apply { subIndex = spec.ring().indexOfFirst { it.title == "倍速" } } })
    }
    @Test fun ringHomeTab() = shot("ring-home-tab-dark") {
        val at = Offset(110f, 2230f); val e = entry(MediaKind.VIDEO, 6)
        Feed(e, remember { released(tabMenu(0, Rect(at, 60f), ThemeMode.DARK, true, {}, {}), at) })
    }
    @Test fun ringCapture() = shot("ring-capture-dark") {
        val at = Offset(540f, 2230f); val e = entry(MediaKind.IMAGE, 5)
        Feed(e, remember { released(captureMenu(Rect(at, 60f), true, {}, {}, {}, {}), at) })
    }
    @Composable private fun Library(overlay: MoonOverlayState) {
        MoonShell(overlay, tab = 1, tabBarVisible = true) {
            LibraryContent(items = sample(15), filter = "全部", onFilter = {}, selectionMode = false, selectedIds = emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> })
        }
    }
    @Test fun ringLibrary() = shot("ring-library-longpress-dark") {
        Library(remember { released(libraryMenuSpec(sample()[4], Rect(380f, 900f, 700f, 1330f), LibraryHandlers()), Offset(540f, 1110f)) })
    }
    @Test fun ringLibraryLight() = shot("ring-library-longpress-light", ThemeMode.LIGHT) {
        Library(remember { released(libraryMenuSpec(sample()[1], Rect(380f, 900f, 700f, 1330f), LibraryHandlers()), Offset(540f, 1110f)) })
    }
    @Test fun ringPlayer() = shot("ring-player-dark") {
        val e = entry(MediaKind.VIDEO, 2)
        MoonShell(remember { released(playerMediaMenu(press, e, false, false, PlayerActions())) }, tab = 0, tabBarVisible = false) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(2, Modifier.fillMaxSize()) }
        }
    }

    private val now = 1_760_000_000_000L
    private val social: SocialState = DemoSocial.seed(SocialState.fresh(), now).let { st ->
        val (v, rest) = st.moments.partition { m -> m.attachments.size == 1 && m.attachments[0].kind == AttachmentKind.VIDEO }
        st.copy(moments = v.take(1).map { it.copy(createdAt = now - 60_000) } + rest)
    }
    @Test fun ringMomentPost() = shot("ring-moments-post-dark") {
        val m = social.moments[1]; val at = Offset(540f, 1500f)
        MoonShell(remember { released(momentPostMenuSpec(m, social, social.selfId, Rect(at, 1f), PostRingHandlers()), at) }, tab = 4, tabBarVisible = true) {
            MomentsContent(social, social.selfId, MomentHandlers(), now)
        }
    }
    @Test fun ringFriend() = shot("ring-friend-light", ThemeMode.LIGHT) {
        val c = social.friends.first(); val at = Offset(110f, 760f)
        MoonShell(remember { released(friendMenuSpec(c, social, Rect(at, 40f), FriendRingHandlers()), at) }, tab = 4, tabBarVisible = true) {
            MomentsContent(social, social.selfId, MomentHandlers(), now)
        }
    }
    @Test fun ringMessage() = shot("ring-message-dark") {
        val conv = social.conversations.first()
        val members = conv.memberIds.mapNotNull { social.contact(it) }
        val msg = social.messagesOf(conv.id).last()
        val at = Offset(600f, 1700f)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        MoonShell(remember { released(messageMenu(msg, Rect(at, 30f), members, ctx, {}), at) }, tab = 5, tabBarVisible = false) {
            ChatContent(conv, social, speakerId = social.selfId, text = "", stripOpen = false, h = ChatHandlers(), now = now)
        }
    }

    // ---------------------------------------------------------------- ··· sheet, info card, jump
    private fun sheetOver(spec: MenuSpec) = MoonOverlayState(initialSheet = SheetSpec(spec.title, spec.forceDark) { d -> ActionSheetContent(spec.sheetGroups(), d) })
    @Test fun sheetFeedVideo() = shot("sheet-more-feed-video-dark") { val e = entry(MediaKind.VIDEO, 6); Feed(e, remember { sheetOver(feedSpec(e)) }) }
    @Test fun sheetFeedVideoLight() = shot("sheet-more-feed-video-light", ThemeMode.LIGHT) { val e = entry(MediaKind.VIDEO, 6); Feed(e, remember { sheetOver(feedSpec(e)) }) }
    @Test fun sheetLibrary() = shot("sheet-more-library-dark") { Library(remember { sheetOver(libraryMenuSpec(sample()[4], Rect.Zero, LibraryHandlers())) }) }
    @Test fun infoCard() = shot("feed-swipe-left-info-card-dark") {
        val e = entry(MediaKind.VIDEO, 6)
        Feed(e, remember { MoonOverlayState(initialSheet = SheetSpec(null, true) { _ -> MediaInfoCard(e, null) }) })
    }
    @Test fun infoCardImageLight() = shot("feed-swipe-left-info-card-image-light", ThemeMode.LIGHT) {
        val e = entry(MediaKind.IMAGE, 3)
        Feed(e, remember { MoonOverlayState(initialSheet = SheetSpec(null, true) { _ -> MediaInfoCard(e, null) }) })
    }
    @Test fun jumpSheet() = shot("feed-counter-jump-sheet-dark") {
        val e = entry(MediaKind.IMAGE, 5)
        Feed(e, remember { MoonOverlayState(initialSheet = SheetSpec("跳转", true) { _ -> JumpSheet(4, 22, {}, {}) }) })
    }
    @Test fun guide() = shot("gesture-guide-dark") {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0D14)).statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) { GestureGuide() }
    }

    // ---------------------------------------------------------------- 长按动画: open mid-frames
    private fun mid(style: RingAnim, p: Float, mode: ThemeMode = ThemeMode.DARK) = shot("anim-${style.name.lowercase()}-mid-${(p * 100).toInt()}-${mode.name.lowercase()}", mode) {
        MenuStyle.debugProgress = p
        val e = entry(MediaKind.VIDEO, 6)
        Feed(e, remember { released(feedSpec(e).copy(anim = style)) })
    }
    @Test fun animSpin() = mid(RingAnim.SPIN, 0.5f)
    @Test fun animFade() = mid(RingAnim.FADE, 0.5f)
    @Composable private fun AnimRow(s: SettingsRepository.Snapshot) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            Column(Modifier.statusBarsPadding().padding(16.dp)) {
                com.moonlight.feed.ui.design.ScreenHeader("外观")
                Column { RingAnimRows(s, SettingsActions()) }
            }
        }
    }
    @Test fun settingsRow() = shot("settings-ring-anim-dark") { AnimRow(SettingsRepository.Snapshot(themeMode = "DARK", ringAnim = "MOON")) }
    @Test fun settingsRowRandom() = shot("settings-ring-anim-random-light", ThemeMode.LIGHT) {
        AnimRow(SettingsRepository.Snapshot(themeMode = "LIGHT", ringAnim = "RANDOM", ringAnimSet = "BLUR,SHATTER,MOON"))
    }

    @After fun reset() { MenuStyle.debugProgress = null }
}
