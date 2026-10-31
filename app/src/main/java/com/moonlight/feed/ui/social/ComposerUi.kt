package com.moonlight.feed.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.SettingsDivider
import com.moonlight.feed.ui.SettingsRow
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.theme.LocalMoonColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MomentDraft(
    val posterId: String,
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** null = 现在 */
    val time: Long? = null,
    val editingId: String? = null,
) { val canPublish get() = text.isNotBlank() || attachments.isNotEmpty() }

class ComposerHandlers(
    val onCancel: () -> Unit = {},
    val onPublish: () -> Unit = {},
    val onText: (String) -> Unit = {},
    val onPoster: (Contact) -> Unit = {},
    val onAddMedia: (Rect) -> Unit = {},
    val onRemoveMedia: (Attachment) -> Unit = {},
    val onOpenMedia: (Int) -> Unit = {},
    val onTime: (Rect) -> Unit = {},
)

fun draftTimeLabel(t: Long?) = if (t == null) "现在" else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(t))

/** 发动态: who posts · text · media · time. */
@Composable
fun ComposerContent(draft: MomentDraft, state: SocialState, h: ComposerHandlers) {
    val c = LocalMoonColors.current
    val addAnchor = rememberAnchor()
    val timeAnchor = rememberAnchor()
    val people = listOf(state.self) + state.friends
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("取消", color = c.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.moonPress(h.onCancel, highlightShape = null).padding(6.dp))
            Text(if (draft.editingId != null) "编辑动态" else "发动态", color = c.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).moonPress(if (draft.canPublish) h.onPublish else null, highlightShape = CircleShape, enabled = draft.canPublish)
                    .background(if (draft.canPublish) c.accent else c.hairline).padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text(if (draft.editingId != null) "保存" else "发布", color = if (draft.canPublish) c.onAccent else c.textDim, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Spacer(Modifier.height(6.dp))
            SpeakerStrip(people, draft.posterId, h.onPoster, label = "以谁的身份发布")
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).glassStatic(RoundedCornerShape(22.dp)).padding(16.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 110.dp)) {
                    val poster = state.contact(draft.posterId)
                    if (draft.text.isEmpty()) Text(if (poster?.isSelf != false) "这一刻的想法…" else "替 ${poster.name} 写点什么…", color = c.textDim, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(draft.text, h.onText, textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 6.dp
                    val cell = (maxWidth - gap * 2) / 3
                    val tiles = draft.attachments.take(9)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        (tiles.map<Attachment, Attachment?> { it } + if (tiles.size < 9) listOf(null) else emptyList()).withIndex().chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { (i, a) ->
                                    if (a == null) {
                                        Box(
                                            Modifier.size(cell).anchor(addAnchor).clip(RoundedCornerShape(12.dp)).moonPress({ h.onAddMedia(addAnchor.rect) }, highlightShape = RoundedCornerShape(12.dp))
                                                .background(c.hairline).border(0.5.dp, c.glassStroke.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Outlined.Add, "添加", tint = c.textDim, modifier = Modifier.size(26.dp))
                                                Text("库 · 相册 · 拍摄", color = c.textDim, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    } else Box(Modifier.size(cell)) {
                                        AttachmentTile(a, Modifier.fillMaxSize(), 12.dp, onClick = { h.onOpenMedia(i) })
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(0.45f))
                                                .moonPress({ h.onRemoveMedia(a) }, highlightShape = CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Outlined.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).glassStatic(RoundedCornerShape(20.dp))) {
                Box(Modifier.anchor(timeAnchor)) {
                    SettingsRow("发布时间", trailing = draftTimeLabel(draft.time), icon = Icons.Outlined.Schedule, iconTint = Color(0xFF6FB7E8), onClick = { h.onTime(timeAnchor.rect) })
                }
                SettingsDivider()
                SettingsRow("谁可以看", trailing = "仅本机", icon = Icons.Outlined.Lock, iconTint = Color(0xFF8FD18B), chevron = false)
            }
            Text("朋友圈与好友都是虚拟的，只保存在这台设备上。", color = c.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp))
        }
    }
}

/** Multi-select grid of library items (sheet content). */
@Composable
fun LibraryPicker(items: List<MediaEntry>, selected: Set<String>, onToggle: (MediaEntry) -> Unit, onDone: () -> Unit) {
    val c = LocalMoonColors.current
    if (items.isEmpty()) {
        Text("库里还没有内容", color = c.textDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        val gap = 6.dp
        val cell = (maxWidth - gap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { e ->
                        val sel = e.id in selected
                        Box(Modifier.size(cell).clip(RoundedCornerShape(12.dp)).moonPress({ onToggle(e) }, highlightShape = null)) {
                            MoonImage(e.coverUrl ?: e.localPath?.let { java.io.File(it) } ?: e.remoteUrl, Modifier.fillMaxSize())
                            if (sel) Box(Modifier.fillMaxSize().background(c.accent.copy(0.22f)).border(2.dp, c.accent, RoundedCornerShape(12.dp)))
                            Icon(if (sel) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (sel) c.accent else Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GlassPill(if (selected.isEmpty()) "完成" else "添加 ${selected.size} 项", onDone, icon = Icons.Outlined.CheckCircle, accent = true) }
}
