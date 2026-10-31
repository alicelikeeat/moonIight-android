package com.moonlight.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import dev.chrisbanes.haze.haze
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.ProviderStatus
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.data.UserSource
import com.moonlight.feed.data.web.FetchMode
import com.moonlight.feed.ui.camera.CamMode
import com.moonlight.feed.ui.camera.CameraCallbacks
import com.moonlight.feed.ui.camera.CameraContent
import com.moonlight.feed.ui.camera.CameraPermissionExplainer
import com.moonlight.feed.ui.camera.CameraUiState
import com.moonlight.feed.ui.camera.PermState
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.RevealMedia
import com.moonlight.feed.ui.design.SheetSpec
import com.moonlight.feed.ui.discover.DiscoverContent
import com.moonlight.feed.ui.discover.SearchHandlers
import com.moonlight.feed.ui.discover.SearchUiState
import com.moonlight.feed.ui.library.LibraryContent
import com.moonlight.feed.ui.library.LibraryHandlers
import com.moonlight.feed.ui.library.libraryMenuSpec
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.settings.SettingsContent
import com.moonlight.feed.ui.sources.SourceDraft
import com.moonlight.feed.ui.sources.SourceEditor
import com.moonlight.feed.ui.sources.SourcesContent
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass

class V28Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) { MoonTheme(mode) { content() } }
    }

    @Composable
    private fun Feed(entry: MediaEntry, overlay: MoonOverlayState, chrome: Boolean = true) {
        MoonShell(overlay, tab = 0, tabBarVisible = chrome) {
            val fh = remember { dev.chrisbanes.haze.HazeState() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().haze(fh)) {
                    FeedItem(entry, chromeVisible = chrome, muted = false, playing = true, showPlayButton = chrome && entry.kind == MediaKind.VIDEO) {
                        MoonImage(entry.coverUrl, Modifier.fillMaxSize())
                    }
                }
                FeedTopChrome(visible = chrome && overlay.liftKey == null, index = 3, total = 24, haze = fh)
            }
        }
    }

    // PIXEL_6: 1080 x 2400 px
    @Test fun radial() = shot("radial-menu-dark") {
        val e = sample()[0]
        Feed(e, remember { MoonOverlayState(initialMenu = feedMenuSpec(e, false, true, 1f, true, true, FeedMenuHandlers()).copy(origin = Offset(560f, 1250f))) }, chrome = false)
    }
    @Test fun radialEdge() = shot("radial-menu-edge-dark") {
        val e = sample()[1]
        Feed(e, remember { MoonOverlayState(initialMenu = feedMenuSpec(e, false, true, 1f, true, false, FeedMenuHandlers(), imageSeconds = 0).copy(origin = Offset(1010f, 2150f))) }, chrome = false)
    }

    // ------------------------------------------------------------------ shared scenes
    @Composable
    private fun Library(overlay: MoonOverlayState) {
        MoonShell(overlay, tab = 1, tabBarVisible = true) {
            LibraryContent(items = sample(15), filter = "全部", onFilter = {}, selectionMode = false, selectedIds = emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> })
        }
    }

    @Composable
    private fun Discover(overlay: MoonOverlayState, search: SearchUiState? = null, sh: SearchHandlers = SearchHandlers()) {
        MoonShell(overlay, tab = 2, tabBarVisible = true) {
            DiscoverContent(
                tab = "热门", onTab = {}, randomOn = false, onToggleRandom = {},
                cards = sample(6).map { it.copy(id = "d${it.id}", kind = MediaKind.EMBED) },
                addedIds = setOf("ds1"), loading = false, status = "共 6 条 · 长按卡片查看更多",
                search = search, sh = sh,
            )
        }
    }

    @Composable
    private fun Settings(overlay: MoonOverlayState, mode: String = "DARK", s: SettingsRepository.Snapshot = SettingsRepository.Snapshot(themeMode = mode, youtubeKey = "x")) {
        MoonShell(overlay, tab = 3, tabBarVisible = true) {
            SettingsContent(s, SettingsActions(), hiddenCount = 2, userSources = demoSources())
        }
    }

    private val now = 1_760_000_000_000L
    private fun demoSources() = listOf(
        UserSource(id = "u1", name = "Night Photo Blog", url = "https://nightphoto.example.org/blog/", mode = FetchMode.RANDOM, depth = 2, sample = 20,
            lastFetchAt = now - 12 * 60_000, lastCount = 18, totalCount = 64),
        UserSource(id = "u2", name = "", url = "https://www.open-gallery.example.com/latest", mode = FetchMode.LATEST, sample = 30,
            lastFetchAt = now - 3 * 3_600_000, lastCount = 0, totalCount = 22, autoRefreshHours = 12),
        UserSource(id = "u3", name = "城市漫步", url = "https://citywalk.example.net/", mode = FetchMode.RANDOM, fetching = true, lastFetchAt = now - 86_400_000, totalCount = 9),
        UserSource(id = "u4", name = "Protected Site", url = "https://members.example.io/", enabled = false,
            lastFetchAt = now - 2 * 86_400_000, lastError = "网站要求人机验证（如 Cloudflare），已停止 — 月光不会绕过验证"),
    )

    @Composable
    private fun Sources(overlay: MoonOverlayState, list: List<UserSource> = demoSources(), clip: String? = "https://photos.example.org/gallery/night") {
        MoonShell(overlay, tab = 3, tabBarVisible = true) {
            SourcesContent(list, thumbs = mapOf("u1" to "art:2", "u2" to "art:4"), clipboard = clip, now = now)
        }
    }

    // ------------------------------------------------------------------ v2.8
    @Test fun tabbar() = shot("tabbar-dark") { Library(remember { MoonOverlayState() }) }
    @Test fun tabbarFeed() = shot("tabbar-feed-dark") { Feed(sample()[3], remember { MoonOverlayState() }) }
    @Test fun captureMenu() = shot("capture-menu-dark") {
        Library(remember { MoonOverlayState(initialMenu = captureMenu(Rect(480f, 2150f, 600f, 2270f), false, {}, {}, {}).copy(origin = Offset(540f, 2210f))) })
    }

    @Test fun camera() = shot("camera-dark") {
        CameraContent(CameraUiState(lastThumb = "art:5", zoom = 1f), CameraCallbacks()) { ProceduralArt(4, Modifier.fillMaxSize()) }
    }
    @Test fun cameraRecording() = shot("camera-recording-dark") {
        CameraContent(CameraUiState(mode = CamMode.VIDEO, recording = true, recordMs = 83_000, zoom = 2f, lastThumb = "art:5"), CameraCallbacks()) { ProceduralArt(2, Modifier.fillMaxSize()) }
    }
    @Test fun cameraPermission() = shot("camera-permission-dark") {
        CameraPermissionExplainer(PermState.ASK, camera = false, mic = false, onContinue = {}, onSettings = {}, onCancel = {}) { ProceduralArt(3, Modifier.fillMaxSize()) }
    }
    @Test fun cameraBlocked() = shot("camera-permission-blocked-dark") {
        CameraPermissionExplainer(PermState.BLOCKED, camera = false, mic = true, onContinue = {}, onSettings = {}, onCancel = {}) { ProceduralArt(3, Modifier.fillMaxSize()) }
    }

    @Test fun sources() = shot("sources-dark") { Sources(remember { MoonOverlayState() }) }
    @Test fun sourcesLight() = shot("sources-light", ThemeMode.LIGHT) { Sources(remember { MoonOverlayState() }) }
    @Test fun sourcesEmpty() = shot("sources-empty-dark") { Sources(remember { MoonOverlayState() }, emptyList(), null) }
    @Test fun addUrl() = shot("add-url-dark") {
        Sources(remember {
            MoonOverlayState(initialSheet = SheetSpec("添加网址") { _ ->
                SourceEditor(SourceDraft(url = "https://photos.example.org/gallery/night", depth = 2), editing = false, clipboard = null) {}
            })
        })
    }
    @Test fun sourceMenu() = shot("source-menu-dark") {
        val s = demoSources()[0]
        Sources(remember { MoonOverlayState(initialMenu = MenuSpec(
            anchor = Rect(48f, 760f, 1032f, 1000f), origin = Offset(600f, 880f), title = s.name,
            actions = listOf(listOf(
                MenuAction("立即抓取", androidx.compose.material.icons.Icons.Outlined.Refresh),
                MenuAction("随机抓取", androidx.compose.material.icons.Icons.Outlined.Casino),
                MenuAction("编辑", androidx.compose.material.icons.Icons.Outlined.Edit),
                MenuAction("暂停", androidx.compose.material.icons.Icons.Outlined.PauseCircle),
            ), listOf(MenuAction("删除", androidx.compose.material.icons.Icons.Outlined.Delete, destructive = true))),
        )) })
    }

    private fun results() = listOf(
        "Aurora over Lofoten — real time" to "dailymotion", "Night sky timelapse (1965)" to "archive", "ISS flyover of Earth at night" to "nasa",
        "Stars over the desert · 4K" to "peertube", "Milky Way rising, Atacama" to "commons", "City lights from above" to "dailymotion",
        "Moonrise over the ocean" to "archive", "Meteor shower compilation" to "peertube", "Night train through snow" to "dailymotion", "Lunar eclipse 2025" to "nasa",
    ).mapIndexed { i, (t, p) -> MediaEntry(id = "r$i", kind = MediaKind.EMBED, title = t, author = listOf("Northern", "Prelinger", "NASA JSC", "framasoft", "Wikimedia")[i % 5], platform = p, coverUrl = "art:$i") }

    private fun searchState() = SearchUiState(
        query = "night sky", batch = results(), total = 87, count = 10,
        status = listOf(ProviderStatus("dailymotion", "Dailymotion", 40, false), ProviderStatus("archive", "Archive", 16, false),
            ProviderStatus("nasa", "NASA", 8, false), ProviderStatus("peertube", "PeerTube", 15, false), ProviderStatus("commons", "Commons", 8, false),
            ProviderStatus("pexels", "Pexels", 0, true)),
        note = "B站搜索需签名，已跳过",
    )
    @Test fun search() = shot("search-dark") { Discover(remember { MoonOverlayState() }, searchState(), SearchHandlers(text = "night sky")) }
    @Test fun searchLight() = shot("search-light", ThemeMode.LIGHT) { Discover(remember { MoonOverlayState() }, searchState(), SearchHandlers(text = "night sky")) }
    @Test fun searchLoading() = shot("search-loading-dark") { Discover(remember { MoonOverlayState() }, SearchUiState(query = "ocean waves", loading = true), SearchHandlers(text = "ocean waves")) }
    @Test fun searchRecent() = shot("search-recent-dark") {
        Discover(remember { MoonOverlayState() }, null, SearchHandlers(recent = listOf("night sky", "海浪", "aurora", "城市夜景", "rain"), forceRecent = true))
    }

    @Test fun radialLibrary() = shot("radial-library-dark") {
        Library(remember { MoonOverlayState(initialMenu = libraryMenuSpec(sample()[4], Rect(380f, 900f, 700f, 1330f), LibraryHandlers()).copy(origin = Offset(540f, 1110f))) })
    }
    @Test fun radialTab() = shot("radial-tab-dark") {
        Library(remember { MoonOverlayState(initialMenu = tabMenu(1, Rect(250f, 2160f, 420f, 2300f), ThemeMode.DARK, false, {}, {}).copy(origin = Offset(330f, 2220f))) })
    }
    @Test fun radialPlayer() = shot("radial-player-dark") {
        androidx.compose.runtime.CompositionLocalProvider(LocalMoonColors provides MediaGlass) {
            val o = remember { MoonOverlayState(initialMenu = com.moonlight.feed.ui.player.playerMediaMenu(Offset(300f, 1500f), sample()[6], muted = true, embed = true, a = com.moonlight.feed.ui.player.PlayerActions())) }
            com.moonlight.feed.ui.design.MoonOverlayHost(o) {
                MoonImage("art:6", Modifier.fillMaxSize())
                com.moonlight.feed.ui.player.PlayerTopBar("YouTube · 7/24", "沙漠星轨 2 小时", muted = true, onHome = {}, onHomeLong = {}, onMute = {}, onMore = {})
            }
        }
    }
    @Test fun moreList() = shot("radial-more-list-dark") {
        val e = sample()[0]
        val spec = feedMenuSpec(e, false, true, 1f, true, true, FeedMenuHandlers())
        Feed(e, remember { MoonOverlayState(initialMenu = spec.copy(radial = null, quick = emptyList(), list = true, liftKey = null, anchor = Rect(Offset(800f, 1500f), 1f))) }, chrome = false)
    }

    @Test fun dwellPicker() = shot("dwell-picker-dark") {
        Settings(remember { MoonOverlayState(initialMenu = MenuSpec(anchor = Rect(48f, 1250f, 1032f, 1400f), title = "图片停留", list = true,
            actions = listOf(com.moonlight.feed.data.ImageDwell.OPTIONS.map { v ->
                MenuAction(if (v == 0) "永久" else com.moonlight.feed.data.ImageDwell.label(v), checked = v == 0, subtitle = if (v == 0) "不自动切换，滑动才换下一张" else null)
            }))) }, s = SettingsRepository.Snapshot(themeMode = "DARK", imageSeconds = 0))
    }

    /** Four frames of the blur → sharp reveal, sampled at real times through the easing curve; each frame is a full phone render scaled down. */
    @Test fun revealFrames() = shot("reveal-frames") {
        val total = 520
        Column(Modifier.fillMaxSize().background(Color(0xFF07080B)).padding(horizontal = 10.dp, vertical = 40.dp), verticalArrangement = Arrangement.Center) {
            Text("模糊渐显 · 标准 520ms · ease-out", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            Text("模糊 32dp→0 · 缩放 1.04→1 · 不透明度 0.4→1 · 视频在下方同步播放", color = Color.White.copy(0.6f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, bottom = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 40, 100, 520).forEach { ms ->
                    val pr = com.moonlight.feed.ui.design.RevealEasing.transform(ms / total.toFloat())
                    Column(Modifier.weight(1f)) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().androidx_aspect().clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                            val s = maxWidth / 412.dp
                            Box(Modifier.requiredSizeCompat().graphicsLayerScale(s)) {
                                RevealMedia(key = ms, play = false, fallback = null, progress = pr, modifier = Modifier.fillMaxSize()) { ProceduralArt(0, Modifier.fillMaxSize()) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${ms}ms", color = Color.White.copy(0.75f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
    private fun Modifier.androidx_aspect() = this.aspectRatio(412f / 915f)
    private fun Modifier.requiredSizeCompat() = this.then(Modifier.wrapContentSize(Alignment.Center, unbounded = true).requiredSize(412.dp, 915.dp))
    private fun Modifier.graphicsLayerScale(s: Float) = this.then(Modifier.graphicsLayer { scaleX = s; scaleY = s })

    // refreshed main screens (v2.8 look)
    @Test fun feed() = shot("feed-dark") { Feed(sample()[0], remember { MoonOverlayState() }) }
    @Test fun library() = shot("library-dark") { Library(remember { MoonOverlayState() }) }
    @Test fun libraryLight() = shot("library-light", ThemeMode.LIGHT) { Library(remember { MoonOverlayState() }) }
    @Test fun discover() = shot("discover-dark") { Discover(remember { MoonOverlayState() }) }
    @Test fun settings() = shot("settings-dark") { Settings(remember { MoonOverlayState() }) }
    @Test fun settingsLight() = shot("settings-light", ThemeMode.LIGHT) { Settings(remember { MoonOverlayState() }, "LIGHT") }
}
