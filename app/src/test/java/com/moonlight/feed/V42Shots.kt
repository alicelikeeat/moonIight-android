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

/** v4.2: 星轨 (buttons fly in along an arc, Paramount-intro motion) + resting state of every style. Fake procedural art only. */
class V42Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot("v42-$name") { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

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
    @Composable private fun Ring(style: RingAnim, art: Int) {
        Feed(remember { released(feedMenuSpec(video, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.ALL).copy(anim = style)) }, art)
    }
    private fun mid(name: String, style: RingAnim, p: Float, mode: ThemeMode = ThemeMode.DARK, art: Int = 6) = shot("anim-$name", mode) {
        MenuStyle.debugProgress = p
        Ring(style, art)
    }

    // 星轨: far away and tiny → swooping around the finger → arriving / landing → final
    @Test fun starFar() = mid("star-1-far-dark", RingAnim.STAR, 0.13f)
    @Test fun starSwoop() = mid("star-2-swooping-dark", RingAnim.STAR, 0.42f)
    @Test fun starArrive() = mid("star-3-arriving-dark", RingAnim.STAR, 0.66f)
    @Test fun starLand() = mid("star-4-landing-dark", RingAnim.STAR, 0.86f)
    @Test fun starFinal() = mid("star-5-final-dark", RingAnim.STAR, 1f)
    @Test fun starSwoopLight() = mid("star-2-swooping-light", RingAnim.STAR, 0.45f, ThemeMode.LIGHT, art = 3)

    // resting state of every style (must be full size)
    @Test fun restSpin() = mid("rest-spin-dark", RingAnim.SPIN, 1f)
    @Test fun restLotus() = mid("rest-lotus-dark", RingAnim.LOTUS, 1f)
    @Test fun restStar() = mid("rest-star-light", RingAnim.STAR, 1f, ThemeMode.LIGHT, art = 3)
    @Test fun restMoon() = mid("rest-moon-dark", RingAnim.MOON, 1f)

    // 星轨 close: shrinking and sweeping back out along the arc (menu dismissed, progress frozen part-way)
    private fun closing(name: String, p: Float) = shot("anim-$name") {
        MenuStyle.debugProgress = 1f
        val st = remember {
            released(feedMenuSpec(video, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.ALL).copy(anim = RingAnim.STAR)).apply {
                kotlinx.coroutines.runBlocking { menuProgress.snapTo(p) }; dismissMenu()
            }
        }
        Feed(st, 6)
    }
    @Test fun starClosing() = closing("star-6-closing-dark", 0.55f)
    @Test fun starClosingFar() = closing("star-7-closing-far-dark", 0.22f)

    @After fun reset() { MenuStyle.debugProgress = null }
}
