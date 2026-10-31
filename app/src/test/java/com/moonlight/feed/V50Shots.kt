package com.moonlight.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.moonlight.feed.data.DeleteAnim
import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.HoldContext
import com.moonlight.feed.data.HoldTier
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.MomentsAutoplayMode
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.V5
import com.moonlight.feed.data.V5Prefs
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.data.trash.TrashItem
import com.moonlight.feed.ui.SettingsGroup
import com.moonlight.feed.ui.design.LocalHomeBg
import com.moonlight.feed.ui.design.MasterPanelSpec
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MenuStyle
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.design.RecordingPill
import com.moonlight.feed.ui.design.Shredder
import com.moonlight.feed.ui.design.panelActions
import com.moonlight.feed.ui.feed.FeedItem
import com.moonlight.feed.ui.feed.FeedMenuHandlers
import com.moonlight.feed.ui.feed.FeedTopChrome
import com.moonlight.feed.ui.feed.HomeRingHandlers
import com.moonlight.feed.ui.feed.feedMenuSpec
import com.moonlight.feed.ui.feed.homeRing
import com.moonlight.feed.ui.library.TrashContent
import com.moonlight.feed.ui.library.TrashHandlers
import com.moonlight.feed.ui.library.TrashUi
import com.moonlight.feed.ui.settings.DeleteAnimRows
import com.moonlight.feed.ui.settings.MomentsAutoplayRows
import com.moonlight.feed.ui.settings.RecordGroup
import com.moonlight.feed.ui.settings.StarSpeedRows
import com.moonlight.feed.ui.settings.TimedHoldGroup
import com.moonlight.feed.ui.social.InlineBadges
import com.moonlight.feed.ui.social.InlineLive
import com.moonlight.feed.ui.social.MomentHandlers
import com.moonlight.feed.ui.social.MomentsContent
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** v5.0: inline 朋友圈 autoplay · 定时长按 / 全部按键 · 录屏 pill · 星轨 v5 · 碎纸机 · new settings. Procedural art only. */
class V50Shots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 1.0)

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) =
        paparazzi.snapshot("v50-$name") { MoonTheme(mode) { CompositionLocalProvider(LocalHomeBg provides HomeBgSpec(HomeBgKind.THEME, null, true, false)) { content() } } }

    @After fun reset() {
        MenuStyle.debugProgress = null; MenuStyle.starSpeed = 1f
        Shredder.debugProgress = null; Shredder.runs.clear(); Shredder.anchors.clear()
        V5.prefs = V5Prefs()
    }

    private val now = 1_760_000_000_000L
    private val press = Offset(540f, 1250f)
    private val video = MediaEntry(id = "e6", kind = MediaKind.VIDEO, title = "山脊上的月出", localPath = "/x/6", coverUrl = "art:6", author = "我", platform = "local", createdAt = 1_759_900_000_000L)
    private fun released(spec: MenuSpec, at: Offset = press) = MoonOverlayState(initialMenu = spec.copy(origin = at)).apply { pressOrigin = at; finger = at; fingerDown = false }
    private fun feedSpec(anim: RingAnim = RingAnim.LOTUS) = feedMenuSpec(video, false, true, 1f, true, true, FeedMenuHandlers(), 5, FeedScope.ALL).copy(anim = anim)

    @Composable private fun Feed(overlay: MoonOverlayState, art: Int = 6, extra: @Composable BoxScope.() -> Unit = {}) {
        MoonShell(overlay, tab = 0, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                FeedItem(video, chromeVisible = true, muted = false, playing = true, showPlayButton = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { ProceduralArt(art, Modifier.fillMaxSize()) }
                }
                FeedTopChrome(visible = true, index = 4, total = 22)
                extra()
            }
        }
    }

    // ================================================================== A · 朋友圈 inline autoplay
    private fun vid(n: Int, t: String) = Attachment(id = "v$n", kind = AttachmentKind.VIDEO, coverUrl = "art:$n", title = t)
    private fun img(n: Int) = Attachment(id = "i$n", kind = AttachmentKind.IMAGE, coverUrl = "art:$n")
    private val social: SocialState = DemoSocial.seed(SocialState.fresh(), now).let { st ->
        val f = st.friends
        val vids = listOf(
            Moment(id = "mv1", authorId = f[0].id, text = "山脊线上的月出，延时 40 秒。", attachments = listOf(vid(3, "月出")), createdAt = now - 4 * 60_000),
            Moment(id = "mv2", authorId = st.selfId, text = "雨夜三连：窗、路灯、湖面。", attachments = listOf(vid(5, "窗"), vid(1, "路灯"), img(2)), createdAt = now - 30 * 60_000, autoplay = true),
            Moment(id = "mv3", authorId = f[1].id, text = "海边的风。", attachments = listOf(vid(4, "海")), createdAt = now - 90 * 60_000),
        )
        st.copy(moments = vids + st.moments.filter { m -> m.attachments.none { it.kind == AttachmentKind.VIDEO } })
    }
    /** Stand-in for the live pool: tiles in [playing] show a "frame" (shifted art) + the inline badges; [held] are paused windows. */
    private fun stand(playing: Set<String>, held: Set<String> = emptySet()) = InlineLive { a ->
        when (a.id) {
            in playing -> { { ProceduralArt(a.id.drop(1).toInt() + 7, Modifier.fillMaxSize()); InlineBadges(paused = false, muted = true) {} } }
            in held -> { { ProceduralArt(a.id.drop(1).toInt() + 7, Modifier.fillMaxSize()); InlineBadges(paused = true, muted = true) {} } }
            else -> null
        }
    }
    private fun moments(name: String, mode: ThemeMode, inline: InlineLive) = shot(name, mode) {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) {
            MomentsContent(social, social.selfId, MomentHandlers(), now, listState = androidx.compose.foundation.lazy.rememberLazyListState(1), inline = inline)
        }
    }
    // 全部同时播放: every visible video plays at once (one the user paused keeps its frame)
    @Test fun inlineAllDark() = moments("moments-inline-all-dark", ThemeMode.DARK, stand(setOf("v3", "v5", "v1"), held = setOf("v4")))
    @Test fun inlineAllLight() = moments("moments-inline-all-light", ThemeMode.LIGHT, stand(setOf("v3", "v5", "v1", "v4")))
    // 只播指定的: only the post marked 自动播放 (mv2); the rest are posters with ▶
    @Test fun inlinePickedDark() = moments("moments-inline-picked-dark", ThemeMode.DARK, stand(setOf("v5", "v1")))
    @Test fun inlinePickedLight() = moments("moments-inline-picked-light", ThemeMode.LIGHT, stand(setOf("v5", "v1")))

    // ================================================================== B · 定时长按 readout + countdown
    private fun timed(name: String, mode: ThemeMode, elapsed: Long, tier: HoldTier, at: Offset = press, prefs: V5Prefs = V5Prefs(timedOn = true)) = shot(name, mode) {
        MenuStyle.debugProgress = 1f
        V5.prefs = prefs
        Feed(remember { released(feedSpec(), at).apply { debugHold = elapsed to tier } }, art = if (mode == ThemeMode.LIGHT) 3 else 6)
    }
    @Test fun timedDark() = timed("timed-readout-dark", ThemeMode.DARK, 3200L, HoldTier.TIMED)
    @Test fun timedLight() = timed("timed-readout-light", ThemeMode.LIGHT, 7400L, HoldTier.TIMED, prefs = V5Prefs(timedOn = true, timedSeconds = 10f))
    @Test fun timedNoAnimDark() = timed("timed-readout-only-dark", ThemeMode.DARK, 2100L, HoldTier.TIMED, prefs = V5Prefs(timedOn = true, timedCountdown = false))
    // centre hold: the moon waxes for 5 s, then 全部按键
    @Test fun centerDark() = timed("center-countdown-dark", ThemeMode.DARK, 3600L, HoldTier.CENTER, Offset(540f, 1200f))
    @Test fun centerLight() = timed("center-countdown-light", ThemeMode.LIGHT, 2000L, HoldTier.CENTER, Offset(540f, 1200f))

    // ================================================================== 全部按键 panel
    private fun panel(name: String, mode: ThemeMode) = shot(name, mode) {
        MenuStyle.debugProgress = 1f
        val ctx = LocalContext.current
        val st = remember {
            MoonOverlayState().apply {
                panel = MasterPanelSpec("山脊上的月出", panelActions(feedSpec()),
                    panelActions(homeRing(Rect(press, 1f), FeedScope.ALL, false, false, HomeRingHandlers())),
                    globalActions(ctx, mode, {}, {}, {}))
            }
        }
        Feed(st, art = if (mode == ThemeMode.LIGHT) 3 else 6)
    }
    @Test fun panelDark() = panel("master-panel-dark", ThemeMode.DARK)
    @Test fun panelLight() = panel("master-panel-light", ThemeMode.LIGHT)

    // ================================================================== 录屏 pill
    @Test fun pillDark() = shot("recording-pill-dark") { Feed(remember { MoonOverlayState() }) { RecordingPill(83_000L) } }
    @Test fun pillLight() = shot("recording-pill-saving-light", ThemeMode.LIGHT) {
        MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) {
            Box(Modifier.fillMaxSize()) {
                MomentsContent(social, social.selfId, MomentHandlers(), now, inline = stand(setOf("v3")))
                RecordingPill(312_000L, stopping = true)
            }
        }
    }

    // ================================================================== C · 星轨 v5 sequence
    private fun star(name: String, p: Float, mode: ThemeMode = ThemeMode.DARK, art: Int = 6) = shot(name, mode) {
        MenuStyle.debugProgress = p
        Feed(remember { released(feedSpec(RingAnim.STAR)) }, art)
    }
    @Test fun star1() = star("star-1-sky", 0.12f)
    @Test fun star2() = star("star-2-spiral", 0.28f)
    @Test fun star3() = star("star-3-accelerating", 0.44f)
    @Test fun star4() = star("star-4-swoop", 0.58f)
    @Test fun star5() = star("star-5-landing-burst", 0.76f)
    @Test fun star6() = star("star-6-shimmer", 0.9f)
    @Test fun star7() = star("star-7-final", 1f)
    @Test fun star8() = shot("star-8-closing") {
        MenuStyle.debugProgress = 1f
        val st = remember { released(feedSpec(RingAnim.STAR)).apply { kotlinx.coroutines.runBlocking { menuProgress.snapTo(0.45f) }; dismissMenu() } }
        Feed(st, 6)
    }
    @Test fun starLight() = star("star-4-swoop-light", 0.58f, ThemeMode.LIGHT, art = 3)
    @Test fun starSlowLight() = star("star-final-light", 1f, ThemeMode.LIGHT, art = 3)

    // ================================================================== D · 碎纸机
    private val shredRect = Rect(60f, 520f, 1020f, 1500f)
    private val trashMoment get() = social.moments[1]
    private fun shred(name: String, mode: ThemeMode, t: Float, reverse: Boolean, momentCard: Boolean) = shot(name, mode) {
        remember {
            Shredder.runs.clear(); Shredder.debugProgress = t
            if (momentCard) Shredder.play("moment:mv2", reverse, Rect(0f, 640f, 1080f, 1500f)) { TrashUi.MomentVisual(trashMoment, social) }
            else Shredder.play("e9", reverse, shredRect) { TrashUi.EntryVisual(video.copy(id = "e9", coverUrl = "art:4", localPath = null)) }
        }
        if (momentCard) MoonShell(remember { MoonOverlayState() }, tab = 4, tabBarVisible = true) {
            MomentsContent(social.copy(moments = social.moments.filter { it.id != "mv2" }), social.selfId, MomentHandlers(), now, inline = stand(emptySet()))
        } else MoonShell(remember { MoonOverlayState() }, tab = 2, tabBarVisible = true) {
            Box(Modifier.fillMaxSize().background(com.moonlight.feed.ui.theme.LocalMoonColors.current.bgTop))
        }
    }
    @Test fun shredDark() = shred("shred-mid-dark", ThemeMode.DARK, 0.42f, false, false)
    @Test fun shredLight() = shred("shred-mid-moment-light", ThemeMode.LIGHT, 0.5f, false, true)
    @Test fun restoreDark() = shred("shred-restore-moment-dark", ThemeMode.DARK, 0.3f, true, true)
    @Test fun restoreLight() = shred("shred-restore-light", ThemeMode.LIGHT, 0.24f, true, false)

    // 库 › 最近删除
    private fun trash(name: String, mode: ThemeMode) = shot(name, mode) {
        val day = TrashItem.DAY
        val items = listOf(
            TrashItem("lib:e1", now - 2 * 3_600_000L, entry = video.copy(id = "e1", coverUrl = "art:2")),
            TrashItem("moment:mv2", now - 3 * day, moment = trashMoment),
            TrashItem("lib:e3", now - 12 * day, entry = video.copy(id = "e3", kind = MediaKind.IMAGE, title = "湖面", coverUrl = "art:5")),
            TrashItem("lib:e4", now - 27 * day, entry = video.copy(id = "e4", title = "旧的延时", coverUrl = "art:1")),
        )
        MoonShell(remember { MoonOverlayState() }, tab = 2, tabBarVisible = true) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) { Spacer(Modifier.height(12.dp)); TrashContent(items, 30, now, TrashHandlers()) }
        }
    }
    @Test fun trashDark() = trash("library-trash-dark", ThemeMode.DARK)
    @Test fun trashLight() = trash("library-trash-light", ThemeMode.LIGHT)

    // ================================================================== new settings sections
    private fun settings(name: String, mode: ThemeMode, p: V5Prefs, second: Boolean) = shot(name, mode) {
        V5.prefs = p; MenuStyle.starSpeed = p.starSpeed
        MoonShell(remember { MoonOverlayState() }, tab = 3, tabBarVisible = false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
                Spacer(Modifier.height(12.dp))
                if (!second) {
                    SettingsGroup("朋友圈") { MomentsAutoplayRows(p, social, {}) }
                    TimedHoldGroup(p, {})
                } else {
                    SettingsGroup("外观") { StarSpeedRows(p, {}); com.moonlight.feed.ui.SettingsDivider(); DeleteAnimRows(p, {}) }
                    RecordGroup(p, {})
                }
            }
        }
    }
    private val picked = V5Prefs(momentsMode = MomentsAutoplayMode.PICKED, autoplayFriends = setOf(social.friends[0].id), timedOn = true, timedSeconds = 7.5f,
        timedAssign = mapOf(HoldContext.FEED_ITEM to "fav", HoldContext.MOMENT_VIDEO to "mv_mute"))
    @Test fun settings1Dark() = settings("settings-autoplay-timed-dark", ThemeMode.DARK, picked, false)
    @Test fun settings1Light() = settings("settings-autoplay-timed-light", ThemeMode.LIGHT, V5Prefs(timedOn = true, timedAssign = mapOf(HoldContext.TAB to "random")), false)
    @Test fun settings2Dark() = settings("settings-star-delete-record-dark", ThemeMode.DARK, V5Prefs(starSpeed = 1.4f, recordMic = true), true)
    @Test fun settings2Light() = settings("settings-star-delete-record-light", ThemeMode.LIGHT, V5Prefs(starSpeed = 0.85f, deleteAnim = DeleteAnim.FADE, trashDays = 7), true)
}
