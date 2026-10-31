package com.moonlight.feed.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.Conversation
import com.moonlight.feed.data.social.Message
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.design.GlassIconButton
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.theme.LocalMoonColors
import androidx.compose.foundation.layout.offset

fun convTitle(conv: Conversation, s: SocialState): String {
    conv.title?.let { return it }
    val names = conv.memberIds.mapNotNull { s.contact(it) }
    return if (conv.memberIds.contains(s.selfId)) names.filter { !it.isSelf }.joinToString("、") { it.name }.ifBlank { "我" }
    else if (names.size == 2) "${names[0].name} · ${names[1].name}" else names.joinToString("、") { it.name }
}

/** Whose messages sit on the right: me if I'm in the chat, otherwise the first member. */
fun perspectiveOf(conv: Conversation, s: SocialState) = if (s.selfId in conv.memberIds) s.selfId else conv.memberIds.firstOrNull() ?: s.selfId

fun messagePreview(m: Message?, s: SocialState, conv: Conversation): String {
    m ?: return "还没有消息"
    val body = m.text.ifBlank { if (m.attachments.any { it.kind != com.moonlight.feed.data.social.AttachmentKind.IMAGE }) "[视频]" else if (m.attachments.isNotEmpty()) "[图片]" else "" }
    val who = s.contact(m.senderId)
    return if (conv.isGroup || s.selfId !in conv.memberIds) "${who?.name ?: "?"}：$body" else if (who?.isSelf == true) "我：$body" else body
}

class ChatListHandlers(
    val onOpen: (Conversation) -> Unit = {},
    val onLong: (Conversation, Rect) -> Unit = { _, _ -> },
    val onNew: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onSeedDemo: () -> Unit = {},
    /** Set when the list is the right page of 朋友圈 (动态 ↔ 消息): back arrow returns to 动态. */
    val onBack: (() -> Unit)? = null,
)

@Composable
fun ChatListContent(state: SocialState, h: ChatListHandlers, now: Long = System.currentTimeMillis()) {
    val c = LocalMoonColors.current
    val convs = state.conversations.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { state.lastMessage(it.id)?.createdAt ?: it.updatedAt })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "h") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (h.onBack != null) 12.dp else 20.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (h.onBack != null) { GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "朋友圈", h.onBack, size = 40.dp, iconSize = 20.dp, skinId = "page.back"); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("消息", color = c.text, style = MaterialTheme.typography.displaySmall)
                    val unread = state.totalUnread
                    Text(if (unread > 0) "$unread 条未读 · 你可以替任何人发言" else "本机对话 · 你可以替任何人发言", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
                GlassIconButton(Icons.Outlined.People, "好友", h.onFriends, size = 40.dp, iconSize = 20.dp, skinId = "moments.friends")
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Outlined.AddComment, "新对话", h.onNew, size = 40.dp, iconSize = 20.dp, skinId = "chat.new")
            }
        }
        if (convs.isEmpty()) item(key = "e") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MoonEmptyState("还没有对话", "和好友聊天，或让两位好友互相聊", action = "新对话", onAction = h.onNew)
                if (state.friends.isEmpty()) GlassPill("载入示例", h.onSeedDemo)
            }
        }
        items(convs, key = { it.id }) { conv ->
            val a = rememberAnchor()
            val last = state.lastMessage(conv.id)
            val members = conv.memberIds.mapNotNull { state.contact(it) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).anchor(a).glassStatic(RoundedCornerShape(20.dp))
                    .moonPress({ h.onOpen(conv) }, { h.onLong(conv, a.rect) }, pressedScale = 0.98f, highlightShape = RoundedCornerShape(20.dp), highlight = c.text.copy(0.05f))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val others = members.filter { !it.isSelf }
                if (conv.isGroup) GroupAvatar(members, 48.dp)
                else if (state.selfId in conv.memberIds) Avatar(others.firstOrNull(), 48.dp)
                else Box(Modifier.size(48.dp)) {
                    Avatar(members.getOrNull(0), 34.dp)
                    Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(11.dp)).background(c.bgTop).padding(2.dp)) { Avatar(members.getOrNull(1), 30.dp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(convTitle(conv, state), color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (conv.isGroup) Text("  ${conv.memberIds.size}", color = c.textDim, style = MaterialTheme.typography.labelSmall)
                        if (conv.pinned) { Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.PushPin, "置顶", tint = c.textDim, modifier = Modifier.size(12.dp)) }
                        Spacer(Modifier.weight(1f))
                        Text(last?.let { relTime(it.createdAt, now) } ?: "", color = c.textDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (last != null && last.senderId == perspectiveOf(conv, state)) { StatusTick(last.status); Spacer(Modifier.width(3.dp)) }
                        Text(messagePreview(last, state, conv), color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val n = state.unread(conv.id)
                        if (n > 0) { Spacer(Modifier.width(6.dp)); UnreadBadge(n, Modifier.offset(x = (-4).dp, y = 2.dp)) }
                    }
                }
            }
        }
    }
}

class ChatHandlers(
    val onBack: () -> Unit = {},
    val onTitle: (Rect) -> Unit = {},
    val onMessageLong: (Message, Rect) -> Unit = { _, _ -> },
    val onOpenMedia: (Message, Int) -> Unit = { _, _ -> },
    val onAvatar: (String) -> Unit = {},
    val onText: (String) -> Unit = {},
    val onSpeaker: (Contact) -> Unit = {},
    val onToggleStrip: () -> Unit = {},
    val onSpeakerLong: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onAttach: () -> Unit = {},
)

@Composable
fun ChatContent(
    conv: Conversation, state: SocialState, speakerId: String, text: String, stripOpen: Boolean, h: ChatHandlers,
    now: Long = System.currentTimeMillis(), listState: LazyListState = rememberLazyListState(),
) {
    val c = LocalMoonColors.current
    val members = conv.memberIds.mapNotNull { state.contact(it) }
    val me = perspectiveOf(conv, state)
    val msgs = state.messagesOf(conv.id)
    val titleAnchor = rememberAnchor()
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", h.onBack, size = 40.dp, iconSize = 20.dp, skinId = "page.back")
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).anchor(titleAnchor).moonPress({ h.onTitle(titleAnchor.rect) }, { h.onTitle(titleAnchor.rect) }, highlightShape = RoundedCornerShape(14.dp)).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conv.isGroup) GroupAvatar(members, 36.dp) else Avatar(members.firstOrNull { it.id != state.selfId } ?: members.firstOrNull(), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(convTitle(conv, state), color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (conv.remote) "服务器对话" else "本机对话 · ${members.size} 人", color = c.textDim, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.hairline))
        val showNames = conv.isGroup || state.selfId !in conv.memberIds
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, reverseLayout = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            val rev = msgs.asReversed()
            items(rev.size, key = { rev[it].id }) { i ->
                val m = rev[i]
                val prev = rev.getOrNull(i + 1)
                val gap = prev == null || m.createdAt - prev.createdAt > 5 * 60_000
                val sameRun = !gap && prev?.senderId == m.senderId
                Column {
                    if (gap) Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(clockTime(m.createdAt, now), color = c.textDim, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clip(CircleShape).background(c.hairline).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    Bubble(m, state, mine = m.senderId == me, showName = showNames && !sameRun, showAvatar = !sameRun, h = h)
                }
            }
        }
        val people = members.sortedBy { if (it.isSelf) 0 else 1 }
        Box(Modifier.navigationBarsPadding()) {
            SpeakInputBar(
                speaker = state.contact(speakerId), people = people, text = text, onText = h.onText, onSpeaker = h.onSpeaker, onSend = h.onSend,
                stripOpen = stripOpen, onToggleStrip = h.onToggleStrip, onAttach = h.onAttach, onSpeakerLong = h.onSpeakerLong,
                placeholder = state.contact(speakerId)?.let { if (it.isSelf) "发消息…" else "以 ${it.name} 的身份说…" } ?: "发消息…",
            )
        }
    }
}

@Composable
private fun Bubble(m: Message, state: SocialState, mine: Boolean, showName: Boolean, showAvatar: Boolean, h: ChatHandlers) {
    val c = LocalMoonColors.current
    val who = state.contact(m.senderId)
    val a = rememberAnchor()
    Row(
        Modifier.fillMaxWidth().padding(top = if (showAvatar) 6.dp else 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            Box(Modifier.width(36.dp)) { if (showAvatar) Box(Modifier.moonPress({ h.onAvatar(m.senderId) }, highlightShape = null)) { Avatar(who, 34.dp) } }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 270.dp)) {
            if (showName) Text(who?.name ?: "已删除", color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 3.dp))
            val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            Column(Modifier.anchor(a).moonPress({}, { h.onMessageLong(m, a.rect) }, pressedScale = 0.97f, highlightShape = shape, hapticOnClick = false)) {
                if (m.attachments.isNotEmpty()) {
                    if (m.attachments.size == 1) {
                        val at = m.attachments[0]
                        AttachmentTile(at, Modifier.width(196.dp).aspectRatio(if (at.kind == com.moonlight.feed.data.social.AttachmentKind.IMAGE) 0.8f else 1.6f), 16.dp, onClick = { h.onOpenMedia(m, 0) }, onLongClick = { h.onMessageLong(m, a.rect) })
                    } else MediaGrid(m.attachments, Modifier.width(220.dp), maxWidth = 220.dp) { i -> h.onOpenMedia(m, i) }
                    if (m.text.isNotBlank()) Spacer(Modifier.height(4.dp))
                }
                if (m.text.isNotBlank()) {
                    Box(
                        (if (mine) Modifier.clip(shape).background(c.accent) else Modifier.glassStatic(shape))
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) { Text(m.text, color = if (mine) c.onAccent else c.text, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (mine) Row(Modifier.padding(top = 2.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.status == com.moonlight.feed.data.social.MessageStatus.FAILED) Text("未发送 ", color = c.danger, style = MaterialTheme.typography.labelSmall)
                StatusTick(m.status)
            }
        }
        if (mine) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(36.dp)) { if (showAvatar) Box(Modifier.moonPress({ h.onAvatar(m.senderId) }, highlightShape = null)) { Avatar(who, 34.dp) } }
        }
    }
}

/** New conversation: pick 2+ participants (me optional), optional group name. */
@Composable
fun NewChatPicker(state: SocialState, selected: Set<String>, onToggle: (Contact) -> Unit, onStart: () -> Unit) {
    val c = LocalMoonColors.current
    Text("选 2 位或更多参与者；可以不包括你自己，让好友之间聊天。", color = c.textDim, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().glassStatic(RoundedCornerShape(18.dp))) {
        (listOf(state.self) + state.friends).forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(0.5.dp).background(c.hairline))
            val sel = p.id in selected
            Row(Modifier.fillMaxWidth().moonPress({ onToggle(p) }, pressedScale = 0.98f, highlight = c.text.copy(0.05f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(if (p.isSelf && p.name != "我") "${p.name}（我）" else p.name, color = c.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(if (sel) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (sel) c.accent else c.textDim, modifier = Modifier.size(22.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val ok = selected.size >= 2
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassPill(if (!ok) "至少选 2 人" else if (selected.size > 2) "建群聊（${selected.size} 人）" else "开始聊天", { if (ok) onStart() }, accent = ok)
    }
}
