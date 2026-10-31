package com.moonlight.feed.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.DeleteAnim
import com.moonlight.feed.data.HoldContext
import com.moonlight.feed.data.MomentsAutoplayMode
import com.moonlight.feed.data.TimedActions
import com.moonlight.feed.data.V5Prefs
import com.moonlight.feed.ui.SettingsDivider
import com.moonlight.feed.ui.SettingsGroup
import com.moonlight.feed.ui.SettingsRow
import com.moonlight.feed.ui.design.AnchorState
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MoonSwitch
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.theme.LocalMoonColors

/** v5.0 settings write-through: read-modify-write of [V5Prefs]. */
typealias V5Update = ((V5Prefs) -> V5Prefs) -> Unit

@Composable
private fun V5Toggle(title: String, subtitle: String, icon: ImageVector, checked: Boolean, tint: Color, onChange: (Boolean) -> Unit) {
    SettingsRow(title, subtitle = subtitle, icon = icon, iconTint = tint, onClick = { onChange(!checked) }, trailingContent = { MoonSwitch(checked, onChange) })
}

/** 朋友圈自动播放: 全部同时播放 / 只播指定的 / 关闭 · 仅 Wi‑Fi · the friends whose posts always play. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MomentsAutoplayRows(p: V5Prefs, social: com.moonlight.feed.data.social.SocialState?, up: V5Update) {
    val c = LocalMoonColors.current
    val mode = p.momentsMode
    SegmentRow(
        "朋友圈自动播放", when (mode) {
            MomentsAutoplayMode.ALL -> "看得见的视频都在动态里静音循环（最多 ${com.moonlight.feed.data.social.MomentAutoplay.MAX_INLINE} 个，其余显示首帧）"
            MomentsAutoplayMode.PICKED -> "只播 ⋯ 里标了「自动播放」的动态，和下面选中的好友"
            MomentsAutoplayMode.OFF -> "不自动播放 · 点视频播放 / 暂停，长按进入播放页"
        },
        Icons.Outlined.PlayCircleOutline, Color(0xFF7FD1AE),
        MomentsAutoplayMode.entries.map { it.label }, mode.ordinal,
    ) { i -> up { it.copy(momentsMode = MomentsAutoplayMode.entries[i]) } }
    if (mode == MomentsAutoplayMode.PICKED) {
        val people = social?.let { listOf(it.self) + it.friends }.orEmpty()
        Text("这些人的动态自动播放", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
        FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (people.isEmpty()) Text("还没有好友", color = c.textDim, style = MaterialTheme.typography.bodySmall)
            people.forEach { f ->
                val on = f.id in p.autoplayFriends
                GlassPill(if (f.isSelf) "我" else f.name, { up { it.copy(autoplayFriends = if (on) it.autoplayFriends - f.id else it.autoplayFriends + f.id) } }, accent = on)
            }
        }
    }
    SettingsDivider()
    V5Toggle("仅 Wi‑Fi", "网络视频只在 Wi‑Fi 下自动播放；本机视频不受影响", Icons.Outlined.Wifi, p.autoplayWifiOnly, Color(0xFF6FB7E8)) { v -> up { it.copy(autoplayWifiOnly = v) } }
}

/** 定时长按 + 全部按键. */
@Composable
fun TimedHoldGroup(p: V5Prefs, up: V5Update) {
    val c = LocalMoonColors.current
    val overlay = LocalOverlay.current
    SettingsGroup("定时长按", footer = "长按照常打开菜单；继续按住到设定时间，分配给该场景的按钮会自动执行（有「撤销」）。删除等危险操作不能被分配。移动手指会取消计时。") {
        V5Toggle("定时长按", if (p.timedOn) "按住 ${fmtSec(p.timedSeconds)} 触发" else "关闭 · 长按只打开菜单", Icons.Outlined.Timer, p.timedOn, Color(0xFFFFD27A)) { v -> up { it.copy(timedOn = v) } }
        androidx.compose.animation.AnimatedVisibility(p.timedOn) {
            Column {
                SettingsDivider()
                val custom = p.timedSeconds != 5f && p.timedSeconds != 10f
                SegmentRow("触发时间", "从按下开始计时", Icons.Outlined.Timer, Color(0xFFFFD27A), listOf("5 秒", "10 秒", "自定义"), if (custom) 2 else if (p.timedSeconds == 10f) 1 else 0) { i ->
                    up { it.copy(timedSeconds = when (i) { 0 -> 5f; 1 -> 10f; else -> if (custom) it.timedSeconds else 7f }) }
                }
                if (custom) {
                    var v by remember(p.timedSeconds) { mutableFloatStateOf(p.timedSeconds) }
                    Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Slider(v, { v = (it * 2).toInt() / 2f }, valueRange = 3f..15f, onValueChangeFinished = { up { it.copy(timedSeconds = V5Prefs.clampSeconds(v)) } },
                            modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent))
                        Spacer(Modifier.width(10.dp))
                        Text(fmtSec(v), color = c.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
                SettingsDivider()
                V5Toggle("显示计时", "按住时在手指旁显示 “3.2s”", Icons.Outlined.Timer, p.timedShowTime, Color(0xFFF2A65A)) { v -> up { it.copy(timedShowTime = v) } }
                SettingsDivider()
                V5Toggle("倒计时动画", "手指周围的光环填满（全部按键为月相）", Icons.Outlined.AutoAwesome, p.timedCountdown, Color(0xFFB9A7FF)) { v -> up { it.copy(timedCountdown = v) } }
                SettingsDivider()
                V5Toggle("到时自动触发", if (p.timedAutoFire) "执行该场景分配的按钮" else "关闭 · 到时只弹出菜单", Icons.Outlined.Bolt, p.timedAutoFire, Color(0xFFFF8A7A)) { v -> up { it.copy(timedAutoFire = v) } }
                HoldContext.entries.forEach { ctx ->
                    SettingsDivider()
                    val anchor = remember(ctx) { AnchorState() }
                    val cur = TimedActions.option(ctx, p.timedAssign[ctx])
                    Box(Modifier.anchor(anchor)) {
                        SettingsRow("分配 · ${ctx.label}", subtitle = cur?.label ?: "不分配 · 到时只打开菜单", icon = Icons.Outlined.TouchApp, iconTint = Color(0xFF9CC7FF), onClick = {
                            overlay.showMenu(MenuSpec(anchor = anchor.rect, title = "${ctx.label} · 到时执行", list = true, actions = listOf(
                                listOf(MenuAction("不分配", checked = cur == null, subtitle = "只打开菜单") { up { it.copy(timedAssign = it.timedAssign - ctx) } }) +
                                    TimedActions.byContext[ctx].orEmpty().map { o ->
                                        MenuAction(o.label, checked = cur?.key == o.key, subtitle = if (o.reversible) "可撤销" else null) { up { it.copy(timedAssign = it.timedAssign + (ctx to o.key)) } }
                                    },
                            )))
                        })
                    }
                }
            }
        }
        SettingsDivider()
        V5Toggle("全部按键", if (p.centerPanel) "按住屏幕中心 5 秒，打开当前页面与全局的所有按钮" else "关闭", Icons.Outlined.Apps, p.centerPanel, Color(0xFFCFDBFF)) { v -> up { it.copy(centerPanel = v) } }
    }
}

/** 录屏. */
@Composable
fun RecordGroup(p: V5Prefs, up: V5Update) {
    SettingsGroup("录屏", footer = "从 底部标签 ··· / 内容的 ··· / 全部按键 开始；顶部的 ⏹ 胶囊或通知停止。保存在 库。受保护（DRM）的内容可能录成黑屏，月淡不会绕过。") {
        V5Toggle("录制应用声音", if (android.os.Build.VERSION.SDK_INT >= 29) "Android 10+ · 允许被录制的声音" else "需要 Android 10 或更新", Icons.Outlined.GraphicEq, p.recordAppAudio, Color(0xFF7FD1AE)) { v -> up { it.copy(recordAppAudio = v) } }
        SettingsDivider()
        V5Toggle("麦克风", "同时录入你的声音（需要录音权限）", Icons.Outlined.Mic, p.recordMic, Color(0xFFFF8A7A)) { v -> up { it.copy(recordMic = v) } }
        SettingsDivider()
        V5Toggle("同时保存到相册", "录完也写入系统相册 Movies/月光", Icons.Outlined.PhotoLibrary, p.recordToGallery, Color(0xFF8FA8FF)) { v -> up { it.copy(recordToGallery = v) } }
        SettingsDivider()
        SettingsRow("开始录屏", subtitle = "顶部出现 ⏹ 计时胶囊；点它停止", icon = Icons.Outlined.FiberManualRecord, iconTint = Color(0xFFFF453A), chevron = false)
    }
}

/** 星轨速度: 慢 / 标准 / 快 + fine 0.5…2× (live for 试一试). */
@Composable
fun StarSpeedRows(p: V5Prefs, up: V5Update) {
    val c = LocalMoonColors.current
    val presets = listOf(V5Prefs.SLOW, V5Prefs.STANDARD, V5Prefs.FAST)
    val sel = presets.indexOfFirst { kotlin.math.abs(it - MenuStyle.starSpeed) < 0.01f }
    SegmentRow("星轨速度", "按钮从天上落下的速度 · 下面可细调，试一试实时预览", Icons.Outlined.Speed, Color(0xFFF6E7B0), listOf("慢", "标准", "快"), sel) { i ->
        MenuStyle.starSpeed = presets[i]; up { it.copy(starSpeed = presets[i]) }
    }
    Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Slider(MenuStyle.starSpeed, { MenuStyle.starSpeed = (it * 20).toInt() / 20f }, valueRange = 0.5f..2f,
            onValueChangeFinished = { val v = V5Prefs.clampSpeed(MenuStyle.starSpeed); up { it.copy(starSpeed = v) } },
            modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent))
        Spacer(Modifier.width(10.dp))
        Text(String.format(java.util.Locale.US, "%.2f×", MenuStyle.starSpeed), color = c.text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 删除动画 + 最近删除 保留天数. */
@Composable
fun DeleteAnimRows(p: V5Prefs, up: V5Update) {
    SegmentRow("删除动画", when (p.deleteAnim) {
        DeleteAnim.SHRED -> "碎纸机：切成纸条从缝里落下，视频在纸条里继续播放；撤销时倒放"
        DeleteAnim.FADE -> "淡出缩小"
        DeleteAnim.NONE -> "不播放动画"
    }, Icons.Outlined.ContentCut, Color(0xFFFF8A7A), DeleteAnim.entries.map { it.label }, p.deleteAnim.ordinal) { i -> up { it.copy(deleteAnim = DeleteAnim.entries[i]) } }
    SettingsDivider()
    val days = listOf(7, 30, 90)
    SegmentRow("最近删除 保留", "库 › 最近删除，到期自动清除", Icons.Outlined.DeleteSweep, Color(0xFF8E9BB8), days.map { "$it 天" }, days.indexOf(p.trashDays).coerceAtLeast(0)) { i -> up { it.copy(trashDays = days[i]) } }
}

private fun fmtSec(v: Float) = if (v % 1f == 0f) "${v.toInt()} 秒" else String.format(java.util.Locale.US, "%.1f 秒", v)
