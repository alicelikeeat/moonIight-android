package com.moonlight.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.SheetSpec
import com.moonlight.feed.ui.design.ConfirmContent
import com.moonlight.feed.ui.discover.DiscoverContent
import com.moonlight.feed.ui.discover.DiscoverHandlers
import com.moonlight.feed.ui.discover.discoverMenuSpec
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.library.LibraryContent
import com.moonlight.feed.ui.library.LibraryHandlers
import com.moonlight.feed.ui.library.libraryMenuSpec
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.settings.SettingsContent
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import dev.chrisbanes.haze.haze

private fun Modifier.hazeSrc(s: dev.chrisbanes.haze.HazeState) = this.haze(s)

private val titles = listOf(
    "湖面上的月光，一次长曝光" to "夜行者", "山脊线与银河" to "Lumen", "海边的潮汐声 · 4K" to "北岛",
    "城市屋顶的蓝调时刻" to "青山", "雪夜里的灯塔" to "Aurora", "晚风与芦苇" to "小满",
    "沙漠星轨 2 小时" to "Dune", "山谷里的萤火虫" to "林间", "旧港口的夜" to "Harbor",
)
private val kinds = listOf(MediaKind.VIDEO, MediaKind.IMAGE, MediaKind.EMBED, MediaKind.BILI, MediaKind.VIDEO, MediaKind.IMAGE, MediaKind.EMBED, MediaKind.VIDEO, MediaKind.IMAGE)
private val plats = listOf("local", "commons", "youtube", "bili", "archive", "nasa", "vimeo", "peertube", "commons")

fun sample(n: Int = 12) = List(n) { i ->
    val (t, a) = titles[i % titles.size]
    MediaEntry(
        id = "s$i", kind = kinds[i % kinds.size], title = t, author = a, coverUrl = "art:$i",
        platform = plats[i % plats.size], source = plats[i % plats.size], favorite = i % 4 == 1,
    )
}

class V27Shots {
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
                Box(Modifier.fillMaxSize().hazeSrc(fh)) {
                FeedItem(entry, chromeVisible = chrome, muted = false, playing = true, showPlayButton = chrome && entry.kind == MediaKind.VIDEO) {
                    MoonImage(entry.coverUrl, Modifier.fillMaxSize())
                }
                }
                FeedTopChrome(visible = chrome && overlay.liftKey == null, index = 3, total = 24, haze = fh)
            }
        }
    }

    @Test fun feedChrome() = shot("feed-chrome-dark") {
        Feed(sample()[0], remember { MoonOverlayState() })
    }

    @Test fun feedMenu() = shot("feed-menu-dark") {
        val e = sample()[0]
        Feed(e, remember { MoonOverlayState(initialMenu = feedMenuSpec(e, muted = false, loop = true, speed = 1f, fit = true, playing = true, h = FeedMenuHandlers())) })
    }

    @Test fun feedImageMenu() = shot("feed-menu-image-dark") {
        val e = sample()[1]
        Feed(e, remember { MoonOverlayState(initialMenu = feedMenuSpec(e, muted = false, loop = true, speed = 1f, fit = true, playing = false, h = FeedMenuHandlers())) })
    }

    @Test fun feedEmbed() = shot("feed-embed-dark") {
        Feed(sample()[2], remember { MoonOverlayState() })
    }

    @Composable
    private fun Library(overlay: MoonOverlayState, selection: Boolean = false) {
        MoonShell(overlay, tab = 1, tabBarVisible = true) {
            val items = sample(15)
            LibraryContent(
                items = items, filter = "全部", onFilter = {}, selectionMode = selection,
                selectedIds = if (selection) setOf("s1", "s4", "s5") else emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> },
            )
        }
    }

    @Test fun library() = shot("library-dark") { Library(remember { MoonOverlayState() }) }
    @Test fun libraryLight() = shot("library-light", ThemeMode.LIGHT) { Library(remember { MoonOverlayState() }) }
    @Test fun librarySelect() = shot("library-select-dark") { Library(remember { MoonOverlayState() }, selection = true) }

    @Test fun libraryMenu() = shot("library-menu-dark") {
        val e = sample()[4]
        // anchor ≈ second-row middle thumbnail on a PIXEL_6 (411dp wide → px)
        Library(remember { MoonOverlayState(initialMenu = libraryMenuSpec(e, Rect(380f, 900f, 700f, 1330f), LibraryHandlers())) })
    }

    @Composable
    private fun Discover(overlay: MoonOverlayState, loading: Boolean = false) {
        MoonShell(overlay, tab = 2, tabBarVisible = true) {
            DiscoverContent(
                tab = "热门", onTab = {}, randomOn = false, onToggleRandom = {},
                cards = if (loading) emptyList() else sample(6).map { it.copy(id = "d${it.id}", kind = MediaKind.EMBED) },
                addedIds = setOf("ds1"), loading = loading, status = if (loading) "" else "共 6 条 · 长按卡片查看更多",
            )
        }
    }

    @Test fun discover() = shot("discover-dark") { Discover(remember { MoonOverlayState() }) }
    @Test fun discoverLight() = shot("discover-light", ThemeMode.LIGHT) { Discover(remember { MoonOverlayState() }) }
    @Test fun discoverLoading() = shot("discover-skeleton-dark") { Discover(remember { MoonOverlayState() }, loading = true) }
    @Test fun discoverMenu() = shot("discover-menu-dark") {
        val e = sample()[2].copy(id = "ds2", kind = MediaKind.EMBED)
        Discover(remember { MoonOverlayState(initialMenu = discoverMenuSpec(e, Rect(40f, 1100f, 1040f, 1700f), false, DiscoverHandlers())) })
    }

    @Composable
    private fun Settings(overlay: MoonOverlayState, mode: String = "DARK") {
        MoonShell(overlay, tab = 3, tabBarVisible = true) {
            SettingsContent(SettingsRepository.Snapshot(themeMode = mode, youtubeKey = "x"), SettingsActions(), hiddenCount = 2)
        }
    }

    @Test fun settings() = shot("settings-dark") { Settings(remember { MoonOverlayState() }) }
    @Test fun settingsLight() = shot("settings-light", ThemeMode.LIGHT) { Settings(remember { MoonOverlayState() }, "LIGHT") }

    @Test fun tabMenu() = shot("tab-menu-dark") {
        Library(remember {
            MoonOverlayState(initialMenu = tabMenu(2, Rect(560f, 2160f, 790f, 2300f), ThemeMode.DARK, false, {}, {}))
        })
    }

    @Composable
    private fun Player(overlay: MoonOverlayState) {
        androidx.compose.runtime.CompositionLocalProvider(com.moonlight.feed.ui.theme.LocalMoonColors provides com.moonlight.feed.ui.theme.MediaGlass) {
            com.moonlight.feed.ui.design.MoonOverlayHost(overlay) {
                MoonImage("art:6", Modifier.fillMaxSize())
                com.moonlight.feed.ui.player.PlayerTopBar("YouTube · 7/24", "沙漠星轨 2 小时", muted = true, onHome = {}, onHomeLong = {}, onMute = {}, onMore = {})
            }
        }
    }

    @Test fun player() = shot("player-chrome-dark") { Player(remember { MoonOverlayState() }) }
    @Test fun playerMenu() = shot("player-home-menu-dark") {
        Player(remember { MoonOverlayState(initialMenu = com.moonlight.feed.ui.player.playerHomeMenu(Rect(31f, 31f, 147f, 147f), com.moonlight.feed.ui.player.PlayerActions())) })
    }

    @Test fun confirmSheet() = shot("confirm-sheet-dark") {
        Feed(sample()[1], remember {
            MoonOverlayState(initialSheet = SheetSpec(null) { d ->
                ConfirmContent("删除这条内容？", "山脊线与银河", listOf(MenuAction("从月光移除", destructive = true), MenuAction("同时从相册删除", destructive = true)), d)
            })
        }, chrome = true)
    }
}
