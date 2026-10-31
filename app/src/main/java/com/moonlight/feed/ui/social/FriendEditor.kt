package com.moonlight.feed.ui.social

import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Delete
import com.moonlight.feed.ui.design.GlassIconButton
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.MoonSwitch
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors

@Composable
fun SheetField(label: String, value: String, onValue: (String) -> Unit, placeholder: String, minHeight: Int = 0, maxLines: Int = 1) {
    val c = LocalMoonColors.current
    Text(label, color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    Box(Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(14.dp)).background(c.hairline).padding(horizontal = 14.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(placeholder, color = c.textDim, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(value, onValue, textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.accent), maxLines = maxLines, modifier = Modifier.fillMaxWidth())
    }
}

/** Name · avatar (gallery photo or monogram colour) · signature · optional preset auto-replies. */
@Composable
fun FriendEditor(d: Contact, onChange: (Contact) -> Unit, onPickPhoto: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)? = null, onAdjust: (() -> Unit)? = null, onDeleteSaved: (() -> Unit)? = null) {
    val c = LocalMoonColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(d.copy(name = d.name.ifBlank { "?" }), 72.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPill("照片或视频", onPickPhoto, icon = Icons.Outlined.PhotoLibrary)
            if (d.avatarPath != null && onAdjust != null) GlassPill("调整位置", onAdjust, icon = Icons.Outlined.CenterFocusStrong)
            if (d.avatarPath != null) GlassPill("使用字母头像", { onChange(ProfileMediaActions.reset(d, true)) }, icon = Icons.Outlined.TextFields)
            // the photo/video is kept: bring it back without re-picking, or delete the stored copy explicitly
            if (d.prevAvatar != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPill(if (d.avatarPath == null) "用回照片 / 视频" else "用回上一个", { onChange(ProfileMediaActions.restore(d, true)) }, icon = Icons.Outlined.History)
                if (onDeleteSaved != null) GlassIconButton(Icons.Outlined.Delete, "删除已保存的头像", onDeleteSaved, size = 40.dp, iconSize = 18.dp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarPalette.forEachIndexed { i, pal ->
            val sel = d.avatarColor == i && d.avatarPath == null
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(pal))
                    .then(if (sel) Modifier.border(2.dp, c.text, CircleShape) else Modifier)
                    .moonPress({ onChange(d.copy(avatarColor = i, avatarPath = null)) }, highlightShape = CircleShape)
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SheetField("名字", d.name, { onChange(d.copy(name = it.take(24))) }, "例如：阿月")
    Spacer(Modifier.height(12.dp))
    SheetField("签名", d.bio, { onChange(d.copy(bio = it.take(80))) }, "一句话介绍", maxLines = 2)
    if (!d.isSelf) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("自动回复", color = c.text, style = MaterialTheme.typography.bodyLarge)
                Text("你发消息后，TA 从预设里随机回一句（离线，无 AI）", color = c.textDim, style = MaterialTheme.typography.bodySmall)
            }
            MoonSwitch(d.autoReplyOn, { onChange(d.copy(autoReplyOn = it)) })
        }
        if (d.autoReplyOn) {
            Spacer(Modifier.height(10.dp))
            SheetField("预设回复（每行一句）", d.autoReplies.joinToString("\n"), { onChange(d.copy(autoReplies = it.split('\n').take(12))) }, "好呀～\n刚看到\n哈哈哈", minHeight = 88, maxLines = 8)
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        if (onDelete != null) GlassPill("删除", onDelete)
        GlassPill("保存", { if (d.name.isNotBlank()) onSave() }, accent = d.name.isNotBlank())
    }
    if (d.name.isBlank()) Text("需要一个名字", color = c.textDim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
