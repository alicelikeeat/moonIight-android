package com.moonlight.feed.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.download.DlState
import com.moonlight.feed.data.download.DownloadFormat
import com.moonlight.feed.data.download.DownloadTask
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors

class DownloadRowHandlers(
    val pause: (DownloadTask) -> Unit = {},
    val resume: (DownloadTask) -> Unit = {},
    val retry: (DownloadTask) -> Unit = {},
    val cancel: (DownloadTask) -> Unit = {},
)

/** 库 › 我的下载: one unfinished 主动下载 (progress, 暂停 / 继续, 取消, 失败时 重试). */
@Composable
fun DownloadTaskRow(t: DownloadTask, h: DownloadRowHandlers, modifier: Modifier = Modifier) {
    val c = LocalMoonColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth().glassStatic(shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) Color(0xFF15171D) else Color(0xFFE7E4DA))) {
            MoonImage(t.coverUrl ?: if (t.kind == MediaKind.IMAGE) t.url else null, Modifier.fillMaxWidth().fillMaxHeight())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title.ifBlank { "下载" }, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val failed = t.state == DlState.FAILED
            val p = t.progress
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.text.copy(0.10f))) {
                Box(Modifier.fillMaxWidth(p ?: if (t.state == DlState.RUNNING) 0.08f else 0f).fillMaxHeight().clip(CircleShape)
                    .background(when { failed -> c.danger; t.state == DlState.PAUSED -> c.textDim; else -> c.accent }))
            }
            Spacer(Modifier.height(5.dp))
            Text(statusLine(t), color = if (failed) c.danger else c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (t.state) {
                DlState.QUEUED, DlState.RUNNING -> RowButton(Icons.Outlined.Pause, "暂停") { h.pause(t) }
                DlState.PAUSED -> RowButton(Icons.Outlined.PlayArrow, "继续") { h.resume(t) }
                DlState.FAILED -> RowButton(Icons.Outlined.Refresh, "重试") { h.retry(t) }
                DlState.DONE -> Unit
            }
            RowButton(Icons.Outlined.Close, "取消") { h.cancel(t) }
        }
    }
}

fun statusLine(t: DownloadTask): String {
    val size = if (t.total > 0) "${DownloadFormat.bytes(t.bytes)} / ${DownloadFormat.bytes(t.total)}" else DownloadFormat.bytes(t.bytes)
    return when (t.state) {
        DlState.QUEUED -> if (t.error != null) "等待重试 · ${t.error}" else if (t.bytes > 0) "排队中 · $size" else "排队中 · 等待网络"
        DlState.RUNNING -> (t.progress?.let { "${(it * 100).toInt()}% · " } ?: "") + size
        DlState.PAUSED -> "已暂停 · $size"
        DlState.FAILED -> "失败：${t.error ?: "未知错误"}"
        DlState.DONE -> "已完成 · $size"
    }
}

@Composable
private fun RowButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    Box(Modifier.size(38.dp).moonPress(onClick, highlightShape = CircleShape).clip(CircleShape).background(c.text.copy(0.07f)), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = c.text, modifier = Modifier.size(20.dp))
    }
}
