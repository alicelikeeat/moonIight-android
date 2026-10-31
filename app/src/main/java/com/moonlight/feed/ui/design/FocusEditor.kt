package com.moonlight.feed.ui.design

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moonlight.feed.data.FocusCrop
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.data.MediaFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 调整位置: drag to move the focal point, pinch to zoom, inside a frame with the same shape as where the
 * media is shown (wide cover / round avatar). The result is a [MediaFocus] saved with that media.
 */
data class FocusRequest(
    val path: String,
    val video: Boolean,
    /** Frame height in dp at full screen width; 0 = square avatar frame. */
    val frameHeightDp: Int,
    val title: String,
    val initial: MediaFocus = MediaFocus.DEFAULT,
    /** > 0: frame with this width/height ratio (e.g. the full screen for backgrounds), scaled to fit the editor. */
    val aspect: Float = 0f,
    val onDone: (MediaFocus) -> Unit,
) {
    val avatar get() = frameHeightDp <= 0 && aspect <= 0f
}

object FocusEditing {
    var request by mutableStateOf<FocusRequest?>(null)
    fun open(r: FocusRequest) { request = r }
}

/** Frame heights matching the real layouts. */
object FocusFrames {
    const val ME_COVER = 216
    const val MOMENTS_COVER = 262
    const val PROFILE_COVER = 200
    const val AVATAR = 0
}

/** Mount once per activity; shows the editor above everything (incl. sheets) when a request is open. */
@Composable
fun FocusEditorHost() {
    val r = FocusEditing.request ?: return
    Dialog(
        onDismissRequest = { FocusEditing.request = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val src by produceState<IntSize?>(null, r.path) { value = withContext(Dispatchers.IO) { mediaSize(r.path, r.video) } }
        FocusEditorContent(r, src, onCancel = { FocusEditing.request = null }) { f ->
            FocusEditing.request = null
            r.onDone(f)
        }
    }
}

/** Source pixel size with rotation applied; art: demo paths are 3:4. Null when unknown. */
fun mediaSize(path: String, video: Boolean): IntSize? = runCatching {
    if (path.startsWith("art:")) return IntSize(ART_SRC_W.toInt(), ART_SRC_H.toInt())
    if (video) {
        val m = MediaMetadataRetriever()
        try {
            m.setDataSource(path)
            val w = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) IntSize(h, w) else IntSize(w, h)
        } finally { m.release() }
    } else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        val orient = runCatching { android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        if (orient in 5..8) IntSize(o.outHeight, o.outWidth) else IntSize(o.outWidth, o.outHeight)
    }
}.getOrNull()

@Composable
fun FocusEditorContent(r: FocusRequest, src: IntSize?, onCancel: () -> Unit, onDone: (MediaFocus) -> Unit) {
    var f by remember(r) { mutableStateOf(r.initial.clamped()) }
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().background(Color(0xFF07080C)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "取消", color = Color.White.copy(0.8f), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.moonPress(onCancel, highlightShape = CircleShape).padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Text(
                r.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            CompositionLocalMedia {
                GlassPill("完成", { onDone(f) }, icon = Icons.Outlined.Check, accent = true)
            }
        }
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val frameW = when { r.avatar -> 280.dp; r.aspect > 0f -> maxWidth * 0.62f; else -> maxWidth }
            val frameH = when { r.avatar -> 280.dp; r.aspect > 0f -> frameW / r.aspect; else -> r.frameHeightDp.dp }
            val shape: Shape = when { r.avatar -> RoundedCornerShape(30); r.aspect > 0f -> RoundedCornerShape(22.dp); else -> RoundedCornerShape(0.dp) }
            val dstW = with(density) { frameW.toPx() }; val dstH = with(density) { frameH.toPx() }
            Box(
                Modifier
                    .size(frameW, frameH)
                    .clip(shape)
                    .background(Color.White.copy(0.05f))
                    .pointerInput(r, src) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val sw = (src?.width ?: 0).toFloat(); val sh = (src?.height ?: 0).toFloat()
                            f = FocusCrop.zoom(f, zoom).let { z -> if (sw > 0f) FocusCrop.pan(z, pan.x, pan.y, sw, sh, dstW, dstH) else z }
                        }
                    },
            ) {
                ProfileMedia(r.path, r.video, Modifier.fillMaxSize(), focus = f) { }
                // rule-of-thirds guide
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1f)
                        drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
                    }
                    drawRect(Color.White.copy(alpha = 0.6f), style = Stroke(2f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "拖动调整位置 · 双指缩放" + if (r.video) " · 视频同样适用" else "",
            color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(14.dp))
        CompositionLocalMedia {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPill("默认（偏上）", { f = MediaFocus.DEFAULT }, icon = Icons.Outlined.RestartAlt)
                GlassPill("居中", { f = MediaFocus(0.5f, 0.5f, f.scale) }, icon = Icons.Outlined.CenterFocusStrong)
                Text("${(f.scale * 100).toInt()}%", color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.weight(1.4f))
    }
}

@Composable
private fun CompositionLocalMedia(content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalMoonColors provides MediaGlass, LocalHazeState provides null) { content() }
