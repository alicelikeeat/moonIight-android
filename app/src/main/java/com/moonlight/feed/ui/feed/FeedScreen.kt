package com.moonlight.feed.ui.feed

import com.moonlight.feed.ui.design.shredAnchor
import com.moonlight.feed.ui.design.skinFill
import com.moonlight.feed.ui.design.playable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.SideEffect
import com.moonlight.feed.ui.design.softShadow
import com.moonlight.feed.ui.design.RevealMedia
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moonlight.feed.BiliPlayerActivity
import com.moonlight.feed.MoonApp
import com.moonlight.feed.R
import com.moonlight.feed.data.GallerySaver
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.MediaProbe
import com.moonlight.feed.ui.AppBus
import com.moonlight.feed.ui.AppEvent
import com.moonlight.feed.ui.openOriginal
import com.moonlight.feed.ui.originalUrl
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.shareEntry
import com.moonlight.feed.ui.design.AnimatedHeart
import com.moonlight.feed.ui.design.GlassBadge
import com.moonlight.feed.ui.design.GlassIconButton
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.HeartBurst
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonEmptyState
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.MuteIcon
import com.moonlight.feed.ui.design.drawCrescent
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.ui.theme.MoonPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.Locale

/*
 * Feed gesture scheme (v4.0 — see docs/INTERACTION.md)
 *  tap             → video: play / pause · image / embed: show / hide chrome
 *  double-tap      → 喜欢 (heart burst at finger); a zoomed image un-zooms
 *  long-press      → ring: ≤ 4 frequent actions + ··· sheet with everything else (delete only there)
 *  vertical drag   → next / previous
 *  swipe left      → details card (source, size, date, tags)
 *  pinch           → video fit / fill · image zoom
 */

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Callbacks for the feed context menu. */
class FeedMenuHandlers(
    val onFavorite: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onInfo: () -> Unit = {},
    val onLoop: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onSpeed: (Float) -> Unit = {},
    val onFit: () -> Unit = {},
    val onOpenOriginal: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onHide: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onImageSeconds: (Int) -> Unit = {},
    val onScope: (com.moonlight.feed.data.FeedScope) -> Unit = {},
    val onPostMoment: () -> Unit = {},
    val onSetBackground: () -> Unit = {},
    val onNextSameSource: () -> Unit = {},
    val onDownload: () -> Unit = {},
)

fun feedMenuSpec(
    entry: MediaEntry,
    muted: Boolean,
    loop: Boolean,
    speed: Float,
    fit: Boolean,
    playing: Boolean,
    h: FeedMenuHandlers,
    imageSeconds: Int = 5,
    @Suppress("UNUSED_PARAMETER") scope: com.moonlight.feed.data.FeedScope = com.moonlight.feed.data.FeedScope.ALL,
    /** v4.4 主动下载: this item's task (progress / paused / done), if any. */
    dl: com.moonlight.feed.data.download.DownloadTask? = null,
): MenuSpec {
    val video = entry.kind == MediaKind.VIDEO
    val offer = com.moonlight.feed.data.download.DownloadPolicy.check(entry)
    // ··· sheet groups: 播放 / 分享与保存 / 整理 / 危险 (view-level 筛选·随机·洗牌 live on the 首页 tab ring, not here)
    val play = buildList {
        if (video) {
            add(MenuAction(if (playing) "暂停" else "播放", if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, onClick = h.onPlayPause))
            add(MenuAction("循环播放", Icons.Outlined.Repeat, checked = loop, onClick = h.onLoop))
            add(MenuAction("静音", Icons.AutoMirrored.Outlined.VolumeOff, checked = muted, onClick = h.onMute))
            add(speedAction(speed, h))
            add(MenuAction("画面", Icons.Outlined.AspectRatio, subtitle = if (fit) "完整显示" else "铺满", children = listOf(
                MenuAction("完整显示（不裁切）", checked = fit, short = "完整", onClick = { if (!fit) h.onFit() }),
                MenuAction("铺满（裁切边缘）", checked = !fit, short = "铺满", onClick = { if (fit) h.onFit() }),
            )))
        }
        if (entry.kind == MediaKind.IMAGE) add(dwellAction(imageSeconds, h))
        add(MenuAction(if (video) "视频信息" else "信息", Icons.Outlined.Info, subtitle = "也可在画面上向左滑", onClick = h.onInfo))
    }
    val share = buildList {
        add(MenuAction("保存到相册", Icons.Outlined.Download, onClick = h.onSave))
        add(com.moonlight.feed.ui.download.DownloadActions.sheetRow(offer, dl, h.onDownload))
        add(MenuAction("分享", Icons.Outlined.Share, onClick = h.onShare))
        add(MenuAction("发到朋友圈", Icons.Outlined.Camera, onClick = h.onPostMoment))
        if (entry.isWebEmbed()) add(MenuAction("在 ${entry.platformLabel()} 打开", Icons.AutoMirrored.Outlined.OpenInNew, onClick = h.onOpenOriginal))
    }
    val tidy = buildList {
        add(MenuAction(if (entry.favorite) "取消收藏" else "收藏", if (entry.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, checked = entry.favorite, onClick = h.onFavorite))
        if (entry.kind == MediaKind.IMAGE) add(MenuAction("设为首页背景", Icons.Outlined.Wallpaper, onClick = h.onSetBackground))
        add(MenuAction("从首页隐藏", Icons.Outlined.VisibilityOff, onClick = h.onHide))
    }
    return MenuSpec(
        anchor = Rect.Zero,
        title = entry.title.ifBlank { null },
        actions = listOf(play, share, tidy, listOf(MenuAction("删除", Icons.Outlined.Delete, destructive = true, onClick = h.onDelete))),
        groupTitles = listOf("播放", "分享与保存", "整理", null),
        liftKey = entry.id,
        context = com.moonlight.feed.data.HoldContext.FEED_ITEM,
        forceDark = true,   // per-item ring: always opened on a feed item, i.e. over media
        radial = feedRing(entry, muted, speed, imageSeconds, h, com.moonlight.feed.ui.download.DownloadActions.ringChild(offer, dl, h.onDownload)),
    )
}

private fun speedAction(speed: Float, h: FeedMenuHandlers) = MenuAction(
    "倍速", Icons.Outlined.Speed, subtitle = speedLabel(speed),
    children = SPEEDS.map { s -> MenuAction(speedLabel(s), checked = s == speed, short = speedLabel(s), onClick = { h.onSpeed(s) }) },
)

private fun dwellAction(imageSeconds: Int, h: FeedMenuHandlers) = MenuAction(
    "停留时间", Icons.Outlined.Timer, subtitle = com.moonlight.feed.data.ImageDwell.label(imageSeconds),
    children = com.moonlight.feed.data.ImageDwell.OPTIONS.map { v ->
        MenuAction(if (v == 0) "永久（不自动切换）" else com.moonlight.feed.data.ImageDwell.label(v), checked = v == imageSeconds,
            short = if (v == 0) "永久" else "${v}秒", onClick = { h.onImageSeconds(v) })
    },
)

/** 分享·保存 on the ring: one slot, its choices fan out on the secondary arc. */
private fun shareSave(h: FeedMenuHandlers, download: MenuAction?) = MenuAction(
    "分享·保存", Icons.Outlined.IosShare, children = listOfNotNull(
        MenuAction("分享", Icons.Outlined.Share, onClick = h.onShare),
        MenuAction("保存到相册", Icons.Outlined.Download, onClick = h.onSave),
        download,   // v4.4 主动下载 → 库 › 我的下载 (only for direct-file sources)
        MenuAction("发到朋友圈", Icons.Outlined.Camera, onClick = h.onPostMoment),
    ),
)

/** The 6–8 primary actions that spring out around the finger. */
/** 筛选 ring item: 全部 / 本地 / 在线, current one checked (no visible switch on the home screen). */
fun scopeAction(scope: com.moonlight.feed.data.FeedScope, onScope: (com.moonlight.feed.data.FeedScope) -> Unit) = MenuAction(
    "筛选", Icons.Outlined.FilterList, subtitle = scope.label, checked = scope != com.moonlight.feed.data.FeedScope.ALL,
    children = com.moonlight.feed.data.FeedScope.entries.map { s -> MenuAction(s.label, subtitle = s.hint, checked = s == scope, short = s.label, onClick = { onScope(s) }) },
)

val MEDIA_AUDIO: androidx.media3.common.AudioAttributes = androidx.media3.common.AudioAttributes.Builder()
    .setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build()

class HomeRingHandlers(
    val top: () -> Unit = {},
    val random: () -> Unit = {},
    val reshuffle: () -> Unit = {},
    val scope: (com.moonlight.feed.data.FeedScope) -> Unit = {},
    val toggleShuffle: () -> Unit = {},
    val toggleMute: () -> Unit = {},
)

/**
 * Feed-level ring — 首页 tab long-press, counter-pill long-press, long-press on an empty feed.
 * Always carries 筛选 (v2.9 only put it on the per-item ring, so the tab ring had just 3 sparse items).
 */
fun homeRing(
    anchor: Rect, scope: com.moonlight.feed.data.FeedScope, muted: Boolean, shuffle: Boolean, h: HomeRingHandlers,
    origin: Offset? = null,
    /** v4.3: true only when the ring opens over media (the video feed); over any other page it follows the theme. */
    overMedia: Boolean = true,
): MenuSpec = MenuSpec(context = com.moonlight.feed.data.HoldContext.TAB, 
    anchor = anchor, origin = origin, forceDark = overMedia, title = "首页 · ${scope.label}",
    // view-level ring (首页 tab / counter / empty feed): 筛选 · 随机 · 洗牌 · 回到顶部; the rest behind ···
    radial = listOf(
        scopeAction(scope, h.scope),
        MenuAction("随机一条", Icons.Outlined.Shuffle, onClick = h.random),
        MenuAction("重新洗牌", Icons.Outlined.Refresh, onClick = h.reshuffle),
        MenuAction("回到顶部", Icons.Outlined.VerticalAlignTop, onClick = h.top),
    ),
    actions = listOf(listOf(
        MenuAction("随机顺序", Icons.Outlined.Casino, checked = shuffle, subtitle = if (shuffle) "开" else "关", onClick = h.toggleShuffle),
        MenuAction(if (muted) "打开声音" else "静音", if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, checked = muted, onClick = h.toggleMute),
    )),
    groupTitles = listOf("首页"),
)

/** The counter pill sits in the top-right corner: hang its ring below it, centred, so no label clips at the edge. */
fun counterRing(spec: MenuSpec): MenuSpec =
    spec.copy(origin = Offset(spec.anchor.center.x, spec.anchor.bottom + spec.anchor.height * 6f), centerHorizontally = true)

/** Ring centre for rings opened from the bottom tab bar: lifted above the bar so the arc isn't squeezed against the edge. */
fun liftedAbove(anchor: Rect): Offset? = if (anchor == Rect.Zero) null else Offset(anchor.center.x, anchor.top - anchor.height * 1.7f)

/** v4.0 per-item ring: the 4 most frequent actions for this kind of item; everything else is behind ···. */
fun feedRing(entry: MediaEntry, muted: Boolean, speed: Float, imageSeconds: Int, h: FeedMenuHandlers, download: MenuAction? = null): List<MenuAction> {
    val fav = MenuAction(if (entry.favorite) "已收藏" else "收藏", if (entry.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, checked = entry.favorite, onClick = h.onFavorite)
    return when {
        entry.isWebEmbed() || entry.kind == MediaKind.BILI || entry.kind == MediaKind.EMBED -> listOf(
            fav,
            MenuAction("在浏览器打开", Icons.AutoMirrored.Outlined.OpenInNew, subtitle = entry.platformLabel(), onClick = h.onOpenOriginal),
            MenuAction("下一个同源", Icons.Outlined.SkipNext, subtitle = entry.platformLabel(), onClick = h.onNextSameSource),
            MenuAction("分享", Icons.Outlined.Share, onClick = h.onShare),
        )
        entry.kind == MediaKind.VIDEO -> listOf(
            fav, speedAction(speed, h),
            MenuAction(if (muted) "打开声音" else "静音", if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, checked = muted, onClick = h.onMute),
            shareSave(h, download),
        )
        else -> listOf(
            fav, dwellAction(imageSeconds, h),
            MenuAction("设为背景", Icons.Outlined.Wallpaper, subtitle = "首页背景", onClick = h.onSetBackground),
            shareSave(h, download),
        )
    }
}

private fun speedLabel(s: Float) = if (s == 1f) "正常" else "${if (s % 1f == 0f) s.toInt().toString() else s.toString()}×"

// =================================================================== stateful screen

@Composable
fun FeedScreen(chromeVisible: Boolean, onToggleChrome: () -> Unit, onGoDiscover: () -> Unit = {}) {
    val app = MoonApp.instance
    val ctx = LocalContext.current
    val overlay = LocalOverlay.current
    val view = LocalView.current
    val all by app.library.items.collectAsState()
    val settings by app.settings.flow.collectAsState(initial = com.moonlight.feed.data.SettingsRepository.Snapshot())
    var shuffleSeed by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    // ids pinned to the front (new capture / search batch "加入信息流")
    var pinned by remember { mutableStateOf<List<String>>(emptyList()) }
    val scopeSetting = com.moonlight.feed.data.FeedScope.of(settings.feedScope)
    // optimistic local copy so the ring choice applies instantly (DataStore write is async)
    var scopeOverride by remember { mutableStateOf<com.moonlight.feed.data.FeedScope?>(null) }
    val feedScope = scopeOverride ?: scopeSetting
    LaunchedEffect(scopeSetting) { if (scopeOverride == scopeSetting) scopeOverride = null }
    val visibleAll = remember(all) { all.filter { !it.hidden } }
    val list = remember(all, settings.shuffle, shuffleSeed, pinned, feedScope) {
        var l = all.filter { !it.hidden && feedScope.matches(it) }
        if (settings.shuffle || shuffleSeed != 0L) l = l.shuffled(kotlin.random.Random(shuffleSeed))
        if (pinned.isNotEmpty()) {
            val byId = l.associateBy { it.id }
            val head = pinned.mapNotNull { byId[it] }
            val set = head.map { it.id }.toSet()
            l = head + l.filter { it.id !in set }
        }
        l
    }
    val start = if (settings.resume && list.isNotEmpty()) settings.lastIndex.coerceIn(0, list.lastIndex) else 0
    fun setScope(s: com.moonlight.feed.data.FeedScope, toast: Boolean = true) {
        scopeOverride = s
        scope.launch { app.settings.setFeedScope(s.name) }
        if (toast) {
            val n = visibleAll.count { s.matches(it) }
            MoonToaster.show("首页 · ${s.label}  ${n} 项", Icons.Outlined.FilterList)
        }
    }
    val pager = rememberPagerState(initialPage = start.coerceAtMost((list.size - 1).coerceAtLeast(0))) { list.size.coerceAtLeast(1) }

    val biliLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val id = data.getStringExtra(BiliPlayerActivity.EXTRA_RESULT_ID)
        val idx = if (id != null) list.indexOfFirst { it.id == id } else data.getIntExtra(BiliPlayerActivity.EXTRA_RESULT_INDEX, -1)
        if (idx in list.indices) scope.launch { pager.scrollToPage(idx); app.settings.setLastIndex(idx) }
    }
    fun openImmersivePlayer(entry: MediaEntry) {
        val i = Intent(app, BiliPlayerActivity::class.java)
            .putExtra(BiliPlayerActivity.EXTRA_START_ID, entry.id)
            .putStringArrayListExtra(BiliPlayerActivity.EXTRA_FEED_IDS, ArrayList(list.map { it.id }))
        biliLauncher.launch(i)
    }

    val sharedPlayer = remember {
        val renderers = DefaultRenderersFactory(app)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        ExoPlayer.Builder(app, renderers).build().apply {
            playWhenReady = false; repeatMode = Player.REPEAT_MODE_ONE
            setHandleAudioBecomingNoisy(true) // headphones unplugged → pause
        }
    }
    // Audio focus only while sound is on: a muted feed must not interrupt the user's music; with sound, focus loss pauses us.
    LaunchedEffect(settings.muted) { sharedPlayer.setAudioAttributes(MEDIA_AUDIO, !settings.muted) }
    var isPlaying by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    // 视频显示: per-item fit/fill override (pinch / 画面 menu) on top of the 智能/完整显示/铺满 setting
    val fillOverride = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val fillNow = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val videoDisplay = com.moonlight.feed.data.VideoFit.of(settings.videoDisplay)
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            // ⏸/▶ follows intent (playWhenReady + state), not the momentary isPlaying — see PlayToggle
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = com.moonlight.feed.data.PlayToggle.showsPlaying(sharedPlayer.playable())
            }
        }
        sharedPlayer.addListener(l)
        onDispose { sharedPlayer.removeListener(l); sharedPlayer.release() }
    }
    LaunchedEffect(speed) { sharedPlayer.setPlaybackSpeed(speed) }

    // ---- pause on leave (home / recents / other app / screen off / another activity on top) ----
    val gate = remember { com.moonlight.feed.data.PlaybackGate() }
    var suspended by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val curEntryRef = rememberUpdatedState(list.getOrNull(pager.settledPage))
    val resumeAllowed = rememberUpdatedState(settings.resumeOnReturn)
    val userPausedRef = rememberUpdatedState(userPaused)
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            when (ev) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    gate.suspend(sharedPlayer.isPlaying || (sharedPlayer.playWhenReady && sharedPlayer.playbackState == Player.STATE_BUFFERING), curEntryRef.value?.id)
                    suspended = true
                    sharedPlayer.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    val cur = curEntryRef.value
                    val go = gate.resume(resumeAllowed.value, cur?.id)
                    suspended = false
                    if (go && cur?.kind == MediaKind.VIDEO && !userPausedRef.value) com.moonlight.feed.data.PlayToggle.resume(sharedPlayer.playable())
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // auto-hide chrome ~3s after interaction while media plays
    var lastInteractAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentEntry = list.getOrNull(pager.settledPage)
    LaunchedEffect(chromeVisible, pager.settledPage, lastInteractAt, isPlaying, overlay.menu) {
        if (!chromeVisible || currentEntry == null || overlay.menu != null) return@LaunchedEffect
        delay(3000)
        val pausedVideo = currentEntry.kind == MediaKind.VIDEO && !isPlaying
        if (!pausedVideo && System.currentTimeMillis() - lastInteractAt >= 2900) onToggleChrome()
    }
    LaunchedEffect(pager, list.size) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (list.isNotEmpty()) app.settings.setLastIndex(page.coerceAtMost(list.lastIndex))
            lastInteractAt = System.currentTimeMillis()
            userPaused = false
        }
    }
    LaunchedEffect(list.size) {
        if (list.isNotEmpty() && pager.currentPage > list.lastIndex) pager.scrollToPage(list.lastIndex)
    }
    var lastScope by remember { mutableStateOf(feedScope) }
    LaunchedEffect(feedScope) {
        if (feedScope != lastScope) { lastScope = feedScope; if (list.isNotEmpty()) pager.scrollToPage(0) }
    }
    val focus by AppBus.feedFocus.collectAsState()
    LaunchedEffect(focus, all) {
        val ids = focus ?: return@LaunchedEffect
        if (ids.none { id -> all.any { it.id == id } }) return@LaunchedEffect // wait until the library has them
        if (all.filter { it.id in ids }.none { feedScope.matches(it) }) setScope(com.moonlight.feed.data.FeedScope.ALL, toast = false)
        pinned = ids
        AppBus.feedFocus.value = null
        userPaused = false
        snapshotFlow { list.firstOrNull()?.id }.first { it == ids.firstOrNull { id -> all.any { e -> e.id == id } } }
        pager.scrollToPage(0)
        app.settings.setLastIndex(0)
    }
    val scopeReq by AppBus.feedScopeRequest.collectAsState()
    LaunchedEffect(scopeReq) { scopeReq?.let { setScope(it); AppBus.feedScopeRequest.value = null } }
    fun homeHandlers() = HomeRingHandlers(
        top = { AppBus.send(AppEvent.FEED_TOP) },
        random = { AppBus.send(AppEvent.FEED_RANDOM) },
        reshuffle = { AppBus.send(AppEvent.FEED_REFRESH) },
        scope = { s -> setScope(s) },
        toggleShuffle = { scope.launch { app.settings.setShuffle(!settings.shuffle) } },
        toggleMute = { scope.launch { app.settings.setMuted(!settings.muted) } },
    )
    LaunchedEffect(Unit) {
        AppBus.events.collect { e ->
            when (e) {
                AppEvent.FEED_TOP -> if (list.isNotEmpty()) pager.animateScrollToPage(0)
                AppEvent.FEED_RANDOM -> if (list.size > 1) pager.animateScrollToPage(list.indices.random())
                AppEvent.FEED_REFRESH -> { shuffleSeed = System.nanoTime(); pager.scrollToPage(0); MoonToaster.show("已重新洗牌", Icons.Outlined.Refresh) }
                else -> Unit
            }
        }
    }

    fun togglePlay() {
        val now = com.moonlight.feed.data.PlayToggle.toggle(sharedPlayer.playable())
        userPaused = !now
        isPlaying = now
        lastInteractAt = System.currentTimeMillis()
    }

    fun requestDelete(target: MediaEntry) {
        overlay.confirm(
            title = "删除这条内容？",
            message = target.title.ifBlank { null },
            actions = listOf(
                MenuAction("从月光移除", destructive = true, subtitle = "移到 库 › 最近删除，可恢复", onClick = {
                    com.moonlight.feed.ui.library.TrashUi.trashEntries(listOf(target))
                }),
                MenuAction("同时从相册删除", destructive = true, onClick = {
                    scope.launch {
                        GallerySaver.deleteGalleryCopy(app, target)
                        com.moonlight.feed.ui.library.TrashUi.shredOnly(target)
                        app.library.remove(target.id, deleteLocalFile = true, deleteGallery = false)
                        MoonToaster.show("已删除", Icons.Outlined.Delete)
                    }
                }),
            ),
        )
    }

    fun showInfo(item: MediaEntry) {
        Haptics.tick(view)
        overlay.showSheet(null, dark = true) { _ -> MediaInfoCard(item, if (item.kind == MediaKind.VIDEO && pager.settledPage == list.indexOf(item)) sharedPlayer else null) }
    }

    fun handlersFor(item: MediaEntry) = FeedMenuHandlers(
        onFavorite = { app.library.update(item.id) { it.copy(favorite = !it.favorite) } },
        onSave = {
            scope.launch {
                MoonToaster.show("正在保存…", Icons.Outlined.Download)
                val r = GallerySaver.saveEntry(app, app.http, item)
                if (r.ok) MoonToaster.show("已保存到相册", Icons.Outlined.CheckCircle)
                else MoonToaster.show("保存失败：${r.error}", Icons.Outlined.ErrorOutline)
            }
        },
        onShare = { if (!shareEntry(ctx, item)) MoonToaster.show("这条内容暂无可分享的链接") },
        onDownload = { com.moonlight.feed.ui.download.DownloadActions.start(item) },
        onInfo = {
            showInfo(item)
        },
        onLoop = { scope.launch { app.settings.setLoop(!settings.loop) } },
        onMute = { scope.launch { app.settings.setMuted(!settings.muted) } },
        onSpeed = { s -> speed = s; MoonToaster.show("倍速 ${speedLabel(s)}", Icons.Outlined.Speed) },
        onFit = {
            val f = !(fillNow[item.id] ?: false)
            fillOverride[item.id] = f
            MoonToaster.show(if (f) "铺满" else "完整显示", Icons.Outlined.AspectRatio)
        },
        onImageSeconds = { v ->
            scope.launch { app.settings.setImageSeconds(v) }
            MoonToaster.show(if (v == 0) "图片停留：永久（滑动切换）" else "图片停留：$v 秒", Icons.Outlined.Timer)
        },
        onOpenOriginal = { openOriginal(ctx, item) },
        onPlayPause = { togglePlay() },
        onHide = {
            app.library.update(item.id) { it.copy(hidden = true) }
            MoonToaster.show("已隐藏", Icons.Outlined.VisibilityOff, action = "撤销") {
                app.library.update(item.id) { it.copy(hidden = false) }
            }
        },
        onDelete = { requestDelete(item) },
        onScope = { s -> setScope(s) },
        onPostMoment = { com.moonlight.feed.ui.AppBus.composeMoment(listOf(item.id)) },
        onSetBackground = {
            scope.launch {
                val src = item.localPath?.let { java.io.File(it) }?.takeIf { it.exists() }
                if (src == null) { MoonToaster.show("这张图片还没下载到本机"); return@launch }
                val dst = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(ctx.filesDir, "bg").apply { mkdirs() }
                    java.io.File(dir, "home_${System.currentTimeMillis()}.${src.extension.ifBlank { "jpg" }}").also { src.copyTo(it, overwrite = true) }
                }
                app.settings.setHomeBg(com.moonlight.feed.data.HomeBgKind.PHOTO.name, dst.absolutePath)
                MoonToaster.show("已设为首页背景", Icons.Outlined.Wallpaper)
            }
        },
        onNextSameSource = {
            val from = pager.currentPage
            val next = com.moonlight.feed.data.FeedNav.nextSameSource(list.map { it.platformLabel() }, from)
            if (next != null) scope.launch { pager.animateScrollToPage(next) } else MoonToaster.show("后面没有同来源的内容了")
        },
    )

    val feedHaze = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            val bg = com.moonlight.feed.ui.design.LocalHomeBg.current
            // 跟随主题 → empty state reads like every other page; photo/video/black → light-on-dark glass
            CompositionLocalProvider(LocalMoonColors provides if (bg.kind == com.moonlight.feed.data.HomeBgKind.THEME) LocalMoonColors.current else MediaGlass) {
                Box(
                    Modifier.fillMaxSize().pointerInput(feedScope, settings.muted, settings.shuffle) {
                        detectTapGestures(onLongPress = { pos ->
                            Haptics.open(view)
                            overlay.showMenu(homeRing(Rect(pos, pos), feedScope, settings.muted, settings.shuffle, homeHandlers(), overMedia = bg.kind != com.moonlight.feed.data.HomeBgKind.THEME))
                        })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (visibleAll.isNotEmpty() && feedScope != com.moonlight.feed.data.FeedScope.ALL) {
                        MoonEmptyState(
                            if (feedScope == com.moonlight.feed.data.FeedScope.LOCAL) "没有本地内容" else "没有在线内容",
                            if (feedScope == com.moonlight.feed.data.FeedScope.LOCAL) "拍摄、导入或下载的图片与视频会出现在这里" else "嵌入播放（B 站、YouTube 等）与网络视频会出现在这里",
                            action = "显示全部", onAction = { setScope(com.moonlight.feed.data.FeedScope.ALL) },
                        )
                    } else MoonEmptyState("还没有内容", "导入本地视频 / 图片，或去发现页添加", action = "去发现", onAction = onGoDiscover)
                }
            }
        } else {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().haze(feedHaze),
                beyondViewportPageCount = 1,
                userScrollEnabled = overlay.menu == null,
                key = { page -> list.getOrNull(page)?.id ?: page },
            ) { page ->
                val item = list[page]
                val active = pager.settledPage == page
                val zoom = remember(item.id) { ZoomState() }
                val isVideo = item.kind == MediaKind.VIDEO
                val lifted = overlay.liftKey == item.id
                FeedItem(
                    entry = item,
                    chromeVisible = chromeVisible,
                    muted = settings.muted,
                    playing = isPlaying && active,
                    showPlayButton = isVideo && active && (chromeVisible || userPaused),
                    zoom = if (item.kind == MediaKind.IMAGE) zoom else null,
                    onPinch = if (isVideo) { out ->
                        // pinch out = 铺满, pinch in = 完整显示 (this item only)
                        if ((fillNow[item.id] ?: false) != out) {
                            fillOverride[item.id] = out
                            Haptics.tick(view)
                            MoonToaster.show(if (out) "铺满" else "完整显示", Icons.Outlined.AspectRatio)
                        }
                    } else null,
                    onTap = {
                        lastInteractAt = System.currentTimeMillis()
                        // v4.0: tap = the primary action — video play/pause anywhere on the picture, image chrome
                        when (com.moonlight.feed.data.FeedTapRule.onTap(isVideo, active)) {
                            com.moonlight.feed.data.FeedTap.TOGGLE_PLAY -> {
                                togglePlay()
                                if (userPaused && !chromeVisible) onToggleChrome()   // paused → show the controls with the ▶
                            }
                            com.moonlight.feed.data.FeedTap.TOGGLE_CHROME -> onToggleChrome()
                        }
                    },
                    onDoubleTap = {
                        lastInteractAt = System.currentTimeMillis()
                        if (!item.favorite) app.library.update(item.id) { it.copy(favorite = true) }
                    },
                    onLongPress = {
                        Haptics.open(view)
                        overlay.showMenu(com.moonlight.feed.withRecord(ctx, feedMenuSpec(item, settings.muted, settings.loop, speed, !(fillNow[item.id] ?: false), isPlaying && active, handlersFor(item), settings.imageSeconds, feedScope, com.moonlight.feed.ui.download.DownloadActions.task(item.id))))
                    },
                    onFavorite = { app.library.update(item.id) { it.copy(favorite = !it.favorite) } },
                    onMute = { scope.launch { app.settings.setMuted(!settings.muted) } },
                    onMore = {
                        Haptics.open(view)
                        overlay.showMenu(com.moonlight.feed.withRecord(ctx, feedMenuSpec(item, settings.muted, settings.loop, speed, !(fillNow[item.id] ?: false), isPlaying && active, handlersFor(item), settings.imageSeconds, feedScope, com.moonlight.feed.ui.download.DownloadActions.task(item.id))))
                    },
                    onPlayPause = { togglePlay() },
                    onOpenEmbed = { openImmersivePlayer(item) },
                    onSwipeLeft = { showInfo(item) },
                ) {
                  RevealMedia(key = item.id, play = active, fallback = item.coverUrl ?: item.localPath?.let { java.io.File(it) }, modifier = Modifier.fillMaxSize()) {
                    when (item.kind) {
                        MediaKind.VIDEO -> VideoPage(
                            item, active, settings.muted, settings.loop, videoDisplay, fillOverride[item.id], sharedPlayer,
                            userPaused || suspended, settings.showProgressWhilePlaying,
                            onFill = { f -> if (fillNow[item.id] != f) fillNow[item.id] = f },
                        )
                        MediaKind.IMAGE -> ImagePage(item, active && !lifted && !suspended, settings.imageSeconds, zoom) {
                            if (page < list.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) }
                        }
                        MediaKind.BILI, MediaKind.EMBED -> EmbedCover(item)
                    }
                  }
                }
            }
        }
        FeedTopChrome(
            visible = chromeVisible && overlay.liftKey == null && list.isNotEmpty(),
            index = if (list.isEmpty()) 0 else pager.currentPage + 1,
            total = list.size,
            scopeLabel = if (feedScope == com.moonlight.feed.data.FeedScope.ALL) null else feedScope.label,
            haze = feedHaze,
            onCounterLongPress = {
                Haptics.open(view)
                overlay.showMenu(counterRing(homeRing(it, feedScope, settings.muted, settings.shuffle, homeHandlers())))
            },
            // counter pill tap = jump / scrub through the list; long-press = the view ring
            onCounterClick = {
                if (list.isNotEmpty()) overlay.showSheet("跳转", dark = true) { dismiss ->
                    JumpSheet(pager.currentPage, list.size,
                        onJump = { i -> dismiss(); scope.launch { pager.scrollToPage(i) } },
                        onRandom = { dismiss(); scope.launch { pager.scrollToPage(list.indices.random()) } })
                }
            },
        )
    }
}

// =================================================================== stateless frame (also used by screenshots)

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    val zoomed get() = scale > 1.05f
    fun toggle() { if (zoomed) { scale = 1f; offset = Offset.Zero } else scale = 2.2f }
}

@Composable
fun FeedItem(
    entry: MediaEntry,
    chromeVisible: Boolean,
    muted: Boolean,
    playing: Boolean,
    showPlayButton: Boolean,
    zoom: ZoomState? = null,
    onPinch: ((Boolean) -> Unit)? = null,
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onFavorite: () -> Unit = {},
    onMute: () -> Unit = {},
    onMore: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onOpenEmbed: () -> Unit = {},
    /** Horizontal swipe to the left on the media → details card (source, size, date, tags). */
    onSwipeLeft: () -> Unit = {},
    media: @Composable () -> Unit,
) {
    val overlay = LocalOverlay.current
    val lift = if (overlay.liftKey == entry.id) overlay.liftProgress else 0f
    var burstKey by remember { mutableLongStateOf(0L) }
    var burstAt by remember { mutableStateOf(Offset.Zero) }
    val chromeAlpha by animateFloatAsState(if (chromeVisible) 1f else 0f, spring(stiffness = 400f), label = "chrome")
    val isVideo = entry.kind == MediaKind.VIDEO
    val itemHaze = remember { HazeState() }
    CompositionLocalProvider(LocalMoonColors provides MediaGlass, LocalHazeState provides itemHaze) {
        Box(Modifier.fillMaxSize().then(Modifier.shredAnchor(entry.id))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val target = overlay.liftScale
                        val s = 1f - (1f - target) * lift
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        scaleX = s; scaleY = s
                        translationY = overlay.liftTop * lift
                        shape = RoundedCornerShape((30 / s.coerceAtLeast(0.3f) * lift.coerceIn(0f, 1f)).dp)
                        clip = lift > 0.001f
                        shadowElevation = 30f * lift
                    }
            ) {
                Box(Modifier.fillMaxSize().haze(itemHaze)) { media() }
                // pointerInput is keyed on the item only → read the *current* handlers, not the first composition's
                val tapNow by androidx.compose.runtime.rememberUpdatedState(onTap)
                val doubleTapNow by androidx.compose.runtime.rememberUpdatedState(onDoubleTap)
                val longPressNow by androidx.compose.runtime.rememberUpdatedState(onLongPress)
                val swipeLeftNow by androidx.compose.runtime.rememberUpdatedState(onSwipeLeft)
                // gesture layer (taps, double-tap, long-press, optional pinch)
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (zoom != null) Modifier.pinchZoom(zoom) else Modifier)
                        .then(if (onPinch != null) Modifier.pinchToggle(onPinch) else Modifier)
                        .swipeLeft(enabled = zoom?.zoomed != true) { swipeLeftNow() }
                        .pointerInput(entry.id, zoom != null) {
                            detectTapGestures(
                                onTap = { tapNow() },
                                onDoubleTap = { p ->
                                    // v4.0: double-tap = 喜欢 for every item; a zoomed image un-zooms instead (zoom = pinch)
                                    if (zoom?.zoomed == true) zoom.toggle()
                                    else { burstAt = p; burstKey = System.nanoTime(); doubleTapNow() }
                                },
                                onLongPress = { if (zoom?.zoomed != true) longPressNow() },
                            )
                        }
                )
                if (burstKey != 0L) HeartBurst(burstAt, burstKey)

                // bottom info
                if (chromeAlpha > 0.01f && lift < 0.99f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = chromeAlpha * (1f - lift); translationY = (1f - chromeAlpha) * 40f }
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                            .padding(start = 20.dp, end = 84.dp, bottom = 108.dp, top = 64.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassBadge(entry.platformLabel())
                                if (entry.author.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("@${entry.author}", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (entry.title.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(entry.title, color = Color.White.copy(0.92f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // minimal side rail: heart · mute · more (everything else lives in the long-press menu)
                    Column(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 112.dp)
                            .graphicsLayer { alpha = chromeAlpha * (1f - lift); translationX = (1f - chromeAlpha) * 60f },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // transparent by default: shadowed icons on 48dp hit targets; 按键背景 → a 46dp picture disc
                        val skLike = com.moonlight.feed.ui.design.rememberButtonSkin("player.like")
                        val skMute = com.moonlight.feed.ui.design.rememberButtonSkin("player.mute")
                        val skMore = com.moonlight.feed.ui.design.rememberButtonSkin("player.more")
                        fun Modifier.rail(sk: com.moonlight.feed.ui.design.SkinPaint?) = if (sk != null) padding(1.dp).skinFill(sk, CircleShape) else this
                        Box(
                            Modifier.size(48.dp).moonPress(onFavorite, highlightShape = CircleShape, highlight = Color.White.copy(0.12f)).rail(skLike),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.softShadow()) { AnimatedHeart(entry.favorite, size = 27.dp, idleTint = skLike?.ink ?: Color.White) } }
                        if (isVideo) {
                            Box(
                                Modifier.size(48.dp).moonPress(onMute, highlightShape = CircleShape, highlight = Color.White.copy(0.12f)).rail(skMute),
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.softShadow()) { MuteIcon(muted, tint = skMute?.ink ?: Color.White) } }
                        }
                        Box(
                            Modifier.size(48.dp).moonPress(onMore, onMore, highlightShape = CircleShape, highlight = Color.White.copy(0.12f)).rail(skMore),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.MoreHoriz, "更多", tint = skMore?.ink ?: Color.White, modifier = Modifier.size(28.dp).softShadow()) }
                    }
                }

                // centre controls
                AnimatedVisibility(
                    visible = showPlayButton && lift < 0.5f,
                    enter = fadeIn() + scaleIn(spring(0.55f, 500f), initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    GlassIconButton(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "暂停" else "播放",
                        onPlayPause, size = 76.dp, iconSize = 38.dp, onLongClick = onLongPress, skinId = "player.play",
                    )
                }
                if (entry.isWebEmbed()) {
                    Column(
                        Modifier.align(Alignment.Center).graphicsLayer { alpha = (0.35f + 0.65f * chromeAlpha) * (1f - lift) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlassIconButton(Icons.Outlined.PlayArrow, "播放", onOpenEmbed, size = 76.dp, iconSize = 40.dp, onLongClick = onLongPress, skinId = "player.play")
                        Spacer(Modifier.height(12.dp))
                        Text("点按播放 · 上下滑动切换", color = Color.White.copy(0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedTopChrome(
    visible: Boolean,
    index: Int,
    total: Int,
    scopeLabel: String? = null,
    haze: HazeState? = null,
    onCounterClick: () -> Unit = {},
    onCounterLongPress: (Rect) -> Unit = {},
) {
    val anchor = rememberAnchor()
    val skin = com.moonlight.feed.ui.design.rememberButtonSkin("player.counter")
    CompositionLocalProvider(LocalMoonColors provides MediaGlass, LocalHazeState provides haze) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(0.75f, 400f)) { -it / 2 },
            exit = fadeOut(spring(stiffness = 600f)) + slideOutVertically(spring(stiffness = 600f)) { -it / 2 },
        ) {
            // no brand pill; the counter is plain shadowed text (no fill) on a ≥48dp touch target
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .anchor(anchor)
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .moonPress(onCounterClick, { onCounterLongPress(anchor.rect) }, highlightShape = CircleShape, highlight = Color.White.copy(0.12f))
                        .then(if (skin != null) Modifier.padding(vertical = 6.dp).skinFill(skin, CircleShape).padding(horizontal = 4.dp) else Modifier)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (scopeLabel != null) "$scopeLabel · $index / $total" else "$index / $total",
                        color = skin?.ink ?: Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(shadow = if (skin != null) null else com.moonlight.feed.ui.design.labelShadow(true)),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun Modifier.pinchZoom(z: ZoomState): Modifier = pointerInput(z) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.fastAny { it.pressed }
            val count = event.changes.count { it.pressed }
            if (count >= 2 || z.zoomed) {
                val ns = (z.scale * event.calculateZoom()).coerceIn(1f, 4f)
                z.scale = ns
                z.offset = if (ns > 1f) z.offset + event.calculatePan() else Offset.Zero
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (pressed)
    }
}

// =================================================================== media pages

@Composable
private fun VideoPage(
    entry: MediaEntry,
    active: Boolean,
    muted: Boolean,
    loop: Boolean,
    display: com.moonlight.feed.data.VideoDisplay,
    override: Boolean?,
    sharedPlayer: ExoPlayer,
    userPaused: Boolean,
    showProgress: Boolean,
    onFill: (Boolean) -> Unit = {},
) {
    var vSize by remember(entry.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // 智能: never crop real content — fit unless the crop would be ≤8% (near-screen-ratio portrait)
    val fill = com.moonlight.feed.data.VideoFit.fill(display, vSize.width.toFloat(), vSize.height.toFloat(), box.width.toFloat(), box.height.toFloat(), override)
    SideEffect { onFill(fill) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    var retry by remember(entry.id) { mutableStateOf(0) }
    val file = entry.localPath?.let { File(it) }
    val probe = remember(entry.id, retry) { file?.takeIf { it.exists() }?.let { MediaProbe.sniff(it) } }

    DisposableEffect(entry.id, retry, active) {
        error = null
        if (!active) {
            onDispose { }
        } else {
            val uri = resolvePlayUri(entry)
            when {
                uri == null -> error = "无法解析视频地址"
                file != null && file.exists() && file.length() == 0L -> error = "文件为 0 字节（下载不完整）"
                probe != null && probe.kind == MediaProbe.Kind.HTML -> error = "文件实际是网页，不是视频"
                else -> {
                    sharedPlayer.stop()
                    sharedPlayer.clearMediaItems()
                    sharedPlayer.setMediaItem(MediaItem.fromUri(uri))
                    sharedPlayer.prepare()
                }
            }
            val listener = object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = "${e.errorCodeName}: ${e.message}" }
                override fun onVideoSizeChanged(v: androidx.media3.common.VideoSize) { vSize = displaySize(v) }
            }
            sharedPlayer.videoSize.takeIf { it.width > 0 }?.let { vSize = displaySize(it) }
            sharedPlayer.addListener(listener)
            onDispose {
                sharedPlayer.removeListener(listener)
                sharedPlayer.pause()
            }
        }
    }
    LaunchedEffect(active, muted, loop) {
        sharedPlayer.volume = if (muted) 0f else 1f
        sharedPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (active && !userPaused) { sharedPlayer.playWhenReady = true; sharedPlayer.play() }
        if (!active) sharedPlayer.pause()
    }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }) {
        if (entry.coverUrl != null && !active) {
            MoonImage(entry.coverUrl, Modifier.fillMaxSize(), if (fill) ContentScale.Crop else ContentScale.Fit)
        }
        if (active) {
            AndroidView(
                factory = { c ->
                    (LayoutInflater.from(c).inflate(R.layout.player_view_texture, null, false) as PlayerView).apply {
                        player = sharedPlayer
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT) // let the 首页 background show in the letterbox
                        resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    it.player = sharedPlayer
                    it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
            )
        }
        if (showProgress && active) VideoProgressLine(sharedPlayer)
        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp).glass(RoundedCornerShape(24.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MoonPalette.Moon, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                Spacer(Modifier.height(14.dp))
                GlassPill("重试", { retry++; error = null }, icon = Icons.Outlined.Refresh, accent = true)
            }
        }
    }
}

@Composable
private fun VideoProgressLine(player: ExoPlayer) {
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            frac = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(200)
        }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 2.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(MoonPalette.Moon.copy(alpha = 0.8f)))
        }
    }
}

@Composable
private fun ImagePage(entry: MediaEntry, active: Boolean, imageSeconds: Int, zoom: ZoomState, onDone: () -> Unit) {
    LaunchedEffect(active, imageSeconds, entry.id, zoom.zoomed) {
        // 永久 (0): no timer at all — the image stays until the user swipes.
        if (!active || zoom.zoomed || !com.moonlight.feed.data.ImageDwell.autoAdvance(imageSeconds)) return@LaunchedEffect
        delay(imageSeconds * 1000L)
        onDone()
    }
    MoonImage(
        model = entry.localPath ?: entry.remoteUrl,
        contentDescription = entry.title,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.scale; scaleY = zoom.scale
                translationX = zoom.offset.x; translationY = zoom.offset.y
            },
    )
}

@Composable
fun EmbedCover(entry: MediaEntry) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10))) {
        if (!entry.coverUrl.isNullOrBlank()) {
            MoonImage(entry.coverUrl, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.28f)))
    }
}

// =================================================================== info sheet

/** Swipe-left / 信息: header (thumb + title), derived tags, then the details rows. */
@Composable
fun MediaInfoCard(entry: MediaEntry, player: ExoPlayer?) {
    val c = LocalMoonColors.current
    val file = entry.localPath?.let { File(it) }?.takeIf { it.exists() }
    val fmt = player?.videoFormat
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            val art = (entry.coverUrl ?: entry.localPath)?.takeIf { it.startsWith("art:") }
            if (art != null) com.moonlight.feed.ui.design.ProceduralArt(art.removePrefix("art:").toIntOrNull() ?: 0, Modifier.fillMaxSize())
            else MoonImage(entry.coverUrl ?: entry.localPath ?: entry.remoteUrl, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title.ifBlank { "未命名" }, color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(entry.platformLabel() + (if (entry.author.isNotBlank()) " · @${entry.author}" else ""), color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.moonlight.feed.data.MediaInfo.tags(entry).forEach { t ->
            Text(t, color = c.text, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(CircleShape).background(c.hairline).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    val lines = buildList {
        add("来源" to entry.platformLabel())
        add("加入" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(java.util.Date(entry.createdAt)))
        file?.let { add("大小" to formatBytes(it.length())) }
        player?.let { p ->
            if (p.duration > 0) add("时长" to String.format(Locale.US, "%.1f 秒", p.duration / 1000.0))
            if (p.videoSize.width > 0) add("分辨率" to "${p.videoSize.width} × ${p.videoSize.height}")
            fmt?.sampleMimeType?.let { add("编码" to it) }
            add("倍速" to speedLabel(p.playbackParameters.speed))
        }
        add("位置" to (entry.localPath ?: entry.originalUrlOrDash()))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.hairline)) {
        lines.forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.6.dp).background(c.hairline))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(k, color = c.textDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                Text(v, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Counter-pill tap: scrub to any position (1…n), or 开头 / 随机 / 末尾. */
@Composable
fun JumpSheet(current: Int, total: Int, onJump: (Int) -> Unit, onRandom: () -> Unit) {
    val c = LocalMoonColors.current
    var v by remember { androidx.compose.runtime.mutableFloatStateOf(current.toFloat()) }
    Text("${v.toInt() + 1} / $total", color = c.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    if (total > 1) androidx.compose.material3.Slider(
        value = v, onValueChange = { v = it }, valueRange = 0f..(total - 1).toFloat(),
        onValueChangeFinished = { onJump(v.toInt()) },
        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.hairline),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassPill("开头", { onJump(0) }, modifier = Modifier.weight(1f))
        GlassPill("随机", onRandom, modifier = Modifier.weight(1f))
        GlassPill("末尾", { onJump(total - 1) }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

/** Left swipe (horizontal, ≥ 72dp, mostly horizontal) → [onSwipe]; vertical drags stay with the pager. */
private fun Modifier.swipeLeft(enabled: Boolean, onSwipe: () -> Unit): Modifier = if (!enabled) this else pointerInput(Unit) {
    val need = 72.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dx = 0f; var dy = 0f; var fired = false
        do {
            val e = awaitPointerEvent()
            if (e.changes.count { it.pressed } > 1) break
            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
            val d = ch.positionChange()
            dx += d.x; dy += d.y
            if (!fired && dx < -need && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.6f) { fired = true; onSwipe() }
        } while (e.changes.any { it.pressed })
    }
}

private fun MediaEntry.originalUrlOrDash() = originalUrl() ?: "-"

private fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", n / 1024.0)
    else -> String.format(Locale.US, "%.2f MB", n / (1024.0 * 1024.0))
}

private fun resolvePlayUri(entry: MediaEntry): Uri? {
    entry.localPath?.let { path ->
        val f = File(path)
        if (f.exists() && f.length() > 0) return Uri.fromFile(f)
        if (path.startsWith("content://") || path.startsWith("file://")) return Uri.parse(path)
    }
    entry.remoteUrl?.let { return Uri.parse(it) }
    return null
}


/** Display size of a decoded video (pixel aspect + rotation applied). */
internal fun displaySize(v: androidx.media3.common.VideoSize): androidx.compose.ui.unit.IntSize {
    var w = (v.width * (if (v.pixelWidthHeightRatio > 0f) v.pixelWidthHeightRatio else 1f)).toInt()
    var h = v.height
    @Suppress("DEPRECATION")
    if (v.unappliedRotationDegrees == 90 || v.unappliedRotationDegrees == 270) { val s = w; w = h; h = s }
    return androidx.compose.ui.unit.IntSize(w, h)
}

/** Two-finger pinch → onPinch(true) when spread (铺满), onPinch(false) when pinched (完整显示). Single-finger untouched. */
private fun Modifier.pinchToggle(onPinch: (Boolean) -> Unit): Modifier = pointerInput(onPinch) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var total = 1f
        var fired = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                total *= event.calculateZoom()
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                if (!fired && (total > 1.15f || total < 0.87f)) { fired = true; onPinch(total > 1f) }
            }
        } while (event.changes.fastAny { it.pressed })
    }
}
