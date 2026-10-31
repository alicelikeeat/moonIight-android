package com.moonlight.feed.ui.library

import android.content.Intent
import com.moonlight.feed.ui.design.shredAnchor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.BiliPlayerActivity
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.GallerySaver
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.ui.AppBus
import com.moonlight.feed.ui.AppEvent
import com.moonlight.feed.ui.openOriginal
import com.moonlight.feed.ui.shareEntry
import com.moonlight.feed.ui.design.AnchorState
import com.moonlight.feed.ui.design.GlassBadge
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonChip
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.ScreenHeader
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MoonPalette
import kotlinx.coroutines.launch

val LIB_FILTERS = listOf("全部", com.moonlight.feed.data.download.DownloadPolicy.CATEGORY, "视频", "图片", "嵌入", "收藏", "已隐藏", TRASH_FILTER)
/** v5.0 最近删除 (deleted 库 items + 朋友圈 posts, kept N days). */
const val TRASH_FILTER = "最近删除"

/** 我的下载 = finished 主动下载 only (source "download"); 自动缓存 never creates 库 entries. */
fun libFilter(items: List<MediaEntry>, f: String) = when (f) {
    com.moonlight.feed.data.download.DownloadPolicy.CATEGORY -> items.filter { com.moonlight.feed.data.download.DownloadPolicy.isManualDownload(it) }
    "视频" -> items.filter { it.kind == MediaKind.VIDEO && !it.hidden }
    "图片" -> items.filter { it.kind == MediaKind.IMAGE && !it.hidden }
    "嵌入" -> items.filter { it.isWebEmbed() && !it.hidden }
    "收藏" -> items.filter { it.favorite }
    "已隐藏" -> items.filter { it.hidden }
    TRASH_FILTER -> emptyList()
    else -> items.filter { !it.hidden }
}

class LibraryHandlers(
    val onOpen: (MediaEntry) -> Unit = {},
    val onFavorite: (MediaEntry) -> Unit = {},
    val onSave: (MediaEntry) -> Unit = {},
    val onShare: (MediaEntry) -> Unit = {},
    val onOpenOriginal: (MediaEntry) -> Unit = {},
    val onHideToggle: (MediaEntry) -> Unit = {},
    val onSelect: (MediaEntry) -> Unit = {},
    val onDelete: (MediaEntry) -> Unit = {},
    val onPostMoment: (MediaEntry) -> Unit = {},
)

/** Long-press on a thumbnail: lifted preview + ring (收藏 · 发朋友圈 · 分享·保存 · 多选); the rest behind ···. Tap = open. */
fun libraryMenuSpec(e: MediaEntry, anchor: Rect, h: LibraryHandlers): MenuSpec = MenuSpec(
    anchor = anchor,
    title = e.title.ifBlank { null },
    previewAspect = 0.75f,
    preview = { ThumbPreview(e) },
    radial = listOf(
        MenuAction(if (e.favorite) "已收藏" else "收藏", if (e.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, checked = e.favorite) { h.onFavorite(e) },
        MenuAction("发朋友圈", Icons.Outlined.Camera) { h.onPostMoment(e) },
        MenuAction("分享·保存", Icons.Outlined.IosShare, children = listOf(
            MenuAction("分享", Icons.Outlined.Share) { h.onShare(e) },
            MenuAction("保存到相册", Icons.Outlined.Download) { h.onSave(e) },
        )),
        MenuAction("多选", Icons.Outlined.CheckCircle) { h.onSelect(e) },
    ),
    groupTitles = listOf("播放", "分享与保存", "整理", null),
    actions = listOf(
        buildList {
            add(MenuAction("播放", Icons.Outlined.PlayArrow) { h.onOpen(e) })
            if (e.isWebEmbed()) add(MenuAction("打开原页", Icons.AutoMirrored.Outlined.OpenInNew) { h.onOpenOriginal(e) })
        },
        listOf(MenuAction("分享", Icons.Outlined.Share) { h.onShare(e) }, MenuAction("保存到相册", Icons.Outlined.Download) { h.onSave(e) }),
        listOf(MenuAction(if (e.hidden) "取消隐藏" else "从首页隐藏", if (e.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff) { h.onHideToggle(e) }),
        listOf(MenuAction("删除", Icons.Outlined.Delete, destructive = true) { h.onDelete(e) }),
    ),
)

@Composable
private fun ThumbPreview(e: MediaEntry) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MoonImage(e.coverUrl ?: e.localPath ?: e.remoteUrl, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.65f))))
                .padding(16.dp)
        ) {
            GlassBadge(e.platformLabel())
            if (e.title.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(e.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// =================================================================== stateful

@Composable
fun LibraryScreen() {
    val app = MoonApp.instance
    val lib = app.library
    val ctx = LocalContext.current
    val overlay = LocalOverlay.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val items by lib.items.collectAsState()
    val taskFlow = remember { if (com.moonlight.feed.data.download.Downloads.ready) com.moonlight.feed.data.download.Downloads.manager.tasks
        else kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }
    val tasks by taskFlow.collectAsState()
    var filter by remember { mutableStateOf("全部") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(items, filter) { libFilter(items, filter) }

    LaunchedEffect(Unit) {
        AppBus.events.collect {
            when (it) {
                AppEvent.LIB_SELECT -> selectionMode = true
                AppEvent.LIB_FAVORITES -> filter = "收藏"
                AppEvent.LIB_TRASH -> filter = TRASH_FILTER
                else -> Unit
            }
        }
    }

    fun open(e: MediaEntry) {
        val i = Intent(ctx, BiliPlayerActivity::class.java)
            .putExtra(BiliPlayerActivity.EXTRA_START_ID, e.id)
            .putStringArrayListExtra(BiliPlayerActivity.EXTRA_FEED_IDS, ArrayList(filtered.map { it.id }))
        ctx.startActivity(i)
    }
    fun confirmDelete(entries: List<MediaEntry>) {
        overlay.confirm(
            title = if (entries.size == 1) "删除这条内容？" else "删除 ${entries.size} 项？",
            message = "应用内文件会被移除，可选同时删除相册副本",
            actions = listOf(
                MenuAction("从月淡移除", destructive = true, subtitle = "移到 最近删除，${com.moonlight.feed.data.V5.prefs.trashDays} 天内可恢复") {
                    TrashUi.trashEntries(entries) { selectionMode = false; selectedIds = emptySet() }
                },
                MenuAction("同时从相册删除", destructive = true) {
                    scope.launch {
                        entries.forEach { GallerySaver.deleteGalleryCopy(app, it) }
                        entries.forEach { TrashUi.shredOnly(it) }
                        lib.removeMany(entries.map { it.id })
                        selectionMode = false; selectedIds = emptySet()
                        MoonToaster.show("已删除 ${entries.size} 项", Icons.Outlined.Delete)
                    }
                },
            ),
        )
    }
    val handlers = LibraryHandlers(
        onPostMoment = { e -> com.moonlight.feed.ui.AppBus.composeMoment(listOf(e.id)) },
        onOpen = { open(it) },
        onFavorite = { e -> lib.update(e.id) { it.copy(favorite = !it.favorite) } },
        onSave = { e ->
            scope.launch {
                val r = GallerySaver.saveEntry(app, app.http, e)
                if (r.ok) MoonToaster.show("已保存到相册", Icons.Outlined.CheckCircle) else MoonToaster.show("保存失败：${r.error}", Icons.Outlined.ErrorOutline)
            }
        },
        onShare = { if (!shareEntry(ctx, it)) MoonToaster.show("这条内容暂无可分享的链接") },
        onOpenOriginal = { openOriginal(ctx, it) },
        onHideToggle = { e ->
            lib.update(e.id) { it.copy(hidden = !it.hidden) }
            MoonToaster.show(if (e.hidden) "已恢复到首页" else "已从首页隐藏", if (e.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff)
        },
        onSelect = { selectionMode = true; selectedIds = setOf(it.id) },
        onDelete = { confirmDelete(listOf(it)) },
    )

    val dm = if (com.moonlight.feed.data.download.Downloads.ready) com.moonlight.feed.data.download.Downloads.manager else null
    val trashItems by app.trash.items.collectAsState()
    LibraryContent(
        trash = trashItems,
        trashHandlers = TrashHandlers(
            restore = { TrashUi.restore(it) },
            deleteForever = { t -> overlay.confirm(title = "彻底删除「${t.title}」？", message = "删除后无法恢复", actions = listOf(
                MenuAction("彻底删除", destructive = true) { app.trash.deleteForever(t.id) })) },
            empty = { overlay.confirm(title = "清空最近删除？", message = "${trashItems.size} 项将被彻底删除，无法恢复", actions = listOf(
                MenuAction("全部清除", destructive = true) { app.trash.empty() })) },
        ),
        downloads = tasks.filter { it.state != com.moonlight.feed.data.download.DlState.DONE },
        dlHandlers = com.moonlight.feed.ui.download.DownloadRowHandlers(
            pause = { dm?.pause(it.id) }, resume = { dm?.resume(it.id) }, retry = { dm?.retry(it.id) },
            cancel = { t -> overlay.confirm(title = "取消下载？", message = "已下载的部分会被删除", actions = listOf(
                MenuAction("取消下载", destructive = true) { dm?.cancel(t.id) })) },
        ),
        items = filtered,
        filter = filter,
        onFilter = { filter = it },
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onToggleSelectionMode = { selectionMode = !selectionMode; if (!selectionMode) selectedIds = emptySet() },
        onItemClick = { e ->
            if (selectionMode) selectedIds = if (e.id in selectedIds) selectedIds - e.id else selectedIds + e.id
            else open(e)
        },
        onItemLongPress = { e, rect ->
            if (selectionMode) {
                selectedIds = if (e.id in selectedIds) selectedIds - e.id else selectedIds + e.id
            } else {
                Haptics.open(view)
                overlay.showMenu(libraryMenuSpec(e, rect, handlers))
            }
        },
        onSelectAll = { selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet() },
        onFavoriteSelected = {
            val ids = selectedIds
            ids.forEach { id -> lib.update(id) { it.copy(favorite = true) } }
            MoonToaster.show("已收藏 ${ids.size} 项", Icons.Outlined.Favorite)
            selectionMode = false; selectedIds = emptySet()
        },
        onDeleteSelected = { confirmDelete(items.filter { it.id in selectedIds }) },
    )
}

// =================================================================== stateless content

@Composable
fun LibraryContent(
    items: List<MediaEntry>,
    filter: String,
    onFilter: (String) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelectionMode: () -> Unit,
    onItemClick: (MediaEntry) -> Unit,
    onItemLongPress: (MediaEntry, Rect) -> Unit,
    onSelectAll: () -> Unit = {},
    onFavoriteSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    /** v4.4: unfinished 主动下载 (shown under 我的下载; the chip counts them). */
    downloads: List<com.moonlight.feed.data.download.DownloadTask> = emptyList(),
    dlHandlers: com.moonlight.feed.ui.download.DownloadRowHandlers = com.moonlight.feed.ui.download.DownloadRowHandlers(),
    trash: List<com.moonlight.feed.data.trash.TrashItem> = emptyList(),
    trashHandlers: TrashHandlers = TrashHandlers(),
    now: Long = System.currentTimeMillis(),
) {
    val c = LocalMoonColors.current
    val dlTab = filter == com.moonlight.feed.data.download.DownloadPolicy.CATEGORY
    val rows = if (dlTab) downloads else emptyList()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("媒体库", subtitle = "${items.size} 项") {
                if (selectionMode) GlassPill(if (selectedIds.size == items.size && items.isNotEmpty()) "取消全选" else "全选", onSelectAll)
                GlassPill(if (selectionMode) "完成" else "选择", onToggleSelectionMode, accent = selectionMode)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LIB_FILTERS.forEach { t ->
                    val active = if (t == com.moonlight.feed.data.download.DownloadPolicy.CATEGORY) downloads.count { it.active } else 0
                    MoonChip(when { active > 0 -> "$t · $active"; t == TRASH_FILTER && trash.isNotEmpty() -> "$t · ${trash.size}"; else -> t }, filter == t, { onFilter(t) })
                }
            }
            if (filter == TRASH_FILTER) {
                TrashContent(trash, com.moonlight.feed.data.V5.prefs.trashDays, now, trashHandlers)
            } else if (items.isEmpty() && rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
                    MoonEmptyState(
                        when { filter == "全部" -> "媒体库还是空的"; dlTab -> "还没有下载"; else -> "这里还没有内容" },
                        when { filter == "全部" -> "从发现页添加，或在首页导入本地文件"; dlTab -> "长按首页或播放页的内容 › 分享·保存 › 下载"; else -> "换个筛选看看" },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (rows.isNotEmpty()) {
                        item(key = "dl-head", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("正在下载 · ${rows.size}", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                        }
                        items(rows, key = { "task:" + it.id }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { t ->
                            com.moonlight.feed.ui.download.DownloadTaskRow(t, dlHandlers)
                        }
                        if (items.isNotEmpty()) item(key = "dl-done", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("已下载 · ${items.size}", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
                        }
                    }
                    items(items, key = { it.id }) { e ->
                        LibraryThumb(e, e.id in selectedIds, selectionMode, { onItemClick(e) }, { r -> onItemLongPress(e, r) })
                    }
                }
            }
        }
        // floating glass selection bar
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn() + slideInVertically(spring(0.75f, 420f)) { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp),
        ) {
            Row(
                Modifier.glass(CircleShape, c, elevation = 18.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("已选 ${selectedIds.size}", color = c.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
                GlassPill("收藏", onFavoriteSelected, icon = Icons.Outlined.FavoriteBorder)
                Row(
                    Modifier
                        .moonPress(onDeleteSelected, enabled = selectedIds.isNotEmpty(), highlightShape = CircleShape)
                        .clip(CircleShape).background(c.danger.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LibraryThumb(
    e: MediaEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
) {
    val c = LocalMoonColors.current
    val a = remember { AnchorState() }
    val shape = RoundedCornerShape(14.dp)
    val selScale by animateFloatAsState(if (selected) 0.92f else 1f, spring(0.55f, 500f), label = "sel")
    Box(
        Modifier
            .aspectRatio(0.75f)
            .anchor(a)
            .shredAnchor(e.id)
            .graphicsLayer { scaleX = selScale; scaleY = selScale }
            .moonPress(onClick, { onLongClick(a.rect) }, pressedScale = 0.94f, highlightShape = shape, highlight = Color.White.copy(0.12f))
            .clip(shape)
            .background(if (c.dark) Color(0xFF15171D) else Color(0xFFE7E4DA))
            .then(if (selected) Modifier.border(2.dp, c.accent, shape) else Modifier)
    ) {
        MoonImage(e.coverUrl ?: e.localPath?.let { java.io.File(it) } ?: e.remoteUrl, Modifier.fillMaxSize(), contentDescription = e.title, revealKey = "lib:" + e.id)
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f))))
                .padding(6.dp)
        ) {
            GlassBadge(
                when (e.kind) {
                    MediaKind.VIDEO -> "视频"
                    MediaKind.IMAGE -> "图片"
                    else -> e.platformLabel()
                },
                icon = if (e.kind == MediaKind.IMAGE) null else Icons.Outlined.PlayArrow,
            )
        }
        if (e.favorite && !selectionMode) {
            Icon(Icons.Filled.Favorite, null, tint = MoonPalette.Heart, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp))
        }
        if (e.hidden && !selectionMode) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = Color.White.copy(0.8f), modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp))
        }
        AnimatedVisibility(
            visible = selectionMode,
            enter = scaleIn(spring(0.5f, 600f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (selected) c.accent else Color.Black.copy(0.3f))
                    .border(1.5.dp, Color.White.copy(0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Outlined.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
            }
        }
    }
}
