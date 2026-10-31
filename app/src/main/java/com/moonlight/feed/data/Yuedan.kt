package com.moonlight.feed.data

import kotlin.math.abs
import kotlin.math.hypot

/**
 * v6.0 / 月淡 v1.43 gesture contract.
 *
 * A stroke is one finger-down. Tap and long-press are mutually exclusive outcomes of that stroke.
 * Movement past the slop cancels a hold: on the tab bar a horizontal move is a drag-switch,
 * anywhere else it stays a scroll. A list that is still dragging or decelerating never starts a hold.
 * 水滴 is a presentation the user opts into; it is not the default.
 */
enum class TouchOrigin { CONTENT, TAB_BAR }

enum class PointerDecision { PENDING, TAP, LONG_PRESS, SCROLL, BAR_DRAG }

object YuedanGesture {
    const val LONG_PRESS_MS = 480L
    const val SLOP_DP = 18f

    fun route(
        origin: TouchOrigin,
        dx: Float,
        dy: Float,
        elapsedMs: Long,
        slopPx: Float,
        listBusy: Boolean,
        longPressMs: Long = LONG_PRESS_MS,
    ): PointerDecision {
        val moved = hypot(dx.toDouble(), dy.toDouble()).toFloat() > slopPx
        if (origin == TouchOrigin.TAB_BAR) {
            if (moved && abs(dx) >= abs(dy)) return PointerDecision.BAR_DRAG
            if (moved) return PointerDecision.SCROLL
            if (elapsedMs >= longPressMs) return PointerDecision.LONG_PRESS
            return PointerDecision.PENDING
        }
        if (listBusy) return PointerDecision.SCROLL
        if (moved) return PointerDecision.SCROLL
        if (elapsedMs >= longPressMs) return PointerDecision.LONG_PRESS
        return PointerDecision.PENDING
    }

    /** Lift while the stroke is still undecided. A long-press is not also a tap. */
    fun onUp(decision: PointerDecision): PointerDecision =
        if (decision == PointerDecision.PENDING) PointerDecision.TAP else decision

    fun tabIndexAt(x: Float, width: Float, count: Int): Int {
        if (count <= 0 || width <= 0f) return 0
        return (x / width * count).toInt().coerceIn(0, count - 1)
    }
}

/** How a long-press ring is presented. [DROPLET] is selectable and off unless the user picks it. */
enum class HoldPresent(val label: String, val short: String, val hint: String) {
    POPOVER("弹出菜单", "弹出", "圆环从按住处展开，松手后保持，再点选"),
    TRACK("跟手圆形玻璃", "跟手", "玻璃圆环跟着移动的手指"),
    DROPLET("水滴融合", "水滴", "可选效果，默认关闭。按钮像水滴从手指处融合展开"),
    BUBBLE("玻璃气泡分离", "气泡", "操作按键从长按位置的一侧依次弹出");

    companion object {
        const val DEFAULT = "POPOVER"
        fun of(raw: String?) = entries.firstOrNull { it.name == raw?.trim() } ?: POPOVER

        /** Pixels the bubbles travel from, matching the iOS edge-avoidance rule. */
        fun bubbleStart(cx: Float, cy: Float, width: Float, height: Float): Pair<Float, Float> = when {
            cy > height * 0.62f -> 0f to -48f
            cx > width * 0.66f -> -48f to 0f
            cx < width * 0.34f -> 48f to 0f
            else -> 48f to -28f
        }
    }
}

enum class TabDock(val label: String, val hint: String) {
    CIRCLES("独立圆形", "五个分开的玻璃圆键；从底栏开始的横向拖动切换页面"),
    SLIDER("滑动底栏", "一条毛玻璃底栏，横向拖动直接切页");

    companion object {
        const val DEFAULT = "CIRCLES"
        fun of(raw: String?) = entries.firstOrNull { it.name == raw?.trim() } ?: CIRCLES
    }
}

object DiscoverLayout {
    const val MIN_SCALE = 0.75f
    const val MAX_SCALE = 1.35f
    fun columns(v: Int) = if (v == 1) 1 else 2
    fun toggle(v: Int) = if (columns(v) == 1) 2 else 1
    fun scale(v: Float) = v.coerceIn(MIN_SCALE, MAX_SCALE)
    fun percent(v: Float) = (scale(v) * 100f).toInt().coerceIn(75, 135)
    fun cardHeightDp(columns: Int, scale: Float) = (if (columns(columns) == 1) 220f else 155f) * scale(scale)
}

object RandomBatch {
    val CHOICES = listOf(10, 15, 20)
    fun normalize(v: Int) = if (v in CHOICES) v else CHOICES.minBy { kotlin.math.abs(it - v) }
    fun <T> pick(items: List<T>, count: Int): List<T> {
        val n = normalize(count)
        if (items.size <= n) return items
        return items.shuffled().take(n)
    }
}

object VirtualCopy {
    val COMMENTS = listOf("这个我也喜欢。", "刚刚看完了。", "转给朋友看看。")
    private val TEMPLATES = listOf("刚看到「%s」，想留在这里。", "今天的一条：%s", "%s · 先留一下")

    fun intervalMinutes(v: Int) = when {
        v < 15 -> 15
        else -> v.coerceAtMost(24 * 60)
    }

    fun local(title: String, author: String): String {
        val t = title.trim().ifBlank { "一条新内容" }.take(40)
        val line = TEMPLATES.random().format(t)
        return if (author.isBlank()) line else "$line — ${author.take(24)}"
    }

    fun comment() = COMMENTS.random()
}
