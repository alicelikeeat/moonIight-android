package com.moonlight.feed.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlight.feed.data.RingAnim
import com.moonlight.feed.data.RingMotion
import com.moonlight.feed.ui.theme.LocalMoonColors
import androidx.compose.ui.draw.blur
import com.moonlight.feed.ui.theme.MediaGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** v4.0: a long-press ring carries at most this many actions; everything else lives in the ··· sheet. */
const val RING_MAX = 4
/** Sub-choices up to this count expand as a secondary arc; longer lists open as a list. */
const val SUB_ARC_MAX = 6

/** The ring: ≤ [RING_MAX] actions, never destructive (删除 only ever sits in the sheet's 危险 section). */
fun MenuSpec.ring(): List<MenuAction> =
    (radial ?: (quick + actions.flatten())).filter { !it.destructive }.distinctBy { it.title }.take(RING_MAX)

/**
 * The ··· sheet: every action of the spec that is not on the ring, grouped (spec.actions groups with
 * [MenuSpec.groupTitles]; leftovers under 其他), destructive ones collected in a separate 危险 section, last.
 */
fun MenuSpec.sheetGroups(ring: List<MenuAction> = ring()): List<Pair<String?, List<MenuAction>>> {
    val onRing = ring.map { it.title }.toSet()
    val seen = HashSet<String>()
    val out = ArrayList<Pair<String?, List<MenuAction>>>()
    val danger = ArrayList<MenuAction>()
    fun place(title: String?, list: List<MenuAction>) {
        val g = list.filter { a -> a.title !in onRing && seen.add(a.title) }
        danger += g.filter { it.destructive }
        g.filter { !it.destructive }.takeIf { it.isNotEmpty() }?.let { out += title to it }
    }
    actions.forEachIndexed { i, g -> place(groupTitles.getOrNull(i), g) }
    place(if (out.isEmpty()) null else "其他", quick + (radial ?: emptyList()))
    if (danger.isNotEmpty()) out += "危险" to danger
    return out
}

internal fun MenuSpec.ringItems(): Pair<List<MenuAction>, List<List<MenuAction>>> {
    val r = ring()
    return r to sheetGroups(r).map { it.second }
}

/**
 * Pure ring geometry (unit-free, px). Returns item centers and the ring centre actually used.
 * The arc faces the open screen (towards the centre); it is rotated, and only if needed shifted, so nothing leaves [bounds].
 */
object RadialGeometry {
    data class Result(val center: Offset, val angles: List<Float>, val radius: Float, val dir: Float) {
        fun pos(i: Int, r: Float = radius, extraDeg: Float = 0f): Offset {
            val a = Math.toRadians((angles[i] + extraDeg).toDouble())
            return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        }
    }

    /** v4.0: ≤ 4 items share a ~110° arc (2 items: 56° apart); larger counts keep the old spacing. */
    fun stepDeg(n: Int) = when { n <= 1 -> 0f; n == 2 -> 56f; n <= 4 -> 110f / (n - 1); n <= 5 -> 44f; n <= 6 -> 40f; n <= 7 -> 37f; else -> 35f }

    /** Secondary arc for sub-choices: [k] chips centred on the parent's angle, [chipGap] px apart at radius [r]. */
    fun subArc(parentDeg: Float, k: Int, r: Float, chipGap: Float): List<Float> {
        if (k <= 0) return emptyList()
        val step = Math.toDegrees((chipGap / r).toDouble()).toFloat()
        return List(k) { j -> parentDeg + (j - (k - 1) / 2f) * step }
    }

    fun layout(origin: Offset, n: Int, bounds: Rect, radius: Float, itemR: Float): Result {
        val step = stepDeg(n)
        val span = step * (n - 1)
        fun anglesFor(dir: Float) = List(n) { i -> dir - span / 2 + step * i }
        fun overflow(c: Offset, dir: Float): Float = anglesFor(dir).sumOf { a ->
            val r = Math.toRadians(a.toDouble())
            val x = c.x + radius * cos(r).toFloat(); val y = c.y + radius * sin(r).toFloat()
            (maxOf(0f, bounds.left + itemR - x) + maxOf(0f, x - (bounds.right - itemR)) +
                maxOf(0f, bounds.top + itemR - y) + maxOf(0f, y - (bounds.bottom - itemR))).toDouble()
        }.toFloat()
        // Preferred direction: away from the nearest edges (towards screen centre); straight up when centred.
        val sc = bounds.center
        val dx = sc.x - origin.x; val dy = sc.y - origin.y
        val preferred = if (hypot(dx, dy) < bounds.width * 0.12f) -90f else Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        var best = preferred; var bestO = overflow(origin, preferred)
        if (bestO > 0f) {
            for (k in 1..36) for (sgn in intArrayOf(1, -1)) {
                val d = preferred + sgn * k * 5f
                val o = overflow(origin, d)
                if (o < bestO - 0.5f) { bestO = o; best = d }
            }
        }
        var c = origin
        if (bestO > 0.5f) {
            // Corners with a wide arc: nudge the centre inward just enough.
            val m = radius + itemR
            c = Offset(origin.x.coerceIn(bounds.left + m, bounds.right - m), origin.y.coerceIn(bounds.top + m, bounds.bottom - m))
            best = if (hypot(sc.x - c.x, sc.y - c.y) < 1f) -90f else Math.toDegrees(atan2((sc.y - c.y).toDouble(), (sc.x - c.x).toDouble())).toFloat()
        }
        return Result(c, anglesFor(best), radius, best)
    }

    /** Index of the item under [p], with a forgiving hit radius. */
    fun hit(r: Result, p: Offset, hitR: Float): Int? {
        var bi: Int? = null; var bd = Float.MAX_VALUE
        r.angles.indices.forEach { i ->
            val d = (r.pos(i) - p).getDistance()
            if (d < hitR && d < bd) { bd = d; bi = i }
        }
        return bi
    }
}

@Composable
internal fun RadialLayer(spec: MenuSpec, state: MoonOverlayState) {
    BackHandler { state.dismissMenu() }
    val view = LocalView.current
    val density = LocalDensity.current
    // one rule (v4.3): over media (feed / full-screen player) → MediaGlass, everywhere else → the theme
    val colors = if (spec.forceDark) MediaGlass else LocalMoonColors.current
    // 按键背景: each ring part may carry its own picture / video (null → theme rule above)
    val skBtn = rememberButtonSkin("ring.button")
    val skMore = rememberButtonSkin("ring.more")
    val skTitle = rememberButtonSkin("ring.title")
    val skSub = rememberButtonSkin("ring.sub")
    val (ring, rest) = remember(spec) { spec.ringItems() }
    val more = rest.isNotEmpty()
    val items = ring
    val n = items.size
    val closing = state.menu == null
    val mp = state.menuProgress.value

    // 长按动画: one style per opening (随机 picks among the enabled ones; system animations off → plain fade)
    val inspect = androidx.compose.ui.platform.LocalInspectionMode.current
    val actx = androidx.compose.ui.platform.LocalContext.current
    val style = remember(spec) { spec.anim ?: com.moonlight.feed.data.RingAnimPick.pick(MenuStyle.anim, MenuStyle.animSet, animatorScale(actx)) }
    androidx.compose.runtime.SideEffect { state.ringStyle = style }
    val settled = inspect || state.menuProgress.value >= 0.999f
    val anims = remember(spec) { List(n) { Animatable(if (settled) 1f else 0f) } }
    val scene = remember(spec) { Animatable(if (settled) 1f else 0f) }   // 月光: darkness, then the moon
    val scaleNow = remember(spec) { animatorScale(actx) }
    val starSpeed = MenuStyle.starSpeed
    LaunchedEffect(spec) { driveRingClocks(style, anims, scene, scaleNow, starSpeed) { Haptics.light(view) } }
    val dbg = MenuStyle.debugProgress
    val sceneP = dbg ?: scene.value
    // 月光 overrides 菜单背景: its own ~95% black night instead of the dim / blur
    val moonDark = when { style != RingAnim.MOON -> 0f; closing -> mp.coerceIn(0f, 1f); else -> RingMotion.moonDark(sceneP) }
    val moonGlow = when { style != RingAnim.MOON -> 0f; closing -> (mp * mp).coerceIn(0f, 1f); else -> RingMotion.moonGlow(sceneP) }
    val fxKit = rememberRingFx(colors.dark)
    // 拖动 mode only: the opening finger hovers items (highlight + light haptic); release on one selects it.
    var hovered by remember(spec) { mutableStateOf<Int?>(null) }
    var pressMode by remember(spec) { mutableStateOf(state.fingerDown && MenuStyle.select == com.moonlight.feed.data.MenuSelect.DRAG) }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (style == RingAnim.MOON) 0f else (if (colors.dark) 0.30f else 0.14f) * MenuStyle.backdrop.dim * mp.coerceIn(0f, 1f)))
            .pointerInput(spec) { detectTapGestures { state.tapOutside() } }
    ) {
        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
        val ins = WindowInsets.systemBars
        val itemD = 56.dp; val itemR = with(density) { (itemD / 2).toPx() }
        val margin = with(density) { 10.dp.toPx() }
        val labelPad = with(density) { 22.dp.toPx() }
        val bounds = Rect(margin, ins.getTop(density) + margin, wPx - margin, hPx - ins.getBottom(density) - margin - labelPad)
        // v4.0: fewer items, more air — 4 on a ~110° arc at 118dp leaves ~18dp between 56dp buttons
        val radius = with(density) { (if (n <= 2) 100.dp else if (n <= 4) 118.dp else if (n <= 6) 120.dp else 128.dp).toPx() }
        val origin0 = spec.origin ?: state.pressOrigin ?: (if (spec.anchor == Rect.Zero) Offset(wPx / 2, hPx / 2) else spec.anchor.center)
        val origin = if (spec.centerHorizontally) origin0.copy(x = wPx / 2) else origin0
        val geo = remember(spec, wPx, hPx) { RadialGeometry.layout(origin, n, bounds, radius, itemR + labelPad) }

        // 点击 (default): long-press only opens the ring; releasing selects nothing; a separate tap selects.
        fun feedback(r: RingTap) = when (r) {
            RingTap.SUBMENU, RingTap.SHEET -> Haptics.tick(view)
            RingTap.SELECTED -> Haptics.confirm(view)
            RingTap.IGNORED -> {}
        }
        fun trigger(i: Int) = feedback(state.tapRingItem(spec, items, rest, i, Rect(geo.pos(i), 1f)))

        // 拖动: follow the finger that opened the ring; release on an item triggers it (v3.1 behaviour).
        val hitR = with(density) { 44.dp.toPx() }
        LaunchedEffect(spec) {
            if (!pressMode) return@LaunchedEffect
            snapshotFlow { state.fingerDown to state.finger }.collect { (down, p) ->
                if (!pressMode) return@collect
                if (down && p != null) {
                    val h = RadialGeometry.hit(geo, p, hitR)
                    if (h != hovered) { hovered = h; if (h != null) Haptics.light(view) }
                } else if (!down) {
                    pressMode = false
                    val h = hovered; hovered = null
                    feedback(state.releaseRingItem(spec, items, rest, h, h?.let { Rect(geo.pos(it), 1f) } ?: Rect.Zero))
                }
            }
        }

        val cp = geo.center
        val sub = state.subIndex
        // ---- per-button pose for this frame. Values the buttons use are captured as plain locals below (never read from
        // shared arrays inside graphicsLayer/layout blocks: with strong skipping those lambdas are reused and would keep stale
        // values — the v4.1 "坠落 buttons stay tiny" bug). The arrays only feed the effect Canvas, keyed by [frame].
        val poses = remember(spec) { List(n) { RingMotion.Pose() } }
        val os = remember(spec) { FloatArray(n) }; val cls = remember(spec) { FloatArray(n) }
        val xs = remember(spec) { FloatArray(n) }; val ys = remember(spec) { FloatArray(n) }
        val ss = remember(spec) { FloatArray(n) }; val al = remember(spec) { FloatArray(n) }
        val ex = remember(spec) { FloatArray(n) }; val ex2 = remember(spec) { FloatArray(n) }
        // 星轨 v5.0: the far start is above the top edge of the screen (the sky), whatever the ring's height
        val liftPx = if (style == RingAnim.STAR) (cp.y - bounds.top) + itemR * 0.8f else minOf(with(density) { 150.dp.toPx() }, (cp.y - bounds.top) * 0.4f)
        val rot = remember(spec) { FloatArray(n) }; val ex3 = remember(spec) { FloatArray(n) }
        val sinkPx = with(density) { 18.dp.toPx() }
        fun place(i: Int, q: RingMotion.Pose): Offset = geo.pos(i, geo.radius * q.rf, q.ang) + Offset(0f, -liftPx * q.lift + sinkPx * q.sink)
        var frame = mp + (if (closing) 7f else 0f)
        for (i in 0 until n) {
            // screenshots freeze the whole opening at [dbg]; 星轨 keeps its launch stagger so the frame shows the real formation
            val o = when {
                dbg == null -> anims[i].value
                style == RingAnim.STAR && dbg < 1f -> ((dbg * RingMotion.openMs(style, n) - i * RingMotion.starStaggerMs(n)) / RingMotion.STAR_FLIGHT_MS).coerceIn(0f, 1f)
                style == RingAnim.STAR && closing -> 1f
                else -> dbg
            }
            val cl = when {
                !closing -> 1f
                style == RingAnim.SPIN -> ((mp - (n - 1 - i) * 0.035f) / (1f - (n - 1) * 0.035f)).coerceIn(0f, 1f)   // last item first
                else -> mp.coerceIn(0f, 1f)
            }
            val q = RingMotion.pose(style, o, cl, closing, poses[i], if (style == RingAnim.STAR) RingMotion.starDepth(i) else 1f)
            val p = place(i, q)
            os[i] = o; cls[i] = cl; xs[i] = p.x; ys[i] = p.y; ss[i] = q.scale; al[i] = q.alpha; ex[i] = q.fx; ex2[i] = q.fx2
            rot[i] = q.rot; ex3[i] = q.fx3
            frame += (i + 1) * (o * 3.1f + cl * 1.7f) + p.x * 1e-3f + p.y * 1e-4f
        }
        // ---- style layer under the buttons (petals / night + moonbeams / comet trails)
        if (style == RingAnim.LOTUS || style == RingAnim.STAR || style == RingAnim.MOON) {
            val scratch = remember(spec) { RingMotion.Pose() }
            val moonC = Offset(wPx * 0.80f, bounds.top + with(density) { 92.dp.toPx() })
            val moonR = with(density) { 21.dp.toPx() }
            val f = frame   // read in draw so the layer redraws every animation frame
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                if (f < -1f) return@Canvas
                when (style) {
                    RingAnim.MOON -> drawMoonNight(fxKit, moonC, moonR, moonDark, moonGlow, n, xs, ys, ex, itemR)
                    RingAnim.LOTUS -> for (i in 0 until n) drawLotus(fxKit, xs[i], ys[i], itemR, ex[i], ex2[i])
                    RingAnim.STAR -> {
                        drawStarSky(fxKit, size.width, size.height, n, os, cls, closing, bounds.top)
                        for (i in 0 until n) drawStarTrail(fxKit, i, os[i], cls[i], closing, itemR, ex[i], ex2[i], al[i], xs[i], ys[i], ss[i], scratch, RingMotion.starDepth(i)) { q -> place(i, q) }
                        for (i in 0 until n) drawStarLanding(fxKit, i, itemR, ex2[i], ex3[i], al[i], xs[i], ys[i])
                    }
                    else -> {}
                }
            }
        }

        // centre: ··· (all other actions, grouped, in a frosted sheet) — or a small puck when there's nothing more
        val puck = if (more) 40.dp else 30.dp
        val puckHalf = with(density) { (puck / 2).toPx() }
        // title / hovered label (拖动): a pill in a clear band (top, or bottom when the ring sits high)
        val label = hovered?.let { items[it].title } ?: spec.title
        val ringTop = cp.y - radius - itemR - labelPad
        val topBand = bounds.top + with(density) { 28.dp.toPx() }
        val ly = if (ringTop > topBand + with(density) { 40.dp.toPx() }) topBand else (cp.y + radius + itemR + labelPad + with(density) { 36.dp.toPx() }).coerceAtMost(bounds.bottom - with(density) { 24.dp.toPx() })

        Box(
            Modifier.offset { IntOffset((cp.x - puckHalf).toInt(), (cp.y - puckHalf).toInt()) }
                .size(puck).graphicsLayer { alpha = mp.coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * mp; scaleY = scaleX }
                .then(if (more) Modifier.moonPress({ if (!closing && state.openSheet(spec) == RingTap.SHEET) Haptics.tick(view) }, highlightShape = CircleShape, hapticOnClick = false) else Modifier)
                .skinOr(skMore, CircleShape) { glass(CircleShape, colors, blurRadius = 16.dp).border(0.5.dp, Color.White.copy(0.35f), CircleShape) },
            contentAlignment = Alignment.Center,
        ) {
            if (more) Icon(Icons.Outlined.MoreHoriz, "更多操作", tint = skMore?.ink ?: colors.text, modifier = Modifier.size(20.dp))
            else Box(Modifier.size(8.dp).background(skMore?.ink ?: colors.accent, CircleShape))
        }
        if (label != null) {
            val lx = wPx / 2
            CompositionLocalProvider(LocalMoonColors provides colors) {
                Text(
                    label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (hovered != null && items[hovered!!].destructive) Color(0xFFFF6B6B) else skTitle?.ink ?: colors.text,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .layout { m, c -> val pl = m.measure(c.copy(minWidth = 0, maxWidth = (wPx * 0.72f).toInt())); layout(pl.width, pl.height) { pl.place((lx - pl.width / 2).toInt(), (ly - pl.height / 2).toInt()) } }
                        .graphicsLayer { alpha = mp.coerceIn(0f, 1f) }
                        .skinOr(skTitle, CircleShape) { glass(CircleShape, colors, blurRadius = 18.dp).border(0.5.dp, Color.White.copy(0.18f), CircleShape) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        items.forEachIndexed { i, a ->
            // plain values captured by the lambdas below → a changed pose always means a new lambda (never stale)
            val p = Offset(xs[i], ys[i])
            val t = al[i]
            val sc0 = ss[i]
            val r0 = rot[i]
            val labelA = poses[i].label
            val bloomExtra = if (style == RingAnim.LOTUS) itemR * 0.62f * ex[i].coerceIn(0f, 1f) else 0f
            val lit = if (style == RingAnim.MOON) ex[i] else 0f
            val bloom = if (style == RingAnim.LOTUS) RingMotion.smooth(0.6f, 1f, ex[i]) else 0f
            val hov = hovered == i
            val hs by animateFloatAsState(if (hov) 1.24f else 1f, spring(0.55f, 600f), label = "hov")
            CompositionLocalProvider(LocalMoonColors provides (if (style == RingAnim.MOON) MediaGlass else colors)) {
                // label sits radially outside the button so neighbours never collide
                val gap = with(density) { 5.dp.toPx() }
                val dx0 = p.x - cp.x; val dy0 = p.y - cp.y
                val dl = kotlin.math.sqrt(dx0 * dx0 + dy0 * dy0).coerceAtLeast(1f)
                val ux = dx0 / dl; val uy = dy0 / dl
                val labelStyle = when {
                    // 月光: labels glow like the buttons
                    style == RingAnim.MOON -> androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xFFCFDBFF).copy(alpha = 0.95f * lit), Offset.Zero, 18f))
                    // 透明 / 轻度: nothing dims what's behind, so labels carry their own soft shadow
                    MenuStyle.backdrop.labelShadow -> androidx.compose.ui.text.TextStyle(shadow = labelShadow(colors.dark))
                    else -> androidx.compose.ui.text.TextStyle.Default
                }
                Text(
                    a.title, maxLines = 1, fontSize = 11.sp, fontWeight = if (style == RingAnim.MOON) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center,
                    style = labelStyle,
                    color = when { a.destructive -> Color(0xFFFF6B6B); style == RingAnim.MOON -> Color(0xFFF4F7FF); else -> colors.text }.copy(alpha = if (style == RingAnim.MOON) 0.35f + 0.65f * lit else 0.9f),
                    modifier = Modifier
                        .layout { m, c -> val pl = m.measure(c.copy(minWidth = 0, maxWidth = (radius).toInt())); layout(pl.width, pl.height) {
                            // push the label's box out along the ray until its near edge clears the button
                            val off = itemR * hs * sc0.coerceAtLeast(0.6f) + gap + bloomExtra + kotlin.math.abs(ux) * pl.width / 2f + kotlin.math.abs(uy) * pl.height / 2f
                            val lx = p.x + ux * off; val ly = p.y + uy * off
                            val x = (lx - pl.width / 2).coerceIn(bounds.left, bounds.right - pl.width)
                            pl.place(x.toInt(), (ly - pl.height / 2).toInt()) } }
                        .graphicsLayer { alpha = labelA.coerceIn(0f, 1f) * (if ((pressMode && !hov && hovered != null) || (sub != null && sub != i)) 0.35f else 1f) },
                )
                RadialItem(
                    a, hov,
                    Modifier
                        .layout { m, c -> val pl = m.measure(c.copy(minWidth = 0, minHeight = 0)); layout(pl.width, pl.height) { pl.place((p.x - pl.width / 2).toInt(), (p.y - pl.height / 2).toInt()) } }
                        .graphicsLayer {
                            val sc = sc0 * hs
                            scaleX = sc; scaleY = sc; rotationZ = r0
                            alpha = t.coerceIn(0f, 1f) * (if (sub != null && sub != i) 0.45f else 1f)
                        },
                    lit = lit, bloom = bloom, skin = skBtn,
                ) { if (!closing) trigger(i) }
            }
        }

        // sub-choices (倍速, 停留时间, 分享·保存 …) expand as a small secondary arc beyond their parent
        val parent = sub?.let { items.getOrNull(it) }
        val kids = parent?.children.orEmpty()
        if (parent != null && kids.isNotEmpty()) {
            val chipD = 46.dp
            val chipR = with(density) { (chipD / 2).toPx() }
            val r2 = geo.radius + with(density) { 74.dp.toPx() }
            val angles = RadialGeometry.subArc(geo.angles[sub!!], kids.size, r2, with(density) { 58.dp.toPx() })
            kids.forEachIndexed { j, k ->
                val a0 = Math.toRadians(angles[j].toDouble())
                val raw = Offset(cp.x + r2 * cos(a0).toFloat(), cp.y + r2 * sin(a0).toFloat())
                val q = Offset(raw.x.coerceIn(bounds.left + chipR, bounds.right - chipR), raw.y.coerceIn(bounds.top + chipR, bounds.bottom - chipR))
                val sa = remember(spec, sub, j) { Animatable(if (inspect || MenuStyle.debugProgress != null) 1f else 0f) }
                LaunchedEffect(spec, sub, j) { delay(j * 22L); sa.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 520f)) }
                CompositionLocalProvider(LocalMoonColors provides colors) {
                    SubChip(k, skSub, Modifier
                        .layout { m, c -> val pl = m.measure(c.copy(minWidth = 0, minHeight = 0)); layout(pl.width, pl.height) {
                            val pp = geo.pos(sub) + (q - geo.pos(sub)) * sa.value
                            pl.place((pp.x - pl.width / 2).toInt(), (pp.y - pl.height / 2).toInt()) } }
                        .graphicsLayer { alpha = sa.value.coerceIn(0f, 1f); scaleX = 0.5f + 0.5f * sa.value; scaleY = scaleX }
                    ) { if (!closing && state.tapSubItem(k) == RingTap.SELECTED) Haptics.confirm(view) }
                }
            }
        }
    }
}

/** A sub-choice on the secondary arc: small glass disc with a short label (checked = current value). */
@Composable
private fun SubChip(a: MenuAction, skin: SkinPaint?, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    val fg = if (a.checked) c.accent else skin?.ink ?: c.text
    Box(
        modifier.size(46.dp)
            .moonPress(onClick, pressedScale = 0.88f, highlightShape = CircleShape, hapticOnClick = false)
            .skinOr(skin, CircleShape) { glass(CircleShape, c, elevation = 8.dp, blurRadius = 18.dp, tint = if (a.checked) c.accent.copy(alpha = 0.30f) else null) }
            .border(if (a.checked) 1.dp else 0.5.dp, if (a.checked) c.accent else Color.White.copy(if (c.dark) 0.35f else 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        a.icon?.takeIf { a.short == null }?.let { Icon(it, a.title, tint = fg, modifier = Modifier.size(20.dp)) }
            ?: Text(a.short ?: a.title.take(3), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun RadialItem(a: MenuAction, hovered: Boolean, modifier: Modifier, lit: Float = 0f, bloom: Float = 0f, skin: SkinPaint? = null, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    val red = Color(0xFFFF5A5F)
    if (skin != null) {
        // 按键背景: this button's own crop of its picture / video + scrim, readable ink (animations unchanged: same layer)
        Box(
            modifier.size(56.dp)
                .moonPress(onClick, pressedScale = 0.88f, highlightShape = CircleShape, hapticOnClick = false)
                .skinFill(skin, CircleShape, edge = !a.checked)
                .then(when {
                    a.checked -> Modifier.background(c.accent.copy(alpha = 0.30f), CircleShape).border(1.dp, c.accent, CircleShape)
                    hovered -> Modifier.background(Color.White.copy(alpha = 0.18f), CircleShape)
                    lit > 0f -> Modifier.background(Color(0xFFE6ECFF).copy(alpha = 0.22f * lit), CircleShape)
                    else -> Modifier
                }),
            contentAlignment = Alignment.Center,
        ) {
            a.icon?.let { Icon(it, a.title, tint = if (a.destructive) red else skin.ink, modifier = Modifier.size(22.dp)) }
                ?: Text(a.title.take(2), color = skin.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    Box(
        modifier.size(56.dp)
            .moonPress(onClick, pressedScale = 0.88f, highlightShape = CircleShape, hapticOnClick = false)  // haptic comes from the outcome
            .glass(CircleShape, c, elevation = if (hovered) 18.dp else 10.dp, blurRadius = 22.dp,
                tint = when {
                    lit > 0f -> Color(0xFFE6ECFF).copy(alpha = 0.10f + 0.70f * lit)          // 月光: a moonlit silver disc
                    bloom > 0f && !a.checked && !hovered && !a.destructive -> Color(0xFFF3A9C4).copy(alpha = 0.26f * bloom)   // 荷花: the heart of the flower
                    a.destructive -> red.copy(alpha = 0.22f); a.checked -> c.accent.copy(alpha = 0.24f); hovered -> Color.White.copy(alpha = if (c.dark) 0.20f else 0.55f); else -> null })
            .border(if (lit > 0f) 1.dp else 0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(if (lit > 0f) 0.45f + 0.55f * lit else if (c.dark) 0.45f else 0.85f), Color.White.copy(0.04f + 0.2f * lit))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (lit > 0f) androidx.compose.ui.graphics.lerp(c.text, Color(0xFF1A2340), (lit * 0.92f).coerceIn(0f, 1f)) else c.text
        a.icon?.let { Icon(it, a.title, tint = when { a.destructive -> red; a.checked -> c.accent; else -> ink }, modifier = Modifier.size(22.dp)) }
            ?: Text(a.title.take(2), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}


/** System 动画时长缩放 (0 = animations off → reduce motion). */
fun animatorScale(ctx: android.content.Context): Float =
    runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)

/** Menu appearance driven by settings (菜单背景, 视频显示); read by every activity. */
object MenuStyle {
    var backdrop by androidx.compose.runtime.mutableStateOf(com.moonlight.feed.data.MenuBackdrop.BLUR)
    var videoDisplay by androidx.compose.runtime.mutableStateOf(com.moonlight.feed.data.VideoDisplay.SMART)
    var select by androidx.compose.runtime.mutableStateOf(com.moonlight.feed.data.MenuSelect.TAP)
    /** 长按动画 setting (style name or RANDOM) and the styles 随机 may pick. */
    var anim by androidx.compose.runtime.mutableStateOf("SPIN")
    /** Tests only: freeze every ring animation at this progress (mid-frame screenshots). */
    var debugProgress by androidx.compose.runtime.mutableStateOf<Float?>(null)
    var animSet by androidx.compose.runtime.mutableStateOf(com.moonlight.feed.data.RingAnim.CHOICES.toSet())
    /** v5.0 星轨速度 (0.5…2×); the settings slider writes it live for the 试一试 preview. */
    var starSpeed by androidx.compose.runtime.mutableFloatStateOf(1f)
}

/** Soft shadow so light text reads on any content (dark shadow under light text, light under dark). */
fun labelShadow(lightText: Boolean) = androidx.compose.ui.graphics.Shadow(
    color = if (lightText) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f),
    offset = Offset(0f, 1f), blurRadius = 8f,
)
