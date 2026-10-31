package com.moonlight.feed.data

/**
 * Where a cropped photo/video sits inside its frame (avatar, 朋友圈 cover, profile background).
 * [x]/[y] are alignment fractions of the overflow (0 = left/top edge shown, 1 = right/bottom edge shown),
 * [scale] ≥ 1 is extra zoom on top of "cover". Default biases to the upper third so faces/heads survive.
 */
data class MediaFocus(val x: Float = 0.5f, val y: Float = DEFAULT_Y, val scale: Float = 1f) {
    fun clamped() = MediaFocus(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), scale.coerceIn(1f, MAX_SCALE))
    fun encode(): String = "%.4f,%.4f,%.4f".format(java.util.Locale.ROOT, x, y, scale)

    companion object {
        const val DEFAULT_Y = 0.3f
        const val MAX_SCALE = 4f
        val DEFAULT = MediaFocus()
        fun decode(s: String?): MediaFocus? = s?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.size == 3 }?.let { MediaFocus(it[0], it[1], it[2]).clamped() }
    }
}

/** Placed rectangle of the source inside the frame (frame pixel space). */
data class Placement(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
}

/** One crop geometry for both images (Compose) and videos (TextureView matrix). Pure; unit-tested. */
object FocusCrop {
    fun coverScale(srcW: Float, srcH: Float, dstW: Float, dstH: Float): Float = maxOf(dstW / srcW, dstH / srcH)

    fun place(srcW: Float, srcH: Float, dstW: Float, dstH: Float, f: MediaFocus): Placement {
        if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) return Placement(0f, 0f, dstW, dstH)
        val c = f.clamped()
        val k = coverScale(srcW, srcH, dstW, dstH) * c.scale
        val w = srcW * k; val h = srcH * k
        return Placement(-(w - dstW) * c.x, -(h - dstH) * c.y, w, h)
    }

    /** Drag in the editor: moving the finger right/down reveals more of the left/top. */
    fun pan(f: MediaFocus, dx: Float, dy: Float, srcW: Float, srcH: Float, dstW: Float, dstH: Float): MediaFocus {
        val p = place(srcW, srcH, dstW, dstH, f)
        val ox = p.width - dstW; val oy = p.height - dstH
        return f.copy(x = if (ox > 0.5f) f.x - dx / ox else f.x, y = if (oy > 0.5f) f.y - dy / oy else f.y).clamped()
    }

    fun zoom(f: MediaFocus, factor: Float): MediaFocus = f.copy(scale = f.scale * factor).clamped()
}

/** 视频显示: 智能 / 完整显示 / 铺满. Pure; unit-tested. */
enum class VideoDisplay(val label: String) { SMART("智能"), FIT("完整显示"), FILL("铺满") }

object VideoFit {
    /** Fraction of the video lost (per axis) when filling the view. */
    fun cropFraction(videoW: Float, videoH: Float, viewW: Float, viewH: Float): Float {
        if (videoW <= 0f || videoH <= 0f || viewW <= 0f || viewH <= 0f) return 1f
        val va = videoW / videoH; val sa = viewW / viewH
        return 1f - minOf(va, sa) / maxOf(va, sa)
    }

    const val SMART_MAX_CROP = 0.08f

    /**
     * true → fill (crop), false → fit (letterbox). Smart never cuts real content: landscape on a portrait screen fits width;
     * a near-screen-ratio portrait video may fill only when that crops ≤ 8%. A per-item override (pinch) wins.
     */
    fun fill(mode: VideoDisplay, videoW: Float, videoH: Float, viewW: Float, viewH: Float, override: Boolean? = null): Boolean {
        override?.let { return it }
        return when (mode) {
            VideoDisplay.FIT -> false
            VideoDisplay.FILL -> true
            VideoDisplay.SMART -> cropFraction(videoW, videoH, viewW, viewH) <= SMART_MAX_CROP
        }
    }

    fun of(name: String?) = VideoDisplay.entries.firstOrNull { it.name == name } ?: VideoDisplay.SMART
}

/** 菜单背景 for menus (radial + list). Pure; unit-tested. */
/** 长按菜单选择方式: 点击 = long-press opens, release keeps it open, tap selects · 拖动 = hold, slide, release selects. */
enum class MenuSelect(val label: String) {
    TAP("点击"), DRAG("拖动");
    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: TAP }
}

enum class MenuBackdrop(val label: String, val blurDp: Float, val dim: Float) {
    CLEAR("透明", 0f, 0f), LIGHT("轻度模糊", 5f, 0.5f), BLUR("模糊", 24f, 1f);

    /** Labels need a text shadow when nothing behind them is blurred/dimmed much. */
    val labelShadow: Boolean get() = dim < 1f

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: BLUR }
}
