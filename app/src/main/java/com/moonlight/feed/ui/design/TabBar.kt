package com.moonlight.feed.ui.design

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.widthIn

import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.outlined.Person

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moonlight.feed.ui.theme.LocalMoonColors

data class TabSpec(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

val MoonTabs = listOf(
    TabSpec("首页", Icons.Outlined.Home, Icons.Filled.Home),
    TabSpec("库", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    TabSpec("发现", Icons.Outlined.Explore, Icons.Filled.Explore),
    TabSpec("设置", Icons.Outlined.Settings, Icons.Filled.Settings),
    TabSpec("朋友圈", Icons.Outlined.Camera, Icons.Filled.Camera),
    TabSpec("消息", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    TabSpec("我", Icons.Outlined.Person, Icons.Filled.Person),
)

/** v3.0 visual order (2 + ● + 2): 首页 · 发现 · [●] · 朋友圈 · 我. -1 = capture. */
val TAB_SLOTS = listOf(0, 2, -1, 4, 6)

/** 按键背景 ids of the tab-bar items by tab index. */
val TAB_SKIN_IDS = mapOf(0 to "tab.home", 2 to "tab.discover", 4 to "tab.moments", 6 to "tab.me")
const val TAB_ME = 6

/** Pages without their own slot light up their parent: 库 / 设置 live under 我, 消息 under 朋友圈. */
fun tabOwner(tab: Int) = when (tab) { 1, 3 -> TAB_ME; 5 -> 4; else -> tab }

/** Where system back goes from [tab] (null = leave the app / let the page handle it). */
fun tabBack(tab: Int): Int? = when (tab) { 0 -> null; 1, 3 -> TAB_ME; 5 -> 4; else -> 0 }

/** Slot of a tab index. */
fun tabSlot(tab: Int) = TAB_SLOTS.indexOf(tabOwner(tab)).coerceAtLeast(0)

/**
 * Floating frosted capsule tab bar: 首页 · 发现 · [●] · 朋友圈 · 我.
 * Spring-animated selection pill; long-press tabs = quick actions; centre = capture (tap) / menu (long-press).
 */
@Composable
fun GlassTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    onLongPress: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier,
    onCapture: () -> Unit = {},
    onCaptureLong: (Rect) -> Unit = {},
    badges: Map<Int, Int> = emptyMap(),
) {
    val c = LocalMoonColors.current
    val barSkin = rememberButtonSkin("tab.bar")
    val anchors = remember { List(MoonTabs.size) { AnchorState() } }
    val captureAnchor = remember { AnchorState() }
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .skinOr(barSkin, CircleShape) { glass(CircleShape, c, elevation = 20.dp, blurRadius = 30.dp) }
                .padding(6.dp)
        ) {
            val itemW = maxWidth / TAB_SLOTS.size
            val x by animateDpAsState(itemW * tabSlot(selected), spring(dampingRatio = 0.72f, stiffness = 420f), label = "tabx")
            Box(
                Modifier
                    .offset(x = x)
                    .width(itemW)
                    .fillMaxHeight()
                    .background(c.accent.copy(alpha = if (c.dark) 0.16f else 0.18f), CircleShape)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                for (slot in TAB_SLOTS.indices) {
                    if (TAB_SLOTS[slot] == -1) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CaptureButton(Modifier.anchor(captureAnchor), onCapture) { onCaptureLong(captureAnchor.rect) }
                        }
                        continue
                    }
                    val i = TAB_SLOTS[slot]
                    TabItem(
                        spec = MoonTabs[i],
                        selected = i == tabOwner(selected),
                        badge = badges[i] ?: 0,
                        modifier = Modifier.weight(1f).fillMaxHeight().anchor(anchors[i]),
                        onClick = { onSelect(i) },
                        onLongClick = { onLongPress(i, anchors[i].rect) },
                        skinId = TAB_SKIN_IDS[i],
                    )
                }
            }
        }
    }
}

/** Round glass shutter: frosted ring + moonlight core. Spring press, haptic on tap; long-press = menu. */
@Composable
fun CaptureButton(modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LocalMoonColors.current
    val skin = rememberButtonSkin("tab.capture")
    if (skin != null) {
        Box(
            modifier.size(52.dp).moonPress(onClick, onLongClick, pressedScale = 0.86f, highlightShape = null)
                .shadow(14.dp, CircleShape, ambientColor = c.accent.copy(0.5f), spotColor = c.accent.copy(0.55f))
                .skinFill(skin, CircleShape).border(1.5.dp, Color.White.copy(0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.CameraAlt, "拍摄", tint = skin.ink, modifier = Modifier.size(22.dp)) }
        return
    }
    Box(
        modifier
            .size(52.dp)
            .moonPress(onClick, onLongClick, pressedScale = 0.86f, highlightShape = null)
            .shadow(14.dp, CircleShape, ambientColor = c.accent.copy(0.5f), spotColor = c.accent.copy(0.55f))
            .glass(CircleShape, c, blurRadius = 20.dp, tint = c.accent.copy(alpha = 0.10f))
            .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(0.75f), c.accent.copy(0.35f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, c.accent, c.accent.copy(alpha = 0.85f)), radius = 60f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CameraAlt, "拍摄", tint = c.onAccent.copy(alpha = 0.78f), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun TabItem(spec: TabSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit, badge: Int = 0, skinId: String? = null) {
    val c = LocalMoonColors.current
    val skin = rememberButtonSkin(skinId)
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) { bounce.snapTo(0.78f); bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 600f)) }
    }
    Column(
        modifier.moonPress(onClick, onLongClick, pressedScale = 0.88f, highlightShape = null)
            .then(if (skin != null) Modifier.padding(horizontal = 3.dp, vertical = 1.dp).skinFill(skin, CircleShape) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box {
            Icon(
                if (selected) spec.selectedIcon else spec.icon,
                spec.label,
                tint = if (skin != null) skin.ink.copy(alpha = if (selected) 1f else 0.82f) else if (selected) c.accent else c.textDim,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
            )
            if (badge > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-4).dp).height(15.dp).widthIn(min = 15.dp)
                    .clip(CircleShape).background(Color(0xFFFF4D5E)).padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 99) "99+" else "$badge", color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1) }
        }
        Spacer(Modifier.height(2.dp))
        Text(spec.label, style = MaterialTheme.typography.labelSmall, color = if (skin != null) skin.ink.copy(alpha = if (selected) 1f else 0.82f) else if (selected) c.text else c.textDim, maxLines = 1, softWrap = false,
            fontWeight = if (skin != null && selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
    }
}
