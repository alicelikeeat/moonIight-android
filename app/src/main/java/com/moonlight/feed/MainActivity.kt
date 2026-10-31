package com.moonlight.feed

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Camera
import com.moonlight.feed.ui.design.MoonToaster
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import com.moonlight.feed.ui.design.ring
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.ui.AppBus
import com.moonlight.feed.ui.AppEvent
import com.moonlight.feed.ui.design.GlassTabBar
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonBackground
import com.moonlight.feed.ui.design.MoonOverlayHost
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.discover.DiscoverScreen
import com.moonlight.feed.ui.feed.FeedScreen
import com.moonlight.feed.ui.library.LibraryScreen
import com.moonlight.feed.ui.settings.SettingsScreen
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** v4.4: social.json writes are debounced on IO; leaving the app writes whatever is still pending. */
    override fun onStop() {
        super.onStop()
        val app = MoonApp.instance
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { app.social.flush() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.moonlight.feed.data.record.ScreenRecorder.attach(this)
        if (Build.VERSION.SDK_INT <= 28) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                1001,
            )
        }
        setContent {
            val app = MoonApp.instance
            val settings by app.settings.flow.collectAsState(initial = SettingsRepository.Snapshot())
            val mode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()
            val revealCfg = remember(settings.revealOn, settings.revealSpeed) { com.moonlight.feed.ui.design.revealConfig(this@MainActivity, settings.revealOn, settings.revealSpeed) }
            MoonTheme(mode) {
              CompositionLocalProvider(com.moonlight.feed.ui.design.LocalReveal provides revealCfg) {
                val overlay = remember { MoonOverlayState() }
                var tabState by remember { mutableIntStateOf(0) }
                var chrome by remember { mutableStateOf(true) }
                val social by app.social.state.collectAsState()
                // 消息 (5) is the right page of 朋友圈; 库 (1) / 设置 (3) are pages under 我 (6)
                fun select(i: Int) {
                    if (i == 5) com.moonlight.feed.ui.social.SocialNav.page = 1
                    tabState = if (i == 5) 4 else i
                    if (i == 0) chrome = true
                }
                val tab = tabState
                val bgPrefs = settings.homeBgPrefs()
                val homeBg = remember(bgPrefs) { bgPrefs.spec { java.io.File(it).exists() } }
                val loopVideos = remember(settings.loopVideos) {
                    val am = getSystemService(android.app.ActivityManager::class.java)
                    val pm = getSystemService(android.os.PowerManager::class.java)
                    settings.loopVideos && am?.isLowRamDevice != true && pm?.isPowerSaveMode != true
                }
                // sane back: 库/设置 → 我, sub-tabs → 首页; 朋友圈 stacks / 消息 page handle their own back first
                val socialBusy = tab == 4 && (!com.moonlight.feed.ui.social.SocialNav.atRoot || com.moonlight.feed.ui.social.SocialNav.page == 1)
                androidx.activity.compose.BackHandler(enabled = tab != 0 && !socialBusy) { com.moonlight.feed.ui.design.tabBack(tab)?.let { select(it)} }
                val dark = LocalMoonColors.current.dark
                // capture → camera closes → 首页 with the new item first (CameraActivity already queued AppBus.focusFeed)
                val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                    // "moment" captures route themselves to 朋友圈 via AppBus.composeMoment
                    if (r.resultCode == RESULT_OK && r.data?.getStringExtra(CameraActivity.EXTRA_FOR) != "moment") { select(0); chrome = true }
                }
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isEmpty()) return@rememberLauncherForActivityResult
                    uris.forEach { u -> runCatching { contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                    scope.launch {
                        val added = app.library.importUris(uris)
                        if (added.isEmpty()) { MoonToaster.show("没有可导入的图片或视频", Icons.Outlined.PhotoLibrary); return@launch }
                        AppBus.focusFeed(added.map { it.id })
                        MoonToaster.show("已导入 ${added.size} 项到库", Icons.Outlined.CheckCircle)
                        added.forEach { e -> runCatching { com.moonlight.feed.data.SaveHelper.maybeSaveEntry(e) } }
                    }
                }
                LaunchedEffect(Unit) { AppBus.goTab.collect { select(it) } }
                LaunchedEffect(Unit) {
                    AppBus.importCount.collect { n ->
                        val added = runCatching { com.moonlight.feed.data.ContentImport.fetch(app, n) }.getOrDefault(emptyList())
                        added.forEach { e -> app.library.upsert(e); com.moonlight.feed.data.SaveHelper.maybeSaveEntry(e) }
                        if (added.isNotEmpty()) AppBus.focusFeed(added.map { it.id })
                        MoonToaster.show(if (added.isEmpty()) "没有拿到新内容" else "已随机添加 ${added.size} 条", Icons.Outlined.Shuffle)
                    }
                }
                // v5.0 录屏 results (saved → 库, declined, failed)
                LaunchedEffect(Unit) { com.moonlight.feed.data.record.ScreenRecorder.state.collect { st -> recResultToast(st) { select(1) } } }
                // v5.0 全部按键: this tab's ring + app-wide actions
                overlay.pageActions = {
                    com.moonlight.feed.ui.design.panelActions(tabMenu(tab, Rect.Zero, mode, tab == 0, go = { select(it) },
                        setMode = { m -> scope.launch { app.settings.setThemeMode(m.name) } }, unread = social.totalUnread,
                        home = HomeTabState(com.moonlight.feed.data.FeedScope.of(settings.feedScope), settings.muted, settings.shuffle,
                            toggleShuffle = { scope.launch { app.settings.setShuffle(!settings.shuffle) } },
                            toggleMute = { scope.launch { app.settings.setMuted(!settings.muted) } })))
                }
                overlay.globals = { globalActions(this@MainActivity, mode, go = { select(it) },
                    setMode = { m -> scope.launch { app.settings.setThemeMode(m.name) } },
                    capture = { v -> cameraLauncher.launch(CameraActivity.intent(this@MainActivity, video = v)) }) }
                val view = LocalView.current
                LaunchedEffect(chrome, tab, dark, com.moonlight.feed.ui.social.SocialNav.page, com.moonlight.feed.ui.social.SocialNav.moments.size) {
                    val window = this@MainActivity.window
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    val darkHeader = tab == 6 || (tab == 4 && com.moonlight.feed.ui.social.SocialNav.page == 0 && com.moonlight.feed.ui.social.SocialNav.moments.size == 1)
                    controller.isAppearanceLightStatusBars = tab != 0 && !darkHeader && !dark
                    controller.isAppearanceLightNavigationBars = tab != 0 && !dark
                    if (tab == 0 && !chrome) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else controller.show(WindowInsetsCompat.Type.systemBars())
                }
                CompositionLocalProvider(com.moonlight.feed.ui.design.LocalHomeBg provides homeBg, com.moonlight.feed.ui.design.LocalLoopVideos provides loopVideos) {
                MoonShell(
                    overlay = overlay,
                    tab = tab,
                    badges = mapOf(4 to social.totalUnread),
                    tabBarVisible = (chrome || tab != 0) && !(tab == 4 && com.moonlight.feed.ui.social.SocialNav.immersive),
                    onTab = { i ->
                        if (i == tab && i == 0) AppBus.send(AppEvent.FEED_TOP)
                        if (i == 4 && tab == 4) com.moonlight.feed.ui.social.SocialNav.page = 0
                        select(i); if (i == 0) chrome = true
                    },
                    onCapture = { cameraLauncher.launch(CameraActivity.intent(this@MainActivity, video = false)) },
                    onCaptureLong = { rect ->
                        Haptics.open(view)
                        overlay.showMenu(captureMenu(rect, tab == 0,
                            photo = { cameraLauncher.launch(CameraActivity.intent(this@MainActivity, video = false)) },
                            video = { cameraLauncher.launch(CameraActivity.intent(this@MainActivity, video = true)) },
                            import = { importLauncher.launch(arrayOf("image/*", "video/*")) },
                            moment = { cameraLauncher.launch(CameraActivity.intent(this@MainActivity, video = false, purpose = "moment")) },
                        ))
                    },
                    onTabLongPress = { i, rect ->
                        Haptics.open(view)
                        overlay.showMenu(withRecord(this@MainActivity,
                            tabMenu(i, rect, mode, tab == 0,
                                go = { select(it); if (it == 0) chrome = true },
                                setMode = { m -> scope.launch { app.settings.setThemeMode(m.name) } },
                                unread = social.totalUnread,
                                home = HomeTabState(
                                    com.moonlight.feed.data.FeedScope.of(settings.feedScope), settings.muted, settings.shuffle,
                                    toggleShuffle = { scope.launch { app.settings.setShuffle(!settings.shuffle) } },
                                    toggleMute = { scope.launch { app.settings.setMuted(!settings.muted) } },
                                ),
                            ))
                        )
                    },
                ) { t ->
                    when (t) {
                        0 -> FeedScreen(chromeVisible = chrome, onToggleChrome = { chrome = !chrome }, onGoDiscover = { select(2)})
                        1 -> LibraryScreen()
                        2 -> DiscoverScreen()
                        4, 5 -> com.moonlight.feed.ui.social.SocialTab()
                        6 -> com.moonlight.feed.ui.me.MeScreen(go = { select(it) }, onTheme = { r ->
                            overlay.showMenu(themeMenu(r, mode) { m -> scope.launch { app.settings.setThemeMode(m.name) } })
                        })
                        else -> SettingsScreen()
                    }
                }
                }
            }
              }
        }
    }
}

/** v5.0: 录屏 as a toggle action (start / stop) with the 录屏 settings. */
fun recordAction(ctx: android.content.Context): MenuAction {
    val on = com.moonlight.feed.data.record.ScreenRecorder.recording()
    val p = com.moonlight.feed.data.V5.prefs
    return MenuAction(if (on) "停止录屏" else "录屏", if (on) Icons.Outlined.StopCircle else Icons.Outlined.FiberManualRecord,
        subtitle = if (on) null else listOfNotNull("画面", if (p.recordAppAudio) "应用声音" else null, if (p.recordMic) "麦克风" else null).joinToString(" + "),
        short = "录屏") {
        com.moonlight.feed.data.record.ScreenRecorder.toggle(ctx, com.moonlight.feed.data.record.RecordOptions(p.recordMic, p.recordAppAudio, p.recordToGallery))
    }
}

/** Adds 录屏 to a ring's ··· sheet (the ring itself keeps its ≤ 4 buttons). */
fun withRecord(ctx: android.content.Context, spec: MenuSpec): MenuSpec =
    spec.copy(radial = spec.radial ?: spec.ring(), actions = spec.actions + listOf(listOf(recordAction(ctx))),
        groupTitles = spec.groupTitles.let { g -> List(spec.actions.size) { g.getOrNull(it) } + "工具" })

/** 全部按键 › 全局. */
fun globalActions(ctx: android.content.Context, mode: ThemeMode, go: (Int) -> Unit, setMode: (ThemeMode) -> Unit, capture: (Boolean) -> Unit): List<MenuAction> = listOf(
    recordAction(ctx),
    MenuAction("设置", Icons.Outlined.Settings) { go(3) },
    MenuAction("外观", Icons.Outlined.DarkMode, subtitle = when (mode) { ThemeMode.DARK -> "深色"; ThemeMode.LIGHT -> "浅色"; else -> "跟随系统" }) {
        setMode(when (mode) { ThemeMode.DARK -> ThemeMode.LIGHT; ThemeMode.LIGHT -> ThemeMode.SYSTEM; else -> ThemeMode.DARK })
    },
    MenuAction("拍照", Icons.Outlined.PhotoCamera) { capture(false) },
    MenuAction("录像", Icons.Outlined.Videocam) { capture(true) },
    MenuAction("首页", Icons.Outlined.Home) { go(0) },
    MenuAction("随机一条", Icons.Outlined.Casino) { go(0); AppBus.send(AppEvent.FEED_RANDOM) },
    MenuAction("筛选", Icons.Outlined.FilterList, children = com.moonlight.feed.data.FeedScope.entries.map { s ->
        MenuAction(s.label) { go(0); AppBus.feedScopeRequest.value = s } }),
    MenuAction("库", Icons.Outlined.PhotoLibrary) { go(1) },
    MenuAction("最近删除", Icons.Outlined.DeleteSweep) { go(1); AppBus.send(AppEvent.LIB_TRASH) },
    MenuAction("发现", Icons.Outlined.Explore) { go(2) },
    MenuAction("朋友圈", Icons.Outlined.People) { go(4) },
    MenuAction("我", Icons.Outlined.Person) { go(6) },
)

/** Toast for a finished recording; then the machine rests. */
fun recResultToast(st: com.moonlight.feed.data.record.RecState, openLibrary: () -> Unit) {
    val m = com.moonlight.feed.data.record.ScreenRecorder.machine
    when (st) {
        is com.moonlight.feed.data.record.RecState.Saved -> { com.moonlight.feed.ui.design.MoonToaster.show("录屏已保存到 库 · ${com.moonlight.feed.data.record.RecordingMachine.timer(st.durationMs)}", Icons.Outlined.CheckCircle, action = "查看", onAction = openLibrary); m.reset() }
        is com.moonlight.feed.data.record.RecState.Denied -> { com.moonlight.feed.ui.design.MoonToaster.show(st.reason, Icons.Outlined.FiberManualRecord); m.reset() }
        is com.moonlight.feed.data.record.RecState.Failed -> { com.moonlight.feed.ui.design.MoonToaster.show("录屏失败：${st.message}", Icons.Outlined.ErrorOutline); m.reset() }
        else -> Unit
    }
}

/** Tab long-press quick actions. */
fun tabMenu(
    index: Int,
    anchor: Rect,
    mode: ThemeMode,
    onFeed: Boolean,
    go: (Int) -> Unit,
    setMode: (ThemeMode) -> Unit,
    home: HomeTabState = HomeTabState(),
    unread: Int = 0,
): MenuSpec {
    if (index == 0) {
        // 首页 tab ring = the shared home ring (always has 筛选), centred above the tab bar
        return com.moonlight.feed.ui.feed.homeRing(
            anchor, home.scope, home.muted, home.shuffle,
            com.moonlight.feed.ui.feed.HomeRingHandlers(
                top = { go(0); AppBus.send(AppEvent.FEED_TOP) },
                random = { go(0); AppBus.send(AppEvent.FEED_RANDOM) },
                reshuffle = { go(0); AppBus.send(AppEvent.FEED_REFRESH) },
                scope = { s -> go(0); AppBus.feedScopeRequest.value = s },
                toggleShuffle = home.toggleShuffle,
                toggleMute = home.toggleMute,
                addRandom = { n -> AppBus.requestImport(n) },
            ),
            origin = com.moonlight.feed.ui.feed.liftedAbove(anchor),
            overMedia = onFeed,   // v4.3: long-pressing 首页 from 我 / 朋友圈 etc. follows the theme
        ).copy(centerHorizontally = true)
    }
    val nav = com.moonlight.feed.ui.social.SocialNav
    fun social(r: com.moonlight.feed.ui.social.SRoute?, page: Int = 0) {
        nav.page = page
        if (r != null) { while (nav.moments.size > 1) nav.moments.removeAt(nav.moments.lastIndex); nav.moments.add(r) }
        go(4)
    }
    val actions = when (index) {
        1 -> listOf(
            MenuAction("选择", Icons.Outlined.CheckCircle) { go(1); AppBus.send(AppEvent.LIB_SELECT) },
            MenuAction("只看收藏", Icons.Outlined.Favorite) { go(1); AppBus.send(AppEvent.LIB_FAVORITES) },
        )
        2 -> listOf(
            MenuAction("切换单双列", Icons.Outlined.GridView) { go(2); AppBus.send(AppEvent.DISCOVER_LAYOUT) },
            MenuAction("随机一下", Icons.Outlined.Casino) { go(2); AppBus.send(AppEvent.DISCOVER_RANDOM) },
            MenuAction("粘贴链接", Icons.Outlined.ContentPaste) { go(2); AppBus.send(AppEvent.DISCOVER_PASTE) },
            MenuAction("库", Icons.Outlined.PhotoLibrary) { go(1) },
        )
        4, 5 -> listOf(
            MenuAction("发动态", Icons.Outlined.PhotoCamera) { nav.page = 0; go(4); com.moonlight.feed.ui.AppBus.composeMoment(emptyList()) },
            MenuAction("消息", Icons.Outlined.ChatBubbleOutline, subtitle = if (unread > 0) "$unread 未读" else null) { social(null, 1) },
            MenuAction("新对话", Icons.Outlined.AddComment) { nav.page = 1; go(4); AppBus.send(AppEvent.CHATS_NEW) },
            MenuAction("好友", Icons.Outlined.People) { social(com.moonlight.feed.ui.social.SRoute.Friends) },
        )
        6, 3 -> listOf(
            MenuAction("库", Icons.Outlined.PhotoLibrary) { go(1) },
            MenuAction("设置", Icons.Outlined.Settings) { go(3) },
            MenuAction("朋友", Icons.Outlined.People) { social(com.moonlight.feed.ui.social.SRoute.Friends) },
            MenuAction("外观", Icons.Outlined.DarkMode, subtitle = when (mode) { ThemeMode.DARK -> "深色"; ThemeMode.LIGHT -> "浅色"; else -> "跟随系统" }, children = themeChoices(mode, setMode)),
        )
        else -> themeChoices(mode, setMode)
    }
    return MenuSpec(anchor = anchor, actions = listOf(actions), forceDark = onFeed, origin = com.moonlight.feed.ui.feed.liftedAbove(anchor), centerHorizontally = true,
        context = com.moonlight.feed.data.HoldContext.TAB)
}

fun themeChoices(mode: ThemeMode, setMode: (ThemeMode) -> Unit) = listOf(
    MenuAction("深色", Icons.Outlined.DarkMode, checked = mode == ThemeMode.DARK) { setMode(ThemeMode.DARK) },
    MenuAction("浅色", Icons.Outlined.LightMode, checked = mode == ThemeMode.LIGHT) { setMode(ThemeMode.LIGHT) },
    MenuAction("跟随系统", Icons.Outlined.SettingsBrightness, checked = mode == ThemeMode.SYSTEM) { setMode(ThemeMode.SYSTEM) },
)

fun themeMenu(anchor: Rect, mode: ThemeMode, setMode: (ThemeMode) -> Unit) = MenuSpec(anchor = anchor, title = "外观", list = true, actions = listOf(themeChoices(mode, setMode)))

class HomeTabState(
    val scope: com.moonlight.feed.data.FeedScope = com.moonlight.feed.data.FeedScope.ALL,
    val muted: Boolean = false,
    val shuffle: Boolean = false,
    val toggleShuffle: () -> Unit = {},
    val toggleMute: () -> Unit = {},
)

/** Long-press on the centre capture button. */
fun captureMenu(anchor: Rect, onFeed: Boolean, photo: () -> Unit, video: () -> Unit, import: () -> Unit, moment: () -> Unit = {}) = MenuSpec(
    anchor = anchor,
    title = "拍摄",
    radial = listOf(
        MenuAction("拍照", Icons.Outlined.PhotoCamera, onClick = photo),
        MenuAction("录像", Icons.Outlined.Videocam, onClick = video),
        MenuAction("导入", Icons.Outlined.PhotoLibrary, subtitle = "图片与视频，直接加入库", onClick = import),
        MenuAction("发朋友圈", Icons.Outlined.Camera, subtitle = "拍一张直接发", onClick = moment),
    ),
    actions = emptyList(),
    forceDark = onFeed,
)

/** App frame: overlay host + per-tab content + floating glass tab bar. Shared with screenshot tests. */
@Composable
fun MoonShell(
    overlay: MoonOverlayState,
    tab: Int,
    tabBarVisible: Boolean,
    badges: Map<Int, Int> = emptyMap(),
    onTab: (Int) -> Unit = {},
    onTabLongPress: (Int, Rect) -> Unit = { _, _ -> },
    onCapture: () -> Unit = {},
    onCaptureLong: (Rect) -> Unit = {},
    content: @Composable (Int) -> Unit,
) {
    MoonOverlayHost(
        overlay,
        chrome = {
            Box(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(
                    visible = tabBarVisible && (overlay.liftKey == null),
                    enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(0.75f, 400f)) { it },
                    exit = fadeOut(spring(stiffness = 600f)) + slideOutVertically(spring(stiffness = 600f)) { it },
                ) {
                    val colors = if (tab == 0) MediaGlass else LocalMoonColors.current
                    CompositionLocalProvider(LocalMoonColors provides colors) {
                        GlassTabBar(tab, onTab, onTabLongPress, onCapture = onCapture, onCaptureLong = onCaptureLong, badges = badges)
                    }
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(spring(stiffness = 500f)) + scaleIn(spring(0.8f, 500f), initialScale = 0.98f)) togetherWith fadeOut(spring(stiffness = 900f))
            },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { t ->
            val bg = com.moonlight.feed.ui.design.LocalHomeBg.current
            if (t == 0 || bg.appliesToOtherPages()) {
                // other pages on a photo/video/black backdrop switch to light-on-dark glass so text stays readable
                com.moonlight.feed.ui.design.HomeBackdrop(bg) {
                    if (t == 0 || bg.kind == com.moonlight.feed.data.HomeBgKind.THEME) content(t)
                    else CompositionLocalProvider(LocalMoonColors provides MediaGlass) { content(t) }
                }
            } else MoonBackground { content(t) }
        }
    }
    com.moonlight.feed.ui.design.FocusEditorHost()
}
