package com.moonlight.feed.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.DockHide
import com.moonlight.feed.data.HoldPresent
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.data.TabDock
import com.moonlight.feed.data.VirtualCopy
import com.moonlight.feed.ui.SettingsDivider
import com.moonlight.feed.ui.SettingsGroup
import com.moonlight.feed.ui.SettingsRow
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.theme.LocalMoonColors

@Composable
fun YuedanAppearanceRows(s: SettingsRepository.Snapshot, a: SettingsActions) {
    val present = HoldPresent.of(s.holdPresent)
    SegmentRow(
        "菜单样式", present.hint,
        Icons.Outlined.BlurOn, LocalMoonColors.current.accent,
        HoldPresent.entries.map { it.short }, present.ordinal,
    ) { a.setHoldPresent(HoldPresent.entries[it].name) }
    SettingsDivider()
    val dock = TabDock.of(s.tabDock)
    SegmentRow(
        "底栏", dock.hint,
        Icons.Outlined.TouchApp, LocalMoonColors.current.accent,
        TabDock.entries.map { it.label }, dock.ordinal,
    ) { a.setTabDock(TabDock.entries[it].name) }
    SettingsDivider()
    val hide = DockHide.normalize(s.dockHideSec)
    SegmentRow(
        "底栏自动隐藏",
        if (hide <= 0) "底栏一直显示" else "无操作 ${hide} 秒后淡出，轻点屏幕底部会再显示",
        Icons.Outlined.TouchApp, LocalMoonColors.current.accent,
        DockHide.CHOICES.map { DockHide.label(it) }, DockHide.CHOICES.indexOf(hide).coerceAtLeast(0),
    ) { a.setDockHideSec(DockHide.CHOICES[it]) }
}

@Composable
fun YuedanPlaybackRow(s: SettingsRepository.Snapshot, a: SettingsActions) {
        Toggle(
        "视频保持竖屏",
        if (s.portraitPlayback) "播放页保持竖屏。关闭后播放页可以横过来。" else "播放页跟随设备方向，其它页面仍是竖屏。",
        Icons.Outlined.ScreenRotation,
        s.portraitPlayback,
        a.setPortraitPlayback,
    )
}

@Composable
fun YuedanVirtualGroup(s: SettingsRepository.Snapshot, a: SettingsActions) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(s.virtualProvider) { mutableStateOf("") }
    SettingsGroup(
        "虚拟好友",
        footer = "默认关闭。打开后，应用在前台按间隔从已启用的公开内容源取一条，用本机模板或你自己的 API Key 写一句文案，只发布在这台手机的朋友圈里。密钥保存在本机，不会写进源码。",
    ) {
        Toggle(
            "自动生成朋友圈动态",
            if (s.virtualFriends) "每 ${VirtualCopy.intervalMinutes(s.virtualInterval)} 分钟一条" else "关闭",
            Icons.Outlined.People,
            s.virtualFriends,
            a.setVirtualFriends,
        )
        SettingsDivider()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text("发布间隔", color = LocalMoonColors.current.text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 240).forEach { minutes ->
                    val label = if (minutes < 60) "${minutes}分" else "${minutes / 60}时"
                    GlassPill(label, { a.setVirtualInterval(minutes) }, accent = VirtualCopy.intervalMinutes(s.virtualInterval) == minutes)
                }
            }
        }
        SettingsDivider()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text("文案服务", color = LocalMoonColors.current.text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill("OpenAI", { a.setVirtualProvider("openai") }, accent = s.virtualProvider != "grok")
                GlassPill("Grok", { a.setVirtualProvider("grok") }, accent = s.virtualProvider == "grok")
            }
        }
        SettingsDivider()
        SettingsRow(
            "API Key",
            subtitle = if (s.virtualKey.isBlank()) "未设置 · 使用本机模板" else "已保存在本机",
            icon = Icons.Outlined.BlurOn,
            onClick = { draft = ""; editing = true },
        )
    }
    if (editing) {
        val c = LocalMoonColors.current
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(if (s.virtualProvider == "grok") "Grok API Key" else "OpenAI API Key") },
            text = {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { a.setVirtualKey(draft); editing = false }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { a.setVirtualKey(""); editing = false }) { Text("清除") }
            },
        )
    }
}
