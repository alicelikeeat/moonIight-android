package com.moonlight.feed.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.MoonImage
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CamMode { PHOTO, VIDEO }
enum class CamFlash { OFF, AUTO, ON }

data class CameraUiState(
    val mode: CamMode = CamMode.PHOTO,
    val flash: CamFlash = CamFlash.OFF,
    val front: Boolean = false,
    val recording: Boolean = false,
    val recordMs: Long = 0,
    val zoom: Float = 1f,
    val hasFlash: Boolean = true,
    val canHoldToRecord: Boolean = true,
    val audio: Boolean = true,
    val lastThumb: Any? = null,
    val busy: Boolean = false,
)

class CameraCallbacks(
    val onClose: () -> Unit = {},
    val onFlash: () -> Unit = {},
    val onFlip: () -> Unit = {},
    val onMode: (CamMode) -> Unit = {},
    val onShutter: () -> Unit = {},
    val onHoldStart: () -> Unit = {},
    val onHoldEnd: () -> Unit = {},
    val onThumb: () -> Unit = {},
    val onFocus: (Offset) -> Unit = {},
    val onPinch: (Float) -> Unit = {},
    val onZoomPreset: () -> Unit = {},
)

fun formatRecord(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

/** Minimal Apple-like camera: full-bleed preview, glass controls. [preview] is the CameraX PreviewView (or a placeholder in renders). */
@Composable
fun CameraContent(ui: CameraUiState, cb: CameraCallbacks, flashPulse: Int = 0, preview: @Composable () -> Unit) {
    val glass = MediaGlass
    val view = LocalView.current
    val density = LocalDensity.current
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableStateOf(0) }
    var zoomShownAt by remember { mutableStateOf(0L) }
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flashPulse) { if (flashPulse > 0) { flashAlpha.snapTo(0.85f); flashAlpha.animateTo(0f, tween(260)) } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize()) { preview() }
        // gestures: tap-to-focus, pinch zoom
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { p -> focusAt = p; focusTick++; Haptics.light(view); cb.onFocus(p) })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) { cb.onPinch(zoom); zoomShownAt = System.currentTimeMillis() } }
                }
        )
        focusAt?.let { p -> FocusRing(p, focusTick) { focusAt = null } }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flashAlpha.value }.background(Color.White))

        // ---------- top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CamCircle(Icons.Outlined.Close, "关闭", cb.onClose, enabled = !ui.recording)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(ui.recording, enter = fadeIn() + scaleIn(spring(0.6f, 500f)), exit = fadeOut() + scaleOut()) { RecordPill(ui.recordMs) }
                androidx.compose.animation.AnimatedVisibility(!ui.recording && ui.mode == CamMode.VIDEO && !ui.audio, enter = fadeIn(), exit = fadeOut()) {
                    Row(Modifier.glass(CircleShape, glass, blurRadius = 18.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mic, null, tint = Color(0xFFFFB86B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("无麦克风权限 · 静音录制", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (ui.hasFlash) {
                CamCircle(
                    when (ui.flash) { CamFlash.OFF -> Icons.Outlined.FlashOff; CamFlash.AUTO -> Icons.Outlined.FlashAuto; CamFlash.ON -> Icons.Outlined.FlashOn },
                    "闪光灯", cb.onFlash,
                    tint = if (ui.flash == CamFlash.OFF) Color.White else Color(0xFFFFD45E),
                )
            } else Spacer(Modifier.size(44.dp))
        }

        // ---------- bottom controls
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f))))
                .navigationBarsPadding().padding(bottom = 18.dp, top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // zoom pill (tap toggles 1× / 2×)
            val zoomColor by animateColorAsState(if (ui.zoom > 1.05f) Color(0xFFFFD45E) else Color.White, label = "zc")
            Text(
                if (ui.zoom >= 10f) "%.0f×".format(ui.zoom) else "%.1f×".format(ui.zoom),
                color = zoomColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                
                modifier = Modifier.moonPress(cb.onZoomPreset, pressedScale = 0.88f, highlightShape = null)
                    .glass(CircleShape, glass, blurRadius = 18.dp).padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.animation.AnimatedVisibility(!ui.recording, enter = fadeIn(), exit = fadeOut()) {
                ModeSwitch(ui.mode) { m -> Haptics.tick(view); cb.onMode(m) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Thumb(ui.lastThumb, ui.recording, cb.onThumb)
                }
                Shutter(ui, cb)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    var turns by remember { mutableFloatStateOf(0f) }
                    val rot by animateFloatAsState(turns, spring(0.7f, 300f), label = "flip")
                    Box(
                        Modifier.size(48.dp)
                            .moonPress({ turns += 180f; cb.onFlip() }, pressedScale = 0.86f, highlightShape = null, enabled = !ui.recording)
                            .glass(CircleShape, glass, blurRadius = 18.dp)
                            .graphicsLayer { alpha = if (ui.recording) 0.35f else 1f },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Cameraswitch, "翻转", tint = Color.White, modifier = Modifier.size(22.dp).graphicsLayer { rotationY = rot })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    ui.recording -> "再次轻点停止"
                    ui.mode == CamMode.PHOTO && ui.canHoldToRecord -> "轻点拍照 · 按住录像"
                    ui.mode == CamMode.PHOTO -> "轻点拍照"
                    else -> "轻点开始录像"
                },
                color = Color.White.copy(0.65f), style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CamCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, tint: Color = Color.White, enabled: Boolean = true) {
    Box(
        Modifier.size(44.dp).moonPress(onClick, pressedScale = 0.86f, highlightShape = null, enabled = enabled)
            .glass(CircleShape, MediaGlass, blurRadius = 18.dp).graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun RecordPill(ms: Long) {
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) { while (true) { blink.animateTo(0.25f, tween(520)); blink.animateTo(1f, tween(520)) } }
    Row(
        Modifier.glass(CircleShape, MediaGlass, blurRadius = 18.dp, tint = Color(0x66D7263D)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = blink.value }.background(Color(0xFFFF3B30), CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(formatRecord(ms), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun ModeSwitch(mode: CamMode, onMode: (CamMode) -> Unit) {
    val items = listOf(CamMode.PHOTO to "照片", CamMode.VIDEO to "视频")
    BoxWithConstraints(Modifier.width(168.dp).height(36.dp).glass(CircleShape, MediaGlass, blurRadius = 18.dp).padding(3.dp)) {
        val w = maxWidth / 2
        val x by animateDpAsState(if (mode == CamMode.PHOTO) 0.dp else w, spring(0.72f, 450f), label = "mode")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().background(Color.White.copy(0.22f), CircleShape))
        Row(Modifier.fillMaxSize()) {
            items.forEach { (m, label) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().moonPress({ onMode(m) }, pressedScale = 0.92f, highlightShape = null, hapticOnClick = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (m == mode) Color(0xFFFFD45E) else Color.White.copy(0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Shutter(ui: CameraUiState, cb: CameraCallbacks) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 600f), label = "sh")
    val video = ui.mode == CamMode.VIDEO || ui.recording
    val coreSize by animateDpAsState(if (ui.recording) 30.dp else 62.dp, spring(0.65f, 420f), label = "core")
    val coreCorner by animateDpAsState(if (ui.recording) 8.dp else 31.dp, spring(0.65f, 420f), label = "corner")
    val coreColor by animateColorAsState(if (video) Color(0xFFFF3B30) else Color.White, label = "cc")
    Box(
        Modifier.size(78.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .border(4.dp, Color.White, CircleShape)
            .pointerInput(ui.mode, ui.recording, ui.canHoldToRecord, ui.busy) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        if (holding) { holding = false; cb.onHoldEnd() }
                    },
                    onTap = { if (!ui.busy) { Haptics.confirm(view); cb.onShutter() } },
                    onLongPress = {
                        if (ui.mode == CamMode.PHOTO && ui.canHoldToRecord && !ui.recording && !ui.busy) {
                            holding = true; Haptics.open(view); cb.onHoldStart()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(coreSize).clip(RoundedCornerShape(coreCorner)).background(coreColor))
    }
}

@Composable
private fun Thumb(model: Any?, recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .moonPress(onClick, pressedScale = 0.88f, highlightShape = null, enabled = model != null && !recording)
            .clip(RoundedCornerShape(12.dp))
            .glass(RoundedCornerShape(12.dp), MediaGlass, blurRadius = 18.dp)
            .border(1.5.dp, Color.White.copy(0.85f), RoundedCornerShape(12.dp))
            .graphicsLayer { alpha = if (recording) 0.35f else 1f },
    ) {
        if (model != null) MoonImage(model, Modifier.fillMaxSize())
    }
}

@Composable
private fun FocusRing(p: Offset, tick: Int, onDone: () -> Unit) {
    val density = LocalDensity.current
    val s = remember(tick) { Animatable(1.45f) }
    val a = remember(tick) { Animatable(1f) }
    LaunchedEffect(tick) {
        launch { s.animateTo(1f, spring(0.55f, 380f)) }
        delay(1100); a.animateTo(0f, tween(300)); onDone()
    }
    val size = 76.dp
    val half = with(density) { (size / 2).toPx() }
    Box(
        Modifier.offset(x = with(density) { (p.x - half).toDp() }, y = with(density) { (p.y - half).toDp() })
            .size(size).graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
            .border(1.5.dp, Color(0xFFFFD45E), RoundedCornerShape(10.dp)),
    )
}

// ------------------------------------------------------------ permission explainer

enum class PermState { ASK, DENIED, BLOCKED }

/** Glass permission explainer shown before the system dialog, and again (with 前往设置) after denial. */
@Composable
fun CameraPermissionExplainer(state: PermState, camera: Boolean, mic: Boolean, onContinue: () -> Unit, onSettings: () -> Unit, onCancel: () -> Unit, backdrop: @Composable () -> Unit = {}) {
    val c = LocalMoonColors.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }) { backdrop() }
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), MediaGlass, elevation = 24.dp, blurRadius = 30.dp)
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).background(Brush.radialGradient(listOf(Color.White, c.accent)), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = Color(0xFF15161C), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when (state) { PermState.ASK -> "允许月光使用相机"; else -> "未获得相机权限" },
                color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (state) {
                    PermState.ASK -> "拍摄的照片和视频会直接加入月光媒体库并出现在首页。录像需要麦克风来录制声音。内容只保存在本机。"
                    PermState.DENIED -> "没有相机权限就无法拍摄。你可以再次授权，或稍后从「从相册导入」添加内容。"
                    PermState.BLOCKED -> "相机权限已被关闭。请在 系统设置 › 权限 中允许「相机」（录像可选「麦克风」）。"
                },
                color = Color.White.copy(0.72f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.07f)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                PermRow(Icons.Outlined.PhotoCamera, "相机", "拍照与录像", camera)
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.12f)))
                PermRow(Icons.Outlined.Mic, "麦克风", "录像声音（可选）", mic)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (state == PermState.BLOCKED) "前往设置" else "继续",
                color = c.onAccent, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .moonPress(if (state == PermState.BLOCKED) onSettings else onContinue, highlightShape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(c.accent).padding(vertical = 14.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "暂不", color = Color.White.copy(0.75f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().moonPress(onCancel, highlightShape = RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun PermRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, granted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(sub, color = Color.White.copy(0.55f), style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (granted) Color(0xFF7FD1AE) else Color.White.copy(0.4f), modifier = Modifier.size(20.dp),
        )
    }
}
