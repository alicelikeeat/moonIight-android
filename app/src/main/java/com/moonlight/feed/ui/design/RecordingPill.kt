package com.moonlight.feed.ui.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlight.feed.data.record.RecState
import com.moonlight.feed.data.record.RecordingMachine
import com.moonlight.feed.data.record.ScreenRecorder
import kotlinx.coroutines.delay

/** Live 录屏 pill: shown in every overlay host while recording. */
@Composable
internal fun BoxScope.RecordingPillHost() {
    val st by ScreenRecorder.state.collectAsState()
    if (st !is RecState.Recording && st != RecState.Stopping) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val ms = (st as? RecState.Recording)?.let { now - it.startedAt } ?: 0L
    RecordingPill(ms, stopping = st == RecState.Stopping) { ScreenRecorder.stop() }
}

/** ⏹ + timer, draggable out of the way. */
@Composable
fun BoxScope.RecordingPill(elapsedMs: Long, stopping: Boolean = false, onStop: () -> Unit = {}) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    val inspect = androidx.compose.ui.platform.LocalInspectionMode.current
    val blink = if (inspect) 1f else rememberInfiniteTransition(label = "rec").animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot").value
    Row(
        Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp)
            .offset { IntOffset(drag.x.toInt(), drag.y.toInt()) }
            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); drag += d } }
            .background(Color(0xE6181A20), CircleShape).border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .moonPress(onStop, highlightShape = CircleShape)
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).background(Color(0xFFFF453A), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).background(Color.White, RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(7.dp).background(Color(0xFFFF453A).copy(alpha = blink), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (stopping) "正在保存…" else RecordingMachine.timer(elapsedMs), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
