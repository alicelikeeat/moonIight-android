package com.moonlight.feed.ui.design

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MoonColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- backgrounds

/** Soft "aurora" night/paper gradient so glass always has something to frost. */
@Composable
fun MoonBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalMoonColors.current
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
                drawCircle(
                    Brush.radialGradient(listOf(c.glow, Color.Transparent), center = Offset(size.width * 0.85f, size.height * 0.04f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f, center = Offset(size.width * 0.85f, size.height * 0.04f),
                )
                val cool = if (c.dark) Color(0x33405A8C) else Color(0x22A9B8D8)
                drawCircle(
                    Brush.radialGradient(listOf(cool, Color.Transparent), center = Offset(size.width * 0.05f, size.height * 0.72f), radius = size.width * 1.0f),
                    radius = size.width * 1.0f, center = Offset(size.width * 0.05f, size.height * 0.72f),
                )
            },
        content = content,
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalMoonColors.current
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = c.text)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textDim) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color? = null,
    colors: MoonColors = LocalMoonColors.current,
    /** 按键背景 id (see ButtonRegistry); null → never skinned. */
    skinId: String? = null,
) {
    val skin = rememberButtonSkin(skinId)
    Box(
        modifier
            .size(size)
            .moonPress(onClick, onLongClick, highlightShape = CircleShape, highlight = colors.text.copy(0.1f))
            .skinOr(skin, CircleShape) { glass(CircleShape, colors) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = skin?.ink ?: tint ?: colors.text, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    accent: Boolean = false,
    colors: MoonColors = LocalMoonColors.current,
) {
    val shape = CircleShape
    val base = modifier.moonPress(onClick, onLongClick, highlightShape = shape, highlight = colors.text.copy(0.1f))
    Row(
        (if (accent) base.clip(shape).background(colors.accent) else base.glass(shape, colors))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (accent) colors.onAccent else colors.text
        icon?.let {
            Icon(it, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
fun MoonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val c = LocalMoonColors.current
    val bg by animateColorAsState(if (selected) c.accent else Color.Transparent, spring(stiffness = 500f), label = "chip")
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, spring(stiffness = 500f), label = "chipfg")
    Row(
        modifier
            .moonPress(onClick, onLongClick, highlightShape = CircleShape, highlight = c.text.copy(0.1f))
            .glassStatic(CircleShape, c)
            .background(bg, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** iOS-style toggle with spring thumb. */
@Composable
fun MoonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMoonColors.current
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "sw")
    val on = if (c.dark) Color(0xFFCDBE84) else c.accent
    val track = lerp(if (c.dark) Color(0x33FFFFFF) else Color(0x22000000), on, t.coerceIn(0f, 1f))
    Box(
        modifier
            .width(50.dp).height(30.dp)
            .moonPress({ onCheckedChange(!checked) }, pressedScale = 0.94f, highlightShape = null)
            .clip(CircleShape)
            .background(track)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = t * 20.dp.toPx() }
                .size(26.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

// ---------------------------------------------------------------- animated icons

/** Heart that pops with a spring and sprays tiny particles when it becomes active. */
@Composable
fun AnimatedHeart(active: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, idleTint: Color = LocalMoonColors.current.text) {
    val inspect = LocalInspectionMode.current
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(if (inspect) 0f else 1f) }
    var first = remember { booleanArrayOf(true) }
    LaunchedEffect(active) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (active) {
            burst.snapTo(0f)
            pop.snapTo(0.6f)
            kotlinx.coroutines.coroutineScope {
                launch { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f)) }
                launch { burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            }
        } else {
            pop.snapTo(0.8f); pop.animateTo(1f, spring(0.5f, 700f))
        }
    }
    val heart = com.moonlight.feed.ui.theme.MoonPalette.Heart
    Box(modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val b = burst.value
            if (b in 0.01f..0.99f) {
                val r0 = this.size.minDimension * 0.22f
                val r1 = this.size.minDimension * 0.5f
                for (i in 0 until 8) {
                    val ang = (i / 8f) * 2 * PI.toFloat() + 0.3f
                    val rr = r0 + (r1 - r0) * b
                    drawCircle(
                        if (i % 2 == 0) heart else Color(0xFFFFC2CF),
                        radius = (1f - b) * 3.dp.toPx() + 0.5f,
                        center = center + Offset(cos(ang) * rr, sin(ang) * rr),
                    )
                }
            }
        }
        Icon(
            if (active) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            null,
            tint = if (active) heart else idleTint,
            modifier = Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

/** Big heart burst at the double-tap point (feed). */
@Composable
fun HeartBurst(at: Offset, key: Long, modifier: Modifier = Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, tween(820, easing = LinearEasing)) }
    val heart = com.moonlight.feed.ui.theme.MoonPalette.Heart
    val rnd = remember(key) { Random(key) }
    val tilt = remember(key) { rnd.nextInt(-18, 18).toFloat() }
    val parts = remember(key) { List(12) { Triple(rnd.nextFloat() * 2 * PI.toFloat(), 0.6f + rnd.nextFloat() * 0.6f, rnd.nextInt(3)) } }
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (t >= 1f) return@Canvas
        val pop = when {
            t < 0.18f -> 0.4f + (t / 0.18f) * 0.9f
            t < 0.3f -> 1.3f - ((t - 0.18f) / 0.12f) * 0.3f
            else -> 1f + (t - 0.3f) * 0.4f
        }
        val alpha = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f
        val s = 96.dp.toPx() * pop
        rotate(tilt, at) { drawHeart(at, s, heart.copy(alpha = alpha)) }
        val bt = (t / 0.7f).coerceAtMost(1f)
        parts.forEach { (ang, sp, kind) ->
            val rr = 40.dp.toPx() + 90.dp.toPx() * sp * bt
            val pos = at + Offset(cos(ang) * rr, sin(ang) * rr - 20.dp.toPx() * bt)
            val col = when (kind) { 0 -> heart; 1 -> Color(0xFFFFC2CF); else -> Color(0xFFE8E3C9) }
            drawCircle(col.copy(alpha = (1f - bt).coerceIn(0f, 1f)), radius = 4.dp.toPx() * (1f - bt * 0.6f), center = pos)
        }
    }
}

fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        val w = s; val h = s * 0.9f
        val x = c.x - w / 2; val y = c.y - h / 2
        moveTo(x + w / 2, y + h)
        cubicTo(x - w * 0.1f, y + h * 0.55f, x + w * 0.05f, y - h * 0.05f, x + w / 2, y + h * 0.25f)
        cubicTo(x + w * 0.95f, y - h * 0.05f, x + w * 1.1f, y + h * 0.55f, x + w / 2, y + h)
        close()
    }
    drawPath(p, color)
}

/** Mute ⇄ sound morph (scale + rotate crossfade). */
@Composable
fun MuteIcon(muted: Boolean, modifier: Modifier = Modifier, tint: Color = LocalMoonColors.current.text, size: Dp = 22.dp) {
    AnimatedContent(
        targetState = muted,
        transitionSpec = {
            (scaleIn(spring(0.45f, 600f), initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut())
        },
        label = "mute",
        modifier = modifier,
    ) { m ->
        Icon(
            if (m) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
            if (m) "已静音" else "声音",
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

// ---------------------------------------------------------------- loading / empty

fun Modifier.shimmer(shape: Shape = RoundedCornerShape(16.dp)): Modifier = composed {
    val c = LocalMoonColors.current
    val inspect = LocalInspectionMode.current
    val t = if (inspect) 0.35f else {
        val tr = rememberInfiniteTransition(label = "shimmer")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "s").value
    }
    val base = if (c.dark) Color(0x14FFFFFF) else Color(0x10000000)
    val hi = if (c.dark) Color(0x2EFFFFFF) else Color(0x22FFFFFF)
    this.clip(shape).drawBehind {
        drawRect(base)
        val x = -size.width + t * size.width * 3
        drawRect(Brush.linearGradient(listOf(Color.Transparent, hi, Color.Transparent), start = Offset(x, 0f), end = Offset(x + size.width, size.height)))
    }
}

@Composable
fun MoonEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = LocalMoonColors.current
    val inspect = LocalInspectionMode.current
    val bob = if (inspect) 0f else {
        val tr = rememberInfiniteTransition(label = "bob")
        tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b").value
    }
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(140.dp).graphicsLayer { translationY = bob * 6.dp.toPx() }) {
            val r = size.minDimension * 0.26f
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(0.28f), Color.Transparent), radius = size.minDimension / 2), radius = size.minDimension / 2)
            drawCrescent(center, r, c.accent, phase = 0.62f)
            val rnd = Random(3)
            repeat(7) {
                val o = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.8f)
                if ((o - center).getDistance() > r * 1.5f) drawStar(o, (1.5f + rnd.nextFloat() * 2.2f).dp.toPx(), c.accent.copy(0.4f + rnd.nextFloat() * 0.5f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textDim, textAlign = TextAlign.Center)
        }
        action?.let {
            Spacer(Modifier.height(20.dp))
            GlassPill(it, onAction, accent = true)
        }
    }
}

/** Crescent: full disc minus an offset disc. phase 0 = full moon, 1 = new moon. */
fun DrawScope.drawCrescent(c: Offset, r: Float, color: Color, phase: Float) {
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(c.x - r * 2, c.y - r * 2, c.x + r * 2, c.y + r * 2), androidx.compose.ui.graphics.Paint())
    drawCircle(color, r, c)
    if (phase > 0.01f) drawCircle(Color.Black, r * 0.98f, c + Offset(r * 1.15f * phase, -r * 0.35f * phase), blendMode = BlendMode.DstOut)
    drawContext.canvas.restore()
}

fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 2); quadraticTo(c.x, c.y, c.x + r * 2, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r * 2); quadraticTo(c.x, c.y, c.x - r * 2, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r * 2); close()
    }
    drawPath(p, color)
}

/** Pull-to-refresh: a moon that waxes as you pull and spins while refreshing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.MoonPullIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val c = LocalMoonColors.current
    val f = state.distanceFraction
    val spin = if (refreshing) {
        val tr = rememberInfiniteTransition(label = "spin")
        tr.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "r").value
    } else 0f
    val vis = if (refreshing) 1f else f.coerceIn(0f, 1f)
    if (vis <= 0.01f) return
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                translationY = (if (refreshing) 1f else f.coerceAtMost(1.4f)) * 72.dp.toPx() - 24.dp.toPx()
                alpha = vis; scaleX = 0.6f + 0.4f * vis; scaleY = 0.6f + 0.4f * vis
            }
            .size(40.dp)
            .glass(CircleShape, c, elevation = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = spin; compositingStrategy = CompositingStrategy.Offscreen }) {
            drawCrescent(center, size.minDimension / 2.2f, c.accent, phase = 1f - 0.75f * (if (refreshing) 1f else f.coerceIn(0f, 1f)))
        }
    }
}

// ---------------------------------------------------------------- images

/**
 * Image loader. `art:N` models render procedural moonlit-landscape art (used for previews / screenshots,
 * no network); anything else goes through Coil.
 */
@Composable
fun MoonImage(
    model: Any?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, contentDescription: String? = null,
    /** Set → blur-to-sharp the first time this key finishes loading (library / result thumbnails). */
    revealKey: String? = null,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
) {
    if (model is String && model.startsWith("art:")) {
        ProceduralArt(model.removePrefix("art:").toIntOrNull() ?: 0, modifier)
    } else {
        val already = revealKey == null || RevealMemory.has(revealKey)
        var loaded by remember(revealKey) { mutableStateOf(false) }
        val p = if (already) 1f else rememberReveal(revealKey!!, loaded)
        Box(modifier.shimmer(RoundedCornerShape(0.dp))) {
            AsyncImage(
                model = model, contentDescription = contentDescription, contentScale = contentScale, alignment = alignment,
                onSuccess = { if (revealKey != null && RevealMemory.first(revealKey)) loaded = true },
                modifier = Modifier.fillMaxSize().revealLayer(p, 22.dp),
            )
        }
    }
}

private val artPalettes = listOf(
    listOf(Color(0xFF0B1426), Color(0xFF243B6B), Color(0xFFE9B98A)),
    listOf(Color(0xFF120E1F), Color(0xFF4A2C5E), Color(0xFFF2A3A0)),
    listOf(Color(0xFF07131A), Color(0xFF1C4A55), Color(0xFFBFE3D6)),
    listOf(Color(0xFF15100A), Color(0xFF5A3B1E), Color(0xFFF3D39A)),
    listOf(Color(0xFF0A0F1E), Color(0xFF2A3C7A), Color(0xFF9DB7FF)),
    listOf(Color(0xFF1A0F12), Color(0xFF6B2E3A), Color(0xFFFFC7A6)),
)

@Composable
fun ProceduralArt(seed: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { drawProceduralArt(seed) }
}

/** The procedural art as a plain draw call (also used by button skins on demo `art:N` media). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProceduralArt(seed: Int) {
    val pal = artPalettes[((seed % artPalettes.size) + artPalettes.size) % artPalettes.size]
    run {
        val w = size.width; val h = size.height
        val rnd = Random(seed * 31 + 7)
        drawRect(Brush.verticalGradient(listOf(pal[0], pal[1], lerp(pal[1], pal[2], 0.55f)), startY = 0f, endY = h * 0.78f))
        // stars
        repeat(40) {
            drawCircle(Color.White.copy(alpha = 0.25f + rnd.nextFloat() * 0.6f), radius = (0.5f + rnd.nextFloat() * 1.3f).dp.toPx(),
                center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.5f))
        }
        // moon + halo
        val mc = Offset(w * (0.25f + rnd.nextFloat() * 0.5f), h * (0.16f + rnd.nextFloat() * 0.16f))
        val mr = minOf(w, h) * (0.07f + rnd.nextFloat() * 0.05f)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFF6DA), Color.Transparent), center = mc, radius = mr * 5), radius = mr * 5, center = mc)
        drawCircle(Color(0xFFF6F0D8), mr, mc)
        // mountain layers
        for (layer in 0 until 4) {
            val baseY = h * (0.5f + layer * 0.12f)
            val amp = h * (0.10f - layer * 0.018f)
            val col = lerp(lerp(pal[1], pal[0], 0.35f), pal[0], layer / 3f).copy(alpha = 1f)
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, baseY)
                val steps = 7
                var px = 0f
                for (i in 1..steps) {
                    val x = w * i / steps
                    val y = baseY - amp * (0.3f + rnd.nextFloat())
                    quadraticTo((px + x) / 2, y - amp * 0.4f * rnd.nextFloat(), x, y)
                    px = x
                }
                lineTo(w, h); close()
            }
            drawPath(path, col)
        }
        // soft moon glow on the water
        val wy = h * 0.84f
        drawRect(Brush.verticalGradient(listOf(pal[0].copy(alpha = 0.55f), pal[0]), startY = wy, endY = h), topLeft = Offset(0f, wy), size = Size(w, h - wy))
        drawOval(
            Brush.radialGradient(listOf(Color(0x40F6F0D8), Color.Transparent), center = Offset(mc.x, wy + (h - wy) * 0.35f), radius = mr * 2.4f),
            topLeft = Offset(mc.x - mr * 2.4f, wy), size = Size(mr * 4.8f, (h - wy) * 0.7f),
        )
    }
}

/** Small glass badge (duration, platform). */
@Composable
fun GlassBadge(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = com.moonlight.feed.ui.theme.MediaGlass
    Row(
        modifier.glass(RoundedCornerShape(8.dp), c, blurRadius = 16.dp).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = c.text, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
    }
}

/**
 * Soft drop shadow for icons drawn straight on media (no glass disc): the content is re-drawn as a
 * black silhouette at a few small offsets beneath itself. Works on every API level and in previews.
 */
fun Modifier.softShadow(
    color: Color = Color.Black.copy(alpha = 0.32f),
    radius: Dp = 1.6.dp,
    dy: Dp = 1.dp,
): Modifier = this.drawWithContent {
    val r = radius.toPx(); val oy = dy.toPx()
    val paint = androidx.compose.ui.graphics.Paint().apply {
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color, androidx.compose.ui.graphics.BlendMode.SrcIn)
    }
    val bounds = androidx.compose.ui.geometry.Rect(-r * 2, -r * 2, size.width + r * 2, size.height + r * 2 + oy)
    drawIntoCanvas { c ->
        for ((ox, oyy) in SHADOW_TAPS) {
            c.saveLayer(bounds, paint)
            translate(ox * r, oy + oyy * r) { this@drawWithContent.drawContent() }
            c.restore()
        }
    }
    drawContent()
}

private val SHADOW_TAPS = listOf(0f to 0f, 1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)
