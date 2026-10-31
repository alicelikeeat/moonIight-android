package com.moonlight.feed.ui.social

import com.moonlight.feed.ui.design.skinFill
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.focusRequester
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.MessageStatus
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ avatars

val AvatarPalette = listOf(
    listOf(Color(0xFF8E9BB8), Color(0xFF4F5B78)),
    listOf(Color(0xFFF2B880), Color(0xFFD9786B)),
    listOf(Color(0xFF7FD1B9), Color(0xFF3C8D93)),
    listOf(Color(0xFF9DB7FF), Color(0xFF5B6CD9)),
    listOf(Color(0xFFC6A6F5), Color(0xFF7E5BC9)),
    listOf(Color(0xFFF5D07A), Color(0xFFC98C3E)),
    listOf(Color(0xFFF59AB0), Color(0xFFC25478)),
    listOf(Color(0xFFA8D88A), Color(0xFF5C9A4C)),
)

fun monogram(name: String): String {
    val n = name.trim()
    if (n.isEmpty()) return "?"
    val first = n.codePointAt(0)
    return if (Character.isLetter(first) && first < 0x2E80) {
        n.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.substring(0, 1).uppercase() }
    } else String(Character.toChars(first))
}

/** Squircle avatar: picked photo, else a gradient monogram. */
@Composable
fun Avatar(contact: Contact?, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false) {
    val c = LocalMoonColors.current
    val shape = RoundedCornerShape(size * 0.3f)
    val pal = AvatarPalette[((contact?.avatarColor ?: 0) % AvatarPalette.size + AvatarPalette.size) % AvatarPalette.size]
    Box(
        modifier.size(size)
            .then(if (ring) Modifier.border(2.dp, c.accent, shape).padding(3.dp) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(pal)),
        contentAlignment = Alignment.Center,
    ) {
        val path = contact?.avatarPath
        if (path != null) com.moonlight.feed.ui.design.ProfileMedia(path, contact.avatarVideo, Modifier.fillMaxSize(), contact.avatarFocus) {}
        else Text(
            monogram(contact?.name ?: "?"), color = Color.White.copy(0.96f),
            fontSize = (size.value * (if (monogram(contact?.name ?: "").length > 1) 0.36f else 0.42f)).sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 2×2 mosaic for group chats. */
@Composable
fun GroupAvatar(members: List<Contact?>, size: Dp) {
    val c = LocalMoonColors.current
    val cell = (size - 6.dp) / 2
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.hairline).padding(2.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            members.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { row.forEach { Avatar(it, cell) } }
            }
        }
    }
}

// ------------------------------------------------------------------ time

fun relTime(t: Long, now: Long = System.currentTimeMillis()): String {
    val d = now - t
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = cal.get(Calendar.YEAR) == then.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.get(Calendar.YEAR) == then.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return when {
        d < 0 -> SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(t))
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        sameDay || d < 6 * 3_600_000 -> "${d / 3_600_000} 小时前"
        yesterday -> "昨天 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(t))
        d < 7 * 86_400_000L -> "${d / 86_400_000L} 天前"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(t))
    }
}

fun clockTime(t: Long, now: Long = System.currentTimeMillis()): String {
    val day = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    return if (day.format(Date(t)) == day.format(Date(now))) SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(t))
    else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(t))
}

// ------------------------------------------------------------------ media

@Composable
fun AttachmentTile(
    a: Attachment, modifier: Modifier, corner: Dp = 10.dp, onClick: () -> Unit = {}, onLongClick: (() -> Unit)? = null,
    live: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color(0x22000000)).moonPress(onClick, onLongClick, pressedScale = 0.96f, highlightShape = null)) {
        MoonImage(a.model(), Modifier.fillMaxSize(), revealKey = "att:" + a.id)
        if (live != null) live()
        else if (a.kind != AttachmentKind.IMAGE) {
            Box(
                Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.32f)).border(0.5.dp, Color.White.copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "播放", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** WeChat-style grid: 1 → large, 2/4 → 2 columns, else 3 columns. */
@Composable
fun MediaGrid(
    items: List<Attachment>, modifier: Modifier = Modifier, maxWidth: Dp = 300.dp,
    liveIndex: Int = -1, live: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    /** Long-press on a video tile (index, tile rect). null → the press falls through to the parent (post menu). */
    onVideoLong: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    /** v5.0: per-tile inline windows (several may play at once). */
    inline: InlineLive? = null,
    onOpen: (Int) -> Unit = {},
) {
    @Composable
    fun tile(i: Int, a: Attachment, m: Modifier, corner: Dp) {
        val an = com.moonlight.feed.ui.design.rememberAnchor()
        val long = onVideoLong?.takeIf { a.kind != AttachmentKind.IMAGE }?.let { f -> { f(i, an.rect) } }
        AttachmentTile(a, m.then(Modifier.anchor(an)), corner, { onOpen(i) }, onLongClick = long, live = inline?.layer(a) ?: live.takeIf { liveIndex == i })
    }
    if (items.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = minOf(this.maxWidth, maxWidth)
        val gap = 4.dp
        if (items.size == 1) {
            val a = items[0]
            val ratio = if (a.kind == AttachmentKind.IMAGE) 0.8f else 16f / 10f
            tile(0, a, Modifier.width(if (ratio < 1f) w * 0.66f else w * 0.86f).aspectRatio(ratio), 12.dp)
        } else {
            val cols = if (items.size == 2 || items.size == 4) 2 else 3
            val cell = (w * (if (cols == 2) 0.72f else 1f) - gap * (cols - 1)) / cols
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                items.take(9).withIndex().chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { (i, a) -> tile(i, a, Modifier.size(cell), 8.dp) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ status ticks

/** Subtle delivery state: ⏱ sending · ✓ sent · ✓✓ delivered · ✓✓ (accent) read · ! failed · 📱 local-only. */
@Composable
fun StatusTick(s: MessageStatus, modifier: Modifier = Modifier, tint: Color = LocalMoonColors.current.textDim) {
    val c = LocalMoonColors.current
    val (icon, color, desc) = when (s) {
        MessageStatus.LOCAL -> Triple(Icons.Outlined.PhoneAndroid, tint.copy(0.6f), "仅本机")
        MessageStatus.SENDING -> Triple(Icons.Outlined.AccessTime, tint.copy(0.7f), "发送中")
        MessageStatus.SENT -> Triple(Icons.Outlined.Done, tint.copy(0.8f), "已发送")
        MessageStatus.DELIVERED -> Triple(Icons.Outlined.DoneAll, tint.copy(0.8f), "已送达")
        MessageStatus.READ -> Triple(Icons.Outlined.DoneAll, c.accent, "已读")
        MessageStatus.FAILED -> Triple(Icons.Outlined.ErrorOutline, c.danger, "发送失败")
    }
    Icon(icon, desc, tint = color, modifier = modifier.size(13.dp))
}

// ------------------------------------------------------------------ speaking-as

/** "以 [avatar] 名字 ▾" chip; tap expands the participant strip, long-press opens the radial picker. */
@Composable
fun SpeakerChip(who: Contact?, expanded: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = LocalMoonColors.current
    Row(
        modifier.height(36.dp).moonPress(onClick, onLongClick, highlightShape = CircleShape).glass(CircleShape, c).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(who, 28.dp)
        Spacer(Modifier.width(6.dp))
        Text(who?.name ?: "?", color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 72.dp))
        Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, tint = c.textDim, modifier = Modifier.size(16.dp))
    }
}

/** Horizontal strip of everyone who can speak; selected gets the accent ring. */
@Composable
fun SpeakerStrip(people: List<Contact>, selectedId: String?, onSelect: (Contact) -> Unit, label: String = "以谁的身份发言") {
    val c = LocalMoonColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass(RoundedCornerShape(20.dp), c).padding(vertical = 10.dp)) {
        Text(label, color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            people.forEach { p ->
                val sel = p.id == selectedId
                val s by animateFloatAsState(if (sel) 1f else 0.92f, spring(0.55f, 600f), label = "sp")
                Column(
                    Modifier.width(58.dp).moonPress({ onSelect(p) }, highlightShape = null).graphicsLayer { scaleX = s; scaleY = s },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(p, 44.dp, ring = sel)
                    Spacer(Modifier.height(4.dp))
                    Text(if (p.isSelf) "我" else p.name, color = if (sel) c.text else c.textDim, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Chat / comment input: speaking-as chip · glass text capsule · attach · send. */
@Composable
fun SpeakInputBar(
    speaker: Contact?,
    people: List<Contact>,
    text: String,
    onText: (String) -> Unit,
    onSpeaker: (Contact) -> Unit,
    onSend: () -> Unit,
    stripOpen: Boolean,
    onToggleStrip: () -> Unit,
    onAttach: (() -> Unit)? = null,
    onSpeakerLong: (() -> Unit)? = null,
    placeholder: String = "发消息…",
    modifier: Modifier = Modifier,
    focus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val c = LocalMoonColors.current
    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(stripOpen, enter = fadeIn() + expandVertically(spring(0.8f, 500f)), exit = fadeOut() + shrinkVertically()) {
            Column { SpeakerStrip(people, speaker?.id, onSpeaker); Spacer(Modifier.height(8.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeakerChip(speaker, stripOpen, onToggleStrip, onSpeakerLong)
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).glass(RoundedCornerShape(18.dp), c).padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) Text(placeholder, color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                BasicTextField(
                    text, onText, textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.accent), maxLines = 5,
                    modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
                )
            }
            if (onAttach != null && text.isBlank()) {
                Box(Modifier.size(36.dp).moonPress(onAttach, highlightShape = CircleShape).glass(CircleShape, c), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AddCircleOutline, "添加图片或视频", tint = c.text, modifier = Modifier.size(20.dp))
                }
            } else {
                val on = text.isNotBlank()
                val skin = com.moonlight.feed.ui.design.rememberButtonSkin("chat.send")
                Box(
                    Modifier.size(36.dp).moonPress(if (on) onSend else null, highlightShape = CircleShape, enabled = on)
                        .then(if (skin != null) Modifier.skinFill(skin, CircleShape).graphicsLayer { alpha = if (on) 1f else 0.55f }
                              else Modifier.clip(CircleShape).background(if (on) c.accent else c.hairline)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.Send, "发送", tint = skin?.ink ?: if (on) c.onAccent else c.textDim, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}


