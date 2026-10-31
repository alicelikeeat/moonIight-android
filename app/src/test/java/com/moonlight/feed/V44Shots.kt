package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.download.DlState
import com.moonlight.feed.data.download.DownloadPolicy
import com.moonlight.feed.data.download.DownloadTask
import com.moonlight.feed.ui.design.ActionSheetContent
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.SheetSpec
import com.moonlight.feed.ui.design.ring
import com.moonlight.feed.ui.design.sheetGroups
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.library.LibraryContent
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test

/** v4.4 主动下载: 下载 on the ring's 分享·保存 arc, the disabled sheet row, 库 › 我的下载 with in-progress + finished items. */
class V44Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot("v44-$name") { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

    private val press = Offset(540f, 1250f)
    private fun released(spec: MenuSpec) = MoonOverlayState(initialMenu = spec.copy(origin = press)).apply { pressOrigin = press; finger = press; fingerDown = false }

    private val archive = MediaEntry(id = "ar6", kind = MediaKind.VIDEO, title = "山脊上的月出 · Archive", remoteUrl = "https://archive.org/download/moonrise/moonrise.mp4",
        coverUrl = "art:6", platform = PlatformId.ARCHIVE, author = "Internet Archive", source = "archive", createdAt = 1_759_900_000_000L)
    private val bili = MediaEntry(id = "bv", kind = MediaKind.BILI, title = "在线 · 夜航", coverUrl = "art:2", biliBvid = "BV1xx", platform = PlatformId.BILI)

    @Composable private fun Feed(e: MediaEntry, overlay: MoonOverlayState) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                FeedItem(e, chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(e.coverUrl!!.removePrefix("art:").toInt(), Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = true, index = 4, total = 22)
            }
        }
    }
    private fun arcOpen(spec: MenuSpec) = released(spec.also { com.moonlight.feed.ui.design.MenuStyle.debugProgress = 1f }).apply { subIndex = spec.ring().indexOfFirst { it.title == "分享·保存" } }

    @Test fun ringDownload() = shot("ring-share-save-download-dark") {
        Feed(archive, remember { arcOpen(feedMenuSpec(archive, false, true, 1f, true, true, FeedMenuHandlers())) })
    }
    @Test fun ringDownloadProgress() = shot("ring-share-save-download-progress-dark") {
        val t = DownloadTask(archive.id, archive.remoteUrl!!, archive.title, MediaKind.VIDEO, state = DlState.RUNNING, bytes = 42, total = 100)
        Feed(archive, remember { arcOpen(feedMenuSpec(archive, false, true, 1f, true, true, FeedMenuHandlers(), dl = t)) })
    }
    @Test fun ringDownloadLightTheme() = shot("ring-share-save-download-light-theme", ThemeMode.LIGHT) {
        Feed(archive, remember { arcOpen(feedMenuSpec(archive, false, true, 1f, true, true, FeedMenuHandlers())) })
    }
    @Test fun sheetDownload() = shot("sheet-download-row-dark") {
        val spec = feedMenuSpec(archive, false, true, 1f, true, true, FeedMenuHandlers())
        Feed(archive, remember { MoonOverlayState(initialSheet = SheetSpec(spec.title, spec.forceDark) { d -> ActionSheetContent(spec.sheetGroups(), d) }) })
    }
    @Test fun sheetNotDownloadable() = shot("sheet-download-disabled-bili-dark") {
        val spec = feedMenuSpec(bili, false, true, 1f, true, true, FeedMenuHandlers())
        Feed(bili.copy(coverUrl = "art:2"), remember { MoonOverlayState(initialSheet = SheetSpec(spec.title, spec.forceDark) { d -> ActionSheetContent(spec.sheetGroups(), d) }) })
    }

    @Test fun ringPlayerDownload() = shot("ring-player-share-save-download-dark") {
        val spec = com.moonlight.feed.ui.player.playerMediaMenu(press, archive, false, false, com.moonlight.feed.ui.player.PlayerActions())
        MoonShell(remember { arcOpen(spec) }, tab = 0, tabBarVisible = false) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(6, Modifier.fillMaxSize()) }
        }
    }

    // ---------------------------------------------------------------- 库 › 我的下载
    private val tasks = listOf(
        DownloadTask("t1", "https://archive.org/download/a/lake.mp4", "湖边延时 · Archive", MediaKind.VIDEO, "art:3", PlatformId.ARCHIVE, state = DlState.RUNNING, bytes = 31_400_000, total = 74_800_000),
        DownloadTask("t2", "https://images-assets.nasa.gov/video/x/x~orig.mp4", "月球轨道 · NASA", MediaKind.VIDEO, "art:5", PlatformId.NASA, state = DlState.PAUSED, bytes = 12_900_000, total = 51_000_000),
        DownloadTask("t3", "https://tube.example/download/videos/u-720.mp4", "星空 · PeerTube", MediaKind.VIDEO, "art:8", PlatformId.PEERTUBE, state = DlState.FAILED, bytes = 0, error = "HTTP 503 · 已重试 6 次"),
        DownloadTask("t4", "https://upload.wikimedia.org/x/moon.jpg", "满月 · Commons", MediaKind.IMAGE, "art:9", PlatformId.COMMONS, state = DlState.QUEUED, bytes = 0, total = -1),
    )
    private val done = listOf(4, 7, 1, 10, 11).mapIndexed { i, art ->
        MediaEntry(id = DownloadPolicy.entryId("d$i"), kind = if (i % 2 == 0) MediaKind.VIDEO else MediaKind.IMAGE, title = "下载 $i", localPath = "/x/dl_$i",
            coverUrl = "art:$art", platform = PlatformId.ARCHIVE, source = DownloadPolicy.SOURCE)
    }
    @Composable private fun Downloads() {
        MoonShell(remember { MoonOverlayState() }, tab = 1, tabBarVisible = true) {
            LibraryContent(items = done, filter = DownloadPolicy.CATEGORY, onFilter = {}, selectionMode = false, selectedIds = emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> }, downloads = tasks)
        }
    }
    @org.junit.After fun reset() { com.moonlight.feed.ui.design.MenuStyle.debugProgress = null }

    @Test fun libraryDownloadsDark() = shot("library-my-downloads-dark") { Downloads() }
    @Test fun libraryDownloadsLight() = shot("library-my-downloads-light", ThemeMode.LIGHT) { Downloads() }
    @Test fun libraryDownloadsEmptyLight() = shot("library-my-downloads-empty-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 1, tabBarVisible = true) {
            LibraryContent(items = emptyList(), filter = DownloadPolicy.CATEGORY, onFilter = {}, selectionMode = false, selectedIds = emptySet(),
                onToggleSelectionMode = {}, onItemClick = {}, onItemLongPress = { _, _ -> })
        }
    }
}
