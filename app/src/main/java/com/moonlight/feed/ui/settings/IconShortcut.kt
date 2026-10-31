package com.moonlight.feed.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.moonlight.feed.MainActivity
import com.moonlight.feed.data.FocusCrop
import com.moonlight.feed.data.MediaFocus
import java.io.File

/**
 * 用照片做桌面图标: a pinned home-screen shortcut (launcher dependent) whose icon is a photo — or one chosen frame
 * of a video — framed with the reposition editor. Android has no animated launcher icons, so video = a still frame.
 */
object IconShortcut {
    /** Adaptive icon layer is 108dp; launchers show the centre 72dp, i.e. this fraction. */
    const val VISIBLE = 72f / 108f

    /** Where the source goes in an adaptive bitmap of [size] px so the editor's square frame = the visible window. Pure. */
    fun layerPlacement(srcW: Float, srcH: Float, size: Float, f: MediaFocus): com.moonlight.feed.data.Placement {
        val win = size * VISIBLE
        val margin = (size - win) / 2f
        val p = FocusCrop.place(srcW, srcH, win, win, f)
        return com.moonlight.feed.data.Placement(margin + p.left, margin + p.top, p.width, p.height)
    }

    fun render(src: Bitmap, f: MediaFocus, size: Int = 432, bg: Int = 0xFF262761.toInt()): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        val p = layerPlacement(src.width.toFloat(), src.height.toFloat(), size.toFloat(), f)
        c.drawBitmap(src, null, RectF(p.left, p.top, p.right, p.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun dir(ctx: Context) = File(ctx.filesDir, "icons").also { it.mkdirs() }

    /** Copies a picked photo into app storage (the editor needs a file). */
    fun copyPhoto(ctx: Context, uri: Uri): File? = runCatching {
        val out = File(dir(ctx), "photo-${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: return null
        out.takeIf { it.length() > 0 }
    }.getOrNull()

    fun duration(ctx: Context, uri: Uri): Long = runCatching {
        MediaMetadataRetriever().run { try { setDataSource(ctx, uri); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { release() } }
    }.getOrDefault(0L)

    fun frameAt(ctx: Context, uri: Uri, ms: Long, maxSide: Int = 720): Bitmap? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(ctx, uri)
                if (android.os.Build.VERSION.SDK_INT >= 27) getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
                else getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally { release() }
        }
    }.getOrNull()

    fun saveFrame(ctx: Context, bmp: Bitmap): File? = runCatching {
        val out = File(dir(ctx), "frame-${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        out
    }.getOrNull()

    fun supported(ctx: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)

    /** Asks the launcher to pin a shortcut that opens 月光 with [path] framed by [f]. Returns false if unsupported. */
    fun pin(ctx: Context, path: String, f: MediaFocus, label: String = "月光"): Boolean {
        if (!supported(ctx)) return false
        val src = BitmapFactory.decodeFile(path) ?: return false
        val icon = render(src, f)
        val info = ShortcutInfoCompat.Builder(ctx, "photo-icon-${System.currentTimeMillis()}")
            .setShortLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
}

/** Video → pick the frame to use (slider over the clip, live preview). */
@androidx.compose.runtime.Composable
fun FramePicker(uri: Uri, onPick: (Bitmap) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = com.moonlight.feed.ui.theme.LocalMoonColors.current
    val dur = androidx.compose.runtime.remember(uri) { IconShortcut.duration(ctx, uri).coerceAtLeast(1L) }
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val frame by androidx.compose.runtime.produceState<Bitmap?>(null, uri, (pos * 40).toInt()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconShortcut.frameAt(ctx, uri, (pos * dur).toLong()) } ?: value
    }
    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.size(220.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(30)).background(c.hairline),
        ) {
            frame?.let {
                androidx.compose.foundation.Image(it.asImageBitmap(), null, androidx.compose.ui.Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
        androidx.compose.material3.Text("拖动选择一帧（安卓不支持动态图标）", color = c.textDim, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Slider(pos, { pos = it })
        com.moonlight.feed.ui.design.GlassPill("用这一帧", { frame?.let(onPick) }, accent = true)
    }
}
