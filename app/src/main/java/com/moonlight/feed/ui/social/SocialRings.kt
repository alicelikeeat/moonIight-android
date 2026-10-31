package com.moonlight.feed.ui.social

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.ui.geometry.Rect
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec

class PostRingHandlers(
    val like: () -> Unit = {}, val dislike: () -> Unit = {}, val comment: () -> Unit = {},
    val forward: (String) -> Unit = {}, val edit: () -> Unit = {}, val copy: () -> Unit = {},
    val setAuthor: (String) -> Unit = {}, val delete: () -> Unit = {},
    /** v5.0: mark the post 自动播放 (只播指定的). */
    val toggleAutoplay: () -> Unit = {},
)

/** 朋友圈动态 long-press: 赞 · 踩 · 评论 · 转发到聊天; 编辑 / 复制 / 改发布者 / 删除 behind ···. */
fun momentPostMenuSpec(m: Moment, s: SocialState, acting: String, anchor: Rect, h: PostRingHandlers): MenuSpec {
    val people = listOf(s.self) + s.friends
    return MenuSpec(
        anchor = anchor, title = s.contact(m.authorId)?.name,
        radial = listOf(
            MenuAction(if (acting in m.likes) "取消赞" else "赞", if (acting in m.likes) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, checked = acting in m.likes, onClick = h.like),
            MenuAction(if (acting in m.dislikes) "取消踩" else "踩", Icons.Outlined.ThumbDown, checked = acting in m.dislikes, onClick = h.dislike),
            MenuAction("评论", Icons.Outlined.ChatBubbleOutline, onClick = h.comment),
            forwardToChat(s, acting, h.forward),
        ),
        groupTitles = listOf("动态"),
        actions = listOf(buildList {
            add(MenuAction("编辑", Icons.Outlined.Edit, onClick = h.edit))
            if (m.text.isNotBlank()) add(MenuAction("复制", Icons.Outlined.ContentCopy, onClick = h.copy))
            if (m.attachments.any { it.kind == com.moonlight.feed.data.social.AttachmentKind.VIDEO })
                add(MenuAction("自动播放", Icons.Outlined.PlayCircleOutline, checked = m.autoplay,
                    subtitle = if (m.autoplay) "只播指定的 模式下这条会自动播放" else "设为 只播指定的 时自动播放", onClick = h.toggleAutoplay))
            add(MenuAction("改发布者", Icons.Outlined.SwapHoriz, children = people.map { p ->
                MenuAction(if (p.isSelf) "我" else p.name, checked = p.id == m.authorId, short = (if (p.isSelf) "我" else p.name).take(3)) { h.setAuthor(p.id) } }))
            add(MenuAction("删除", Icons.Outlined.Delete, destructive = true, onClick = h.delete))
        }),
    )
}

class FriendRingHandlers(
    val chat: () -> Unit = {}, val profile: () -> Unit = {}, val compose: () -> Unit = {}, val edit: () -> Unit = {},
    val chatBetween: (String) -> Unit = {}, val toggleAuto: () -> Unit = {}, val delete: () -> Unit = {},
)

/** 好友 long-press: 聊天 · 主页 · 以 TA 发动态 · 编辑; 替 TA 聊天 / 自动回复 / 删除 behind ···. */
fun friendMenuSpec(c: Contact, s: SocialState, anchor: Rect, h: FriendRingHandlers): MenuSpec = MenuSpec(
    anchor = anchor, title = c.name,
    radial = buildList {
        if (!c.isSelf) add(MenuAction("聊天", Icons.AutoMirrored.Outlined.Chat, onClick = h.chat))
        add(MenuAction("主页", Icons.Outlined.Person, onClick = h.profile))
        add(MenuAction(if (c.isSelf) "发动态" else "以 TA 发动态", Icons.Outlined.CameraAlt, onClick = h.compose))
        add(MenuAction("编辑", Icons.Outlined.Edit, onClick = h.edit))
    },
    groupTitles = listOf("好友"),
    actions = listOf(buildList {
        if (!c.isSelf) {
            val others = s.friends.filter { it.id != c.id }
            if (others.isNotEmpty()) add(MenuAction("替 TA 聊天", Icons.Outlined.Forum, subtitle = "和另一位好友", children = others.map { o -> MenuAction(o.name, short = o.name.take(3)) { h.chatBetween(o.id) } }))
            add(MenuAction("自动回复", Icons.Outlined.SmartToy, checked = c.autoReplyOn, subtitle = if (c.autoReplies.isEmpty()) "未设置预设" else "${c.autoReplies.size} 句", onClick = h.toggleAuto))
            add(MenuAction("删除", Icons.Outlined.Delete, destructive = true, onClick = h.delete))
        }
    }),
)
