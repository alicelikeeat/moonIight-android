package com.moonlight.feed.ui.sources

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.web.FetchMode
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.PlatformResolver
import com.moonlight.feed.data.RemoteApis
import com.moonlight.feed.data.SaveHelper
import com.moonlight.feed.data.SourceFetcher
import com.moonlight.feed.data.UserSource
import com.moonlight.feed.data.web.UrlNorm
import com.moonlight.feed.ui.design.AnchorState
import com.moonlight.feed.ui.design.GlassIconButton
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonChip
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.MoonSwitch
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.settings.Segmented
import com.moonlight.feed.ui.theme.LocalMoonColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ================================================================= model helpers (pure)

data class SourceDraft(
    val url: String = "",
    val name: String = "",
    val mode: FetchMode = FetchMode.RANDOM,
    val depth: Int = 1,
    val sample: Int = 20,
    val autoHours: Int = 0,
)

fun UserSource.toDraft() = SourceDraft(url, name, mode, depth, sample, autoRefreshHours)

/** What an entered URL will become. */
sealed class UrlKind {
    object Empty : UrlKind()
    object Invalid : UrlKind()
    data class Unsupported(val msg: String) : UrlKind()
    data class Platform(val label: String) : UrlKind()
    object Direct : UrlKind()
    data class Web(val host: String) : UrlKind()
}

fun normalizeInput(raw: String): String {
    val t = raw.trim().lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (t.isBlank()) return ""
    return if (!t.startsWith("http://", true) && !t.startsWith("https://", true) && Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(t)) "https://$t" else t
}

fun classifyUrl(raw: String): UrlKind {
    val t = normalizeInput(raw)
    if (t.isBlank()) return UrlKind.Empty
    PlatformResolver.isUnsupported(t)?.let { return UrlKind.Unsupported(it) }
    if (RemoteApis.extractBvid(t) != null) return UrlKind.Platform("哔哩哔哩")
    if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) return UrlKind.Invalid
    val host = UrlNorm.host(t) ?: return UrlKind.Invalid
    if (!host.contains('.')) return UrlKind.Invalid
    val path = t.substringBefore('?').lowercase()
    if (listOf(".mp4", ".webm", ".mov", ".mkv", ".jpg", ".jpeg", ".png", ".webp", ".gif").any { path.endsWith(it) }) return UrlKind.Direct
    val p = PlatformResolver.detectPlatform(t)
    // Platform pages that resolve to a single official embed are added as one item.
    val single = PlatformResolver.extractYoutubeId(t) != null || PlatformResolver.extractVimeoId(t) != null ||
        PlatformResolver.extractDailymotionId(t) != null || PlatformResolver.extractTwitchClip(t) != null
    if (p != null && p != PlatformId.WEB && single) return UrlKind.Platform(PlatformId.label(p))
    return UrlKind.Web(host.removePrefix("www."))
}

fun relTime(now: Long, t: Long): String {
    if (t <= 0) return "从未抓取"
    val d = (now - t) / 1000
    return when {
        d < 60 -> "刚刚"
        d < 3600 -> "${d / 60} 分钟前"
        d < 86400 -> "${d / 3600} 小时前"
        else -> "${d / 86400} 天前"
    }
}

private fun clipboardUrl(ctx: Context): String? = runCatching {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
    Regex("""https?://\S+""").find(t)?.value
}.getOrNull()

// ================================================================= actions (shared with 发现)

object SourceActions {
    /** Opens the glass add / edit sheet. */
    fun openEditor(overlay: MoonOverlayState, scope: CoroutineScope, ctx: Context, editing: UserSource? = null, prefill: String? = null) {
        val clip = if (editing == null) clipboardUrl(ctx) else null
        overlay.showSheet(if (editing == null) "添加网址" else "编辑网址源") { dismiss ->
            SourceEditor(
                initial = editing?.toDraft() ?: SourceDraft(url = prefill ?: ""),
                editing = editing != null,
                clipboard = clip,
            ) { d -> dismiss(); submit(scope, editing, d) }
        }
    }

    fun submit(scope: CoroutineScope, editing: UserSource?, d: SourceDraft) {
        val app = MoonApp.instance
        val url = normalizeInput(d.url)
        when (val k = classifyUrl(url)) {
            is UrlKind.Unsupported -> { MoonToaster.show(k.msg, Icons.Outlined.Block); return }
            UrlKind.Empty, UrlKind.Invalid -> { MoonToaster.show("请输入有效的网址", Icons.Outlined.ErrorOutline); return }
            is UrlKind.Platform, UrlKind.Direct -> if (editing == null) {
                scope.launch {
                    val r = RemoteApis.resolveUrl(url)
                    val e = r.entry
                    if (e == null) { MoonToaster.show(r.error ?: "无法识别链接", Icons.Outlined.ErrorOutline); return@launch }
                    app.library.upsert(e); runCatching { SaveHelper.maybeSaveEntry(e) }
                    MoonToaster.show("已作为 ${e.platformLabelSafe()} 内容加入库", Icons.Outlined.CheckCircle)
                }
                return
            }
            is UrlKind.Web -> Unit
        }
        if (editing == null) app.sources.findByUrl(url)?.let { MoonToaster.show("这个网址已经添加过了：${it.name}", Icons.Outlined.Link); return }
        val src = (editing ?: UserSource(name = "", url = url)).copy(
            url = url,
            name = d.name.trim().ifBlank { editing?.name?.takeIf { e -> e.isNotBlank() && editing.url == url } ?: "" },
            mode = d.mode, depth = d.depth.coerceIn(1, 2), sample = d.sample.coerceIn(5, 60), autoRefreshHours = d.autoHours,
            lastError = if (editing?.url != url) null else editing.lastError,
        )
        app.sources.upsert(src)
        SourceFetcher.reschedule(app, app.sources.items.value)
        if (editing == null) fetch(scope, src, src.mode) else MoonToaster.show("已保存", Icons.Outlined.CheckCircle)
    }

    fun fetch(scope: CoroutineScope, src: UserSource, mode: FetchMode) {
        val app = MoonApp.instance
        MoonToaster.show(if (mode == FetchMode.RANDOM) "正在随机抓取 ${src.name.ifBlank { src.host }}…" else "正在抓取 ${src.name.ifBlank { src.host }}…", Icons.Outlined.Refresh)
        scope.launch {
            val cur = app.sources.items.value.firstOrNull { it.id == src.id } ?: src
            val o = runCatching { SourceFetcher.run(cur, mode) }.getOrElse { SourceFetcher.Outcome(0, 0, 0, it.message ?: "抓取失败") }
            when {
                o.error != null -> MoonToaster.show(o.error, Icons.Outlined.ErrorOutline)
                o.added == 0 && o.found > 0 -> MoonToaster.show("找到 ${o.found} 项，但都已在库中", Icons.Outlined.CheckCircle)
                else -> MoonToaster.show("已随机插入信息流 ${o.added} 项 · 浏览 ${o.pages} 页", Icons.Outlined.CheckCircle)
            }
        }
    }

    fun menu(overlay: MoonOverlayState, scope: CoroutineScope, ctx: Context, s: UserSource, rect: Rect): MenuSpec {
        val app = MoonApp.instance
        return MenuSpec(
            anchor = rect,
            title = s.name.ifBlank { s.host },
            actions = listOf(
                listOf(
                    MenuAction("立即抓取", Icons.Outlined.Refresh, subtitle = if (s.mode == FetchMode.RANDOM) "按设置：随机" else "按设置：最新（仅此页）") { fetch(scope, s, s.mode) },
                    MenuAction("随机抓取", Icons.Outlined.Casino, subtitle = "同站随机跟随 ≤${s.maxPages} 页 · 取 ${s.sample} 项") { fetch(scope, s, FetchMode.RANDOM) },
                    MenuAction("编辑", Icons.Outlined.Edit) { openEditor(overlay, scope, ctx, s) },
                    MenuAction(if (s.enabled) "暂停" else "启用", if (s.enabled) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle, subtitle = if (s.enabled) "不参与多源发现与后台刷新" else null) {
                        app.sources.update(s.id) { it.copy(enabled = !it.enabled) }
                        SourceFetcher.reschedule(app, app.sources.items.value)
                    },
                ),
                listOf(
                    MenuAction("删除", Icons.Outlined.Delete, destructive = true) {
                        val count = app.library.items.value.count { it.source == "user:${s.id}" }
                        overlay.confirm(
                            "删除「${s.name.ifBlank { s.host }}」？",
                            if (count > 0) "已抓取的 $count 项可以保留在库中，或一并删除。" else null,
                            listOfNotNull(
                                MenuAction("删除网址源") { removeSource(s, false) },
                                if (count > 0) MenuAction("删除网址源及其 $count 项内容", destructive = true) { removeSource(s, true) } else null,
                            ),
                        )
                    },
                ),
            ),
        )
    }

    private fun removeSource(s: UserSource, withItems: Boolean) {
        val app = MoonApp.instance
        app.sources.remove(s.id)
        if (withItems) app.library.removeMany(app.library.items.value.filter { it.source == "user:${s.id}" }.map { it.id })
        SourceFetcher.reschedule(app, app.sources.items.value)
        MoonToaster.show("已删除", Icons.Outlined.Delete, action = "撤销") {
            app.sources.upsert(s); SourceFetcher.reschedule(app, app.sources.items.value)
        }
    }
}

private fun MediaEntry.platformLabelSafe() = PlatformId.label(platform ?: PlatformId.WEB)

// ================================================================= screen

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val app = MoonApp.instance
    val ctx = LocalContext.current
    val overlay = LocalOverlay.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sources by app.sources.items.collectAsState()
    val library by app.library.items.collectAsState()
    var clip by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { clip = clipboardUrl(ctx)?.takeIf { u -> app.sources.findByUrl(u) == null } }
    LaunchedEffect(sources) { now = System.currentTimeMillis() }
    BackHandler(onBack = onBack)
    val thumbs = remember(library, sources) {
        sources.associate { s -> s.id to library.firstOrNull { it.source == "user:${s.id}" && it.coverUrl != null }?.coverUrl }
    }
    SourcesContent(
        sources = sources, thumbs = thumbs, clipboard = clip, now = now,
        onBack = onBack,
        onAdd = { SourceActions.openEditor(overlay, scope, ctx) },
        onPasteClip = { u -> clip = null; SourceActions.openEditor(overlay, scope, ctx, prefill = u) },
        onToggle = { s, on -> app.sources.update(s.id) { it.copy(enabled = on) }; SourceFetcher.reschedule(app, app.sources.items.value) },
        onOpen = { s -> SourceActions.openEditor(overlay, scope, ctx, s) },
        onLong = { s, r -> Haptics.open(view); overlay.showMenu(SourceActions.menu(overlay, scope, ctx, s, r)) },
    )
}

@Composable
fun SourcesContent(
    sources: List<UserSource>,
    thumbs: Map<String, String?> = emptyMap(),
    clipboard: String? = null,
    now: Long = System.currentTimeMillis(),
    onBack: () -> Unit = {},
    onAdd: () -> Unit = {},
    onPasteClip: (String) -> Unit = {},
    onToggle: (UserSource, Boolean) -> Unit = { _, _ -> },
    onOpen: (UserSource) -> Unit = {},
    onLong: (UserSource, Rect) -> Unit = { _, _ -> },
) {
    val c = LocalMoonColors.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, skinId = "page.back")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("网址源", style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text("${sources.size} 个 · ${sources.count { it.enabled }} 个启用", style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "add") { AddUrlCard(clipboard, onAdd, onPasteClip) }
            if (sources.isEmpty()) item(key = "empty") {
                MoonEmptyState(
                    "还没有网址源",
                    "添加任意公开网页地址。月光会解析页面中的图片与视频，「随机」模式还会在同站内随机跟随少量链接，抽样加入信息流。",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                item(key = "hdr") { Text("长按：立即抓取 / 随机抓取 / 编辑 / 暂停 / 删除", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
                items(sources, key = { it.id }) { s -> SourceCard(s, thumbs[s.id], now, { onToggle(s, it) }, { onOpen(s) }, { r -> onLong(s, r) }) }
            }
            item(key = "foot") {
                Text(
                    "遵守 robots.txt · 每站约 1 次/秒 · 普通浏览器 UA · 不登录、不读写 Cookie、不绕过验证码或反爬。抖音 / TikTok / 快手 / 小红书 不支持。",
                    color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AddUrlCard(clipboard: String?, onAdd: () -> Unit, onPasteClip: (String) -> Unit) {
    val c = LocalMoonColors.current
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().glass(shape, c, elevation = 10.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().moonPress(onAdd, highlightShape = shape).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.55f))), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AddLink, null, tint = c.onAccent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("添加网址", color = c.text, style = MaterialTheme.typography.titleMedium)
                Text("粘贴网页地址，自动识别平台；其他网站作为网页源随机抓取", color = c.textDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (clipboard != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(c.hairline))
            Row(
                Modifier.fillMaxWidth().moonPress({ onPasteClip(clipboard) }, highlightShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ContentPaste, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("粘贴剪贴板：", color = c.textDim, style = MaterialTheme.typography.labelLarge)
                Text(clipboard.removePrefix("https://").removePrefix("http://"), color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SourceCard(s: UserSource, thumb: String?, now: Long, onToggle: (Boolean) -> Unit, onClick: () -> Unit, onLong: (Rect) -> Unit) {
    val c = LocalMoonColors.current
    val anchor = remember { AnchorState() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().anchor(anchor)
            .moonPress(onClick, { onLong(anchor.rect) }, pressedScale = 0.97f, highlightShape = shape)
            .glassStatic(shape)
            .graphicsLayer { alpha = if (s.enabled) 1f else 0.62f }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF2B3350), Color(0xFF151A2A)))), contentAlignment = Alignment.Center) {
            if (thumb != null) MoonImage(thumb, Modifier.fillMaxSize())
            else Text(s.host.removePrefix("www.").take(1).uppercase(), color = c.accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name.ifBlank { s.host }, color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeTag(if (s.mode == FetchMode.RANDOM) "随机" else "最新")
                Spacer(Modifier.width(6.dp))
                Text(
                    s.host.removePrefix("www.") + (if (s.mode == FetchMode.RANDOM) " · ${s.depth} 层 · ${s.sample} 项" else "") + (if (s.autoRefreshHours > 0) " · 每 ${s.autoRefreshHours}h" else ""),
                    color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            StatusLine(s, now)
        }
        Spacer(Modifier.width(8.dp))
        MoonSwitch(s.enabled, onToggle)
    }
}

@Composable
private fun ModeTag(t: String) {
    val c = LocalMoonColors.current
    Text(t, color = c.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(c.accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

@Composable
private fun StatusLine(s: UserSource, now: Long) {
    val c = LocalMoonColors.current
    when {
        s.fetching -> {
            val tr = rememberInfiniteTransition(label = "f")
            val a by tr.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "fa")
            Text("正在抓取…", color = c.accent.copy(alpha = a), style = MaterialTheme.typography.labelSmall)
        }
        s.lastError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = Color(0xFFFF7A7A), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.lastError, color = Color(0xFFFF8E8E), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        else -> Text(
            if (s.lastFetchAt <= 0) "尚未抓取 · 长按立即抓取"
            else "上次 ${relTime(now, s.lastFetchAt)} · 新增 ${s.lastCount} · 共 ${s.totalCount}",
            color = c.textDim, style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ================================================================= editor sheet

@Composable
fun ColumnScope.SourceEditor(initial: SourceDraft, editing: Boolean, clipboard: String?, onSubmit: (SourceDraft) -> Unit) {
    val c = LocalMoonColors.current
    var d by remember { mutableStateOf(initial) }
    val kind = classifyUrl(d.url)
    val isWeb = kind is UrlKind.Web
    val canSubmit = kind is UrlKind.Web || ((kind is UrlKind.Platform || kind is UrlKind.Direct) && !editing)

    Field(d.url, { d = d.copy(url = it) }, "https://example.com/…", KeyboardType.Uri, Icons.Outlined.Link) {
        if (clipboard != null && d.url.isBlank()) {
            Text("粘贴", color = c.accent, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.moonPress({ d = d.copy(url = clipboard) }, highlightShape = CircleShape).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    DetectLine(kind)
    if (isWeb || editing) {
        Spacer(Modifier.height(14.dp))
        Field(d.name, { d = d.copy(name = it) }, (kind as? UrlKind.Web)?.host?.let { "名称（默认：网站标题或 $it）" } ?: "名称", KeyboardType.Text, Icons.Outlined.Edit)
        Spacer(Modifier.height(16.dp))
        Label("抓取方式")
        Segmented(listOf("最新", "随机"), if (d.mode == FetchMode.LATEST) 0 else 1) { d = d.copy(mode = if (it == 0) FetchMode.LATEST else FetchMode.RANDOM) }
        Text(
            if (d.mode == FetchMode.LATEST) "只解析这一页里的图片与视频。" else "从这一页出发，在同一网站内随机跟随少量链接（每次最多 20 页），解析媒体后随机抽取，插入信息流的随机位置。",
            color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
        if (d.mode == FetchMode.RANDOM) {
            Spacer(Modifier.height(14.dp))
            Label("跟随深度")
            Segmented(listOf("1 层", "2 层"), com.moonlight.feed.data.segmentIndex(listOf(1, 2), d.depth)) { d = d.copy(depth = it + 1) }
        }
        Spacer(Modifier.height(14.dp))
        Label("每次加入")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 50).forEach { n -> MoonChip("$n 项", d.sample == n, { d = d.copy(sample = n) }) }
        }
        Spacer(Modifier.height(14.dp))
        Label("后台自动刷新")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "关", 6 to "6 小时", 12 to "12 小时", 24 to "每天").forEach { (h, l) -> MoonChip(l, d.autoHours == h, { d = d.copy(autoHours = h) }) }
        }
        if (d.autoHours > 0) Text("仅在 Wi-Fi 且电量充足时运行", color = c.textDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    }
    Spacer(Modifier.height(18.dp))
    val label = when {
        editing -> "保存"
        kind is UrlKind.Web -> "添加并抓取"
        kind is UrlKind.Platform || kind is UrlKind.Direct -> "加入库"
        else -> "添加"
    }
    Text(
        label, color = if (canSubmit) c.onAccent else c.textDim, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
            .moonPress({ onSubmit(d) }, highlightShape = RoundedCornerShape(18.dp), enabled = canSubmit)
            .clip(RoundedCornerShape(18.dp)).background(if (canSubmit) c.accent else c.hairline).padding(vertical = 15.dp),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(t: String) {
    val c = LocalMoonColors.current
    Text(t, color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun DetectLine(kind: UrlKind) {
    val c = LocalMoonColors.current
    val (icon, text, color) = when (kind) {
        UrlKind.Empty -> Triple(Icons.Outlined.Language, "支持任意公开网页；YouTube / B站 / Vimeo 等链接会自动识别为单条内容", c.textDim)
        UrlKind.Invalid -> Triple(Icons.Outlined.ErrorOutline, "这看起来不是有效的网址", Color(0xFFFF8E8E))
        is UrlKind.Unsupported -> Triple(Icons.Outlined.Block, kind.msg, Color(0xFFFF8E8E))
        is UrlKind.Platform -> Triple(Icons.Outlined.CheckCircle, "已识别：${kind.label} · 将作为单条内容加入库", Color(0xFF7FD1AE))
        UrlKind.Direct -> Triple(Icons.Outlined.CheckCircle, "媒体直链 · 将直接加入库", Color(0xFF7FD1AE))
        is UrlKind.Web -> Triple(Icons.Outlined.Language, "通用网页源 · ${kind.host}", c.accent)
    }
    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType,
    icon: androidx.compose.ui.graphics.vector.ImageVector, trailing: @Composable () -> Unit = {},
) {
    val c = LocalMoonColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.hairline).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = type),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing()
    }
}
