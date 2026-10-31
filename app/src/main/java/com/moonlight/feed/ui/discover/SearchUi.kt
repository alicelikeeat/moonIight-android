package com.moonlight.feed.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.ProviderStatus
import com.moonlight.feed.ui.design.AnchorState
import com.moonlight.feed.ui.design.GlassBadge
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.shimmer
import com.moonlight.feed.ui.theme.LocalMoonColors

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val batch: List<MediaEntry> = emptyList(),
    val status: List<ProviderStatus> = emptyList(),
    val note: String? = null,
    val total: Int = 0,
    val recycled: Boolean = false,
    val count: Int = 10,
)

/** Apple-style glass search capsule: magnifier, text, clear (×), and 取消 while focused. */
@Composable
fun SearchField(
    text: String,
    onText: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
) {
    val c = LocalMoonColors.current
    val focus = LocalFocusManager.current
    val fr = remember { FocusRequester() }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(40.dp).glass(RoundedCornerShape(13.dp), c, blurRadius = 20.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = c.textDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("搜索视频", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    text, onText, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) { focus.clearFocus(); onSubmit(text.trim()) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { onFocus(it.isFocused) },
                )
            }
            AnimatedVisibility(text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Outlined.Cancel, "清除", tint = c.textDim,
                    modifier = Modifier.size(18.dp).moonPress({ onText(""); fr.requestFocus() }, pressedScale = 0.8f, highlightShape = null),
                )
            }
        }
        AnimatedVisibility(focused || text.isNotEmpty(), enter = fadeIn() + expandHorizontally(spring(0.85f, 500f)), exit = fadeOut() + shrinkHorizontally(spring(0.9f, 600f))) {
            Text(
                "取消", color = c.accent, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp).moonPress({ focus.clearFocus(); onCancel() }, pressedScale = 0.9f, highlightShape = null),
            )
        }
    }
}

@Composable
fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    val c = LocalMoonColors.current
    if (recent.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("最近搜索", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("清除", color = c.accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.moonPress(onClear, highlightShape = null))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { q ->
                Row(
                    Modifier.moonPress({ onPick(q) }, { onRemove(q) }, highlightShape = CircleShape)
                        .glass(CircleShape, c, blurRadius = 16.dp).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, null, tint = c.textDim, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(q, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** Header of a result batch: summary, per-platform status chips, and the three actions. */
@Composable
fun SearchHeader(s: SearchUiState, onAddAll: () -> Unit, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    val c = LocalMoonColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("「${s.query}」", color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(
                if (s.loading && s.batch.isEmpty()) "搜索中…" else "随机 ${s.batch.size} 条 · 共 ${s.total} 条",
                color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        if (s.status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.status.filter { it.count > 0 }.forEach { p -> StatusChip("${p.label} ${p.count}", ok = true) }
            }
            val failed = s.status.filter { it.failed }.map { it.label }
            val notes = listOfNotNull(failed.takeIf { it.isNotEmpty() }?.let { "未响应：${it.joinToString("、")}" }, s.note, if (s.recycled) "已看完全部结果，重新抽样" else null)
            if (notes.isNotEmpty()) Text(notes.joinToString(" · "), color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enabled = s.batch.isNotEmpty() && !s.loading
            ActionPill("全部播放", Icons.Outlined.PlayArrow, primary = true, enabled = enabled, onClick = onPlayAll, modifier = Modifier.weight(1f))
            ActionPill("加入信息流", Icons.AutoMirrored.Outlined.PlaylistAdd, enabled = enabled, onClick = onAddAll, modifier = Modifier.weight(1f))
            ActionPill("换一批", Icons.Outlined.Shuffle, enabled = s.total > 0 && !s.loading, onClick = onShuffle, modifier = Modifier.weight(0.82f))
        }
    }
}

@Composable
private fun StatusChip(t: String, ok: Boolean) {
    val c = LocalMoonColors.current
    Row(
        Modifier.background(if (ok) c.accent.copy(alpha = 0.12f) else c.hairline, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(if (ok) Color(0xFF7FD1AE) else c.textDim, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(t, color = c.text.copy(0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ActionPill(t: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val c = LocalMoonColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(42.dp)
            .moonPress(onClick, highlightShape = shape, enabled = enabled)
            .then(if (primary) Modifier.clip(shape).background(if (enabled) c.accent else c.hairline) else Modifier.glass(shape, c, blurRadius = 18.dp))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = when { primary && enabled -> c.onAccent; enabled -> c.text; else -> c.textDim }
        Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(t, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Compact result card: 16:9 cover, platform badge, two-line title. */
@Composable
fun SearchResultCard(e: MediaEntry, index: Int, modifier: Modifier, onClick: () -> Unit, onLong: (Rect) -> Unit) {
    val c = LocalMoonColors.current
    val a = remember { AnchorState() }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.anchor(a).moonPress(onClick, { onLong(a.rect) }, pressedScale = 0.96f, highlightShape = shape).glassStatic(shape).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(13.dp)).background(Color(0xFF12141B))) {
            MoonImage(e.coverUrl ?: "art:$index", Modifier.fillMaxSize(), revealKey = "s:" + e.id)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(0.35f))))
            GlassBadge(com.moonlight.feed.data.PlatformId.label(e.platform ?: ""), Modifier.align(Alignment.TopStart).padding(6.dp))
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp).glass(CircleShape, com.moonlight.feed.ui.theme.MediaGlass, blurRadius = 12.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(e.title.ifBlank { "未命名" }, color = c.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(e.author, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SearchSkeletonCard(modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.glassStatic(shape).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(13.dp)).shimmer())
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(CircleShape).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(CircleShape).shimmer())
        Spacer(Modifier.height(8.dp))
    }
}
