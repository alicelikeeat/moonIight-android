package com.moonlight.feed.ui.me

import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.PlayCircle
import com.moonlight.feed.ui.design.FocusFrames
import com.moonlight.feed.ui.social.ProfileMediaActions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.SettingsGroup
import com.moonlight.feed.ui.SettingsRow
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.ProfileMedia
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.social.Avatar
import com.moonlight.feed.ui.social.SRoute
import com.moonlight.feed.ui.social.SocialNav
import com.moonlight.feed.ui.social.UnreadBadge
import com.moonlight.feed.ui.theme.LocalMoonColors
import kotlinx.coroutines.launch

class MeHandlers(
    val onProfile: () -> Unit = {},
    val onLibrary: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onChats: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onAvatar: (Rect) -> Unit = {},
    val onCover: (Rect) -> Unit = {},
    /** Long-press avatar / cover → full-screen player. */
    val onAvatarLong: (Rect) -> Unit = {},
    val onCoverLong: (Rect) -> Unit = {},
    val onTheme: (Rect) -> Unit = {},
)

data class MeStats(val posts: Int, val friends: Int, val library: Int, val favorites: Int, val unread: Int)

/** 我: my profile header (cover · avatar), then 库 / 朋友 / 消息 / 设置. Stateless (screenshots). */
@Composable
fun MeContent(self: Contact, stats: MeStats, h: MeHandlers) {
    val c = LocalMoonColors.current
    val coverA = rememberAnchor(); val avatarA = rememberAnchor(); val themeA = rememberAnchor()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item(key = "head") {
            Box(Modifier.fillMaxWidth().height(286.dp)) {
                Box(Modifier.fillMaxWidth().height(216.dp).anchor(coverA).moonPress({ h.onCover(coverA.rect) }, { h.onCoverLong(coverA.rect) }, highlightShape = null, pressedScale = 1f)) {
                    ProfileMedia(self.coverPath, self.coverVideo, Modifier.fillMaxSize(), self.coverFocus) { ProceduralArt(7, Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to c.bgTop.copy(alpha = 0.96f))))
                }
                Row(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.anchor(avatarA).moonPress({ h.onAvatar(avatarA.rect) }, { h.onAvatarLong(avatarA.rect) }, highlightShape = null)) { Avatar(self, 84.dp, ring = true) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                        Text(self.name, color = c.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(self.bio.ifBlank { "点按头像或封面更换 / 调整位置，长按播放" }, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                Text("我", color = Color.White, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${stats.posts}", "动态", Modifier.weight(1f), h.onProfile)
                Stat("${stats.friends}", "朋友", Modifier.weight(1f), h.onFriends)
                Stat("${stats.library}", "库", Modifier.weight(1f), h.onLibrary)
                Stat("${stats.favorites}", "收藏", Modifier.weight(1f), h.onLibrary)
            }
        }
        item(key = "mine") {
            SettingsGroup("我的") {
                SettingsRow("我的主页", "${stats.posts} 条动态", icon = Icons.Outlined.Person, onClick = h.onProfile)
                SettingsRow("编辑资料", "名字、签名、头像（照片或视频）", icon = Icons.Outlined.Edit, onClick = h.onEdit, iconTint = Color(0xFF8FB8F2))
            }
        }
        item(key = "go") {
            SettingsGroup("内容与社交") {
                SettingsRow("库", "${stats.library} 项 · ${stats.favorites} 收藏", icon = Icons.Outlined.ViewModule, onClick = h.onLibrary, iconTint = Color(0xFFF2C46A))
                SettingsRow("朋友", "${stats.friends} 位虚拟好友", icon = Icons.Outlined.People, onClick = h.onFriends, iconTint = Color(0xFF7FD1AE))
                SettingsRow("消息", if (stats.unread > 0) "${stats.unread} 条未读" else "没有未读", icon = Icons.AutoMirrored.Outlined.Chat, onClick = h.onChats, iconTint = Color(0xFFB18CFF),
                    trailingContent = if (stats.unread > 0) ({ Box(Modifier.padding(end = 6.dp)) { UnreadBadge(stats.unread) } }) else null)
            }
        }
        item(key = "app") {
            SettingsGroup("App") {
                Box(Modifier.anchor(themeA)) { SettingsRow("外观", "深色 / 浅色 / 跟随系统", icon = Icons.Outlined.Palette, onClick = { h.onTheme(themeA.rect) }, iconTint = Color(0xFFFF8A7A)) }
                SettingsRow("设置", "播放、首页背景、朋友圈、备份…", icon = Icons.Outlined.Settings, onClick = h.onSettings, iconTint = c.textDim)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    Column(
        modifier.glassStatic(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).moonPress(onClick, pressedScale = 0.95f).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = c.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, color = c.textDim, style = MaterialTheme.typography.labelSmall)
    }
}

/** Stateful 我 tab. Library / settings open as pages under 我; friends / profile / chats jump into 朋友圈. */
@Composable
fun MeScreen(go: (Int) -> Unit, onTheme: (Rect) -> Unit) {
    val app = MoonApp.instance
    val overlay = LocalOverlay.current
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s by app.social.state.collectAsState()
    val lib by app.library.items.collectAsState()
    var target by remember { mutableStateOf("avatar") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val (p, video) = app.social.saveProfileMedia(uri) ?: run { MoonToaster.show("无法读取这个文件"); return@launch }
            val avatar = target == "avatar"
            val me = ProfileMediaActions.withNewMedia(app.social.value.self, avatar, p, video)
            app.social.upsertContact(me)
            MoonToaster.show(if (video) "已设为视频（静音循环）" else "已更换", Icons.Outlined.CheckCircle)
            // straight into 调整位置 so the subject can be framed (default already biases to the upper third)
            ProfileMediaActions.adjust(me, avatar, if (avatar) FocusFrames.AVATAR else FocusFrames.ME_COVER) { f ->
                app.social.upsertContact(ProfileMediaActions.withFocus(app.social.value.self, avatar, f))
            }
        }
    }
    fun mediaMenu(r: Rect, what: String) {
        val me = s.self
        val has = if (what == "avatar") me.avatarPath != null else me.coverPath != null
        overlay.showMenu(MenuSpec(anchor = r, title = if (what == "avatar") "头像" else "封面", actions = listOf(buildList {
            val avatar = what == "avatar"
            add(MenuAction("更换", Icons.Outlined.PhotoLibrary, subtitle = "照片或视频 · 复制到 App 内") { target = what; picker.launch(arrayOf("image/*", "video/*")) })
            if (has) add(MenuAction("调整位置", Icons.Outlined.CenterFocusStrong, subtitle = "拖动 · 双指缩放") {
                ProfileMediaActions.adjust(me, avatar, if (avatar) FocusFrames.AVATAR else FocusFrames.ME_COVER) { f ->
                    app.social.upsertContact(ProfileMediaActions.withFocus(app.social.value.self, avatar, f))
                }
            })
            if (has) add(MenuAction("播放", Icons.Outlined.PlayCircle, subtitle = "轻点也可直接打开") { ProfileMediaActions.play(ctx, me, avatar) })
            if (has) add(MenuAction("恢复默认", Icons.Outlined.Refresh, subtitle = "照片 / 视频会保留，可用回") { app.social.upsertContact(ProfileMediaActions.reset(me, avatar)) })
            addAll(com.moonlight.feed.ui.social.savedMediaActions(ctx, overlay, me, avatar, { app.social.value.self }, { app.social.value.contacts }) { app.social.upsertContact(it) })
        })))
    }
    fun social(r: SRoute?, page: Int = 0) {
        SocialNav.page = page
        if (r != null) { while (SocialNav.moments.size > 1) SocialNav.moments.removeAt(SocialNav.moments.lastIndex); SocialNav.moments.add(r) }
        go(4)
    }
    MeContent(
        s.self,
        MeStats(s.momentsBy(s.selfId).size, s.friends.size, lib.count { !it.hidden }, lib.count { it.favorite && !it.hidden }, s.totalUnread),
        MeHandlers(
            onProfile = { social(SRoute.Profile(s.selfId)) },
            onLibrary = { go(1) },
            onFriends = { social(SRoute.Friends) },
            onChats = { social(null, 1) },
            onSettings = { go(3) },
            onEdit = { social(SRoute.Profile(s.selfId)); SocialNav.pendingEditSelf = true },
            // v4.0: tap = open/play full screen (nothing custom yet → the menu); long-press = ring (更换 · 调整位置 …)
            onAvatar = { r -> if (!ProfileMediaActions.play(ctx, s.self, true)) mediaMenu(r, "avatar") },
            onCover = { r -> if (!ProfileMediaActions.play(ctx, s.self, false)) mediaMenu(r, "cover") },
            onAvatarLong = { mediaMenu(it, "avatar") },
            onCoverLong = { mediaMenu(it, "cover") },
            onTheme = onTheme,
        ),
    )
}
