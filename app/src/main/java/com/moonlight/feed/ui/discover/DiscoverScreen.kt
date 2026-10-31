package com.moonlight.feed.ui.discover

import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Language
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.DownloadJob
import com.moonlight.feed.data.Downloader
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.PlatformResolver
import com.moonlight.feed.data.RemoteApis
import com.moonlight.feed.data.DiscoverLayout
import com.moonlight.feed.data.SaveHelper
import com.moonlight.feed.ui.AppBus
import com.moonlight.feed.ui.AppEvent
import com.moonlight.feed.ui.openOriginal
import com.moonlight.feed.ui.shareEntry
import com.moonlight.feed.ui.design.AnchorState
import com.moonlight.feed.ui.design.GlassBadge
import com.moonlight.feed.ui.design.GlassIconButton
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonChip
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.MoonPullIndicator
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.ScreenHeader
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.design.shimmer
import com.moonlight.feed.ui.theme.LocalMoonColors
import kotlinx.coroutines.launch
import java.util.UUID

val DISCOVER_TABS = listOf("热门", "必刷", "多源", "图库")

class DiscoverHandlers(
    val onAdd: (MediaEntry) -> Unit = {},
    val onOpenOriginal: (MediaEntry) -> Unit = {},
    val onShare: (MediaEntry) -> Unit = {},
    val onBlockAuthor: (MediaEntry) -> Unit = {},
)

fun discoverMenuSpec(e: MediaEntry, anchor: Rect, added: Boolean, h: DiscoverHandlers) = MenuSpec(
    anchor = anchor,
    previewAspect = 16f / 11f,
    preview = { CardPreview(e) },
    actions = listOf(
        listOf(
            MenuAction(if (added) "已加入" else "加入月淡", if (added) Icons.Outlined.CheckCircle else Icons.Outlined.Add, checked = added) { h.onAdd(e) },
            MenuAction("打开原页", Icons.AutoMirrored.Outlined.OpenInNew, subtitle = e.platformLabel()) { h.onOpenOriginal(e) },
            MenuAction("分享", Icons.Outlined.Share) { h.onShare(e) },
        ),
        listOfNotNull(
            if (e.author.isNotBlank()) MenuAction("屏蔽 ${e.author}", Icons.Outlined.Block, destructive = true, subtitle = "随机发现中不再出现") { h.onBlockAuthor(e) } else null
        ),
    ).filter { it.isNotEmpty() },
)

@Composable
private fun CardPreview(e: MediaEntry) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MoonImage(e.coverUrl, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                .padding(16.dp)
        ) {
            GlassBadge(e.platformLabel())
            Spacer(Modifier.height(6.dp))
            Text(e.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =================================================================== stateful

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    val ctx = LocalContext.current
    val app = MoonApp.instance
    val overlay = LocalOverlay.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.flow.collectAsState(initial = com.moonlight.feed.data.SettingsRepository.Snapshot())
    val jobs by app.library.jobs.collectAsState()
    val libItems by app.library.items.collectAsState()
    val addedIds = remember(libItems) { libItems.map { it.id }.toSet() }

    var cards by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("热门") }

    fun loadFeed() {
        scope.launch {
            loading = true
            val enabled = settings.enabledPlatforms
            cards = runCatching {
                when {
                    settings.randomOn || tab == "多源" -> RemoteApis.multiRandomDiscovery(
                        keywords = settings.keywords.toList(), exclude = settings.exclude, enabled = enabled,
                        youtubeKey = settings.youtubeKey, pexelsKey = settings.pexelsKey, pixabayKey = settings.pixabayKey,
                        userSources = app.sources.items.value,
                    )
                    tab == "必刷" && PlatformId.BILI in enabled -> RemoteApis.biliPrecious().map { RemoteApis.biliCardToEntry(it) }
                    PlatformId.BILI in enabled -> RemoteApis.biliPopular(1, 24).map { RemoteApis.biliCardToEntry(it) }
                    else -> RemoteApis.multiRandomDiscovery(
                        keywords = settings.keywords.toList(), exclude = settings.exclude, enabled = enabled,
                        youtubeKey = settings.youtubeKey, pexelsKey = settings.pexelsKey, pixabayKey = settings.pixabayKey, limitPer = 8,
                        userSources = app.sources.items.value,
                    )
                }
            }.getOrDefault(emptyList()).filter { c -> settings.exclude.none { x -> x.isNotBlank() && (c.author == x || c.title.contains(x)) } }
            status = if (cards.isEmpty()) "暂无数据（网络受限或源已关闭）" else "共 ${cards.size} 条 · 长按卡片查看更多"
            loading = false
        }
    }

    fun loadCommons() {
        scope.launch {
            loading = true
            val kws = settings.keywords.toList().ifEmpty { app.settings.defaultsKeywords.toList() }
            val found = runCatching {
                if (settings.randomOn) RemoteApis.commonsRandom(kws, settings.exclude) else RemoteApis.commonsSearch(kws.random(), 16)
            }.getOrDefault(emptyList())
            var extra = emptyList<MediaEntry>()
            if (settings.pexelsKey.isNotBlank() && PlatformId.PEXELS in settings.enabledPlatforms) extra = extra + RemoteApis.pexelsSearch(settings.pexelsKey, kws.random())
            if (settings.pixabayKey.isNotBlank() && PlatformId.PIXABAY in settings.enabledPlatforms) extra = extra + RemoteApis.pixabaySearch(settings.pixabayKey, kws.random())
            (found + extra).forEach { app.library.upsert(it); SaveHelper.maybeSaveEntry(it) }
            cards = found + extra
            status = "已加入媒体库 ${(found + extra).size} 项"
            loading = false
            Haptics.confirm(view)
            MoonToaster.show("已加入 ${(found + extra).size} 项", Icons.Outlined.CheckCircle)
        }
    }

    fun randomAdd() {
        scope.launch {
            loading = true
            val found = runCatching {
                RemoteApis.multiRandomDiscovery(
                    keywords = settings.keywords.toList(), exclude = settings.exclude, enabled = settings.enabledPlatforms,
                    youtubeKey = settings.youtubeKey, pexelsKey = settings.pexelsKey, pixabayKey = settings.pixabayKey,
                        userSources = app.sources.items.value,
                )
            }.getOrDefault(emptyList())
            found.forEach { app.library.upsert(it); SaveHelper.maybeSaveEntry(it) }
            cards = found
            loading = false
            status = "已加入 ${found.size} 项"
            Haptics.confirm(view)
            MoonToaster.show("随机加入 ${found.size} 项", Icons.Outlined.Casino)
        }
    }

    fun addUrls(raw: String, picked: String) {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        scope.launch {
            var ok = 0; var fail = 0; var lastErr: String? = null
            lines.forEach { line ->
                PlatformResolver.isUnsupported(line)?.let { lastErr = it; fail++; return@forEach }
                val resolved = RemoteApis.resolveUrl(line)
                if (resolved.unsupported) { lastErr = resolved.error ?: "不支持"; fail++; return@forEach }
                var entry = resolved.entry ?: run { fail++; return@forEach }
                if (picked != "auto" && entry.platform != picked) entry = entry.copy(platform = picked, source = picked)
                if ((entry.kind == MediaKind.VIDEO || entry.kind == MediaKind.IMAGE) &&
                    entry.remoteUrl != null && entry.localPath == null &&
                    entry.platform in setOf(PlatformId.URL, PlatformId.WEB) &&
                    Regex("""\.(mp4|webm|jpg|png|webp)([?#]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(line)
                ) {
                    val id = UUID.randomUUID().toString()
                    app.library.setJobs(app.library.jobs.value + DownloadJob(id, line, line.takeLast(40)))
                    try {
                        val d = Downloader.downloadUrl(url = line, onProgress = { p -> app.library.updateJob(id) { it.copy(progress = p) } })
                        app.library.upsert(d.copy(platform = entry.platform, source = entry.source))
                        app.library.updateJob(id) { it.copy(progress = 1f, done = true) }
                        SaveHelper.maybeSaveEntry(d)
                    } catch (t: Throwable) {
                        app.library.updateJob(id) { it.copy(error = t.message ?: "失败", done = true) }
                        app.library.upsert(entry); SaveHelper.maybeSaveEntry(entry)
                    }
                    ok++
                    return@forEach
                }
                app.library.upsert(entry); SaveHelper.maybeSaveEntry(entry); ok++
            }
            status = "完成：成功 $ok · 跳过 $fail"
            Haptics.confirm(view)
            MoonToaster.show(if (fail == 0) "已添加 $ok 条" else "成功 $ok · 跳过 $fail${lastErr?.let { "（$it）" } ?: ""}", Icons.Outlined.AddLink)
        }
    }

    fun openUrlSheet(prefill: String = "") {
        overlay.showSheet("添加链接") { dismiss -> UrlSheetContent(prefill) { text, picked -> dismiss(); addUrls(text, picked) } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uris = mutableListOf<android.net.Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri } ?: data.data?.let { uris += it }
        scope.launch {
            val added = app.library.importUris(uris)
            if (settings.autoSaveGallery) added.forEach { e -> SaveHelper.maybeSaveEntry(e) }
            Haptics.confirm(view)
            MoonToaster.show("已导入 ${added.size} 个文件", Icons.Outlined.PhotoLibrary)
        }
    }
    fun importLocal() {
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    LaunchedEffect(tab, settings.randomOn, settings.enabledPlatforms) { if (tab != "图库") loadFeed() else cards = emptyList() }
    LaunchedEffect(Unit) {
        AppBus.events.collect {
            when (it) {
                AppEvent.DISCOVER_RANDOM -> randomAdd()
                AppEvent.DISCOVER_LAYOUT -> scope.launch { app.settings.setDiscoverColumns(DiscoverLayout.toggle(settings.discoverColumns)) }
                AppEvent.DISCOVER_PASTE -> openUrlSheet(clipboardText(ctx))
                else -> Unit
            }
        }
    }

    // ------------------------------------------------ keyword video search
    var searchText by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<com.moonlight.feed.data.SearchSession?>(null) }
    var searchUi by remember { mutableStateOf<SearchUiState?>(null) }
    fun publish(s: com.moonlight.feed.data.SearchSession, loading: Boolean) {
        searchUi = SearchUiState(s.query, loading, s.batch, s.status, com.moonlight.feed.data.VideoSearch.skippedNote(settings), s.pool.size, s.recycled, settings.searchCount)
    }
    fun doSearch(q: String) {
        val query = q.trim(); if (query.isBlank()) return
        scope.launch { app.settings.pushRecentSearch(query) }
        val providers = com.moonlight.feed.data.VideoSearch.providers(settings)
        if (providers.isEmpty()) {
            searchUi = SearchUiState(query, false, note = "没有可搜索的平台：请在设置中启用 Dailymotion / Archive / NASA / PeerTube / Commons")
            return
        }
        val s = com.moonlight.feed.data.SearchSession(query, providers, settings.searchCount, settings.exclude)
        session = s
        searchUi = SearchUiState(query, loading = true, count = settings.searchCount)
        scope.launch {
            s.start()
            if (session === s) { publish(s, false); if (s.batch.isNotEmpty()) Haptics.light(view) }
        }
    }
    fun shuffleSearch() {
        val s = session ?: return
        searchUi = searchUi?.copy(loading = true)
        scope.launch { s.next(); if (session === s) { publish(s, false); Haptics.tick(view) } }
    }
    fun commitBatch(play: Boolean, startAt: MediaEntry? = null) {
        val batch = searchUi?.batch.orEmpty().ifEmpty { return }
        val existing = app.library.items.value.associateBy { it.id }
        // keep library identity if already present; insert newcomers
        val ids = batch.map { e -> (existing[e.id] ?: app.library.items.value.firstOrNull { x -> com.moonlight.feed.data.SearchMerge.key(x) == com.moonlight.feed.data.SearchMerge.key(e) })?.id ?: run { app.library.upsert(e); e.id } }
        val ordered = if (startAt != null) { val i = batch.indexOf(startAt).coerceAtLeast(0); ids.drop(i) + ids.take(i) } else ids
        AppBus.focusFeed(ordered)
        if (play) {
            ctx.startActivity(
                Intent(ctx, com.moonlight.feed.BiliPlayerActivity::class.java)
                    .putExtra(com.moonlight.feed.BiliPlayerActivity.EXTRA_START_ID, ordered.first())
                    .putStringArrayListExtra(com.moonlight.feed.BiliPlayerActivity.EXTRA_FEED_IDS, ArrayList(ordered))
            )
        }
        Haptics.confirm(view)
        MoonToaster.show(if (play) "正在播放 ${ordered.size} 条" else "已加入信息流 ${ordered.size} 条", Icons.Outlined.CheckCircle)
    }
    val searchHandlers = SearchHandlers(
        text = searchText,
        onText = { searchText = it; if (it.isBlank()) { session = null; searchUi = null } },
        onSubmit = { q -> searchText = q; doSearch(q) },
        onCancel = { searchText = ""; session = null; searchUi = null },
        recent = settings.recentSearches,
        onRecentRemove = { q -> Haptics.open(view); scope.launch { app.settings.removeRecentSearch(q) }; MoonToaster.show("已移除「$q」") },
        onRecentClear = { scope.launch { app.settings.clearRecentSearches() } },
        onAddAll = { commitBatch(play = false) },
        onPlayAll = { commitBatch(play = true) },
        onShuffle = { shuffleSearch() },
        onCard = { e -> commitBatch(play = true, startAt = e) },
        onCardLong = { e, r -> Haptics.open(view); overlay.showMenu(searchCardMenu(e, r, { commitBatch(true, e) }, { app.library.upsert(e); MoonToaster.show("已加入：${e.title.take(16)}", Icons.Outlined.CheckCircle) }, { openOriginal(ctx, e) }, { shareEntry(ctx, e) })) },
    )

    val handlers = DiscoverHandlers(
        onAdd = { e ->
            app.library.upsert(e); scope.launch { SaveHelper.maybeSaveEntry(e) }
            MoonToaster.show("已加入：${e.title.take(16)}", Icons.Outlined.CheckCircle)
        },
        onOpenOriginal = { openOriginal(ctx, it) },
        onShare = { shareEntry(ctx, it) },
        onBlockAuthor = { e ->
            scope.launch { app.settings.setExclude(settings.exclude + e.author) }
            cards = cards.filter { it.author != e.author }
            MoonToaster.show("已屏蔽 ${e.author}", Icons.Outlined.Block, action = "撤销") {
                scope.launch { app.settings.setExclude(settings.exclude - e.author) }
            }
        },
    )

    DiscoverContent(
        search = searchUi,
        sh = searchHandlers,
        tab = tab,
        onTab = { tab = it },
        randomOn = settings.randomOn,
        onToggleRandom = { scope.launch { app.settings.setRandomOn(!settings.randomOn) } },
        cards = cards,
        addedIds = addedIds,
        loading = loading,
        status = status,
        jobs = jobs.filter { !it.done },
        onRefresh = { if (tab == "图库") loadCommons() else loadFeed() },
        onImport = { importLocal() },
        onPaste = { openUrlSheet(clipboardText(ctx).takeIf { it.startsWith("http") || it.startsWith("BV") }.orEmpty()) },
        onRandomAdd = { randomAdd() },
        onCardClick = { handlers.onAdd(it) },
        onCardLongPress = { e, r ->
            Haptics.open(view)
            overlay.showMenu(discoverMenuSpec(e, r, e.id in addedIds, handlers))
        },
        columns = DiscoverLayout.columns(settings.discoverColumns),
        bannerScale = DiscoverLayout.scale(settings.discoverBanner / 100f),
        onToggleColumns = { scope.launch { app.settings.setDiscoverColumns(DiscoverLayout.toggle(settings.discoverColumns)) } },
        onBannerScale = { v -> scope.launch { app.settings.setDiscoverBanner(DiscoverLayout.percent(v)) } },
        onAddLongPress = { r ->
            Haptics.open(view)
            overlay.showMenu(
                MenuSpec(
                    anchor = r,
                    actions = listOf(listOf(
                        MenuAction("导入本地", Icons.Outlined.PhotoLibrary, subtitle = "图片与视频，可多选") { importLocal() },
                        MenuAction("粘贴链接", Icons.Outlined.AddLink, subtitle = "自动识别平台") { openUrlSheet(clipboardText(ctx)) },
                        MenuAction("多源随机加入", Icons.Outlined.Casino) { randomAdd() },
                        MenuAction("添加网址源", Icons.Outlined.Language, subtitle = "任意网页 · 随机抓取") { com.moonlight.feed.ui.sources.SourceActions.openEditor(overlay, scope, ctx) },
                    )),
                )
            )
        },
    )
}

private fun clipboardText(ctx: Context): String = runCatching {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
}.getOrDefault("")

class SearchHandlers(
    val text: String = "",
    val onText: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onCancel: () -> Unit = {},
    val recent: List<String> = emptyList(),
    val onRecentRemove: (String) -> Unit = {},
    val onRecentClear: () -> Unit = {},
    val onAddAll: () -> Unit = {},
    val onPlayAll: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onCard: (MediaEntry) -> Unit = {},
    val onCardLong: (MediaEntry, Rect) -> Unit = { _, _ -> },
    val forceRecent: Boolean = false,
)

// =================================================================== stateless content

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverContent(
    tab: String,
    onTab: (String) -> Unit,
    randomOn: Boolean,
    onToggleRandom: () -> Unit,
    cards: List<MediaEntry>,
    addedIds: Set<String>,
    loading: Boolean,
    status: String,
    jobs: List<DownloadJob> = emptyList(),
    onRefresh: () -> Unit = {},
    onImport: () -> Unit = {},
    onPaste: () -> Unit = {},
    onRandomAdd: () -> Unit = {},
    onCardClick: (MediaEntry) -> Unit = {},
    onCardLongPress: (MediaEntry, Rect) -> Unit = { _, _ -> },
    onAddLongPress: (Rect) -> Unit = {},
    search: SearchUiState? = null,
    sh: SearchHandlers = SearchHandlers(),
    columns: Int = 2,
    bannerScale: Float = 1f,
    onToggleColumns: () -> Unit = {},
    onBannerScale: (Float) -> Unit = {},
) {
    val c = LocalMoonColors.current
    val pull = rememberPullToRefreshState()
    var searchFocused by remember { mutableStateOf(false) }
    val addAnchor = rememberAnchor()
    Column(Modifier.fillMaxSize()) {
        val cols = DiscoverLayout.columns(columns)
        var draggingScale by remember { mutableStateOf(false) }
        var scaleDraft by remember { mutableFloatStateOf(bannerScale) }
        val shownScale = if (draggingScale) scaleDraft else bannerScale
        ScreenHeader("发现", subtitle = "官方嵌入 · 公开接口") {
            GlassIconButton(
                if (cols == 1) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda,
                "切换单双列",
                onToggleColumns,
            )
            GlassIconButton(Icons.Outlined.Refresh, "刷新", onRefresh, onLongClick = onToggleRandom, skinId = "discover.refresh")
            GlassIconButton(
                Icons.Outlined.Add, "添加", onPaste,
                modifier = Modifier.anchor(addAnchor),
                onLongClick = { onAddLongPress(addAnchor.rect) },
            )
        }
        SearchField(
            sh.text, sh.onText, sh.onSubmit, { searchFocused = false; sh.onCancel() },
            focused = searchFocused, onFocus = { searchFocused = it },
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
        androidx.compose.animation.AnimatedVisibility(searchFocused && search == null || (sh.forceRecent && search == null)) {
            RecentSearches(sh.recent, { q -> sh.onText(q); sh.onSubmit(q) }, sh.onRecentRemove, sh.onRecentClear)
        }
        androidx.compose.animation.AnimatedVisibility(search == null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DISCOVER_TABS.forEach { t -> MoonChip(t, tab == t, { onTab(t) }) }
                MoonChip(if (randomOn) "随机 · 开" else "随机 · 关", randomOn, onToggleRandom, icon = Icons.Outlined.Shuffle)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("横幅大小", color = c.text, style = MaterialTheme.typography.labelLarge)
                    Text("${DiscoverLayout.percent(shownScale)}%", color = c.textDim, style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = shownScale,
                    onValueChange = { draggingScale = true; scaleDraft = it },
                    onValueChangeFinished = { onBannerScale(scaleDraft); draggingScale = false },
                    valueRange = DiscoverLayout.MIN_SCALE..DiscoverLayout.MAX_SCALE,
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onRefresh,
            state = pull,
            modifier = Modifier.fillMaxSize(),
            indicator = { MoonPullIndicator(pull, loading) },
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (search != null) {
                    item(key = "s-head") { SearchHeader(search, sh.onAddAll, sh.onPlayAll, sh.onShuffle) }
                    if (search.loading && search.batch.isEmpty()) {
                        items(3, key = { "ssk$it" }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SearchSkeletonCard(Modifier.weight(1f)); SearchSkeletonCard(Modifier.weight(1f)) }
                        }
                    } else if (search.batch.isEmpty()) {
                        item(key = "s-empty") {
                            MoonEmptyState("没有找到视频", "换个关键词试试，或在设置中启用更多平台（YouTube / Pexels / Pixabay 需要 API Key）。", modifier = Modifier.padding(top = 24.dp))
                        }
                    } else {
                        val rows = search.batch.chunked(2)
                        items(rows.size, key = { i -> "s-row-" + rows[i].joinToString { it.id } }) { i ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateItem()) {
                                rows[i].forEachIndexed { j, e ->
                                    SearchResultCard(e, i * 2 + j, Modifier.weight(1f), { sh.onCard(e) }, { r -> sh.onCardLong(e, r) })
                                }
                                if (rows[i].size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    return@LazyColumn
                }
                item(key = "quick") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickTile("导入本地", Icons.Outlined.PhotoLibrary, Modifier.weight(1f), onImport)
                        QuickTile("粘贴链接", Icons.Outlined.AddLink, Modifier.weight(1f), onPaste)
                        QuickTile("随机加入", Icons.Outlined.Casino, Modifier.weight(1f), onRandomAdd)
                    }
                }
                if (jobs.isNotEmpty()) item(key = "jobs") {
                    Column(Modifier.fillMaxWidth().glassStatic(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        jobs.forEach { j ->
                            Text(j.title, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.hairline)) {
                                Box(Modifier.fillMaxWidth(j.progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(c.accent))
                            }
                        }
                    }
                }
                if (status.isNotBlank() && !loading) item(key = "status") {
                    Text(status, color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                }
                if (tab == "图库" && cards.isEmpty() && !loading) {
                    item(key = "gallery") {
                        MoonEmptyState(
                            "Commons / 图库",
                            "从 Wikimedia Commons（及已配置 Key 的 Pexels / Pixabay）按关键词拉取图片并加入媒体库",
                            action = "立即获取", onAction = onRefresh,
                        )
                    }
                } else if (loading && cards.isEmpty()) {
                    items(3, key = { "sk$it" }) { SkeletonCard() }
                } else if (cards.isEmpty()) {
                    item(key = "empty") {
                        MoonEmptyState(
                            "暂时没有内容",
                            "下拉刷新，或在设置中启用更多平台。\n抖音 / TikTok / 快手 / 小红书 / 优酷 / 爱奇艺 / 腾讯 / 西瓜暂不支持。",
                            action = "刷新", onAction = onRefresh,
                        )
                    }
                } else {
                    val mediaH = DiscoverLayout.cardHeightDp(cols, shownScale).dp
                    val rows = cards.chunked(cols)
                    items(rows.size, key = { i -> "d-row-" + rows[i].joinToString("-") { it.id } }) { i ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rows[i].forEach { e ->
                                Box(Modifier.weight(1f)) {
                                    DiscoverCardView(e, e.id in addedIds, { onCardClick(e) }, { r -> onCardLongPress(e, r) }, mediaH)
                                }
                            }
                            repeat(cols - rows[i].size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    Column(
        modifier
            .moonPress(onClick, highlightShape = RoundedCornerShape(20.dp), highlight = c.text.copy(0.06f))
            .glassStatic(RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = c.text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SkeletonCard() {
    Column(Modifier.fillMaxWidth().glassStatic(RoundedCornerShape(22.dp)).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmer(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).shimmer(RoundedCornerShape(7.dp)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.4f).height(10.dp).shimmer(RoundedCornerShape(5.dp)))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DiscoverCardView(e: MediaEntry, added: Boolean, onClick: () -> Unit, onLongClick: (Rect) -> Unit, mediaHeight: androidx.compose.ui.unit.Dp = 180.dp) {
    val c = LocalMoonColors.current
    val a = remember { AnchorState() }
    Column(
        Modifier
            .fillMaxWidth()
            .anchor(a)
            .moonPress(onClick, { onLongClick(a.rect) }, pressedScale = 0.96f, highlightShape = RoundedCornerShape(22.dp), highlight = c.text.copy(0.05f))
            .glassStatic(RoundedCornerShape(22.dp))
            .padding(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(mediaHeight).clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0xFF14161C) else Color(0xFFE4E1D6))) {
            if (!e.coverUrl.isNullOrBlank()) MoonImage(e.coverUrl, Modifier.fillMaxSize())
            else Text(e.platformLabel(), color = c.textDim, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
            GlassBadge(e.platformLabel(), Modifier.align(Alignment.TopStart).padding(10.dp))
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                AnimatedContent(
                    targetState = added,
                    transitionSpec = { (scaleIn(spring(0.45f, 600f), initialScale = 0.5f) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                    label = "added",
                ) { isAdded ->
                    if (isAdded) Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, "已加入", tint = c.onAccent, modifier = Modifier.size(20.dp))
                    } else GlassIconButton(Icons.Outlined.Add, "加入", onClick, size = 36.dp, iconSize = 20.dp, colors = com.moonlight.feed.ui.theme.MediaGlass, skinId = "discover.add")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(e.title, color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            listOf(e.platformLabel(), e.author.ifBlank { null }).filterNotNull().joinToString(" · "),
            color = c.textDim, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun UrlSheetContent(prefill: String, onSubmit: (String, String) -> Unit) {
    val c = LocalMoonColors.current
    var text by remember { mutableStateOf(prefill) }
    var picked by remember { mutableStateOf("auto") }
    Text(
        "支持 YouTube / B站 / Vimeo / Dailymotion / Archive / NASA / PeerTube / Twitch Clip / 直链 / 普通网页，每行一条。",
        color = c.textDim, style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "auto" to "自动", PlatformId.BILI to "哔哩", PlatformId.YOUTUBE to "YouTube", PlatformId.VIMEO to "Vimeo",
            PlatformId.DAILYMOTION to "Dailymotion", PlatformId.ARCHIVE to "Archive", PlatformId.WEB to "网页", PlatformId.URL to "直链",
        ).forEach { (id, label) -> MoonChip(label, picked == id, { picked = id }) }
    }
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp)).background(c.hairline).padding(14.dp)) {
        if (text.isEmpty()) Text("https://…\n或 BV1…", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxSize(),
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth()
            .moonPress({ onSubmit(text, picked) }, highlightShape = RoundedCornerShape(18.dp), enabled = text.isNotBlank())
            .clip(RoundedCornerShape(18.dp)).background(if (text.isNotBlank()) c.accent else c.hairline)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FileDownload, null, tint = if (text.isNotBlank()) c.onAccent else c.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("添加", color = if (text.isNotBlank()) c.onAccent else c.textDim, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(8.dp))
}


fun searchCardMenu(e: MediaEntry, anchor: Rect, onPlay: () -> Unit, onAdd: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit) = MenuSpec(
    anchor = anchor,
    title = e.title.ifBlank { null },
    actions = listOf(listOf(
        MenuAction("从这里播放", Icons.Outlined.PlayArrow, onClick = onPlay),
        MenuAction("只加入这一条", Icons.Outlined.Add, onClick = onAdd),
        MenuAction("打开原页", Icons.AutoMirrored.Outlined.OpenInNew, onClick = onOpen),
        MenuAction("分享", Icons.Outlined.Share, onClick = onShare),
    )),
)
