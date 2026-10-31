package com.moonlight.feed.ui.design

import androidx.compose.ui.graphics.drawscope.clipPath
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlight.feed.data.HoldConfig
import com.moonlight.feed.data.HoldContext
import com.moonlight.feed.data.HoldEvent
import com.moonlight.feed.data.HoldOutcome
import com.moonlight.feed.data.HoldTier
import com.moonlight.feed.data.TimedActions
import com.moonlight.feed.data.V5
import com.moonlight.feed.ui.theme.LocalMoonColors

/** A long-press waiting for the finger-up (see [MoonOverlayState.deferHold]). */
class PendingHold(val context: HoldContext, val ring: () -> MenuSpec, val onRelease: () -> Unit) { internal var fired = false }

/** 全部按键: [current] = the ring that was open (its title [currentTitle]), [page] = this screen, [global] = everywhere. */
data class MasterPanelSpec(val currentTitle: String?, val current: List<MenuAction>, val page: List<MenuAction>, val global: List<MenuAction>) {
    val sections: List<Pair<String, List<MenuAction>>> get() = listOfNotNull(
        current.takeIf { it.isNotEmpty() }?.let { (currentTitle?.let { t -> "当前内容 · $t" } ?: "当前内容") to it },
        page.takeIf { it.isNotEmpty() }?.let { "页面" to it },
        global.takeIf { it.isNotEmpty() }?.let { "全局" to it },
    )
}

/** Every action of a ring, flattened (parents with short child lists become "父 · 子" tiles), de-duplicated by title. */
fun panelActions(spec: MenuSpec): List<MenuAction> {
    val all = (spec.radial.orEmpty() + spec.quick + spec.actions.flatten())
    val out = ArrayList<MenuAction>(); val seen = HashSet<String>()
    for (a in all) {
        val kids = a.children
        if (kids != null) kids.forEach { k -> val t = "${a.title} · ${k.title}"; if (seen.add(t)) out += k.copy(title = t, icon = k.icon ?: a.icon) }
        else if (seen.add(a.title)) out += a
    }
    return out
}

/**
 * The action a timed hold may fire on [spec]: the assigned option's ring/sheet action, never one that is destructive,
 * disabled or a parent of sub-choices. null → nothing fires (the ring just stays up).
 */
fun timedAction(spec: MenuSpec?, ctx: HoldContext?, key: String?): MenuAction? {
    if (spec == null || ctx == null || key == null) return null
    if (spec.context != ctx) return null
    val opt = TimedActions.option(ctx, key) ?: return null
    val flat = spec.radial.orEmpty() + spec.quick + spec.actions.flatten() + (spec.radial.orEmpty() + spec.actions.flatten()).flatMap { it.children.orEmpty() }
    return flat.firstOrNull { it.title in opt.titles && !it.destructive && it.children == null && it.disabledReason == null }
}

/** Feeds the root pointer into [MoonOverlayState.hold] and acts on its events (works for Compose and View hosts). */
@Composable
internal fun HoldDriver(state: MoonOverlayState) {
    val view = LocalView.current
    val slop = with(LocalDensity.current) { 18.dp.toPx() }
    fun enabled() = V5.prefs.timedOn || V5.prefs.centerPanel
    fun refresh(now: Long) {
        val h = state.hold
        state.holdShowing = h.showing() && enabled()
        state.holdElapsedMs = h.elapsed(now); state.holdProgress = h.progress(now); state.holdTier = h.tier()
    }
    fun handle(e: HoldEvent) {
        when (e) {
            HoldEvent.Long, HoldEvent.Cancelled -> Unit
            HoldEvent.Center -> {
                Haptics.confirm(view)
                val cur = state.menu
                state.pending?.fired = true
                state.dismissMenu(); state.dismissSheet()
                state.openPanel(cur)
            }
            is HoldEvent.Timed -> {
                val p = state.pending
                if (e.deferred && p != null && !p.fired) { p.fired = true; state.showMenu(p.ring(), fromLongPress = true) }
                val spec = state.menu
                val a = timedAction(spec, e.context, e.fireKey)
                if (a != null) {
                    state.dismissMenu()
                    a.onClick()
                    Haptics.confirm(view)
                    val opt = e.context?.let { TimedActions.option(it, e.fireKey) }
                    val undo = if (opt?.reversible == true) (a.undo ?: a.onClick) else null
                    MoonToaster.show("定时长按 · ${a.title}", Icons.Outlined.Bolt, action = if (undo != null) "撤销" else null, onAction = undo)
                } else Haptics.tick(view)   // nothing assigned / 到时自动触发 off: the ring (now) up is the result
            }
        }
    }
    LaunchedEffect(state) {
        var wasDown = false
        snapshotFlow { state.fingerDown to state.finger }.collect { (down, f) ->
            val now = SystemClock.uptimeMillis()
            val h = state.hold
            val ph = h.phase
            if (down && !wasDown && ph != com.moonlight.feed.data.HoldMachine.Phase.PRESSED && ph != com.moonlight.feed.data.HoldMachine.Phase.LONG) {
                h.cfg = HoldConfig.of(V5.prefs, slop)
                val o = state.pressOrigin ?: f ?: Offset.Zero
                h.down(state.pressStartedAt ?: now, o.x, o.y, state.rootSize.width, state.rootSize.height)
            } else if (down && f != null) {
                h.move(f.x, f.y)?.let(::handle)
            } else if (!down && wasDown) {
                val out = h.up(now)
                state.pressStartedAt = null
                val p = state.pending; state.pending = null
                if (p != null && !p.fired && (out == HoldOutcome.LONG || out == HoldOutcome.CANCELLED || out == HoldOutcome.TAP)) p.onRelease()
            }
            wasDown = down
            refresh(now)
        }
    }
    LaunchedEffect(state, state.fingerDown) {
        if (!state.fingerDown || !enabled()) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            val now = SystemClock.uptimeMillis()
            state.hold.tick(now)?.let(::handle)
            refresh(now)
        }
    }
}

/** 定时长按 readout (“3.2s”) + countdown (ring fill for 定时, moon phase for 全部按键) around the finger. */
@Composable
internal fun BoxScope.HoldIndicator(state: MoonOverlayState) {
    val dbg = state.debugHold
    val showing = dbg != null || state.holdShowing
    if (!showing) return
    val tier = dbg?.second ?: state.holdTier
    val elapsed = dbg?.first ?: state.holdElapsedMs
    val target = if (tier == HoldTier.CENTER) 5000L else V5.prefs.timedMs
    val progress = if (dbg != null) ((elapsed - 500f) / (target - 500f)).coerceIn(0f, 1f) else state.holdProgress
    val p = state.finger ?: state.pressOrigin ?: return
    val density = LocalDensity.current
    val colors = LocalMoonColors.current
    val r = with(density) { 46.dp.toPx() }
    val accent = if (tier == HoldTier.CENTER) Color(0xFFCFDBFF) else Color(0xFFFFD27A)
    val moon = remember { androidx.compose.ui.graphics.Path() }
    if (V5.prefs.timedCountdown) Canvas(Modifier.fillMaxSize()) {
        val sw = 5.dp.toPx()
        if (tier == HoldTier.CENTER) {
            // moon phase: a crescent waxing to full as the 5 s run
            val mr = r * 0.62f
            drawCircle(Color.Black.copy(alpha = 0.28f), mr + sw, p)
            drawCircle(Color.White.copy(alpha = 0.16f), mr, p)
            val k = progress
            moon.reset(); moon.addOval(androidx.compose.ui.geometry.Rect(p, mr))
            clipPath(moon) {
                drawCircle(accent.copy(alpha = 0.92f), mr, p)
                // the shadow slides off to the right as the 5 s run (crescent → full moon)
                drawCircle(Color(0xFF1A2033).copy(alpha = 0.92f * (1f - k)), mr * 1.02f, Offset(p.x + mr * 2.1f * k, p.y))
            }
            drawCircle(Color.White.copy(alpha = 0.55f), mr + sw * 0.6f, p, style = Stroke(1.dp.toPx()))
        } else {
            drawCircle(Color.Black.copy(alpha = 0.22f), r, p, style = Stroke(sw + 3.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.30f), r, p, style = Stroke(sw))
            drawArc(accent, -90f, 360f * progress, false, Offset(p.x - r, p.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            // leading spark
            val ang = Math.toRadians((-90f + 360f * progress).toDouble())
            val sp = Offset(p.x + r * kotlin.math.cos(ang).toFloat(), p.y + r * kotlin.math.sin(ang).toFloat())
            drawCircle(Color.White, sw * 0.75f, sp)
            drawCircle(accent.copy(alpha = 0.35f), sw * 2.2f, sp)
        }
    }
    if (V5.prefs.timedShowTime) {
        val label = (if (tier == HoldTier.CENTER) "全部按键 · " else "") + String.format(java.util.Locale.US, "%.1fs", elapsed / 1000f)
        // beside the countdown, on the roomier side — the ring fans out above the finger, the hand is below it
        val gap = r + with(density) { 12.dp.toPx() }
        val toRight = p.x < state.rootSize.width / 2f || state.rootSize.width == 0f
        Text(
            label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier
                .layoutBeside(p.x, p.y, gap, toRight)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape).border(0.5.dp, accent.copy(alpha = 0.7f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Places the element vertically centred on [y], starting [gap] px to the right of [x] (or ending [gap] px left of it). */
internal fun Modifier.layoutBeside(x: Float, y: Float, gap: Float, toRight: Boolean): Modifier = this.then(Modifier.layout { m, c ->
    val pl = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(pl.width, pl.height) { pl.place((if (toRight) x + gap else x - gap - pl.width).toInt(), (y - pl.height / 2).toInt()) }
})

/** Places the element centred on (x, y) (root px). */
internal fun Modifier.layoutAt(x: Float, y: Float): Modifier = this.then(Modifier.layout { m, c ->
    val pl = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(pl.width, pl.height) { pl.place((x - pl.width / 2).toInt(), (y - pl.height / 2).toInt()) }
})

/** 全部按键 — every action for the current screen and context, plus globals, in a frosted grid. */
@Composable
internal fun MasterPanelLayer(spec: MasterPanelSpec, state: MoonOverlayState) {
    BackHandler { state.dismissPanel() }
    val colors = LocalMoonColors.current
    val inspect = androidx.compose.ui.platform.LocalInspectionMode.current
    val a = remember(spec) { Animatable(if (inspect || MenuStyle.debugProgress != null) 1f else 0f) }
    LaunchedEffect(spec) { a.animateTo(1f, spring(0.8f, 380f)) }
    val view = LocalView.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = (if (colors.dark) 0.45f else 0.25f) * a.value))
            .pointerInputTap { state.dismissPanel() },
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 18.dp)
                .graphicsLayer { alpha = a.value; scaleX = 0.92f + 0.08f * a.value; scaleY = scaleX }
                .glass(RoundedCornerShape(30.dp), colors, blurRadius = 30.dp).border(0.5.dp, Color.White.copy(0.25f), RoundedCornerShape(30.dp))
                .pointerInputTap { }
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Apps, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text("全部按键", color = colors.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("长按屏幕中心 5 秒", color = colors.textDim, fontSize = 11.sp)
            }
            spec.sections.forEach { (title, acts) ->
                Spacer(Modifier.height(16.dp))
                Text(title, color = colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                acts.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { act ->
                            PanelTile(act, Modifier.weight(1f)) {
                                if (act.disabledReason != null) { MoonToaster.show(act.disabledReason); return@PanelTile }
                                Haptics.tick(view); state.dismissPanel(); act.onClick()
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelTile(a: MenuAction, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalMoonColors.current
    val tint = when { a.destructive -> Color(0xFFFF6B6B); a.disabledReason != null -> colors.textDim; else -> colors.text }
    Column(
        modifier.height(78.dp).background(if (a.checked) colors.accent.copy(alpha = 0.18f) else colors.text.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .border(0.5.dp, Color.White.copy(alpha = if (colors.dark) 0.12f else 0.4f), RoundedCornerShape(18.dp))
            .moonPress(onClick, highlightShape = RoundedCornerShape(18.dp)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (a.icon != null) Icon(a.icon, null, tint = if (a.checked) colors.accent else tint, modifier = Modifier.size(22.dp))
        else Text(a.short ?: a.title.take(2), color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(a.title, color = tint, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

private fun Modifier.pointerInputTap(f: () -> Unit): Modifier =
    this.then(Modifier.pointerInputCompat { detectTapGestures { f() } })

private fun Modifier.pointerInputCompat(block: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit, block))
