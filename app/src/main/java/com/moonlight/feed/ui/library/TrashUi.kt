package com.moonlight.feed.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.VideoBudget
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.data.trash.TrashItem
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.LoopVideo
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.Shredder
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.shredAnchor
import com.moonlight.feed.ui.theme.LocalMoonColors
import java.io.File

/**
 * v5.0 deletes: things go to 最近删除 (kept N days) with a 碎纸机 animation; 撤销 / 恢复 play it backwards.
 * Destructive confirmation rules are unchanged — this runs after the user confirmed.
 */
object TrashUi {
    private val budget = VideoBudget(2)

    /** What the shredder cuts up: the image, or the video still playing. */
    @Composable
    fun EntryVisual(e: MediaEntry) {
        val path = e.localPath?.takeIf { e.kind == MediaKind.VIDEO && File(it).exists() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (path != null) LoopVideo(path, Modifier.fillMaxSize(), budget)
            else MoonImage(e.coverUrl ?: e.localPath?.let { File(it) } ?: e.remoteUrl, Modifier.fillMaxSize())
        }
    }

    @Composable
    fun MomentVisual(m: Moment, s: SocialState) {
        // opaque backing: the card itself is glass, and the list reflows underneath while it shreds
        val c = com.moonlight.feed.ui.theme.LocalMoonColors.current
        Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(c.bgTop)) { MomentCardCopy(m, s) }
    }

    @Composable
    private fun MomentCardCopy(m: Moment, s: SocialState) {
        com.moonlight.feed.ui.social.MomentCard(m, s, s.selfId, com.moonlight.feed.ui.social.MomentHandlers(), System.currentTimeMillis(),
            inline = com.moonlight.feed.ui.social.InlineLive { a ->
                val p = a.localPath ?: a.libraryEntryId?.let { id -> MoonApp.instance.library.items.value.firstOrNull { it.id == id }?.localPath }
                if (a.kind == AttachmentKind.VIDEO && p != null && File(p).exists()) ({ LoopVideo(p, Modifier.fillMaxSize(), budget) }) else null
            })
    }

    /** 从月光移除 → 最近删除 (the app file stays until purged). */
    fun trashEntries(entries: List<MediaEntry>, after: () -> Unit = {}) {
        if (entries.isEmpty()) return
        val app = MoonApp.instance
        entries.forEach { e -> app.trash.putEntry(e); Shredder.play(e.id) { EntryVisual(e) } }
        app.library.removeMany(entries.map { it.id }, deleteLocalFile = false)
        after()
        MoonToaster.show(if (entries.size == 1) "已移到 最近删除" else "已移到 最近删除 · ${entries.size} 项", Icons.Outlined.Delete, action = "撤销") { restoreEntries(entries) }
    }

    fun restoreEntries(entries: List<MediaEntry>) {
        val app = MoonApp.instance
        entries.forEach { e -> app.trash.restore("lib:" + e.id); app.library.upsert(e); Shredder.play(e.id, reverse = true) { EntryVisual(e) } }
    }

    /** Permanent deletes (同时从相册删除) still shred, but there is nothing to restore. */
    fun shredOnly(e: MediaEntry) = Shredder.play(e.id) { EntryVisual(e) }

    fun trashMoment(m: Moment) {
        val app = MoonApp.instance
        val s = app.social.state.value
        app.trash.putMoment(m)
        Shredder.play("moment:" + m.id) { MomentVisual(m, s) }
        app.social.deleteMoment(m.id)
        MoonToaster.show("已删除动态 · 可在 最近删除 恢复", Icons.Outlined.Delete, action = "撤销") { restoreMoment(m) }
    }

    fun restoreMoment(m: Moment) {
        val app = MoonApp.instance
        app.trash.restore("moment:" + m.id); app.social.upsertMoment(m)
        Shredder.play("moment:" + m.id, reverse = true) { MomentVisual(m, app.social.state.value) }
    }

    /** 恢复 from the 最近删除 list (the reverse shred plays on its row). */
    fun restore(t: TrashItem) {
        val app = MoonApp.instance
        val rowRect = Shredder.anchors["trash:" + t.id]
        app.trash.restore(t.id)
        t.entry?.let { e -> app.library.upsert(e); Shredder.play(e.id, reverse = true, at = rowRect) { EntryVisual(e) } }
        t.moment?.let { m -> app.social.upsertMoment(m); Shredder.play(m.id, reverse = true, at = rowRect) { RowVisual(t) } }
        MoonToaster.show("已恢复「${t.title}」", Icons.Outlined.Restore)
    }

    @Composable
    internal fun RowVisual(t: TrashItem) {
        val c = LocalMoonColors.current
        Box(Modifier.fillMaxSize().background(c.text.copy(alpha = 0.08f))) { TrashThumb(t, Modifier.fillMaxSize()) }
    }
}

class TrashHandlers(val restore: (TrashItem) -> Unit = {}, val deleteForever: (TrashItem) -> Unit = {}, val empty: () -> Unit = {})

@Composable
private fun TrashThumb(t: TrashItem, modifier: Modifier) {
    val e = t.entry
    val m = t.moment
    val model: Any? = e?.let { it.coverUrl ?: it.localPath?.let { p -> File(p) } ?: it.remoteUrl }
        ?: m?.attachments?.firstOrNull()?.let { a -> a.coverUrl ?: a.localPath?.let { File(it) } ?: a.remoteUrl }
    if (model != null) MoonImage(model, modifier)
    else Box(modifier.background(LocalMoonColors.current.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(t.title.take(2), color = LocalMoonColors.current.text, fontWeight = FontWeight.SemiBold)
    }
}

/** 库 › 最近删除: what's left, how long it stays, 恢复 / 彻底删除 / 清空. */
@Composable
fun TrashContent(items: List<TrashItem>, keepDays: Int, now: Long, h: TrashHandlers) {
    val c = LocalMoonColors.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
            MoonEmptyState("最近删除是空的", "删除的内容会在这里保留 $keepDays 天，之后自动清除")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("保留 $keepDays 天 · ${items.size} 项", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                GlassPill("全部清除", h.empty, icon = Icons.Outlined.DeleteForever)
            }
        }
        items(items, key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).shredAnchor("trash:" + t.id).glassStatic(RoundedCornerShape(18.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrashThumb(t, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    val kind = when { t.moment != null -> "朋友圈动态"; t.entry?.kind == MediaKind.VIDEO -> "视频"; t.entry?.kind == MediaKind.IMAGE -> "图片"; else -> "内容" }
                    Text("$kind · ${t.daysLeft(now, keepDays)} 天后删除", color = c.textDim, style = MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.Outlined.Restore, "恢复", tint = c.accent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).moonPress({ h.restore(t) }).padding(9.dp))
                Icon(Icons.Outlined.DeleteForever, "彻底删除", tint = c.danger, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).moonPress({ h.deleteForever(t) }).padding(9.dp))
            }
        }
    }
}
