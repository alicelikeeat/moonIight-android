package com.moonlight.feed.ui.social

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.VideoBudget
import com.moonlight.feed.data.social.InlinePlan
import com.moonlight.feed.data.social.VideoTile
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.MomentAutoplay
import com.moonlight.feed.data.social.VisibleCard
import com.moonlight.feed.ui.design.centerCrop
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.playable
import com.moonlight.feed.ui.feed.MEDIA_AUDIO
import java.io.File

/**
 * v5.0 — one inline window per playing video tile (muted loop, inside the post's media frame).
 *
 * Why v4 showed a static ▶: one shared player was bound by `LaunchedEffect(target, byId)` — not keyed on the player.
 * `rememberMomentPlayback(mode)` rebuilt the player whenever the autoplay setting changed (the settings flow starts at the
 * default "ALWAYS", then the stored value arrives), and the new player was never bound, so `currentAtt` stayed null and every
 * tile kept its poster. Profile pages opened from 消息 got no player at all, and only one card could ever play.
 * Now: a pool of players (cap [MomentAutoplay.MAX_INLINE], guarded by a [VideoBudget]) is driven by [MomentInlineEffect],
 * which re-applies the plan whenever the plan, the pool, or the lifecycle epoch changes.
 */
@Stable
class InlineSlot internal constructor(val attId: String, val player: ExoPlayer) {
    var firstFrame by mutableStateOf(false); internal set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN); internal set
    /** ⏸/▶ as shown (intent-based, see PlayToggle). */
    var playing by mutableStateOf(false); internal set
    var muted by mutableStateOf(true); internal set
    internal val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() { firstFrame = true }
        override fun onEvents(p: Player, events: Player.Events) { playing = com.moonlight.feed.data.PlayToggle.showsPlaying(p.playable()) }
        override fun onVideoSizeChanged(v: VideoSize) { videoSize = v }
    }
}

@Stable
class MomentPlayers(private val ctx: Context, val cap: Int = MomentAutoplay.MAX_INLINE) {
    /** The pooled player budget (v4 VideoBudget): a tile without a budget slot shows its poster frame. */
    val budget = VideoBudget(cap)
    val slots = androidx.compose.runtime.mutableStateMapOf<String, InlineSlot>()
    /** Tiles the user started (tap) — play even when 关闭 / not picked. */
    var manualOn by mutableStateOf(emptySet<String>()); private set
    /** Tiles the user paused (tap) — keep their window frozen; autoplay never restarts them. */
    var manualOff by mutableStateOf(emptySet<String>()); private set
    /** Bumped on lifecycle start / return from the full-screen player so the effect re-applies the plan. */
    var epoch by androidx.compose.runtime.mutableIntStateOf(0); private set
    internal var stopped = false
    private val idle = ArrayDeque<ExoPlayer>()
    private var released = false

    private fun unmetered(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Playable source; [manual] (a tap) skips the Wi‑Fi-only gate. null → stays a still poster. */
    fun uriFor(a: Attachment, manual: Boolean = false): Uri? {
        if (a.kind != AttachmentKind.VIDEO) return null
        val lib = a.libraryEntryId?.let { id -> MoonApp.instance.library.items.value.firstOrNull { it.id == id } }
        val local = (a.localPath ?: lib?.localPath)?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        if (local != null) return Uri.fromFile(local)
        val remote = (a.remoteUrl ?: lib?.remoteUrl)?.takeIf { it.startsWith("http") } ?: return null
        return if (manual || !com.moonlight.feed.data.V5.prefs.autoplayWifiOnly || unmetered()) Uri.parse(remote) else null
    }

    private fun obtain(): ExoPlayer = idle.removeFirstOrNull() ?: ExoPlayer.Builder(ctx.applicationContext).build().apply {
        volume = 0f; repeatMode = Player.REPEAT_MODE_ONE; setHandleAudioBecomingNoisy(true)
    }

    private fun drop(id: String) {
        val s = slots.remove(id) ?: return
        budget.release(id)
        s.player.removeListener(s.listener); s.player.stop(); s.player.clearMediaItems(); s.player.volume = 0f
        s.player.setAudioAttributes(MEDIA_AUDIO, false)
        if (idle.size < 2 && !released) idle.addLast(s.player) else s.player.release()   // keep ~2 warm for fast scrolling
    }

    /** Make the pool match [plan]: off-screen windows are released, new ones get a budget slot or stay a poster. */
    fun apply(plan: InlinePlan, atts: Map<String, Attachment>) {
        if (released) return
        val want = plan.all.toSet()
        slots.keys.filter { it !in want }.forEach(::drop)
        for (id in plan.all) {
            if (id in slots) continue
            val a = atts[id] ?: continue
            val uri = uriFor(a, manual = id in manualOn) ?: continue
            if (!budget.acquire(id)) continue
            val p = obtain()
            val s = InlineSlot(id, p)
            p.addListener(s.listener)
            p.setMediaItem(MediaItem.fromUri(uri)); p.prepare()
            slots[id] = s
        }
        for (id in plan.play) slots[id]?.let { if (!stopped) com.moonlight.feed.data.PlayToggle.resume(it.player.playable()) else it.player.pause() }
        for (id in plan.held) slots[id]?.player?.pause()
    }

    /** 点按: play ⇄ pause inline (PlayToggle). A tile without a window starts one (even when autoplay is 关闭). */
    fun tap(a: Attachment) {
        val s = slots[a.id]
        val nowPlaying = if (s != null) com.moonlight.feed.data.PlayToggle.toggle(s.player.playable()) else true
        if (nowPlaying) { manualOn = manualOn + a.id; manualOff = manualOff - a.id } else { manualOff = manualOff + a.id; manualOn = manualOn - a.id }
        s?.playing = nowPlaying
    }
    fun isPlaying(id: String) = slots[id]?.playing == true
    fun canPlay(a: Attachment) = uriFor(a, manual = true) != null

    /** One audible window at a time. */
    fun mute(id: String, m: Boolean) {
        for ((k, s) in slots) {
            val mm = if (k == id) m else true
            s.muted = mm; s.player.volume = if (mm) 0f else 1f
            s.player.setAudioAttributes(MEDIA_AUDIO, !mm)
        }
    }

    /** Opening the full-screen player / leaving the screen: freeze every window. */
    fun pauseAll() { slots.values.forEach { it.player.pause() } }
    fun onStop() { stopped = true; pauseAll() }
    fun onStart() { stopped = false; epoch++ }
    fun handOff() { pauseAll(); epoch++ }

    fun releaseAll() {
        released = true
        slots.keys.toList().forEach(::drop)
        idle.forEach { it.release() }; idle.clear()
    }
}

/** null in previews. Pauses on lifecycle stop, resumes the plan on start, releases on dispose. */
@Composable
fun rememberMomentPlayers(): MomentPlayers? {
    if (LocalInspectionMode.current) return null
    val ctx = LocalContext.current
    val pool = remember { MomentPlayers(ctx.applicationContext) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(pool, owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> pool.onStop()
                Lifecycle.Event.ON_START -> pool.onStart()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); pool.releaseAll() }
    }
    return pool
}

/** The video tiles of the posts currently laid out in [list] (pure mapping, used by the effect and tests). */
fun visibleVideoTiles(list: LazyListState, byId: Map<String, Moment>): List<VideoTile> {
    val info = list.layoutInfo
    val out = ArrayList<VideoTile>()
    for (it in info.visibleItemsInfo) {
        val m = byId[it.key as? String ?: continue] ?: continue
        val vis = MomentAutoplay.visibleFraction(VisibleCard(m.id, it.offset, it.size, true), info.viewportStartOffset, info.viewportEndOffset)
        m.attachments.take(9).forEachIndexed { i, a ->
            if (a.kind == AttachmentKind.VIDEO) out += VideoTile(a.id, m.id, m.authorId, m.autoplay, vis, it.offset, i)
        }
    }
    return out
}

/**
 * Drives [pool] from the list's layout under the 朋友圈自动播放 mode. [active] = this list is the top route of the visible
 * page and nothing covers it (offscreen → empty plan → every window released). Keyed on the pool (the v4 bug) and its epoch.
 */
@Composable
fun MomentInlineEffect(list: LazyListState, moments: List<Moment>, pool: MomentPlayers?, active: Boolean = true) {
    if (pool == null) return
    val byId = remember(moments) { moments.associateBy { it.id } }
    val atts = remember(moments) { moments.flatMap { it.attachments }.associateBy { it.id } }
    val prefs = com.moonlight.feed.data.V5.prefs
    val plan by remember(list, byId, active, prefs.momentsMode, prefs.autoplayFriends, pool) {
        derivedStateOf {
            if (!active) InlinePlan.EMPTY
            else MomentAutoplay.plan(prefs.momentsMode, visibleVideoTiles(list, byId).map { t -> t.copy(playable = atts[t.attId]?.let { pool.uriFor(it) } != null) },
                prefs.autoplayFriends, pool.manualOn, pool.manualOff, pool.cap)
        }
    }
    LaunchedEffect(pool, plan, pool.epoch) { pool.apply(plan, atts) }
    DisposableEffect(pool) { onDispose { pool.apply(InlinePlan.EMPTY, emptyMap()) } }
}

/** Live layer over a tile with a window: the video + a small mute toggle (+ ▶ when paused). */
@Composable
fun BoxScope.MomentLiveLayer(pool: MomentPlayers, slot: InlineSlot) {
    val a by animateFloatAsState(if (slot.firstFrame) 1f else 0f, tween(240), label = "live")
    val size = slot.videoSize
    AndroidView(
        factory = { c -> TextureView(c).also { slot.player.setVideoTextureView(it) } },
        update = { tv -> centerCrop(tv, size) },
        onRelease = { tv -> slot.player.clearVideoTextureView(tv) },
        modifier = Modifier.fillMaxSize().alpha(a),
    )
    InlineBadges(paused = !slot.playing, muted = slot.muted) { pool.mute(slot.attId, !slot.muted) }
}

/** ▶ (paused) + mute badge — shared by the real layer and the screenshot stand-in. */
@Composable
fun BoxScope.InlineBadges(paused: Boolean, muted: Boolean, onMute: () -> Unit) {
    if (paused) Box(
        Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.32f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.PlayArrow, "已暂停", tint = Color.White, modifier = Modifier.size(22.dp)) }
    Box(
        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(0.4f))
            .moonPress(onMute, highlightShape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, if (muted) "打开声音" else "静音",
            tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/**
 * How a post's tiles get their live layer. Real app: [MomentPlayers]. Screenshots: a stand-in that marks which tiles
 * would be playing (no ExoPlayer under Paparazzi).
 */
fun interface InlineLive {
    fun layer(a: Attachment): (@Composable BoxScope.() -> Unit)?
    companion object {
        fun of(pool: MomentPlayers?): InlineLive? = pool?.let { p -> InlineLive { a -> p.slots[a.id]?.let { s -> { MomentLiveLayer(p, s) } } } }
    }
}

/** Actions of the 朋友圈 video ring (wired in SocialScreens). */
class MomentVideoHandlers(
    val toggle: () -> Unit = {},
    val open: () -> Unit = {},
    val sound: () -> Unit = {},
    val save: () -> Unit = {},
    val download: () -> Unit = {},
)

/**
 * Long-press on a video tile in a 朋友圈 card → radial ring (respects 点击/拖动 + 菜单背景 like every ring).
 * Media long-press wins over the card's post menu; text / empty card area still opens the post menu.
 */
fun momentVideoMenuSpec(
    anchor: androidx.compose.ui.geometry.Rect,
    title: String?,
    playing: Boolean,
    muted: Boolean,
    canPlayInline: Boolean,
    canOpen: Boolean,
    canSave: Boolean,
    h: MomentVideoHandlers,
    /** v4.4: direct-file video → 保存 fans out to 保存到相册 / 下载 on the secondary arc. */
    download: com.moonlight.feed.ui.design.MenuAction? = null,
): com.moonlight.feed.ui.design.MenuSpec {
    val ring = buildList {
        if (canPlayInline) add(com.moonlight.feed.ui.design.MenuAction(if (playing) "暂停" else "播放",
            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, onClick = h.toggle))
        if (canOpen) add(com.moonlight.feed.ui.design.MenuAction("进入播放页面", Icons.Outlined.Fullscreen, onClick = h.open))
        if (canPlayInline) add(com.moonlight.feed.ui.design.MenuAction(if (muted) "打开声音" else "静音",
            if (muted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff, onClick = h.sound))
        when {
            download != null -> add(com.moonlight.feed.ui.design.MenuAction("保存", Icons.Outlined.Download, children = listOfNotNull(
                if (canSave) com.moonlight.feed.ui.design.MenuAction("保存到相册", Icons.Outlined.Download, onClick = h.save) else null, download)))
            canSave -> add(com.moonlight.feed.ui.design.MenuAction("保存", Icons.Outlined.Download, onClick = h.save))
        }
    }
    return com.moonlight.feed.ui.design.MenuSpec(anchor = anchor, title = title, actions = emptyList(), radial = ring,
        context = com.moonlight.feed.data.HoldContext.MOMENT_VIDEO) // no overflow → no 更多
}
