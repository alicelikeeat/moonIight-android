package com.moonlight.feed

import androidx.compose.ui.geometry.Rect
import com.moonlight.feed.data.DeleteAnim
import com.moonlight.feed.data.HoldConfig
import com.moonlight.feed.data.HoldContext
import com.moonlight.feed.data.HoldEvent
import com.moonlight.feed.data.HoldMachine
import com.moonlight.feed.data.HoldOutcome
import com.moonlight.feed.data.HoldTier
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.MomentsAutoplayMode
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.RingMotion
import com.moonlight.feed.data.TimedActions
import com.moonlight.feed.data.V5Prefs
import com.moonlight.feed.data.VideoBudget
import com.moonlight.feed.data.record.ProjectionPort
import com.moonlight.feed.data.record.RecState
import com.moonlight.feed.data.record.RecordOptions
import com.moonlight.feed.data.record.RecordingMachine
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.InlinePlan
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.MomentAutoplay
import com.moonlight.feed.data.social.SocialJson
import com.moonlight.feed.data.social.VideoTile
import com.moonlight.feed.data.trash.TrashItem
import com.moonlight.feed.data.trash.TrashStore
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.ShredMath
import com.moonlight.feed.ui.design.panelActions
import com.moonlight.feed.ui.design.timedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V50LogicTest {
    // ================================================================== gesture tiers (HoldMachine)
    private val W = 1080f; private val H = 2400f
    private fun machine(timedOn: Boolean = true, timedMs: Long = 5000L, autoFire: Boolean = true, centerOn: Boolean = true) =
        HoldMachine(HoldConfig(longMs = 500L, timedOn = timedOn, timedMs = timedMs, autoFire = autoFire, centerOn = centerOn, slopPx = 24f))
    private val off = 300f to 600f   // well outside the centre circle

    @Test fun tapIsAnUpBeforeTheLongPress() {
        val m = machine(); m.down(0, off.first, off.second, W, H)
        assertNull(m.tick(300)); assertEquals(HoldOutcome.TAP, m.up(400))
    }

    @Test fun longPressHappensAtTheUsualTime() {
        val m = machine(); m.down(0, off.first, off.second, W, H)
        assertNull(m.tick(499)); assertEquals(HoldEvent.Long, m.tick(500))
        m.ring(HoldContext.FEED_ITEM, null)
        assertEquals(HoldOutcome.LONG, m.up(2000))
    }

    @Test fun timedTierFiresTheAssignedOptionAtTheThreshold() {
        val m = machine(); m.down(0, off.first, off.second, W, H)
        m.tick(500); m.ring(HoldContext.FEED_ITEM, "fav")
        assertTrue(m.showing()); assertEquals(HoldTier.TIMED, m.tier())
        assertNull(m.tick(4999))
        assertEquals(HoldEvent.Timed(HoldContext.FEED_ITEM, "fav", false), m.tick(5000))
        assertNull("fires once", m.tick(6000))
        assertEquals(HoldOutcome.TIMED, m.up(6500))
    }

    @Test fun noAssignmentOrAutoFireOffJustOpensTheRing() {
        val m = machine(); m.down(0, off.first, off.second, W, H); m.tick(500); m.ring(HoldContext.TAB, null)
        assertEquals(HoldEvent.Timed(HoldContext.TAB, null, false), m.tick(5000))
        val m2 = machine(autoFire = false); m2.down(0, off.first, off.second, W, H); m2.tick(500); m2.ring(HoldContext.TAB, "random")
        assertEquals(HoldEvent.Timed(HoldContext.TAB, null, false), m2.tick(5000))
    }

    @Test fun destructiveActionsCanNeverAutoFire() {
        // not assignable at all
        for ((ctx, opts) in TimedActions.byContext) for (o in opts) assertFalse("$ctx ${o.key}", o.titles.any { it.contains("删除") })
        val m = machine(); m.down(0, off.first, off.second, W, H); m.tick(500); m.ring(HoldContext.FEED_ITEM, "delete")
        assertEquals(HoldEvent.Timed(HoldContext.FEED_ITEM, null, false), m.tick(5000))
        // and the resolver refuses a destructive action even if its title matches
        val evil = MenuSpec(Rect.Zero, actions = listOf(listOf(MenuAction("收藏", destructive = true))), context = HoldContext.FEED_ITEM)
        assertNull(timedAction(evil, HoldContext.FEED_ITEM, "fav"))
        val ok = MenuSpec(Rect.Zero, actions = listOf(listOf(MenuAction("删除", destructive = true), MenuAction("收藏"))), context = HoldContext.FEED_ITEM)
        assertEquals("收藏", timedAction(ok, HoldContext.FEED_ITEM, "fav")?.title)
        assertNull("wrong context", timedAction(ok, HoldContext.PLAYER, "fav"))
        // decoding stored prefs drops anything not assignable
        val p = V5Prefs.decode("""{"assign":{"FEED_ITEM":"delete","TAB":"random"}}""")
        assertEquals(mapOf(HoldContext.TAB to "random"), p.timedAssign)
    }

    @Test fun centreHoldOpensThePanelAndWinsOverTimed() {
        val m = machine(timedMs = 3000L)
        m.down(0, W / 2 + 40f, H / 2 - 30f, W, H)
        assertTrue(m.inCenter)
        m.tick(500); m.ring(HoldContext.FEED_ITEM, "fav")   // the normal ring opens at its usual time …
        assertEquals(HoldTier.CENTER, m.tier())
        assertNull("no timed fire inside the centre", m.tick(3000))
        assertEquals(HoldEvent.Center, m.tick(5000))         // … and turns into 全部按键 at 5 s
        assertEquals(HoldOutcome.CENTER, m.up(5200))
    }

    @Test fun centreWorksWithoutTimedAndWithoutAnyRing() {
        val m = machine(timedOn = false); m.down(0, W / 2, H / 2, W, H)
        assertEquals(HoldEvent.Long, m.tick(500)); assertEquals(HoldEvent.Center, m.tick(5000))
    }

    @Test fun centreCircleIs30PercentOfTheShortSide() {
        val r = W * 0.30f / 2f
        val m = machine(); m.down(0, W / 2 + r - 1f, H / 2, W, H); assertTrue(m.inCenter)
        m.down(0, W / 2 + r + 2f, H / 2, W, H); assertFalse(m.inCenter)
        val off = machine(centerOn = false); off.down(0, W / 2, H / 2, W, H); off.tick(500)
        assertEquals(HoldTier.NONE, off.tier())
        off.ring(HoldContext.FEED_ITEM, "fav"); assertEquals("centre off → timed applies", HoldTier.TIMED, off.tier())
    }

    @Test fun movingBeyondSlopCancelsTheTimedTiers() {
        val m = machine(); m.down(0, W / 2, H / 2, W, H)
        assertNull("within slop", m.move(W / 2 + 10f, H / 2 + 10f))
        m.tick(500); m.ring(HoldContext.FEED_ITEM, "fav")
        assertEquals(HoldEvent.Cancelled, m.move(W / 2 + 30f, H / 2))
        assertNull(m.tick(5000)); assertNull(m.tick(9000)); assertFalse(m.showing())
        assertEquals(HoldOutcome.CANCELLED, m.up(9100))
    }

    @Test fun nothingTimedWithoutARingOutsideTheCentre() {
        val m = machine(); m.down(0, off.first, off.second, W, H)
        m.tick(500); assertFalse(m.showing()); assertNull(m.tick(20_000))
        assertEquals(HoldOutcome.LONG, m.up(20_001))
    }

    @Test fun deferredHoldIsFlaggedSoTheRingOpensAtTheThreshold() {
        val m = machine(); m.down(0, off.first, off.second, W, H); m.tick(500)
        m.ring(HoldContext.MOMENT_VIDEO, "mv_mute", deferred = true)
        m.ring(HoldContext.FEED_ITEM, "fav")   // a later ring of another kind doesn't steal the press
        assertEquals(HoldEvent.Timed(HoldContext.MOMENT_VIDEO, "mv_mute", true), m.tick(5000))
    }

    @Test fun countdownProgressAndConfigClamp() {
        val m = machine(); m.down(1000, off.first, off.second, W, H); m.tick(1500); m.ring(HoldContext.TAB, null)
        assertEquals(0f, m.progress(1500), 1e-4f); assertEquals(0.5f, m.progress(1000 + 500 + 2250), 1e-3f); assertEquals(1f, m.progress(99_999), 0f)
        assertEquals(3200L, m.elapsed(4200))
        val c = HoldConfig.of(V5Prefs(timedOn = true, timedSeconds = 3f), 20f)
        assertEquals(3000L, c.timedMs); assertTrue(c.timedMs >= c.longMs + 500)
        assertEquals(15_000L, V5Prefs(timedSeconds = 99f).timedMs)
    }

    @Test fun v5PrefsRoundTripAndMigration() {
        val p = V5Prefs(momentsMode = MomentsAutoplayMode.PICKED, autoplayFriends = setOf("a", "b"), timedOn = true, timedSeconds = 7.5f,
            timedAssign = mapOf(HoldContext.PLAYER to "mute"), centerPanel = false, recordMic = true, starSpeed = 1.6f, deleteAnim = DeleteAnim.FADE, trashDays = 7)
        assertEquals(p, V5Prefs.decode(p.encode()))
        assertEquals(V5Prefs(), V5Prefs.decode(null))
        assertEquals(V5Prefs(), V5Prefs.decode("{broken"))
        // v4 moments_autoplay → v5 mode
        assertEquals(MomentsAutoplayMode.ALL, V5Prefs.decode(null, "ALWAYS").momentsMode)
        V5Prefs.decode(null, "WIFI").let { assertEquals(MomentsAutoplayMode.ALL, it.momentsMode); assertTrue(it.autoplayWifiOnly) }
        assertEquals(MomentsAutoplayMode.OFF, V5Prefs.decode(null, "OFF").momentsMode)
        assertEquals("stored v5 wins", MomentsAutoplayMode.PICKED, V5Prefs.decode("""{"momentsMode":"PICKED"}""", "OFF").momentsMode)
        // defaults the user asked for
        assertFalse(V5Prefs().timedOn); assertTrue(V5Prefs().centerPanel); assertEquals(DeleteAnim.SHRED, V5Prefs().deleteAnim)
        assertEquals(2f, V5Prefs.decode("""{"starSpeed":9}""").starSpeed, 0f); assertEquals(0.5f, V5Prefs.clampSpeed(0.1f), 0f)
    }

    // ================================================================== 朋友圈自动播放 + budget
    private fun tile(id: String, vis: Float, author: String = "f1", marked: Boolean = false, off: Int = 0, playable: Boolean = true) =
        VideoTile(id, "m-$id", author, marked, vis, off, 0, playable)

    @Test fun allModePlaysEveryVisibleVideoUpToTheCap() {
        val tiles = (0 until 9).map { tile("v$it", 1f - it * 0.05f, off = it * 100) }
        val p = MomentAutoplay.plan(MomentsAutoplayMode.ALL, tiles, emptySet(), emptySet(), emptySet())
        assertEquals(MomentAutoplay.MAX_INLINE, p.play.size)
        assertEquals((0 until 6).map { "v$it" }, p.play)   // most visible first; the rest stay posters
        assertTrue(p.held.isEmpty())
    }

    @Test fun barelyVisibleOffscreenAndUnplayableTilesDontAutoplay() {
        val tiles = listOf(tile("a", 0.9f), tile("b", 0.1f), tile("c", 0f), tile("d", 1f, playable = false))
        assertEquals(listOf("a"), MomentAutoplay.plan(MomentsAutoplayMode.ALL, tiles, emptySet(), emptySet(), emptySet()).play)
    }

    @Test fun pickedModeOnlyPlaysMarkedPostsAndPickedFriends() {
        val tiles = listOf(tile("a", 1f, author = "x"), tile("b", 1f, author = "x", marked = true), tile("c", 1f, author = "friend"))
        assertEquals(setOf("b", "c"), MomentAutoplay.plan(MomentsAutoplayMode.PICKED, tiles, setOf("friend"), emptySet(), emptySet()).play.toSet())
        assertEquals(listOf("b"), MomentAutoplay.plan(MomentsAutoplayMode.PICKED, tiles, emptySet(), emptySet(), emptySet()).play)
    }

    @Test fun offModeOnlyPlaysWhatTheUserTapped() {
        val tiles = listOf(tile("a", 1f), tile("b", 0.5f))
        assertTrue(MomentAutoplay.plan(MomentsAutoplayMode.OFF, tiles, emptySet(), emptySet(), emptySet()).play.isEmpty())
        assertEquals(listOf("b"), MomentAutoplay.plan(MomentsAutoplayMode.OFF, tiles, emptySet(), setOf("b"), emptySet()).play)
    }

    @Test fun userPausedTilesKeepTheirWindowAndAreNeverRestarted() {
        val tiles = listOf(tile("a", 1f), tile("b", 0.9f))
        val p = MomentAutoplay.plan(MomentsAutoplayMode.ALL, tiles, emptySet(), emptySet(), setOf("a"))
        assertEquals(listOf("b"), p.play); assertEquals(listOf("a"), p.held)
        // scrolled away → dropped (player released)
        val gone = MomentAutoplay.plan(MomentsAutoplayMode.ALL, listOf(tile("a", 0f), tile("b", 0f)), emptySet(), emptySet(), setOf("a"))
        assertEquals(InlinePlan.EMPTY, gone)
    }

    @Test fun manualTapsWinTheBudget() {
        val tiles = (0 until 8).map { tile("v$it", 1f, off = it) }
        val p = MomentAutoplay.plan(MomentsAutoplayMode.ALL, tiles, emptySet(), setOf("v7"), setOf("v6"), cap = 3)
        assertEquals(listOf("v7", "v0"), p.play); assertEquals(listOf("v6"), p.held); assertEquals(3, p.all.size)
    }

    @Test fun videoBudgetIsThePoolGuard() {
        val b = VideoBudget(MomentAutoplay.MAX_INLINE)
        repeat(6) { assertTrue(b.acquire("t$it")) }
        assertFalse("7th window → poster", b.acquire("t6")); assertTrue("same tile re-acquires", b.acquire("t0"))
        b.release("t0"); assertTrue(b.acquire("t6")); assertEquals(6, b.active)
    }

    @Test fun momentAutoplayFlagRoundTripsThroughJson() {
        val m = Moment(id = "m1", authorId = "a", attachments = listOf(Attachment(kind = AttachmentKind.VIDEO, localPath = "/x.mp4")), autoplay = true)
        assertTrue(SocialJson.moment(SocialJson.moment(m)).autoplay)
        assertFalse(SocialJson.moment(SocialJson.moment(m.copy(autoplay = false))).autoplay)
        assertFalse("old files", SocialJson.moment(org.json.JSONObject("""{"id":"x","authorId":"a"}""")).autoplay)
    }

    // ================================================================== 录屏 state machine (fake projection)
    private class FakePort : ProjectionPort {
        var consents = 0; var starts = mutableListOf<RecordOptions>(); var stops = 0
        override fun requestConsent() { consents++ }
        override fun start(consent: Any, opts: RecordOptions) { starts += opts }
        override fun stop() { stops++ }
    }

    @Test fun recordingHappyPath() {
        var now = 1_000L
        val port = FakePort(); val m = RecordingMachine(port) { now }
        val seen = mutableListOf<RecState>(); m.listener = { seen += it }
        assertTrue(m.request(RecordOptions(mic = true)))
        assertEquals(RecState.Requesting, m.state); assertEquals(1, port.consents)
        assertFalse("no double start", m.request(RecordOptions()))
        m.consent(true, "token")
        assertEquals(RecordOptions(mic = true), port.starts.single()); assertTrue(m.state is RecState.Starting)
        m.started(); assertTrue(m.state is RecState.Recording)
        now += 65_000; assertEquals(65_000L, m.elapsedMs()); assertEquals("1:05", RecordingMachine.timer(m.elapsedMs()))
        m.stop(); assertEquals(RecState.Stopping, m.state); assertEquals(1, port.stops)
        m.saved("/lib/rec.mp4")
        assertEquals(RecState.Saved("/lib/rec.mp4", 65_000L), m.state)
        m.reset(); assertEquals(RecState.Idle, m.state)
        assertEquals(6, seen.size)
    }

    @Test fun declinedConsentIsHandledGracefully() {
        val port = FakePort(); val m = RecordingMachine(port)
        m.request(RecordOptions()); m.consent(false, null)
        assertTrue(m.state is RecState.Denied); assertTrue(port.starts.isEmpty())
        assertTrue("can try again", m.request(RecordOptions()))
        m.consent(true, null); assertTrue("granted without data = denied", m.state is RecState.Denied)
    }

    @Test fun systemStopFailuresAndEarlyStop() {
        val port = FakePort(); val m = RecordingMachine(port)
        m.request(RecordOptions()); m.consent(true, 1); m.started()
        m.projectionEnded(); assertEquals(RecState.Stopping, m.state); assertEquals(1, port.stops)
        m.saved("/a.mp4"); m.reset()
        m.request(RecordOptions()); m.consent(true, 1); m.started()
        m.failed("encoder"); assertEquals(RecState.Failed("encoder"), m.state); assertEquals(2, port.stops)
        m.reset()
        m.request(RecordOptions()); m.consent(true, 1)
        m.stop(); assertEquals("stop while starting", RecState.Idle, m.state); assertEquals(3, port.stops)
        m.request(RecordOptions()); m.stop(); assertEquals("stop while asking", RecState.Idle, m.state)
        m.saved("/late.mp4"); assertEquals("late callbacks ignored", RecState.Idle, m.state)
        m.consent(true, 1); assertEquals(RecState.Idle, m.state)
    }

    // ================================================================== 最近删除 persistence
    private fun tmp(): File = File.createTempFile("trash", ".json").also { it.delete(); it.deleteOnExit() }
    private val e1 = MediaEntry(id = "e1", kind = MediaKind.VIDEO, title = "山", localPath = "/m/e1.mp4", coverUrl = "art:1", favorite = true, createdAt = 42L)

    @Test fun trashSurvivesARestartAndRestores() {
        val f = tmp(); var now = 10_000L
        val a = TrashStore(f, { now })
        a.putEntry(e1); a.putMoment(Moment(id = "m1", authorId = "me", text = "今晚的月亮", autoplay = true))
        val b = TrashStore(f, { now })
        assertEquals(listOf("moment:m1", "lib:e1"), b.items.value.map { it.id })
        assertEquals(e1, b.items.value.last().entry)
        assertTrue(b.items.value.first().moment!!.autoplay)
        assertEquals("今晚的月亮", b.items.value.first().title)
        val r = b.restore("lib:e1"); assertEquals(e1, r?.entry)
        assertEquals(listOf("moment:m1"), TrashStore(f, { now }).items.value.map { it.id })
        assertNull(b.restore("lib:e1"))
    }

    @Test fun purgeDropsWhatIsOlderThanNDays() {
        val f = tmp(); var now = 0L; val purged = mutableListOf<String>()
        val s = TrashStore(f, { now }) { purged += it.id }
        s.putEntry(e1); now = 5 * TrashItem.DAY; s.putEntry(e1.copy(id = "e2"))
        assertEquals(25, s.items.value.first { it.id == "lib:e1" }.daysLeft(now, 30)); assertEquals(30, s.items.value.first { it.id == "lib:e2" }.daysLeft(now, 30))
        now = 30 * TrashItem.DAY
        assertEquals(listOf("lib:e1"), s.purge(30).map { it.id }); assertEquals(listOf("lib:e1"), purged)
        assertEquals(listOf("lib:e2"), TrashStore(f, { now }).items.value.map { it.id })
        assertEquals(0, s.items.value.single().daysLeft(now + 40 * TrashItem.DAY, 30))
        s.deleteForever("lib:e2"); assertEquals(listOf("lib:e1", "lib:e2"), purged); assertTrue(s.items.value.isEmpty())
    }

    @Test fun emptyAndCorruptFiles() {
        val f = tmp(); val gone = mutableListOf<String>()
        val s = TrashStore(f) { gone += it.id }; s.putEntry(e1); s.putEntry(e1.copy(id = "e3")); s.empty()
        assertEquals(2, gone.size); assertTrue(TrashStore(f).items.value.isEmpty())
        f.writeText("{not json"); assertTrue(TrashStore(f).items.value.isEmpty())
        f.writeText("""[{"id":"x","deletedAt":1}]"""); assertTrue("item without payload skipped", TrashStore(f).items.value.isEmpty())
    }

    // ================================================================== 星轨 v5.0
    @Test fun starStartsInTheSkyTinyAndSpinning() {
        val q = RingMotion.pose(RingAnim.STAR, 0f, 1f, false)
        assertTrue(q.scale <= 0.08f); assertEquals(1f, q.lift, 1e-4f); assertEquals(RingMotion.STAR_SPIN, q.rot, 1e-2f)
        assertTrue("more than a full turn around the finger", q.ang >= 360f)
    }

    @Test fun starAcceleratesWhileFalling() {
        val step = 0.05f
        val d = (0 until 14).map { k -> RingMotion.starTravel((k + 1) * step) - RingMotion.starTravel(k * step) }
        assertTrue("each step covers more ground", d.zipWithNext().all { (a, b) -> b > a })
        assertTrue(RingMotion.starSpeed(0.7f) > RingMotion.starSpeed(0.1f))
    }

    @Test fun starLandsWithOvershootGlowAndShimmerThenRestsAtFullSize() {
        val land = RingMotion.pose(RingAnim.STAR, RingMotion.STAR_LAND + 0.001f, 1f, false)
        assertTrue(land.scale > 1.1f)
        val mid = RingMotion.pose(RingAnim.STAR, RingMotion.STAR_LAND + 0.12f, 1f, false)
        assertTrue("burst", mid.fx2 > 0.3f); assertTrue("positional overshoot dips inside the slot", mid.rf < 1f)
        assertTrue("shimmer", RingMotion.pose(RingAnim.STAR, 0.93f, 1f, false).fx3 > 0.3f)
        for (i in 0 until 8) {
            val rest = RingMotion.pose(RingAnim.STAR, 1f, 1f, false, depth = RingMotion.starDepth(i))
            assertTrue("button $i rests full size", rest.isRest); assertEquals(0f, rest.fx2, 0f); assertEquals(0f, rest.fx3, 0f)
        }
    }

    @Test fun starDepthGivesParallaxButNeverChangesTheFinalPose() {
        val ds = (0 until 12).map { RingMotion.starDepth(it) }
        assertTrue(ds.all { it in 0.8f..1.2f }); assertTrue(ds.toSet().size > 6)
        val far = RingMotion.pose(RingAnim.STAR, 0.2f, 1f, false, depth = 0.8f); val near = RingMotion.pose(RingAnim.STAR, 0.2f, 1f, false, depth = 1.2f)
        assertTrue(far.scale < near.scale); assertTrue(far.lift > near.lift); assertTrue(far.alpha < near.alpha)
    }

    @Test fun starCloseSpinsBackUpIntoTheSky() {
        val gone = RingMotion.pose(RingAnim.STAR, 1f, 0f, true)
        assertTrue(gone.scale <= 0.08f); assertEquals(1f, gone.lift, 1e-4f); assertEquals(-RingMotion.STAR_SPIN, gone.rot, 1e-2f); assertEquals(0f, gone.alpha, 1e-4f)
        val lifts = (100 downTo 0).map { RingMotion.pose(RingAnim.STAR, 1f, it / 100f, true).lift }
        assertTrue("rises steadily", lifts.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun starSpeedScalesTheTimelineAndTheWatchdog() {
        val std = RingMotion.openMs(RingAnim.STAR, 4)
        assertEquals(std / 2, RingMotion.openMs(RingAnim.STAR, 4, 2f))
        assertEquals(std * 2, RingMotion.openMs(RingAnim.STAR, 4, 0.5f))
        assertEquals("clamped", RingMotion.openMs(RingAnim.STAR, 4, 2f), RingMotion.openMs(RingAnim.STAR, 4, 9f))
        assertTrue(RingMotion.settleDeadlineMs(RingAnim.STAR, 4, 1f, 0.5f) > RingMotion.openMs(RingAnim.STAR, 4, 0.5f))
        assertEquals("other styles ignore it", RingMotion.openMs(RingAnim.LOTUS, 4), RingMotion.openMs(RingAnim.LOTUS, 4, 2f))
    }

    // ================================================================== 碎纸机 maths + 全部按键 collection
    @Test fun shredderTimeline() {
        assertEquals(0f, ShredMath.feed(0f), 0f); assertEquals(1f, ShredMath.feed(0.85f), 1e-5f); assertEquals(1f, ShredMath.feed(1f), 0f)
        val f = (0..20).map { ShredMath.feed(it / 20f) }; assertTrue(f.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0f, ShredMath.mouthAlpha(0f), 0f); assertEquals(1f, ShredMath.mouthAlpha(0.5f), 0f); assertEquals(0f, ShredMath.mouthAlpha(1f), 0f)
        assertEquals(8, ShredMath.strips(50f)); assertEquals(22, ShredMath.strips(5000f))
        assertEquals("fresh strip is opaque", 1f, ShredMath.piece(3, 0, 12, 10f, 1000f, false).alpha, 0f)
        assertEquals("far below the slot it's gone", 0f, ShredMath.piece(3, 0, 12, 1300f, 1000f, false).alpha, 0f)
        val l = ShredMath.piece(0, 0, 12, 400f, 1000f, false).dx; val r = ShredMath.piece(11, 0, 12, 400f, 1000f, false).dx
        assertTrue("strips spread apart", l < 0f && r > 0f)
    }

    @Test fun masterPanelListsEveryActionOfTheRingFlattened() {
        val spec = MenuSpec(Rect.Zero, title = "t",
            radial = listOf(MenuAction("收藏"), MenuAction("倍速", children = listOf(MenuAction("1×"), MenuAction("2×")))),
            actions = listOf(listOf(MenuAction("收藏"), MenuAction("分享")), listOf(MenuAction("删除", destructive = true))))
        assertEquals(listOf("收藏", "倍速 · 1×", "倍速 · 2×", "分享", "删除"), panelActions(spec).map { it.title })
        assertNotNull(panelActions(spec).last().takeIf { it.destructive })
    }
}
