package com.moonlight.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonBackground
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ScreenHeader
import com.moonlight.feed.ui.design.SheetSpec
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.feed.scopeAction
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.settings.SettingsContent
import com.moonlight.feed.ui.settings.SocialSettingsGroups
import com.moonlight.feed.ui.social.ChatContent
import com.moonlight.feed.ui.social.ChatHandlers
import com.moonlight.feed.ui.social.ChatListContent
import com.moonlight.feed.ui.social.ChatListHandlers
import com.moonlight.feed.ui.social.ComposerContent
import com.moonlight.feed.ui.social.ComposerHandlers
import com.moonlight.feed.ui.social.FriendEditor
import com.moonlight.feed.ui.social.FriendProfileContent
import com.moonlight.feed.ui.social.FriendsContent
import com.moonlight.feed.ui.social.FriendsHandlers
import com.moonlight.feed.ui.social.MomentDraft
import com.moonlight.feed.ui.social.MomentHandlers
import com.moonlight.feed.ui.social.MomentsContent
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import dev.chrisbanes.haze.haze
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Delete
import org.junit.Rule
import org.junit.Test

class V29Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) { MoonTheme(mode) { content() } }
    }

    // fixed clock so relative times are stable; all data is fake (示例) with procedural art only
    private val now = 1_760_000_000_000L
    private val social: SocialState = DemoSocial.seed(SocialState.fresh(), now)
    private fun friend(n: String) = social.friends.first { it.name == n }

    // ---------------------------------------------------------------- 1. blur-speed bug fixed
    @Test fun settingsBlurSpeedFast() = shot("settings-blur-speed-fast-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            SettingsContent(SettingsRepository.Snapshot(themeMode = "DARK", revealOn = true, revealSpeed = "FAST"), SettingsActions(), social = social)
        }
    }
    @Test fun settingsBlurSpeedFastLight() = shot("settings-blur-speed-fast-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            SettingsContent(SettingsRepository.Snapshot(themeMode = "LIGHT", revealOn = true, revealSpeed = "FAST"), SettingsActions(), social = social)
        }
    }

    // ---------------------------------------------------------------- 2. home 筛选 inside the radial menu
    @Composable
    private fun Feed(entry: MediaEntry, overlay: MoonOverlayState, scopeLabel: String? = null) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            val fh = remember { dev.chrisbanes.haze.HazeState() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().haze(fh)) {
                    FeedItem(entry, chromeVisible = true, muted = false, playing = true, showPlayButton = false) { MoonImage(entry.coverUrl, Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = overlay.liftKey == null, index = 2, total = 9, scopeLabel = scopeLabel, haze = fh)
            }
        }
    }
    private val localVideo = MediaEntry(id = "cap1", kind = MediaKind.VIDEO, title = "拍摄 10月20日 21:14", localPath = "/x/cap.mp4", coverUrl = "art:4", author = "我", platform = "local")

    @Test fun homeFilterRing() = shot("home-filter-radial-dark") {
        val spec = feedMenuSpec(localVideo, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.LOCAL).copy(origin = Offset(540f, 1250f))
        Feed(localVideo, remember { MoonOverlayState(initialMenu = spec) }, "本地")
    }
    @Test fun homeFilterChoices() = shot("home-filter-choices-dark") {
        val f = scopeAction(FeedScope.LOCAL) {}
        val spec = MenuSpec(anchor = Rect(Offset(540f, 1250f), 1f), title = "筛选", actions = listOf(f.children!!), forceDark = true, list = true)
        Feed(localVideo, remember { MoonOverlayState(initialMenu = spec) }, "本地")
    }
    @Test fun homeLocalIndicator() = shot("home-local-dark") { Feed(localVideo, remember { MoonOverlayState() }, "本地") }

    // ---------------------------------------------------------------- 3. 朋友圈
    @Test fun moments() = shot("moments-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) { MomentsContent(social, social.selfId, MomentHandlers(), now) }
    }
    @Test fun momentsLight() = shot("moments-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) { MomentsContent(social, social.selfId, MomentHandlers(), now) }
    }
    @Test fun momentsEmpty() = shot("moments-empty-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) { MomentsContent(SocialState.fresh(), "x", MomentHandlers(), now) }
    }
    @Test fun momentRing() = shot("moment-radial-dark") {
        val m = social.timeline.first()
        val spec = MenuSpec(anchor = Rect(24f, 700f, 1056f, 1500f), origin = Offset(560f, 1180f), title = friend("阿月").name, actions = listOf(listOf(
            com.moonlight.feed.ui.design.MenuAction("赞", androidx.compose.material.icons.Icons.Outlined.FavoriteBorder),
            com.moonlight.feed.ui.design.MenuAction("评论", androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline),
            com.moonlight.feed.ui.design.MenuAction("编辑", androidx.compose.material.icons.Icons.Outlined.Edit),
            com.moonlight.feed.ui.design.MenuAction("复制", androidx.compose.material.icons.Icons.Outlined.ContentCopy),
            com.moonlight.feed.ui.design.MenuAction("改发布者", androidx.compose.material.icons.Icons.Outlined.SwapHoriz),
            com.moonlight.feed.ui.design.MenuAction("删除", androidx.compose.material.icons.Icons.Outlined.Delete, destructive = true),
        )))
        MoonShell(remember { MoonOverlayState(initialMenu = spec) }, tab = 4, tabBarVisible = true) { MomentsContent(social, social.selfId, MomentHandlers(), now) }
    }
    @Test fun profile() = shot("friend-profile-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) {
            FriendProfileContent(friend("阿月").copy(autoReplyOn = true, id = "friend-ayue-fixed"), social, social.selfId, MomentHandlers(), now)
        }
    }
    @Test fun friends() = shot("friends-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) { FriendsContent(social, FriendsHandlers(onBack = {})) }
    }
    @Test fun friendEditor() = shot("friend-editor-dark") {
        val d = friend("阿月").copy(autoReplyOn = true)
        MoonShell(remember { MoonOverlayState(initialSheet = SheetSpec("编辑好友") { _ -> FriendEditor(d, {}, {}, {}) }) }, tab = 4, tabBarVisible = true) {
            FriendsContent(social, FriendsHandlers(onBack = {}))
        }
    }
    @Test fun composer() = shot("post-composer-dark") {
        val draft = MomentDraft(
            posterId = friend("阿月").id, text = "今晚的月亮刚从山脊后面出来，风很小，适合长曝光。",
            attachments = listOf(0, 2, 4).map { Attachment(kind = if (it == 2) AttachmentKind.VIDEO else AttachmentKind.IMAGE, coverUrl = "art:$it") },
            time = now - 3_600_000,
        )
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = false) { ComposerContent(draft, social, ComposerHandlers()) }
    }

    // ---------------------------------------------------------------- 4. 消息
    @Test fun chatList() = shot("chats-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 5, tabBarVisible = true) { ChatListContent(social, ChatListHandlers(), now) }
    }
    @Test fun chatSpeakingAs() = shot("chat-speaking-as-dark") {
        val conv = social.conversations.first { friend("阿月").id in it.memberIds }
        MoonShell(remember { MoonOverlayState() }, tab = 5, tabBarVisible = false) {
            ChatContent(conv, social, speakerId = friend("阿月").id, text = "", stripOpen = true, h = ChatHandlers(), now = now)
        }
    }
    @Test fun chatSpeakingAsLight() = shot("chat-speaking-as-light", ThemeMode.LIGHT) {
        val conv = social.conversations.first { friend("阿月").id in it.memberIds }
        MoonShell(remember { MoonOverlayState() }, tab = 5, tabBarVisible = false) {
            ChatContent(conv, social, speakerId = social.selfId, text = "那我先去占位置", stripOpen = false, h = ChatHandlers(), now = now)
        }
    }
    @Test fun chatFriends() = shot("chat-friend-to-friend-dark") {
        val conv = social.conversations.first { social.selfId !in it.memberIds }
        MoonShell(remember { MoonOverlayState() }, tab = 5, tabBarVisible = false) {
            ChatContent(conv, social, speakerId = friend("Nova").id, text = "", stripOpen = true, h = ChatHandlers(), now = now)
        }
    }

    // ---------------------------------------------------------------- 5. chat server settings
    @Test fun chatServer() = shot("settings-chat-server-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenHeader("设置")
                SocialSettingsGroups(SettingsRepository.Snapshot(themeMode = "DARK", chatServerUrl = "https://chat.example.com"), SettingsActions(), social)
            }
        }
    }
    @Test fun chatServerLight() = shot("settings-chat-server-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenHeader("设置")
                SocialSettingsGroups(SettingsRepository.Snapshot(themeMode = "LIGHT"), SettingsActions(), social)
            }
        }
    }

    // tab bar (3 + ● + 3) on the library tab for regression
    @Test fun tabbar() = shot("tabbar-dark") {
        MoonShell(remember { MoonOverlayState() }, tab = 1, tabBarVisible = true) {
            com.moonlight.feed.ui.library.LibraryContent(items = sample(15), filter = "全部", onFilter = {}, selectionMode = false, selectedIds = emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> })
        }
    }
}
