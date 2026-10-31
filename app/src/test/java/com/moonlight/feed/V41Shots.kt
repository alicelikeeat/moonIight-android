package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.settings.RingAnimRows
import com.moonlight.feed.ui.settings.SettingsActions
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** v4.1: 长按动画 = 旋转 · 荷花 · 星轨(v4.2, was 坠落) · 月光; mid-frames frozen with MenuStyle.debugProgress. Fake procedural art only. */
class V41Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot("v41-$name") { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

    private val press = Offset(540f, 1250f)
    private val video = MediaEntry(id = "e6", kind = MediaKind.VIDEO, title = "山脊上的月出", localPath = "/x/6", coverUrl = "art:6", author = "我", platform = "local", createdAt = 1_759_900_000_000L)
    private fun released(spec: MenuSpec, at: Offset = press) = MoonOverlayState(initialMenu = spec.copy(origin = at)).apply { pressOrigin = at; finger = at; fingerDown = false }

    @Composable private fun Feed(overlay: MoonOverlayState, art: Int = 6) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                FeedItem(video, chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(art, Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = true, index = 4, total = 22)
            }
        }
    }
    private fun mid(name: String, style: RingAnim, p: Float, mode: ThemeMode = ThemeMode.DARK, art: Int = 6) = shot("anim-$name", mode) {
        MenuStyle.debugProgress = p
        Feed(remember { released(feedMenuSpec(video, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.ALL).copy(anim = style)) }, art)
    }

    // 荷花: bud → half-open → full bloom
    @Test fun lotusBud() = mid("lotus-1-bud-dark", RingAnim.LOTUS, 0.12f)
    @Test fun lotusHalf() = mid("lotus-2-half-open-dark", RingAnim.LOTUS, 0.45f)
    @Test fun lotusFull() = mid("lotus-3-full-dark", RingAnim.LOTUS, 1f)
    @Test fun lotusHalfLight() = mid("lotus-2-half-open-light", RingAnim.LOTUS, 0.5f, ThemeMode.LIGHT, art = 3)
    // 月光: screen falls dark, then the buttons are lit
    @Test fun moonDarkening() = mid("moon-1-darkening-dark", RingAnim.MOON, 0.3f)
    @Test fun moonLit() = mid("moon-2-lit-dark", RingAnim.MOON, 1f)
    @Test fun moonLitLight() = mid("moon-2-lit-light", RingAnim.MOON, 1f, ThemeMode.LIGHT, art = 3)
    @Test fun spinStill() = mid("spin-mid-dark", RingAnim.SPIN, 0.5f)

    // ---- settings › 外观 › 长按动画 (试一试 now scrolls with edge fades)
    @Composable private fun AnimRow(s: SettingsRepository.Snapshot, scrolled: Boolean = false, fontScale: Float? = null) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
            MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
                Column(Modifier.statusBarsPadding().padding(16.dp)) {
                    com.moonlight.feed.ui.design.ScreenHeader("外观")
                    Column { RingAnimRows(s, SettingsActions(), remember { LazyListState(if (scrolled) 2 else 0) }) }
                }
            }
        }
    }
    @Test fun settingsRow() = shot("settings-ring-anim-dark") { AnimRow(SettingsRepository.Snapshot(themeMode = "DARK", ringAnim = "LOTUS")) }
    @Test fun settingsRowScrolled() = shot("settings-ring-anim-scrolled-light", ThemeMode.LIGHT) { AnimRow(SettingsRepository.Snapshot(themeMode = "LIGHT", ringAnim = "MOON"), scrolled = true) }
    @Test fun settingsRowRandom() = shot("settings-ring-anim-random-dark") { AnimRow(SettingsRepository.Snapshot(themeMode = "DARK", ringAnim = "RANDOM", ringAnimSet = "SHATTER,MOON")) }
    @Test fun settingsRowLargeFont() = shot("settings-ring-anim-font-1.35-light", ThemeMode.LIGHT) { AnimRow(SettingsRepository.Snapshot(themeMode = "LIGHT", ringAnim = "STAR"), fontScale = 1.35f) }

    @Test fun settingsRowHugeFont() = shot("settings-ring-anim-font-2.0-dark") { AnimRow(SettingsRepository.Snapshot(themeMode = "DARK", ringAnim = "LOTUS"), fontScale = 2.0f) }

    @After fun reset() { MenuStyle.debugProgress = null }
}
