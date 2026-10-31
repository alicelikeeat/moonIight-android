package com.moonlight.feed.ui.social

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.social.Comment
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.GlassIconButton
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.theme.LocalMoonColors
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbDown

class MomentHandlers(
    val onOpenMedia: (Moment, Int) -> Unit = { _, _ -> },
    val onLike: (Moment) -> Unit = {},
    val onLikeLong: (Moment, Rect) -> Unit = { _, _ -> },
    val onDislike: (Moment) -> Unit = {},
    val onDislikeLong: (Moment, Rect) -> Unit = { _, _ -> },
    /** Tap on the 朋友圈 cover / a profile background → change it (photo or video). */
    val onCover: (Contact, Rect) -> Unit = { _, _ -> },
    val onComment: (Moment, Comment?) -> Unit = { _, _ -> },
    val onCommentLong: (Moment, Comment, Rect) -> Unit = { _, _, _ -> },
    val onMomentLong: (Moment, Rect) -> Unit = { _, _ -> },
    /** Long-press on a video tile → video ring (wins over the post menu). */
    val onVideoLong: ((Moment, Int, Rect) -> Unit)? = null,
    val onAuthor: (String) -> Unit = {},
    val onCompose: () -> Unit = {},
    val onComposeLong: (Rect) -> Unit = {},
    val onFriends: () -> Unit = {},
    val onChats: () -> Unit = {},
    val onSeedDemo: () -> Unit = {},
    val onNewFriend: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onEditProfile: (Contact) -> Unit = {},
    val onMessage: (Contact) -> Unit = {},
    val onPostAs: (Contact) -> Unit = {},
    val onProfileLong: (Contact, Rect) -> Unit = { _, _ -> },
    /** Long-press a cover / avatar → full-screen player (avatar = true for the avatar). */
    val onMediaLong: (Contact, Boolean, Rect) -> Unit = { _, _, _ -> },
)

private fun seedOf(id: String) = (id.hashCode() and 0x7fffffff) % 6

/** WeChat-moments-like timeline, Apple-minimal: cover · self · cards. */
@Composable
fun MomentsContent(
    state: SocialState, actingId: String, h: MomentHandlers, now: Long = System.currentTimeMillis(), bottomPad: Int = 120,
    players: MomentPlayers? = null, autoplayPaused: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    /** Screenshot stand-in for the inline windows (defaults to the real pool). */
    inline: InlineLive? = InlineLive.of(players),
) {
    val c = LocalMoonColors.current
    val composeAnchor = rememberAnchor()
    val coverAnchor = rememberAnchor()
    val moments = state.timeline
    MomentInlineEffect(listState, moments, players, active = !autoplayPaused)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottomPad.dp)) {
        item(key = "cover") {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Box(Modifier.fillMaxWidth().height(262.dp).anchor(coverAnchor).moonPress({ h.onCover(state.self, coverAnchor.rect) }, { h.onMediaLong(state.self, false, coverAnchor.rect) }, highlightShape = null, pressedScale = 1f)) {
                    com.moonlight.feed.ui.design.ProfileMedia(state.self.coverPath, state.self.coverVideo, Modifier.fillMaxSize(), state.self.coverFocus) { ProceduralArt(7, Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to c.bgTop.copy(alpha = 0.9f))))
                }
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("朋友圈", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    val media = com.moonlight.feed.ui.theme.MediaGlass
                    GlassIconButton(Icons.Outlined.People, "好友", h.onFriends, size = 40.dp, iconSize = 20.dp, colors = media, skinId = "moments.friends")
                    Spacer(Modifier.width(8.dp))
                    Box {
                        GlassIconButton(Icons.AutoMirrored.Outlined.Chat, "消息", h.onChats, size = 40.dp, iconSize = 20.dp, colors = media, skinId = "moments.chats")
                        UnreadBadge(state.totalUnread, Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(Icons.Outlined.CameraAlt, "发动态", h.onCompose, modifier = Modifier.anchor(composeAnchor), size = 40.dp, iconSize = 20.dp,
                        colors = media, onLongClick = { h.onComposeLong(composeAnchor.rect) }, skinId = "moments.compose")
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.padding(end = 12.dp, bottom = 34.dp), horizontalAlignment = Alignment.End) {
                        Text(state.self.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    val selfA = rememberAnchor()
                    Box(Modifier.anchor(selfA).moonPress({ h.onAuthor(state.selfId) }, { h.onMediaLong(state.self, true, selfA.rect) }, highlightShape = null)) { Avatar(state.self, 72.dp) }
                }
            }
            if (state.self.bio.isNotBlank()) Text(state.self.bio, color = c.textDim, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.height(14.dp))
        }
        if (moments.isEmpty()) item(key = "empty") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MoonEmptyState(
                    if (state.friends.isEmpty()) "朋友圈还空着" else "还没有动态",
                    if (state.friends.isEmpty()) "新建几位虚拟好友，替他们发第一条动态。全部只保存在本机。" else "点右上角相机，以自己或任意好友的身份发布",
                    action = if (state.friends.isEmpty()) "新建好友" else "发动态",
                    onAction = if (state.friends.isEmpty()) h.onNewFriend else h.onCompose,
                )
                if (state.friends.isEmpty()) GlassPill("载入示例", h.onSeedDemo)
            }
        }
        items(moments, key = { it.id }) { m -> MomentCard(m, state, actingId, h, now, players = players, inline = inline) }
    }
}

/** Small red count bubble (99+). */
@Composable
fun UnreadBadge(n: Int, modifier: Modifier = Modifier) {
    if (n <= 0) return
    Box(
        modifier.offset(x = 4.dp, y = (-2).dp).heightIn(min = 17.dp).widthIn(min = 17.dp).clip(CircleShape).background(Color(0xFFFF4D5E))
            .border(1.5.dp, Color.White.copy(0.85f), CircleShape).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
fun MomentCard(m: Moment, state: SocialState, actingId: String, h: MomentHandlers, now: Long, showAuthor: Boolean = true,
               players: MomentPlayers? = null, inline: InlineLive? = InlineLive.of(players)) {
    val c = LocalMoonColors.current
    val author = state.contact(m.authorId)
    val cardAnchor = rememberAnchor()
    val likeAnchor = rememberAnchor()
    val dislikeAnchor = rememberAnchor()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).anchor(cardAnchor).then(com.moonlight.feed.ui.design.ShredAnchorMod("moment:" + m.id))
            .glassStatic(RoundedCornerShape(22.dp))
            .moonPress({}, { h.onMomentLong(m, cardAnchor.rect) }, pressedScale = 0.985f, highlightShape = RoundedCornerShape(22.dp), highlight = c.text.copy(0.04f), hapticOnClick = false,
                onDoubleClick = { if (actingId !in m.likes) h.onLike(m) })  // double-tap = 赞 (never un-likes)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
    ) {
        if (showAuthor) {
            Box(Modifier.moonPress({ h.onAuthor(m.authorId) }, highlightShape = null)) { Avatar(author, 42.dp) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (showAuthor) {
                Text(author?.name ?: "已删除的好友", color = c.accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.moonPress({ h.onAuthor(m.authorId) }, highlightShape = null))
                Spacer(Modifier.height(3.dp))
            }
            if (m.text.isNotBlank()) {
                Text(m.text, color = c.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            if (m.attachments.isNotEmpty()) {
                MediaGrid(m.attachments, maxWidth = 270.dp, inline = inline,
                    onVideoLong = h.onVideoLong?.let { f -> { i, r -> players?.handOff(); f(m, i, r) } }) { i ->
                    // v5.0: tap a video = play ⇄ pause inline (PlayToggle); long-press = full-screen player.
                    // Images (and videos with no playable source) still open the swipe player.
                    val a = m.attachments[i]
                    if (players != null && a.kind == com.moonlight.feed.data.social.AttachmentKind.VIDEO && players.canPlay(a)) players.tap(a)
                    else { players?.handOff(); h.onOpenMedia(m, i) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(relTime(m.createdAt, now) + if (m.editedAt != null) " · 已编辑" else "", color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                val liked = actingId in m.likes
                Row(
                    Modifier.anchor(likeAnchor).clip(CircleShape).moonPress({ h.onLike(m) }, { h.onLikeLong(m, likeAnchor.rect) }, highlightShape = CircleShape)
                        .background(c.hairline, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "赞", tint = if (liked) Color(0xFFFF6B81) else c.textDim, modifier = Modifier.size(15.dp))
                    if (m.likes.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text("${m.likes.size}", color = c.textDim, style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.width(6.dp))
                val disliked = actingId in m.dislikes
                Row(
                    Modifier.anchor(dislikeAnchor).clip(CircleShape).moonPress({ h.onDislike(m) }, { h.onDislikeLong(m, dislikeAnchor.rect) }, highlightShape = CircleShape)
                        .background(if (disliked) c.text.copy(0.10f) else c.hairline, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "踩", tint = if (disliked) c.text else c.textDim, modifier = Modifier.size(14.dp))
                    if (m.dislikes.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text("${m.dislikes.size}", color = c.textDim, style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.clip(CircleShape).moonPress({ h.onComment(m, null) }, highlightShape = CircleShape).background(c.hairline, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, "评论", tint = c.textDim, modifier = Modifier.size(14.dp))
                    if (m.comments.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text("${m.comments.size}", color = c.textDim, style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (m.likes.isNotEmpty() || m.dislikes.isNotEmpty() || m.comments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.hairline.copy(alpha = c.hairline.alpha * 0.9f)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if (m.likes.isNotEmpty()) Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.FavoriteBorder, null, tint = c.accent, modifier = Modifier.padding(top = 2.dp).size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(m.likes.mapNotNull { state.contact(it)?.name }.joinToString("，"), color = c.accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (m.dislikes.isNotEmpty()) Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = if (m.likes.isNotEmpty()) 4.dp else 0.dp)) {
                        Icon(Icons.Outlined.ThumbDown, null, tint = c.textDim, modifier = Modifier.padding(top = 2.dp).size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(m.dislikes.mapNotNull { state.contact(it)?.name }.joinToString("，"), color = c.textDim, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if ((m.likes.isNotEmpty() || m.dislikes.isNotEmpty()) && m.comments.isNotEmpty()) Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(0.5.dp).background(c.hairline))
                    m.comments.forEach { cm ->
                        val a = rememberAnchor()
                        val who = state.contact(cm.authorId)?.name ?: "已删除"
                        val to = cm.replyToId?.let { state.contact(it)?.name }
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium)) { append(who) }
                                if (to != null) { append(" 回复 "); withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium)) { append(to) } }
                                append("：" + cm.text)
                            },
                            color = c.text, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().anchor(a).moonPress({ h.onComment(m, cm) }, { h.onCommentLong(m, cm, a.rect) }, pressedScale = 0.99f, highlightShape = RoundedCornerShape(6.dp), highlight = c.text.copy(0.06f))
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Friend (or self) profile: cover · avatar · bio · actions · their posts. */
@Composable
fun FriendProfileContent(contact: Contact, state: SocialState, actingId: String, h: MomentHandlers, now: Long = System.currentTimeMillis(),
                         players: MomentPlayers? = null, active: Boolean = true, inline: InlineLive? = InlineLive.of(players)) {
    val c = LocalMoonColors.current
    val posts = state.momentsBy(contact.id)
    val headAnchor = rememberAnchor()
    val coverAnchor = rememberAnchor()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    MomentInlineEffect(listState, posts, players, active)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "head") {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                Box(Modifier.fillMaxWidth().height(200.dp).anchor(coverAnchor).moonPress({ h.onCover(contact, coverAnchor.rect) }, { h.onMediaLong(contact, false, coverAnchor.rect) }, highlightShape = null, pressedScale = 1f)) {
                    com.moonlight.feed.ui.design.ProfileMedia(contact.coverPath, contact.coverVideo, Modifier.fillMaxSize(), contact.coverFocus) { ProceduralArt(seedOf(contact.id) + 1, Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to c.bgTop.copy(alpha = 0.95f))))
                }
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    val media = com.moonlight.feed.ui.theme.MediaGlass
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", h.onBack, size = 40.dp, iconSize = 20.dp, colors = media, skinId = "page.back")
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(Icons.Outlined.MoreHoriz, "更多", { h.onProfileLong(contact, headAnchor.rect) }, Modifier.anchor(headAnchor), size = 40.dp, iconSize = 20.dp, colors = media, skinId = "page.more")
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 20.dp).moonPress({ h.onEditProfile(contact) }, { h.onMediaLong(contact, true, headAnchor.rect) }, highlightShape = null)) {
                    Avatar(contact, 88.dp)
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.name, color = c.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (contact.isSelf) "我" else if (contact.isVirtual) "虚拟好友" else "真实账号",
                        color = c.textDim, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(CircleShape).background(c.hairline).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (contact.autoReplyOn && contact.autoReplies.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text("自动回复", color = c.accent, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clip(CircleShape).background(c.accent.copy(0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (contact.bio.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(contact.bio, color = c.textDim, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(6.dp))
                Text("${posts.size} 条动态 · ${posts.sumOf { it.likes.size }} 个赞" + posts.sumOf { it.dislikes.size }.let { if (it > 0) " · $it 个踩" else "" }, color = c.textDim, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!contact.isSelf) GlassPill("发消息", { h.onMessage(contact) }, icon = Icons.AutoMirrored.Outlined.Chat, accent = true)
                    GlassPill(if (contact.isSelf) "发动态" else "以 TA 发布", { h.onPostAs(contact) }, icon = Icons.Outlined.CameraAlt, accent = contact.isSelf)
                    GlassPill("编辑", { h.onEditProfile(contact) }, icon = Icons.Outlined.Edit)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (posts.isEmpty()) item(key = "none") {
            MoonEmptyState("还没有动态", if (contact.isSelf) "发第一条吧" else "替 ${contact.name} 发第一条吧", action = "发布", onAction = { h.onPostAs(contact) })
        }
        items(posts, key = { it.id }) { m -> MomentCard(m, state, actingId, h, now, showAuthor = false, players = players, inline = inline) }
    }
}

class FriendsHandlers(
    val onOpen: (Contact) -> Unit = {},
    val onLong: (Contact, Rect) -> Unit = { _, _ -> },
    val onNew: () -> Unit = {},
    val onBack: (() -> Unit)? = null,
    val onSeedDemo: () -> Unit = {},
)

/** 好友 (contacts): self first, then virtual friends. */
@Composable
fun FriendsContent(state: SocialState, h: FriendsHandlers) {
    val c = LocalMoonColors.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "h") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (h.onBack != null) { GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", h.onBack, size = 40.dp, iconSize = 20.dp, skinId = "page.back"); Spacer(Modifier.width(10.dp)) }
                else Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("好友", color = c.text, style = MaterialTheme.typography.displaySmall)
                    Text("${state.friends.size} 位虚拟好友 · 仅保存在本机", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
                GlassIconButton(Icons.Outlined.PersonAdd, "新建好友", h.onNew, size = 40.dp, iconSize = 20.dp, skinId = "friends.new")
            }
            Spacer(Modifier.height(8.dp))
        }
        item(key = "self") { FriendRow(state.self, state, h, subtitle = state.self.bio.ifBlank { "我的资料 · 点按编辑" }) }
        item(key = "gap") { Spacer(Modifier.height(10.dp)) }
        if (state.friends.isEmpty()) item(key = "e") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MoonEmptyState("还没有好友", "名字、头像、签名都由你来写", action = "新建好友", onAction = h.onNew)
                GlassPill("载入示例", h.onSeedDemo)
            }
        }
        items(state.friends.sortedBy { it.name }, key = { it.id }) { f -> FriendRow(f, state, h) }
    }
}

@Composable
private fun FriendRow(f: Contact, state: SocialState, h: FriendsHandlers, subtitle: String? = null) {
    val c = LocalMoonColors.current
    val a = rememberAnchor()
    val posts = state.moments.count { it.authorId == f.id }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).anchor(a).glassStatic(RoundedCornerShape(18.dp))
            .moonPress({ h.onOpen(f) }, { h.onLong(f, a.rect) }, pressedScale = 0.98f, highlightShape = RoundedCornerShape(18.dp), highlight = c.text.copy(0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(f, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (f.isSelf && f.name != "我") "${f.name}（我）" else f.name, color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(subtitle ?: f.bio.ifBlank { "没有签名" }, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (posts > 0) Text("$posts 条", color = c.textDim, style = MaterialTheme.typography.labelSmall)
        if (f.autoReplyOn && f.autoReplies.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        }
    }
}
