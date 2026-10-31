package com.moonlight.feed.ui.design

import androidx.compose.ui.input.pointer.changedToDown
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.ui.theme.MoonColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- model

data class MenuAction(
    val title: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val checked: Boolean = false,
    val subtitle: String? = null,
    val children: List<MenuAction>? = null,
    /** Short label for a secondary-arc chip ("1.5×", "5 秒"); null → icon or first chars of [title]. */
    val short: String? = null,
    /** v4.4: non-null → shown dimmed in sheets with this short reason; tapping only explains (never on a ring). */
    val disabledReason: String? = null,
    /** v5.0: how to reverse this action (定时长按 shows 撤销 when set). */
    val undo: (() -> Unit)? = null,
    val onClick: () -> Unit = {},
)

/**
 * A long-press context menu.
 *  - [preview] set → item "lifts" from [anchor] into a rounded preview card, backdrop blurs.
 *  - [liftKey] set → in-place lift: the item itself scales down (feed), no separate preview.
 *  - neither → compact menu anchored above/below [anchor] (tab icons, buttons).
 */
data class MenuSpec(
    val anchor: Rect,
    val actions: List<List<MenuAction>>,
    val quick: List<MenuAction> = emptyList(),
    /** Section titles for [actions] groups in the ··· sheet (播放 / 分享与保存 / 整理 …); destructive → 危险 automatically. */
    val groupTitles: List<String?> = emptyList(),
    val title: String? = null,
    val preview: (@Composable () -> Unit)? = null,
    val previewAspect: Float = 0.75f,
    val liftKey: String? = null,
    val forceDark: Boolean = false,
    /** Explicit primary ring (≤ 8). null → derived from quick + actions; leftovers go behind 更多. */
    val radial: List<MenuAction>? = null,
    /** Ring centre (root px); default = finger position at open, else anchor centre. */
    val origin: androidx.compose.ui.geometry.Offset? = null,
    /** Radial only: centre the ring horizontally on screen (tab-bar rings fan out above the ● button). */
    val centerHorizontally: Boolean = false,
    /** true → classic glass list (pickers, 更多, sub-menus). Default is the radial ring. */
    val list: Boolean = false,
    /** Force an open/close style (settings preview); null → 长按动画 setting. */
    val anim: com.moonlight.feed.data.RingAnim? = null,
    /** v5.0: which 定时长按 context this ring belongs to (assignable options); null = none. */
    val context: com.moonlight.feed.data.HoldContext? = null,
)

data class ToastSpec(
    val text: String,
    val icon: ImageVector? = null,
    val action: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

class SheetSpec(
    val title: String?,
    /** Same rule as [MenuSpec.forceDark]: opened over media → MediaGlass, else the theme. */
    val dark: Boolean = false,
    val content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
)

/** Global glass toast — callable from anywhere (coroutines, callbacks). */
object MoonToaster {
    val current = MutableStateFlow<ToastSpec?>(null)
    fun show(text: String, icon: ImageVector? = null, action: String? = null, onAction: (() -> Unit)? = null) {
        current.value = ToastSpec(text, icon, action, onAction)
    }
}

@Stable
class MoonOverlayState(initialMenu: MenuSpec? = null, initialSheet: SheetSpec? = null) {
    var menu by mutableStateOf(initialMenu)
        private set
    var sheet by mutableStateOf(initialSheet)
        private set
    internal var shownMenu by mutableStateOf(initialMenu)
    internal var shownSheet by mutableStateOf(initialSheet)
    internal val menuProgress = Animatable(if (initialMenu != null) 1f else 0f)
    /** The 长按动画 style the open ring picked (月光 replaces the backdrop blur/dim with its own night). */
    internal var ringStyle by mutableStateOf<com.moonlight.feed.data.RingAnim?>(null)
    internal val sheetProgress = Animatable(if (initialSheet != null) 1f else 0f)

    /** Live pointer, fed by the host's root tracker (or by a View host such as the player). */
    var finger by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var fingerDown by mutableStateOf(false)
    /** Where the current press started — becomes the ring centre. */
    var pressOrigin by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    /**
     * True while the finger that long-pressed a menu open is still down. That finger never selects anything:
     * its moves and its final up are swallowed (pager / taps underneath don't react) and the menu stays open.
     * Selection is a separate tap afterwards.
     */
    var fingerCaptured by mutableStateOf(false)
    val holdingOpen: Boolean get() = fingerCaptured

    /** Feed every pointer event here (Compose root tracker or a View host). Returns true when it must be consumed. */
    fun onPointer(anyPressed: Boolean): Boolean {
        if (!fingerCaptured) return false
        if (!anyPressed) fingerCaptured = false   // the opening finger lifted: swallow this up, then tap-to-select is live
        return true
    }

    /**
     * Tap on ring item [i] of [items] (the last one is 更多 when [rest] is non-empty). Opens sub-choices for parents,
     * otherwise closes the menu and runs the action. Ignored while the opening finger is still down or while closing.
     */
    fun tapRingItem(spec: MenuSpec, items: List<MenuAction>, rest: List<List<MenuAction>>, i: Int, at: Rect = Rect.Zero): RingTap {
        if (holdingOpen || menu == null) return RingTap.IGNORED
        val a = items.getOrNull(i) ?: return RingTap.IGNORED
        return when {
            // v4.0: short sub-choice lists expand as a secondary arc around the ring (tap the parent again to fold)
            a.children != null && a.children.size <= SUB_ARC_MAX -> { subIndex = if (subIndex == i) null else i; RingTap.SUBMENU }
            a.children != null -> {
                showMenu(spec.copy(radial = null, quick = emptyList(), actions = listOf(a.children), title = a.title, list = true, liftKey = null, preview = null, anchor = at), fromLongPress = false)
                RingTap.SUBMENU
            }
            else -> { dismissMenu(); a.onClick(); RingTap.SELECTED }
        }
    }

    /**
     * The opening finger lifted while hovering ring item [i] (null = over nothing). Only 拖动 selects on release;
     * 点击 ignores it (the ring stays open for a tap). Call after [onPointer] processed the up.
     */
    fun releaseRingItem(spec: MenuSpec, items: List<MenuAction>, rest: List<List<MenuAction>>, i: Int?, at: Rect = Rect.Zero): RingTap {
        if (i == null || MenuStyle.select != com.moonlight.feed.data.MenuSelect.DRAG) return RingTap.IGNORED
        return tapRingItem(spec, items, rest, i, at)
    }

    /** Index of the ring item whose sub-choices are fanned out on the secondary arc (null = none). */
    var subIndex by mutableStateOf<Int?>(null)
        internal set

    /** Tap on a chip of the secondary arc. */
    fun tapSubItem(a: MenuAction): RingTap {
        if (holdingOpen || menu == null) return RingTap.IGNORED
        dismissMenu(); a.onClick(); return RingTap.SELECTED
    }

    /** The centre ··· : ring closes, every other action of [spec] opens grouped in a frosted bottom sheet. */
    fun openSheet(spec: MenuSpec): RingTap {
        if (holdingOpen || menu == null) return RingTap.IGNORED
        val groups = spec.sheetGroups()
        if (groups.isEmpty()) return RingTap.IGNORED
        dismissMenu()
        showSheet(spec.title, dark = spec.forceDark) { dismiss -> ActionSheetContent(groups, dismiss) }
        return RingTap.SHEET
    }

    /** Tap on a list-menu row (sub-choices, 更多). */
    fun tapMenuAction(a: MenuAction): RingTap {
        if (holdingOpen || menu == null) return RingTap.IGNORED
        if (a.children != null) return RingTap.SUBMENU   // the panel swaps to the children itself
        dismissMenu(); a.onClick(); return RingTap.SELECTED
    }

    /** Tap on empty space / scrim: closes (not while the opening finger is still down). */
    fun tapOutside(): Boolean {
        if (holdingOpen || menu == null) return false
        dismissMenu(); return true
    }

    /** Feed reads these to scale the lifted item in place (list menus only; the ring blurs the backdrop instead). */
    val liftKey: String? get() = shownMenu?.takeIf { it.list }?.liftKey
    val liftProgress: Float get() = if (shownMenu?.liftKey != null) menuProgress.value else 0f
    /** Target scale / top (px) for the in-place lifted item, computed by the menu layout so it sits above the menu. */
    var liftScale by mutableFloatStateOf(0.62f)
        internal set
    var liftTop by mutableFloatStateOf(0f)
        internal set

    fun showMenu(spec: MenuSpec) = showMenu(spec, fromLongPress = fingerDown)

    /** [fromLongPress] = the finger that opened it is still down → hold until it lifts (its release selects nothing). */
    fun showMenu(spec: MenuSpec, fromLongPress: Boolean) {
        fingerCaptured = fromLongPress
        subIndex = null
        menu = spec
        // 定时长按: the ring this press opened decides what the timed tier may fire
        if (fromLongPress) ensureHoldDown()
        if (fromLongPress) hold.ring(spec.context, spec.context?.let { com.moonlight.feed.data.V5.prefs.timedAssign[it] })
    }

    // ---------------------------------------------------------------- v5.0 定时长按 / 全部按键 (driven by HoldDriver)
    val hold = com.moonlight.feed.data.HoldMachine()
    /** Readout + countdown on screen (and what they show). */
    var holdShowing by mutableStateOf(false); internal set
    var holdElapsedMs by androidx.compose.runtime.mutableLongStateOf(0L); internal set
    var holdProgress by mutableFloatStateOf(0f); internal set
    var holdTier by mutableStateOf(com.moonlight.feed.data.HoldTier.NONE); internal set
    /** Root size (px), for the centre circle. */
    var rootSize by mutableStateOf(androidx.compose.ui.geometry.Size.Zero); internal set
    internal var pending: PendingHold? = null
    /** View hosts (player): the real press time (uptimeMillis) — they only report the finger once the long-press fired. */
    var pressStartedAt: Long? = null
    /** The press is known to the machine even if the driver hasn't seen it yet (View host opens the ring in the same call). */
    internal fun ensureHoldDown() {
        val ph = hold.phase
        if (ph == com.moonlight.feed.data.HoldMachine.Phase.PRESSED || ph == com.moonlight.feed.data.HoldMachine.Phase.LONG) return
        if (!fingerDown) return
        val o = pressOrigin ?: finger ?: return
        hold.cfg = com.moonlight.feed.data.HoldConfig.of(com.moonlight.feed.data.V5.prefs, hold.cfg.slopPx)
        hold.down(pressStartedAt ?: android.os.SystemClock.uptimeMillis(), o.x, o.y, rootSize.width, rootSize.height)
    }
    /** Tests: freeze the readout at this elapsed time (ms) with this tier. */
    var debugHold by mutableStateOf<Pair<Long, com.moonlight.feed.data.HoldTier>?>(null)

    /**
     * A long-press whose normal action should wait for the finger-up (朋友圈 video → full-screen player) so the timed tier
     * can pop [ring] at the threshold instead. Returns false (caller acts now) when 定时长按 is off or no finger is down.
     */
    fun deferHold(ctx: com.moonlight.feed.data.HoldContext, ring: () -> MenuSpec, onRelease: () -> Unit): Boolean {
        if (!com.moonlight.feed.data.V5.prefs.timedOn || !fingerDown) return false
        ensureHoldDown()
        val ph = hold.phase
        if (ph != com.moonlight.feed.data.HoldMachine.Phase.PRESSED && ph != com.moonlight.feed.data.HoldMachine.Phase.LONG) return false
        pending = PendingHold(ctx, ring, onRelease)
        hold.ring(ctx, com.moonlight.feed.data.V5.prefs.timedAssign[ctx], deferred = true)
        return true
    }

    /** 全部按键 panel (null = closed). */
    var panel by mutableStateOf<MasterPanelSpec?>(null)
    /** Global actions for the panel (settings, theme, record, filters…) — set by the activity. */
    var globals: () -> List<MenuAction> = { emptyList() }
    /** Actions of the current screen / tab — set by the activity (tab ring of the current tab, player chrome …). */
    var pageActions: () -> List<MenuAction> = { emptyList() }
    fun openPanel(current: MenuSpec?) {
        panel = MasterPanelSpec(current?.title, current?.let { panelActions(it) }.orEmpty(), pageActions(), globals())
    }
    fun dismissPanel() { panel = null }
    fun dismissMenu() { menu = null }
    fun showSheet(title: String? = null, dark: Boolean = false, content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
        sheet = SheetSpec(title, dark, content)
    }
    fun dismissSheet() { sheet = null }

    /** iOS-style glass action sheet confirmation. */
    fun confirm(title: String, message: String? = null, actions: List<MenuAction>) {
        showSheet(null) { dismiss -> ConfirmContent(title, message, actions, dismiss) }
    }
    fun toast(text: String, icon: ImageVector? = null, action: String? = null, onAction: (() -> Unit)? = null) =
        MoonToaster.show(text, icon, action, onAction)
}

val LocalOverlay = staticCompositionLocalOf { MoonOverlayState() }

class AnchorState { var rect by mutableStateOf(Rect.Zero) }

@Composable
fun rememberAnchor() = remember { AnchorState() }
fun Modifier.anchor(a: AnchorState): Modifier = onGloballyPositioned { a.rect = it.boundsInRoot() }

// ---------------------------------------------------------------- host

/**
 * Root of every screen. Records the backdrop for real glass blur (Haze/RenderEffect on 12+),
 * blurs + dims content while a menu/sheet is open, and renders menus, sheets and toasts.
 */
@Composable
fun MoonOverlayHost(
    state: MoonOverlayState,
    modifier: Modifier = Modifier,
    chrome: @Composable BoxScope.() -> Unit = {},
    /** false when the backdrop is not Compose (player WebView): glass uses the translucent+grain fill. */
    backdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = remember { HazeState() }
    val inspect = LocalInspectionMode.current

    LaunchedEffect(state.menu) {
        val m = state.menu
        if (m != null) {
            state.shownMenu = m
            state.menuProgress.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 420f))
        } else if (state.shownMenu != null) {
            state.menuProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = if (state.shownMenu?.list == false) 420f else 900f))
            state.shownMenu = null
            state.fingerCaptured = false
        }
    }
    LaunchedEffect(state.sheet) {
        val s = state.sheet
        if (s != null) {
            state.shownSheet = s
            state.sheetProgress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
        } else if (state.shownSheet != null) {
            state.sheetProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 700f))
            state.shownSheet = null
        }
    }

    // 菜单背景 (透明 / 轻度模糊 / 模糊) scales the menu blur; sheets keep their full blur
    val menuBlurDp = if (state.shownMenu != null && state.liftKey == null && !(state.shownMenu?.list == false && state.ringStyle == com.moonlight.feed.data.RingAnim.MOON)) state.menuProgress.value * MenuStyle.backdrop.blurDp else 0f
    val blurDp = maxOf(menuBlurDp, 24f * state.sheetProgress.value)

    val blurMod = if (blurDp > 0.25f) Modifier.blur(blurDp.dp) else Modifier
    CompositionLocalProvider(LocalOverlay provides state) {
        HoldDriver(state)
        Box(modifier.fillMaxSize().onSizeChanged { state.rootSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.pointerInput(state) {
            // Root pointer tracker (Initial pass = sees events before children; never consumes unless a ring captured the finger).
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val ch = e.changes.firstOrNull() ?: continue
                    if (ch.changedToDown()) state.pressOrigin = ch.position
                    state.finger = ch.position
                    state.fingerDown = e.changes.any { it.pressed }
                    if (state.onPointer(state.fingerDown)) e.changes.forEach { it.consume() }
                }
            }
        }) {
            // Backdrop source. Glass inside it may not sample the same state (Haze rule),
            // so content gets no root haze; it uses local sources (feed) or the frosted fallback.
            CompositionLocalProvider(LocalHazeState provides null) {
                Box(Modifier.fillMaxSize().haze(haze).then(blurMod), content = content)
            }
            CompositionLocalProvider(LocalHazeState provides if (backdrop) haze else null) {
                // Floating chrome (tab bar) sits outside the source and frosts the content behind it.
                Box(Modifier.fillMaxSize().then(blurMod), content = chrome)
                ShredLayer()
                state.shownMenu?.let { if (it.list) MenuLayer(it, state) else RadialLayer(it, state) }
                state.shownSheet?.let { SheetLayer(it, state) }
                state.panel?.let { MasterPanelLayer(it, state) }
                HoldIndicator(state)
                RecordingPillHost()
                ToastLayer()
            }
        }
    }
}

// ---------------------------------------------------------------- menu

@Composable
private fun MenuLayer(spec: MenuSpec, state: MoonOverlayState) {
    BackHandler { state.dismissMenu() }
    val p = state.menuProgress.value
    val colors = if (spec.forceDark) MediaGlass else LocalMoonColors.current
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val safeTop = insets.getTop(density) + with(density) { 12.dp.roundToPx() }
    val safeBottom = insets.getBottom(density) + with(density) { 16.dp.roundToPx() }
    var groups by remember(spec) { mutableStateOf(spec.actions) }
    var nested by remember(spec) { mutableStateOf(false) }
    val view = LocalView.current

    val scrimAlpha = when {
        spec.liftKey != null -> 0.35f
        !canRealBlur -> 0.5f
        colors.dark -> 0.28f
        else -> 0.12f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha * (if (spec.liftKey != null) 1f else MenuStyle.backdrop.dim) * p.coerceIn(0f, 1f)))
            .pointerInput(spec) { detectTapGestures { state.tapOutside() } }
    ) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier
                        .shadow(28.dp, RoundedCornerShape(24.dp), clip = false)
                        .clip(RoundedCornerShape(24.dp))
                        .pointerInput(Unit) { detectTapGestures { state.tapOutside() } }
                ) { spec.preview?.invoke() }
                CompositionLocalProvider(LocalMoonColors provides colors) {
                    MenuPanel(
                        spec = spec,
                        groups = groups,
                        nested = nested,
                        onBack = { nested = false; groups = spec.actions; Haptics.tick(view) },
                        onAction = { a ->
                            when (state.tapMenuAction(a)) {
                                RingTap.SUBMENU -> { Haptics.tick(view); groups = listOf(a.children!!); nested = true }
                                RingTap.SELECTED -> Haptics.confirm(view)
                                RingTap.IGNORED, RingTap.SHEET -> {}
                            }
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val dp8 = 8.dp.roundToPx()
            val menuW = 260.dp.roundToPx().coerceAtMost(w - 4 * dp8)
            val menuP = measurables[1].measure(
                Constraints(minWidth = menuW, maxWidth = menuW, maxHeight = (h * 0.62f).toInt())
            )
            val a = spec.anchor
            val gap = 2 * dp8
            val hasPreview = spec.preview != null
            var pw = 0; var ph = 0; var px = 0; var py = 0
            val menuX: Int
            val menuY: Int
            if (hasPreview) {
                val maxPW = w - 6 * dp8
                val maxPH = (h - safeTop - safeBottom - menuP.height - gap).coerceAtLeast((h * 0.25f).toInt())
                pw = (w * 0.74f).toInt().coerceAtMost(maxPW)
                ph = (pw / spec.previewAspect).toInt()
                if (ph > maxPH) { ph = maxPH; pw = (ph * spec.previewAspect).toInt() }
                val total = ph + gap + menuP.height
                val cy = if (a == Rect.Zero) h / 2f else a.center.y
                py = (cy - total / 2f).toInt().coerceIn(safeTop, (h - safeBottom - total).coerceAtLeast(safeTop))
                val cx = if (a == Rect.Zero) w / 2f else a.center.x
                px = (cx - pw / 2f).toInt().coerceIn(3 * dp8, (w - 3 * dp8 - pw).coerceAtLeast(3 * dp8))
                menuX = (if (cx < w / 2f) px else px + pw - menuW).coerceIn(2 * dp8, w - 2 * dp8 - menuW)
                menuY = py + ph + gap
            } else if (spec.liftKey != null) {
                // In-place lift: shrink the item so card + menu fit, card on top, menu below.
                val avail = h - safeTop - safeBottom - menuP.height - gap
                val s = (avail.toFloat() / h).coerceIn(0.34f, 0.8f)
                val cardH = h * s
                val total = cardH + gap + menuP.height
                val top = ((h - total) / 2f).coerceAtLeast(safeTop.toFloat())
                state.liftScale = s
                state.liftTop = top
                menuX = (w - menuW) / 2
                menuY = (top + cardH + gap).toInt()
            } else {
                val above = a.center.y > h / 2f
                menuY = if (above) (a.top.toInt() - menuP.height - 12.dp.roundToPx()).coerceAtLeast(safeTop)
                else (a.bottom.toInt() + 12.dp.roundToPx()).coerceAtMost(h - safeBottom - menuP.height)
                menuX = (a.center.x - menuW / 2f).toInt().coerceIn(2 * dp8, w - 2 * dp8 - menuW)
            }
            val previewP = if (hasPreview) measurables[0].measure(Constraints.fixed(pw, ph)) else null
            layout(w, h) {
                previewP?.placeWithLayer(px, py) {
                    val sx = if (a.width > 0f) a.width / pw else 0.9f
                    val sy = if (a.height > 0f) a.height / ph else 0.9f
                    val s0 = minOf(sx, sy)
                    val k = p
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    scaleX = s0 + (1f - s0) * k
                    scaleY = s0 + (1f - s0) * k
                    if (a != Rect.Zero) {
                        translationX = (a.center.x - (px + pw / 2f)) * (1f - k)
                        translationY = (a.center.y - (py + ph / 2f)) * (1f - k)
                    }
                    alpha = (k * 1.6f).coerceIn(0f, 1f)
                }
                menuP.placeWithLayer(menuX, menuY) {
                    val k = p.coerceIn(0f, 1.2f)
                    val ox = if (a == Rect.Zero) 0.5f else ((a.center.x - menuX) / menuW).coerceIn(0f, 1f)
                    val oy = when {
                        hasPreview || spec.liftKey != null -> 0f
                        a.center.y > h / 2f -> 1f
                        else -> 0f
                    }
                    transformOrigin = TransformOrigin(ox, oy)
                    scaleX = 0.4f + 0.6f * k
                    scaleY = 0.4f + 0.6f * k
                    alpha = p.coerceIn(0f, 1f)
                }
            }
        }
    }
}

@Composable
private fun MenuPanel(
    spec: MenuSpec,
    groups: List<List<MenuAction>>,
    nested: Boolean,
    onBack: () -> Unit,
    onAction: (MenuAction) -> Unit,
) {
    val c = LocalMoonColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .glass(shape, c, elevation = 24.dp, blurRadius = 36.dp)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        AnimatedContent(
            targetState = nested to groups,
            transitionSpec = {
                (fadeIn(spring(stiffness = 600f)) + scaleIn(spring(0.75f, 500f), initialScale = 0.94f)) togetherWith
                    (fadeOut(spring(stiffness = 900f)) + scaleOut(targetScale = 0.96f))
            },
            label = "menu-page",
        ) { (isNested, gs) ->
            Column {
                if (isNested) {
                    MenuRow(MenuAction("返回", Icons.AutoMirrored.Outlined.ArrowBack), leadingIcon = true) { onBack() }
                    ThickSeparator(c)
                } else {
                    spec.title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                        )
                        Hairline(c)
                    }
                    if (spec.quick.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            spec.quick.forEach { q ->
                                QuickButton(q, Modifier.weight(1f)) { onAction(q) }
                            }
                        }
                        ThickSeparator(c)
                    }
                }
                gs.forEachIndexed { gi, g ->
                    if (gi > 0) ThickSeparator(c)
                    g.forEachIndexed { i, a ->
                        if (i > 0) Hairline(c)
                        MenuRow(a) { onAction(a) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickButton(a: MenuAction, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.hairline)
            .moonPress(onClick, pressedScale = 0.9f, highlightShape = RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        a.icon?.let {
            Icon(it, a.title, tint = if (a.destructive) c.danger else if (a.checked) c.accent else c.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(a.title, style = MaterialTheme.typography.labelSmall, color = if (a.destructive) c.danger else c.text, maxLines = 1)
    }
}

@Composable
private fun MenuRow(a: MenuAction, leadingIcon: Boolean = false, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    val tint = if (a.destructive) c.danger else c.text
    Row(
        Modifier
            .fillMaxWidth()
            .moonPress(onClick, pressedScale = 0.98f, highlight = c.text.copy(alpha = 0.08f), hapticOnClick = false)
            .padding(horizontal = 16.dp, vertical = if (a.subtitle != null) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon && a.icon != null) {
            Icon(a.icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        if (a.checked) {
            Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1)
            a.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (a.children != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (!leadingIcon && a.icon != null) {
            Icon(a.icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Hairline(c: MoonColors) {
    Box(Modifier.fillMaxWidth().height(0.6.dp).background(c.hairline))
}

@Composable
private fun ThickSeparator(c: MoonColors) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(if (c.dark) Color.Black.copy(0.22f) else Color.Black.copy(0.05f)))
}

// ---------------------------------------------------------------- sheet

@Composable
private fun SheetLayer(spec: SheetSpec, state: MoonOverlayState) {
    BackHandler { state.dismissSheet() }
    val p = state.sheetProgress.value
    val c = if (spec.dark) MediaGlass else LocalMoonColors.current
    var drag by remember(spec) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissPx = with(density) { 120.dp.toPx() }
    val back = remember { Animatable(0f) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (if (canRealBlur) 0.25f else 0.45f) * p.coerceIn(0f, 1f)))
                .pointerInput(spec) { detectTapGestures { state.dismissSheet() } }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp)
                .graphicsLayer {
                    translationY = (1f - p) * (size.height + 80f) + drag + back.value
                }
                .glass(RoundedCornerShape(30.dp), c, elevation = 28.dp, blurRadius = 36.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) },
                        onDragStopped = {
                            if (drag > dismissPx) state.dismissSheet()
                            else scope.launch {
                                back.snapTo(drag); drag = 0f
                                back.animateTo(0f, spring(0.6f, 500f))
                            }
                        },
                    )
                    .padding(top = 10.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(c.textDim.copy(alpha = 0.45f)))
            }
            spec.title?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 12.dp))
            }
            CompositionLocalProvider(LocalMoonColors provides c) { spec.content(this) { state.dismissSheet() } }
        }
    }
}

@Composable
fun ConfirmContent(title: String, message: String?, actions: List<MenuAction>, dismiss: () -> Unit) {
    val c = LocalMoonColors.current
    val view = LocalView.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        message?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textDim, textAlign = TextAlign.Center, maxLines = 3)
        }
        Spacer(Modifier.height(16.dp))
        val skAct = rememberButtonSkin("dialog.action")
        val skCancel = rememberButtonSkin("dialog.cancel")
        Column(Modifier.fillMaxWidth().skinOr(skAct, RoundedCornerShape(18.dp)) { clip(RoundedCornerShape(18.dp)).background(c.hairline) }) {
            actions.forEachIndexed { i, a ->
                if (i > 0) Hairline(c)
                Text(
                    a.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (a.destructive) c.danger else skAct?.ink ?: c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .moonPress({ Haptics.confirm(view); dismiss(); a.onClick() }, pressedScale = 0.98f, hapticOnClick = false, highlight = c.text.copy(0.08f))
                        .padding(vertical = 15.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "取消",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = skCancel?.ink ?: c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .skinOr(skCancel, RoundedCornerShape(18.dp)) { clip(RoundedCornerShape(18.dp)).background(c.hairline) }
                .moonPress(dismiss, pressedScale = 0.98f, highlight = c.text.copy(0.08f))
                .padding(vertical = 15.dp),
        )
    }
}

/**
 * ··· sheet: all actions not on the ring, grouped with small section captions; 危险 (删除…) separated at the end.
 * Rows with children expand inline. Frosted glass + swipe-down come from the sheet host.
 */
@Composable
fun ActionSheetContent(groups: List<Pair<String?, List<MenuAction>>>, dismiss: () -> Unit) {
    val c = LocalMoonColors.current
    val view = LocalView.current
    var open by remember(groups) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groups.forEach { (title, rows) ->
            val danger = rows.all { it.destructive }
            Column {
                if (title != null) Text(title, style = MaterialTheme.typography.labelMedium, color = if (danger) c.danger.copy(0.85f) else c.textDim,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.hairline)) {
                    rows.forEachIndexed { i, a ->
                        if (i > 0) Hairline(c)
                        SheetRow(a, expanded = open == a.title) {
                            if (a.disabledReason != null) { Haptics.tick(view); MoonToaster.show(a.disabledReason) }
                            else if (a.children != null) { Haptics.tick(view); open = if (open == a.title) null else a.title }
                            else { Haptics.confirm(view); dismiss(); a.onClick() }
                        }
                        if (open == a.title) a.children?.forEach { k ->
                            Hairline(c)
                            SheetRow(k, expanded = false, indent = true) { Haptics.confirm(view); dismiss(); k.onClick() }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetRow(a: MenuAction, expanded: Boolean, indent: Boolean = false, onClick: () -> Unit) {
    val c = LocalMoonColors.current
    val tint = when { a.disabledReason != null -> c.textDim.copy(0.7f); a.destructive -> c.danger; a.checked -> c.accent; else -> c.text }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .moonPress(onClick, pressedScale = 0.98f, hapticOnClick = false, highlight = c.text.copy(0.08f))
            .padding(start = if (indent) 48.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!indent) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { a.icon?.let { Icon(it, null, tint = tint, modifier = Modifier.size(22.dp)) } }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (a.disabledReason ?: a.subtitle)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        when {
            a.children != null -> Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = c.textDim, modifier = Modifier.size(20.dp))
            a.checked -> Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------- toast

@Composable
private fun BoxScope.ToastLayer() {
    val toast by MoonToaster.current.collectAsState()
    var shown by remember { mutableStateOf<ToastSpec?>(null) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(toast) {
        val t = toast
        if (t != null) {
            shown = t
            p.snapTo(0f)
            p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 500f))
            delay(if (t.action != null) 4000 else 2200)
            if (MoonToaster.current.value?.id == t.id) MoonToaster.current.value = null
        } else if (shown != null) {
            p.animateTo(0f, spring(dampingRatio = 1f, stiffness = 800f))
            shown = null
        }
    }
    val t = shown ?: return
    val c = MediaGlass
    val density = LocalDensity.current
    CompositionLocalProvider(LocalMoonColors provides c) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp, start = 24.dp, end = 24.dp)
                .graphicsLayer {
                    val k = p.value
                    alpha = k.coerceIn(0f, 1f)
                    translationY = (1f - k) * with(density) { 32.dp.toPx() }
                    scaleX = 0.9f + 0.1f * k; scaleY = 0.9f + 0.1f * k
                }
                .glass(CircleShape, c, elevation = 18.dp)
                .padding(start = 18.dp, end = if (t.action != null) 6.dp else 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            t.icon?.let {
                Icon(it, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(t.text, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2)
            if (t.action != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    t.action,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onAccent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.accent)
                        .moonPress({ t.onAction?.invoke(); MoonToaster.current.value = null }, highlightShape = CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}


/** Outcome of a tap inside a menu. */
enum class RingTap { IGNORED, SUBMENU, SELECTED, SHEET }
